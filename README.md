# Smart Factory Vibration Monitoring

모터 진동 시계열 데이터를 분석해 설비 이상을 감지하는 스마트팩토리 MVP입니다.

```text
진동 데이터 -> MQTT -> Spring Boot -> FastAPI AI 분석 -> MySQL -> Vue 대시보드
```

## 이 프로젝트는 무엇인가요?

공장 모터는 고장이 나기 전에 진동 패턴이 달라집니다.  
이 프로젝트는 모터의 진동 데이터를 계속 받아서 “정상인지, 위험한 상태인지”를 자동으로 판단하는 시스템입니다.

쉽게 말하면:

```text
모터가 흔들리는 정도를 숫자로 기록하고
그 숫자를 분석해서
고장 조짐이 있으면 대시보드에 알람을 띄우는 프로젝트
```

## 예시 데이터

현재 프로젝트는 3대의 모터 데이터를 재생해서 비교합니다.

| 설비 | 파일 의미 | 상태 |
| --- | --- | --- |
| `MOTOR_001` | `H_H` | 정상 회전체 + 정상 베어링 |
| `MOTOR_002` | `H_IR` | 정상 회전체 + 베어링 내륜 결함 |
| `MOTOR_003` | `U1_H` | 불균형 회전체 + 정상 베어링 |

각 설비의 첫 번째 2초 데이터를 계산하면 차이가 이렇게 보입니다.

| 설비 | RMS | Peak-to-Peak | Crest | Kurtosis | 이상점수 | 판정 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `MOTOR_001` | 0.135065 | 1.428144 | 5.638 | 3.483 | 0 | `normal` |
| `MOTOR_002` | 0.337597 | 7.361042 | 11.652 | 10.994 | 1 | `danger` |
| `MOTOR_003` | 0.139535 | 1.440008 | 5.749 | 3.656 | 0 | `normal` |

`MOTOR_002`는 정상 설비보다 진동 크기와 충격성이 훨씬 큽니다.  
그래서 시스템은 `MOTOR_002`를 위험 상태인 `danger`로 판단합니다.

## 한눈에 보기

- 2초 단위 진동 window를 수집합니다.
- RMS, Peak-to-Peak, Kurtosis, FFT 등 핵심 특징을 계산합니다.
- AI 모델이 고장 유형을 예측합니다.
- 이상 점수에 따라 `normal`, `warning`, `danger` 알람을 생성합니다.
- Vue 대시보드에서 원본 파형, 분석 지표, 알람 이력을 확인합니다.

## 프로젝트 구조

```text
backend/    Spring Boot API, MQTT 수신, DB 저장
ai-api/     FastAPI 기반 진동 분석 및 AI 추론
frontend/   Vue 대시보드
node-red/   JSONL 데이터 재생 플로우
mqtt/       Mosquitto 설정
database/   MySQL 스키마 및 초기 데이터
data/       MAT, JSONL, 원본 window 데이터
scripts/    데이터 변환 및 모델 유틸리티
docs/       상세 설계 문서
```

## 데이터 흐름

1. MAT 진동 데이터를 JSONL window로 변환합니다.
2. Node-RED가 JSONL을 읽어 MQTT로 발행합니다.
3. Spring Boot가 MQTT 메시지를 받아 FastAPI로 분석 요청을 보냅니다.
4. FastAPI가 특징값, FFT, AI 예측, 이상 점수를 반환합니다.
5. Spring Boot가 결과를 MySQL에 저장하고 알람을 관리합니다.
6. Vue 대시보드가 최신 상태를 시각화합니다.

## 실행

```bash
docker compose up --build
```

초기화가 필요하면:

```bash
docker compose down -v
docker compose up --build
```

## 접속 주소

```text
Dashboard   http://localhost:5173
Backend     http://localhost:8080
FastAPI     http://localhost:8001/docs
Node-RED    http://localhost:1880
MySQL       localhost:3306
MQTT        localhost:1883
```

## 주요 데이터 포맷

```json
{
  "equipmentId": "MOTOR_001",
  "timestamp": "2026-05-06T12:00:00.000Z",
  "samplingRate": 16000,
  "rpm": 1200,
  "windowSize": 32000,
  "windowIndex": 0,
  "values": [0.152174, 0.105499, 0.065324]
}
```

`values`는 시간 순서대로 기록된 진동값입니다.  
`samplingRate=16000`, `windowSize=32000`이면 하나의 window는 2초 분량입니다.

## 참고 문서

- [Architecture](docs/architecture.md)
- [API Spec](docs/api-spec.md)
- [Data Replay](docs/phase1-data-replay.md)
- [Model Integration](docs/model-integration.md)
- [진동 시계열 분석 리포트](analysis/REPORT.md)
