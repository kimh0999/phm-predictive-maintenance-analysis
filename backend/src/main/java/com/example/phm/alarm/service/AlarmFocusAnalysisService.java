package com.example.phm.alarm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.example.phm.alarm.dto.AlarmFocusAnalysisResponse;
import com.example.phm.alarm.dto.AlarmFocusAnalysisResponse.FocusAnalysisPoint;
import com.example.phm.alarm.dto.AlarmFocusAnalysisResponse.FocusRawPoint;
import com.example.phm.alarm.dto.AlarmFocusSelectionResponse;
import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.entity.VibrationWindow;
import com.example.phm.vibration.repository.VibrationWindowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlarmFocusAnalysisService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    /** 한 번의 요청이 읽어 들이는 구간 폭 상한. 16kHz x 32000 window 기준 약 60개 window. */
    private static final int MAX_RANGE_SECONDS = 120;

    /** 구간 폭과 별개로 메모리에 올리는 원본 샘플 수의 절대 상한. */
    private static final int MAX_RAW_SAMPLES = 2_000_000;

    private final AlarmHistoryRepository alarmHistoryRepository;
    private final VibrationWindowRepository vibrationWindowRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final ObjectMapper objectMapper;

    public AlarmFocusAnalysisService(
            AlarmHistoryRepository alarmHistoryRepository,
            VibrationWindowRepository vibrationWindowRepository,
            AnalysisResultRepository analysisResultRepository,
            AiAnalysisClient aiAnalysisClient,
            ObjectMapper objectMapper
    ) {
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.vibrationWindowRepository = vibrationWindowRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.aiAnalysisClient = aiAnalysisClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AlarmFocusAnalysisResponse findFocusAnalysis(Long alarmId, int paddingSeconds, int maxPoints) {
        AlarmHistory alarm = findAlarm(alarmId);
        TimeRange range = focusRange(alarm, paddingSeconds);
        List<VibrationWindow> windows = findCandidateWindows(alarm.getEquipmentCode(), range);
        List<FocusRawPoint> originalPoints = readRawPoints(windows, range.start(), range.end());
        List<FocusRawPoint> displayPoints = downsampleMinMax(originalPoints, safeMaxPoints(maxPoints));
        List<FocusAnalysisPoint> trend = analysisResultRepository
                .findByEquipmentCodeAndMeasuredAtBetween(alarm.getEquipmentCode(), range.start(), range.end())
                .stream()
                .map(this::toFocusAnalysisPoint)
                .toList();

        return new AlarmFocusAnalysisResponse(
                alarm.getId(),
                alarm.getEquipmentCode(),
                alarm.getAlarmLevel(),
                alarm.getStatus(),
                alarm.getOccurredAt(),
                alarm.getEndedAt(),
                range.start(),
                range.end(),
                windows.isEmpty() ? null : windows.get(0).getSamplingRate(),
                windows.size(),
                displayPoints.size(),
                originalPoints.size(),
                displayPoints.size() < originalPoints.size(),
                windows.isEmpty() ? null : windows.get(0).getWindowIndex(),
                windows.isEmpty() ? null : windows.get(windows.size() - 1).getWindowIndex(),
                displayPoints,
                trend
        );
    }

    @Transactional(readOnly = true)
    public AlarmFocusSelectionResponse analyzeSelection(
            Long alarmId,
            long startMillis,
            long endMillis,
            int maxSamples
    ) {
        if (endMillis <= startMillis) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endMillis must be greater than startMillis");
        }
        // 선택 구간은 잘라내지 않고 거절한다. 조용히 줄이면 FFT 결과가 요청 구간과 어긋난다.
        if (endMillis - startMillis > MAX_RANGE_SECONDS * 1000L) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected range must be %d seconds or shorter".formatted(MAX_RANGE_SECONDS)
            );
        }

        AlarmHistory alarm = findAlarm(alarmId);
        LocalDateTime selectedStart = fromEpochMillis(startMillis);
        LocalDateTime selectedEnd = fromEpochMillis(endMillis);
        TimeRange range = new TimeRange(selectedStart, selectedEnd);
        List<VibrationWindow> windows = findCandidateWindows(alarm.getEquipmentCode(), range);
        List<FocusRawPoint> points = readRawPoints(windows, selectedStart, selectedEnd);
        if (points.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No raw vibration samples found in selected range");
        }

        int originalSampleCount = points.size();
        int safeMaxSamples = Math.max(1024, Math.min(maxSamples, 128000));
        List<Double> values = toValues(points);
        boolean downsampled = false;
        if (values.size() > safeMaxSamples) {
            values = resampleEvenly(values, safeMaxSamples);
            downsampled = true;
        }

        int samplingRate = inferSamplingRate(windows, values.size(), startMillis, endMillis, downsampled);
        VibrationWindowMessage message = new VibrationWindowMessage(
                alarm.getEquipmentCode(),
                selectedStart.atZone(DEFAULT_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                samplingRate,
                windows.isEmpty() ? null : windows.get(0).getRpm(),
                values.size(),
                0,
                values
        );
        AnalyzeResponse analysis = aiAnalysisClient.analyze(message);

        return new AlarmFocusSelectionResponse(
                alarm.getId(),
                alarm.getEquipmentCode(),
                selectedStart,
                selectedEnd,
                values.size(),
                originalSampleCount,
                downsampled,
                samplingRate,
                analysis.getFeatures(),
                analysis.getFft(),
                analysis.getAnomalyScore(),
                analysis.getAlarmLevel(),
                analysis.getPrediction(),
                analysis.getConfidence(),
                analysis.getModelVersion(),
                analysis.getModelInputType(),
                analysis.getModelInputSize(),
                analysis.getModelExpectedInputSize(),
                analysis.getModelInputStrategy(),
                analysis.getModelStatus()
        );
    }

    private AlarmHistory findAlarm(Long alarmId) {
        return alarmHistoryRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alarm not found"));
    }

    private TimeRange focusRange(AlarmHistory alarm, int paddingSeconds) {
        int safePaddingSeconds = Math.max(0, Math.min(paddingSeconds, 120));
        LocalDateTime occurredAt = alarm.getOccurredAt();
        LocalDateTime endedAt = alarm.getEndedAt();
        if (endedAt == null) {
            endedAt = alarm.getAnalysisResult().getVibrationWindow().getMeasuredAt();
        }
        if (endedAt.isBefore(occurredAt)) {
            endedAt = occurredAt;
        }
        LocalDateTime start = occurredAt.minusSeconds(safePaddingSeconds);
        LocalDateTime end = endedAt.plusSeconds(safePaddingSeconds);
        // open 상태로 오래 남은 알람은 endedAt이 계속 밀려 구간이 무한정 늘어난다. 발생 시점 기준으로 잘라낸다.
        LocalDateTime maxEnd = start.plusSeconds(MAX_RANGE_SECONDS);
        if (end.isAfter(maxEnd)) {
            end = maxEnd;
        }
        return new TimeRange(start, end);
    }

    private List<VibrationWindow> findCandidateWindows(String equipmentCode, TimeRange range) {
        return vibrationWindowRepository.findByEquipmentCodeAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                equipmentCode,
                range.start().minusSeconds(10),
                range.end()
        );
    }

    private List<FocusRawPoint> readRawPoints(
            List<VibrationWindow> windows,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        double startMillis = toEpochMillis(rangeStart);
        double endMillis = toEpochMillis(rangeEnd);
        List<FocusRawPoint> points = new ArrayList<>();

        for (VibrationWindow window : windows) {
            VibrationWindowMessage message = readRawWindowMessage(window);
            if (message == null) {
                continue;
            }
            List<Double> values = message.getValues() == null ? List.of() : message.getValues();
            if (values.isEmpty() || window.getSamplingRate() == null || window.getSamplingRate() <= 0) {
                continue;
            }

            double baseMillis = toEpochMillis(window.getMeasuredAt());
            double intervalMillis = 1000.0 / window.getSamplingRate();
            for (int index = 0; index < values.size(); index++) {
                double timestamp = baseMillis + index * intervalMillis;
                if (timestamp >= startMillis && timestamp <= endMillis) {
                    points.add(new FocusRawPoint(timestamp, values.get(index), window.getWindowIndex()));
                }
            }

            if (points.size() >= MAX_RAW_SAMPLES) {
                break;
            }
        }

        return points;
    }

    private VibrationWindowMessage readRawWindowMessage(VibrationWindow window) {
        Path rawFile = Path.of(window.getRawFilePath());
        if (!Files.exists(rawFile)) {
            // 보존 기간이 지나 원본이 삭제된 window. 특징값은 DB에 남으므로 파형만 건너뛴다.
            return null;
        }

        try {
            return objectMapper.readValue(Files.readString(rawFile), VibrationWindowMessage.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read raw vibration window file", exception);
        }
    }

    private FocusAnalysisPoint toFocusAnalysisPoint(AnalysisResult result) {
        VibrationWindow window = result.getVibrationWindow();
        return new FocusAnalysisPoint(
                result.getId(),
                window.getId(),
                window.getMeasuredAt(),
                window.getWindowIndex(),
                result.getRms(),
                result.getPeakFrequency(),
                result.getPeakToPeak(),
                result.getCrestFactor(),
                result.getKurtosis(),
                result.getAnomalyScore(),
                result.getAlarmLevel(),
                result.getPrediction(),
                result.getConfidence()
        );
    }

    private int safeMaxPoints(int maxPoints) {
        return Math.max(2000, Math.min(maxPoints, 60000));
    }

    private List<FocusRawPoint> downsampleMinMax(List<FocusRawPoint> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }

        int bucketCount = Math.max(1, maxPoints / 2);
        double bucketSize = points.size() / (double) bucketCount;
        List<FocusRawPoint> downsampled = new ArrayList<>(maxPoints);

        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = (int) Math.floor(bucket * bucketSize);
            int end = Math.min(points.size(), (int) Math.floor((bucket + 1) * bucketSize));
            if (end <= start) {
                continue;
            }

            FocusRawPoint minPoint = points.get(start);
            FocusRawPoint maxPoint = points.get(start);
            for (int index = start + 1; index < end; index++) {
                FocusRawPoint point = points.get(index);
                if (point.value() < minPoint.value()) {
                    minPoint = point;
                }
                if (point.value() > maxPoint.value()) {
                    maxPoint = point;
                }
            }

            if (minPoint.timestamp() <= maxPoint.timestamp()) {
                downsampled.add(minPoint);
                if (maxPoint.timestamp() != minPoint.timestamp()) {
                    downsampled.add(maxPoint);
                }
            } else {
                downsampled.add(maxPoint);
                downsampled.add(minPoint);
            }
        }

        return downsampled;
    }

    private List<Double> toValues(List<FocusRawPoint> points) {
        return points.stream()
                .map(FocusRawPoint::value)
                .toList();
    }

    private List<Double> resampleEvenly(List<Double> values, int targetSize) {
        if (values.size() <= targetSize) {
            return values;
        }
        List<Double> resized = new ArrayList<>(targetSize);
        double scale = (values.size() - 1) / (double) (targetSize - 1);
        for (int index = 0; index < targetSize; index++) {
            double sourceIndex = index * scale;
            int left = (int) Math.floor(sourceIndex);
            int right = Math.min(values.size() - 1, left + 1);
            double weight = sourceIndex - left;
            resized.add(values.get(left) * (1.0 - weight) + values.get(right) * weight);
        }
        return resized;
    }

    private int inferSamplingRate(
            List<VibrationWindow> windows,
            int sampleCount,
            long startMillis,
            long endMillis,
            boolean resampled
    ) {
        if (!resampled && !windows.isEmpty() && windows.get(0).getSamplingRate() != null) {
            return windows.get(0).getSamplingRate();
        }
        double durationSeconds = Math.max(0.001, (endMillis - startMillis) / 1000.0);
        return Math.max(1, (int) Math.round(sampleCount / durationSeconds));
    }

    private double toEpochMillis(LocalDateTime value) {
        return value.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    private LocalDateTime fromEpochMillis(long value) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), DEFAULT_ZONE);
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }
}
