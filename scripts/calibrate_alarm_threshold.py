"""Measure feature distributions per condition and propose alarm thresholds.

anomalyScore의 상수(rms/0.5, (crest-3)/5 ...)는 원래 다른 베어링 기준으로 잡힌 값이라
현재 데이터셋에서는 정상 설비도 warning이 된다. 이 스크립트는 실제 분포를 재서
정상 구간을 기준선으로 하는 상수를 제안하고, 그 상수로 재분류했을 때의 결과를 검증한다.

컨테이너에서 실행:
  docker run --rm -v "$(pwd -W)/data:/data:ro" -v "$(pwd -W)/scripts:/scripts:ro" \
    smart-factory-ai-api python /scripts/calibrate_alarm_threshold.py --mat-root /data/raw_mat
"""

from __future__ import annotations

import argparse
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

FEATURES = ("rms", "peakToPeak", "crestFactor", "kurtosis")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mat-root", required=True)
    parser.add_argument("--app-dir", default="/app")
    parser.add_argument("--window-size", type=int, default=32000)
    parser.add_argument("--stride", type=int, default=32000)
    parser.add_argument("--limit-files", type=int, default=None)
    parser.add_argument(
        "--normal-percentile", type=float, default=99.0,
        help="정상 구간의 상한선으로 삼을 백분위. 이 값이 점수 0의 기준이 된다",
    )
    parser.add_argument(
        "--span-percentile", type=float, default=90.0,
        help="점수 1.0에 도달할 결함 구간 백분위. 낮을수록 빨리 포화된다",
    )
    parser.add_argument("--dump-features", default=None, help="특징값을 npz로 저장 (재튜닝용)")
    return parser


def main() -> int:
    args = build_arg_parser().parse_args()

    sys.path.insert(0, args.app_dir)
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from app.services.feature_service import calculate_features
    from convert_mat_to_jsonl import load_signal, parse_metadata

    mat_files = sorted(Path(args.mat_root).rglob("*.mat"))
    if args.limit_files:
        mat_files = mat_files[: args.limit_files]

    samples: dict[str, list[list[float]]] = defaultdict(list)
    print(f"MAT files: {len(mat_files)}", flush=True)

    for index, mat_file in enumerate(mat_files, start=1):
        metadata = parse_metadata(mat_file)
        if not metadata:
            continue
        rotating = metadata["rotatingCondition"]
        bearing = metadata["bearingCondition"]
        group = "정상(H_H)" if rotating == "H" and bearing == "H" else f"베어링={bearing}"

        signal = load_signal(mat_file, None)
        rate = int(metadata["samplingRate"])
        for start in range(0, len(signal) - args.window_size + 1, args.stride):
            features, _ = calculate_features(
                signal[start : start + args.window_size].tolist(), rate
            )
            samples[group].append([getattr(features, name) for name in FEATURES])

        if index % 40 == 0 or index == len(mat_files):
            print(f"  [{index}/{len(mat_files)}]", flush=True)

    arrays = {group: np.asarray(rows) for group, rows in samples.items()}

    print("\n=== 조건별 특징값 분포 (중앙값 / 99백분위) ===")
    header = "그룹".ljust(14) + "".join(f"{name:>22}" for name in FEATURES)
    print(header)
    for group in sorted(arrays):
        data = arrays[group]
        cells = "".join(
            f"{np.median(data[:, i]):>10.3f} /{np.percentile(data[:, i], 99):>9.3f}"
            for i in range(len(FEATURES))
        )
        print(f"{group:<14}{cells}   n={len(data)}")

    normal = arrays["정상(H_H)"]
    faulty = np.concatenate([arrays[g] for g in arrays if g != "정상(H_H)"])

    print(f"\n=== 제안 상수 (정상 {args.normal_percentile}백분위를 0점으로) ===")
    if args.dump_features:
        np.savez_compressed(args.dump_features, **{g: arrays[g] for g in arrays})
        print(f"features saved: {args.dump_features}")

    base = np.percentile(normal, args.normal_percentile, axis=0)
    span = np.percentile(faulty, args.span_percentile, axis=0) - base
    span = np.where(span <= 0, np.abs(base) * 0.5 + 1e-6, span)

    for i, name in enumerate(FEATURES):
        print(f"  {name:<14} base={base[i]:>9.4f}   span={span[i]:>9.4f}")

    def score(data: np.ndarray) -> np.ndarray:
        terms = np.clip((data - base) / span, 0.0, 1.0)
        return terms.max(axis=1)

    print("\n=== 제안 상수로 재분류한 결과 ===")
    print(f"{'그룹':<14}{'normal':>10}{'warning':>10}{'danger':>10}   중앙 점수")
    for group in sorted(arrays):
        scores = score(arrays[group])
        n_total = len(scores)
        n_danger = int((scores >= 0.8).sum())
        n_warning = int(((scores >= 0.5) & (scores < 0.8)).sum())
        n_normal = n_total - n_danger - n_warning
        print(
            f"{group:<14}{n_normal / n_total:>9.1%}{n_warning / n_total:>10.1%}"
            f"{n_danger / n_total:>10.1%}   {np.median(scores):.3f}"
        )

    print("\n=== feature_service.py에 넣을 코드 ===")
    names = ("rms", "peakToPeak", "crestFactor", "kurtosis")
    for i, name in enumerate(names):
        print(f"    {name}_score = _clamp((features.{name} - {base[i]:.4f}) / {span[i]:.4f})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
