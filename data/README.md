# Data

로컬 데이터 저장 위치입니다.

```text
raw_mat/      다운로드한 원본 MAT 데이터
jsonl/        Node-RED가 읽는 변환 JSONL
raw_windows/  Spring Boot가 저장할 원본 window JSON
sample/       Git에 올릴 수 있는 작은 샘플 데이터
```

`raw_mat`, `jsonl`, `raw_windows`는 용량이 커질 수 있어 Git에서 제외합니다.
따라서 저장소를 새로 clone하면 이 폴더들이 비어 있고, 아래 절차로 직접 채워야 합니다.

## 데이터셋 출처

이 프로젝트는 아래 공개 데이터셋의 **subset 3**을 사용합니다.

```text
Multi-domain vibration dataset with various bearing types
under compound machine fault scenarios: subset 3 (tapered roller bearing)

저자    Seongjae Lee, Taewan Kim, Taehyoun Kim (University of Seoul)
공개일  2024-07-15  (version 1)
DOI     10.17632/2cygy6y4rk.1
URL     https://data.mendeley.com/datasets/2cygy6y4rk/1
라이선스 CC BY 4.0
베어링  NTN 30204 tapered roller bearing
```

데이터셋은 베어링 종류별로 3개 subset으로 나뉘어 있습니다. 이 프로젝트는 **subset 3만** 사용합니다.

`ai-api/app/models/model.pkl`도 같은 subset 3으로 학습되었습니다
(`scripts/train_bearing_model.py` 참고).

## 배치 방법

내려받은 `SamplingRate_16000` 폴더를 `data/raw_mat/` 아래에 그대로 둡니다.

```text
data/raw_mat/
└── SamplingRate_16000/
    ├── RotatingSpeed_600/
    ├── RotatingSpeed_800/
    ├── RotatingSpeed_1000/
    ├── RotatingSpeed_1200/     <- 재생에 사용
    ├── RotatingSpeed_1400/
    └── RotatingSpeed_1600/
```

회전수 폴더마다 MAT 파일 32개가 들어 있습니다 (회전체 상태 8종 x 베어링 상태 4종).
16 kHz 전체는 192개 파일, 약 3.6 GB입니다.

### 파일명 규칙

```text
H_IR_16_30204_1200.mat
│  │  │   │     └─ 회전 속도 (rpm)
│  │  │   └─────── 베어링 모델
│  │  └─────────── 샘플링 레이트 (kHz)
│  └────────────── 베어링 상태  H=정상 B=볼 IR=내륜 OR=외륜
└───────────────── 회전체 상태  H=정상 L=풀림 M1~M3=미스얼라인먼트 U1~U3=불평형
```

### MAT 파일 구조

```text
Data         raw 진동 신호 (1,280,000 샘플 = 16 kHz x 80초)
Spectrogram  미리 계산된 스펙트로그램 (dB 스케일)
STFTFreq     스펙트로그램 주파수 벡터
STFTTime     스펙트로그램 시간 벡터
```

주의: 학습과 추론 모두 `Data`만 사용합니다. `Spectrogram` 필드는 dB 스케일이라
운영 FastAPI가 raw 신호에서 만드는 선형 스펙트로그램과 단위가 달라, 그대로 학습에 쓰면
추론 시점과 어긋납니다.

## 재생 파일 생성

배치가 끝나면 MAT을 JSONL window로 변환합니다.

```bash
python -m pip install -r scripts/requirements.txt
bash scripts/generate_model_replay_jsonl.sh
```

생성되는 파일은 3개이며, Node-RED flow가 이 경로를 그대로 참조합니다.

```text
data/jsonl/MOTOR_001_H_H_16_30204_1200_32000_79.jsonl    정상
data/jsonl/MOTOR_002_H_IR_16_30204_1200_32000_79.jsonl   베어링 내륜 결함
data/jsonl/MOTOR_003_U1_H_16_30204_1200_32000_79.jsonl   회전체 불평형
```

각 파일은 79개 window(window 32,000 샘플 = 2초, stride 16,000)로 약 24 MB입니다.

## 인용

이 데이터셋을 사용한 결과물을 공개할 때는 원저작자를 표기해야 합니다 (CC BY 4.0).

```text
Lee, Seongjae; Kim, Taewan; Kim, Taehyoun (2024),
"Multi-domain vibration dataset with various bearing types under compound
machine fault scenarios: subset 3 (tapered roller bearing)",
Mendeley Data, V1, doi: 10.17632/2cygy6y4rk.1
```
