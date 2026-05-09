from __future__ import annotations

from fastapi import APIRouter

from app.schemas.vibration_schema import AnalyzeResponse, VibrationWindowRequest
from app.services.feature_service import calculate_features, classify_alarm_level, estimate_anomaly_score
from app.services.predict_service import get_fault_model_service


router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: VibrationWindowRequest) -> AnalyzeResponse:
    features, fft = calculate_features(request.values, request.samplingRate)
    prediction = get_fault_model_service().predict(request.values, request.samplingRate, request.spectrogram)
    anomaly_score = estimate_anomaly_score(features)
    alarm_level = classify_alarm_level(anomaly_score)

    return AnalyzeResponse(
        equipmentId=request.equipmentId,
        timestamp=request.timestamp,
        samplingRate=request.samplingRate,
        rpm=request.rpm,
        windowSize=request.windowSize,
        windowIndex=request.windowIndex,
        features=features,
        fft=fft,
        anomalyScore=anomaly_score,
        alarmLevel=alarm_level,
        prediction=prediction.prediction,
        confidence=prediction.confidence,
        modelVersion=prediction.model_version,
        modelInputType=prediction.input_type,
        modelInputSize=prediction.input_size,
        modelExpectedInputSize=prediction.expected_input_size,
        modelInputStrategy=prediction.input_strategy,
        modelStatus=prediction.status,
    )
