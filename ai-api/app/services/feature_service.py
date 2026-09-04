from __future__ import annotations

import math

import numpy as np
from scipy.stats import kurtosis as scipy_kurtosis

from app.core.config import settings
from app.schemas.vibration_schema import FeatureResponse, FftResponse
from app.services.fft_service import calculate_fft, peak_frequency


def calculate_features(values: list[float], sampling_rate: int) -> tuple[FeatureResponse, FftResponse]:
    signal = np.asarray(values, dtype=float)
    frequencies, magnitudes = calculate_fft(signal, sampling_rate)

    rms = _safe_float(np.sqrt(np.mean(np.square(signal))))
    peak_to_peak = _safe_float(np.max(signal) - np.min(signal))
    crest_factor = _safe_float(np.max(np.abs(signal)) / rms) if rms > 0 else 0.0
    kurtosis_value = _calculate_kurtosis(signal)
    peak_freq = _safe_float(peak_frequency(frequencies, magnitudes))

    features = FeatureResponse(
        rms=round(rms, 8),
        peakFrequency=round(peak_freq, 8),
        peakToPeak=round(peak_to_peak, 8),
        crestFactor=round(crest_factor, 8),
        kurtosis=round(kurtosis_value, 8),
    )
    display_frequencies, display_magnitudes = _downsample_fft(
        frequencies,
        magnitudes,
        settings.fft_max_bins,
    )
    fft = FftResponse(
        frequencyResolution=round(float(frequencies[1] - frequencies[0]), 8) if len(frequencies) > 1 else 0.0,
        binCount=len(display_frequencies),
        frequencies=np.round(display_frequencies, decimals=8).tolist(),
        magnitudes=np.round(display_magnitudes, decimals=8).tolist(),
    )

    return features, fft


# 기준선(BASE)은 정상 구간(H_H) 특징값의 99백분위, 폭(SPAN)은 결함 구간의 55백분위까지.
# scripts/calibrate_alarm_threshold.py로 데이터셋 전체(192파일 7,680 window)를 재서 산출했다.
# 데이터셋이 바뀌면 그 스크립트를 다시 돌려 이 값을 갱신해야 한다.
RMS_BASE, RMS_SPAN = 0.2366, 0.0400
PEAK_TO_PEAK_BASE, PEAK_TO_PEAK_SPAN = 2.1648, 3.6230
CREST_FACTOR_BASE, CREST_FACTOR_SPAN = 9.2796, 1.4261
KURTOSIS_BASE, KURTOSIS_SPAN = 5.7771, 5.6251


def estimate_anomaly_score(features: FeatureResponse) -> float:
    rms_score = _clamp((features.rms - RMS_BASE) / RMS_SPAN)
    peak_to_peak_score = _clamp((features.peakToPeak - PEAK_TO_PEAK_BASE) / PEAK_TO_PEAK_SPAN)
    crest_score = _clamp((features.crestFactor - CREST_FACTOR_BASE) / CREST_FACTOR_SPAN)
    kurtosis_score = _clamp((features.kurtosis - KURTOSIS_BASE) / KURTOSIS_SPAN)

    score = max(rms_score, peak_to_peak_score, crest_score, kurtosis_score)
    return round(score, 4)


def classify_alarm_level(anomaly_score: float) -> str:
    if anomaly_score >= 0.8:
        return "danger"
    if anomaly_score >= 0.5:
        return "warning"
    return "normal"


def _calculate_kurtosis(signal: np.ndarray) -> float:
    if np.std(signal) == 0:
        return 0.0

    value = scipy_kurtosis(signal, fisher=False, bias=False)
    return _safe_float(value)


def _safe_float(value: float) -> float:
    if math.isnan(value) or math.isinf(value):
        return 0.0
    return float(value)


def _clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))


def _downsample_fft(
    frequencies: np.ndarray,
    magnitudes: np.ndarray,
    max_bins: int,
) -> tuple[np.ndarray, np.ndarray]:
    if max_bins <= 0 or len(frequencies) <= max_bins:
        return frequencies, magnitudes

    indices = np.linspace(0, len(frequencies) - 1, num=max_bins, dtype=int)
    return frequencies[indices], magnitudes[indices]
