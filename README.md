# Smart Factory Vibration Monitoring MVP

공개 베어링 진동 데이터셋을 실제 센서 데이터처럼 재생해서, 로컬에서 스마트팩토리 PHM 흐름을 검증하는 프로젝트입니다.

```text
MAT dataset
-> JSONL window replay
-> Node-RED
-> MQTT/Mosquitto
-> Spring Boot
-> FastAPI analysis
-> MySQL
-> Vue dashboard
```

## Project Structure

```text
backend/     Spring Boot API, MQTT subscriber, DB persistence
frontend/    Vue dashboard
ai-api/      FastAPI signal analysis and AI inference
node-red/    JSONL replay flow
mqtt/        Mosquitto config
database/    MySQL schema and seed SQL
data/        Local MAT/JSONL/raw-window data
scripts/     Dataset conversion and utility scripts
docs/        Architecture and interface documents
```

## Current Phase

현재는 1차 데이터 재생 준비까지 되어 있습니다.

- MAT 내부 구조 확인
- MAT `Data` 배열을 window 단위 JSONL로 변환
- Node-RED import용 MQTT replay flow
- 테스트용 20개 window JSONL 생성

## Ubuntu Quick Start

이 프로젝트의 Python 기준은 Ubuntu + Python `3.12.10`입니다.

Python 3.12 확인:

```bash
python3.12 --version
```

FastAPI 가상환경 생성:

```bash
bash scripts/setup_ai_api_ubuntu.sh
source ai-api/.venv/bin/activate
python --version
```

MAT 구조 확인:

```bash
cd ..
python scripts/inspect_mat.py data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_H_16_6204_1200.mat
```

MAT -> JSONL 변환:

```bash
python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_H_16_6204_1200.mat \
  --output data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl \
  --equipment-id MOTOR_001 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6
```

결과 확인:

```bash
head -n 1 data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
wc -l data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
```

프론트 실시간 확인용 긴 설비별 샘플:

```text
data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
data/jsonl/MOTOR_002_H_IR_16_6204_1200_32000_79.jsonl
data/jsonl/MOTOR_003_U1_H_16_6204_1200_32000_79.jsonl
```

Node-RED flow는 위 3개 파일을 병렬로 읽습니다. 각 브랜치가 1 msg / 2 sec로 발행하므로 각 설비는 약 2초마다 갱신됩니다.

Node-RED flow:

```text
node-red/vibration-jsonl-replay-flow.json
```

자세한 1차 재생 절차는 `docs/phase1-data-replay.md`를 확인합니다.
