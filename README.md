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

수집부터 대시보드까지 전 경로가 동작합니다.

- MAT `Data` 배열을 window 단위 JSONL로 변환
- Node-RED가 설비 3대를 2초 주기로 MQTT 발행
- Spring Boot가 구독해 원본 저장 + FastAPI 분석 + MySQL 적재
- FastAPI가 FFT, 통계 특징값, 베어링 고장 분류를 반환
- Vue 대시보드가 파형, 특징값 추세, 알람을 표시

베어링 고장 분류 모델은 데이터셋 전체(192파일)로 직접 학습했습니다. 학습에 쓰지 않은
회전수(800, 1200 rpm)에서 정확도 0.999입니다. `scripts/train_bearing_model.py` 참고.

알람 임계값도 같은 데이터셋의 정상 구간 분포에서 산출했습니다.
`scripts/calibrate_alarm_threshold.py` 참고.

데이터셋 출처와 배치 방법은 `data/README.md`를 확인합니다.

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
python scripts/inspect_mat.py data/raw_mat/SamplingRate_16000/RotatingSpeed_1200/H_H_16_30204_1200.mat
```

MAT -> JSONL 변환:

```bash
python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/SamplingRate_16000/RotatingSpeed_1200/H_H_16_30204_1200.mat \
  --output data/jsonl/MOTOR_001_H_H_16_30204_1200_32000_79.jsonl \
  --equipment-id MOTOR_001 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6
```

결과 확인:

```bash
head -n 1 data/jsonl/MOTOR_001_H_H_16_30204_1200_32000_79.jsonl
wc -l data/jsonl/MOTOR_001_H_H_16_30204_1200_32000_79.jsonl
```

프론트 실시간 확인용 긴 설비별 샘플:

```text
data/jsonl/MOTOR_001_H_H_16_30204_1200_32000_79.jsonl
data/jsonl/MOTOR_002_H_IR_16_30204_1200_32000_79.jsonl
data/jsonl/MOTOR_003_U1_H_16_30204_1200_32000_79.jsonl
```

Node-RED flow는 위 3개 파일을 병렬로 읽습니다. 각 브랜치가 1 msg / 2 sec로 발행하므로 각 설비는 약 2초마다 갱신됩니다.

Node-RED flow:

```text
node-red/vibration-jsonl-replay-flow.json
```

자세한 1차 재생 절차는 `docs/phase1-data-replay.md`를 확인합니다.

## Docker Compose

전체 서비스를 Docker로 한 번에 실행할 수 있습니다.

```bash
docker compose up --build
```

DB volume까지 초기화해서 새 스키마로 다시 시작하려면:

```bash
docker compose down -v
docker compose up --build
```

접속 주소:

```text
Vue dashboard: http://localhost:5173
Spring Boot:   http://localhost:8080
FastAPI docs:  http://localhost:8001/docs
Node-RED:      http://localhost:1880
MySQL:         localhost:3306
Mosquitto:     localhost:1883
```

Docker 실행 기준에서 Node-RED flow는 `/project-data/jsonl` 경로와 `mosquitto`, `backend` service name을 사용합니다.
