"""Train the fault classification model from raw MAT signals.

핵심 원칙: 전처리를 직접 구현하지 않고 ai-api 추론 코드를 그대로 import 한다.
MAT 파일 안의 Spectrogram 필드(dB 스케일)는 추론이 만드는 선형 스펙트로그램과
단위가 달라 학습/추론 불일치의 원인이 되므로 사용하지 않는다.

컨테이너에서 실행:
  docker run --rm \
    -v "$(pwd -W)/data:/data:ro" -v "$(pwd -W)/scripts:/scripts:ro" \
    -v "$(pwd -W)/ai-api/app/models:/out" \
    smart-factory-ai-api python /scripts/train_bearing_model.py --mat-root /data/raw_mat --out-dir /out
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

import numpy as np

BEARING_LABELS = {"H": "normal", "B": "ball", "IR": "inner_race", "OR": "outer_race"}
ROTATING_LABELS = {
    "H": "normal",
    "L": "looseness",
    "M1": "misalignment", "M2": "misalignment", "M3": "misalignment",
    "U1": "unbalance", "U2": "unbalance", "U3": "unbalance",
}


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mat-root", required=True, help="MAT 파일 최상위 디렉터리")
    parser.add_argument("--out-dir", required=True, help="model.pkl 저장 위치")
    parser.add_argument("--app-dir", default="/app", help="ai-api 패키지 경로 (추론 전처리 재사용)")
    parser.add_argument("--target", choices=("bearing", "rotating"), default="bearing")
    parser.add_argument("--window-size", type=int, default=32000)
    parser.add_argument("--stride", type=int, default=32000, help="기본은 비중첩. 중첩은 상관 표본을 늘린다")
    parser.add_argument(
        "--test-speeds", default="800,1200",
        help="검증에만 쓸 회전수. 회전수 단위로 나눠야 같은 파일의 window가 양쪽에 섞이지 않는다",
    )
    parser.add_argument("--pca-components", type=int, default=64)
    parser.add_argument("--trees", type=int, default=300)
    parser.add_argument("--model-version", default=None)
    parser.add_argument("--limit-files", type=int, default=None, help="스모크 테스트용")
    return parser


def load_inference_preprocessing(app_dir: str):
    """추론이 쓰는 바로 그 전처리 함수와 설정을 가져온다."""
    sys.path.insert(0, app_dir)
    from app.core.config import settings
    from app.services.predict_service import _signal_to_spectrogram_vector

    stft_params = {
        "window": settings.fault_model_stft_window,
        "nperseg": settings.fault_model_stft_nperseg,
        "noverlap": settings.fault_model_stft_noverlap,
        "detrend": settings.fault_model_stft_detrend,
        "scaling": settings.fault_model_stft_scaling,
        "mode": settings.fault_model_stft_mode,
    }
    size = settings.fault_model_spectrogram_size
    return _signal_to_spectrogram_vector, (size, size), stft_params


def load_dataset_helpers():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from convert_mat_to_jsonl import load_signal, parse_metadata

    return load_signal, parse_metadata


def label_for(metadata: dict, target: str) -> str | None:
    if target == "bearing":
        return BEARING_LABELS.get(metadata.get("bearingCondition"))
    return ROTATING_LABELS.get(metadata.get("rotatingCondition"))


def main() -> int:
    args = build_arg_parser().parse_args()

    to_spectrogram, spectrogram_shape, stft_params = load_inference_preprocessing(args.app_dir)
    load_signal, parse_metadata = load_dataset_helpers()

    test_speeds = {int(value) for value in args.test_speeds.split(",") if value.strip()}
    mat_files = sorted(Path(args.mat_root).rglob("*.mat"))
    if args.limit_files:
        mat_files = mat_files[: args.limit_files]
    if not mat_files:
        raise SystemExit(f"No MAT files under {args.mat_root}")

    print(f"MAT files: {len(mat_files)}  target={args.target}  test speeds={sorted(test_speeds)}", flush=True)

    vectors: list[np.ndarray] = []
    labels: list[str] = []
    speeds: list[int] = []
    skipped = 0

    for index, mat_file in enumerate(mat_files, start=1):
        metadata = parse_metadata(mat_file)
        label = label_for(metadata, args.target)
        if not label or not metadata.get("samplingRate"):
            skipped += 1
            continue

        signal = load_signal(mat_file, None)
        sampling_rate = int(metadata["samplingRate"])
        speed = int(metadata["rpm"])

        for start in range(0, len(signal) - args.window_size + 1, args.stride):
            window = signal[start : start + args.window_size]
            vectors.append(
                to_spectrogram(window, sampling_rate, spectrogram_shape, stft_params).astype(np.float32)
            )
            labels.append(label)
            speeds.append(speed)

        if index % 20 == 0 or index == len(mat_files):
            print(f"  [{index}/{len(mat_files)}] windows={len(vectors)}", flush=True)

    if skipped:
        print(f"파일명 규칙과 맞지 않아 건너뜀: {skipped}개", flush=True)

    X = np.asarray(vectors, dtype=np.float32)
    y = np.asarray(labels)
    speed_array = np.asarray(speeds)
    del vectors

    is_test = np.isin(speed_array, list(test_speeds))
    X_train, y_train = X[~is_test], y[~is_test]
    X_test, y_test = X[is_test], y[is_test]

    print(f"\n입력 차원: {X.shape[1]}  학습 {len(y_train)}  검증 {len(y_test)}")
    print(f"학습 분포: {dict(sorted(Counter(y_train).items()))}")
    print(f"검증 분포: {dict(sorted(Counter(y_test).items()))}\n", flush=True)
    if len(y_test) == 0 or len(y_train) == 0:
        raise SystemExit("학습/검증 한쪽이 비었습니다. --test-speeds를 확인하세요.")

    from sklearn.decomposition import PCA
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.metrics import classification_report, confusion_matrix
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import StandardScaler

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("pca", PCA(n_components=min(args.pca_components, X_train.shape[0], X_train.shape[1]), random_state=0)),
        ("forest", RandomForestClassifier(n_estimators=args.trees, random_state=0, n_jobs=-1)),
    ])

    print("학습 중...", flush=True)
    pipeline.fit(X_train, y_train)

    predicted = pipeline.predict(X_test)
    class_names = list(pipeline.named_steps["forest"].classes_)

    print("\n=== 안 배운 회전수에서의 성능 ===")
    print(classification_report(y_test, predicted, digits=3))
    print("혼동 행렬 (행=실제, 열=예측)")
    print("      " + "  ".join(f"{name:>11}" for name in class_names))
    for name, row in zip(class_names, confusion_matrix(y_test, predicted, labels=class_names)):
        print(f"{name:>11} " + "  ".join(f"{value:>11d}" for value in row))

    import joblib

    version = args.model_version or f"spectrogram-pca-rf-{args.target}-30204-v2"
    artifact = {
        "model": pipeline,
        "model_version": version,
        "input_type": "spectrogram",
        "sampling_rate": 16000,
        "window_size": args.window_size,
        "window_seconds": args.window_size / 16000,
        "spectrogram_shape": list(spectrogram_shape),
        "stft_params": stft_params,
        "class_names": class_names,
    }

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    model_path = out_dir / "model.pkl"
    joblib.dump(artifact, model_path)
    print(f"\n저장: {model_path}  version={version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
