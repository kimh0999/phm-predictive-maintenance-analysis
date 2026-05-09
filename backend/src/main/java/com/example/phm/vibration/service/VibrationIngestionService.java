package com.example.phm.vibration.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.alarm.repository.AlarmHistoryRepository;
import com.example.phm.analysis.dto.AnalysisFeatures;
import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.analysis.repository.AnalysisResultRepository;
import com.example.phm.analysis.service.AiAnalysisClient;
import com.example.phm.equipment.entity.Equipment;
import com.example.phm.equipment.repository.EquipmentRepository;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import com.example.phm.vibration.entity.VibrationWindow;
import com.example.phm.vibration.repository.VibrationWindowRepository;
import org.springframework.stereotype.Service;

@Service
public class VibrationIngestionService {

    private final RawWindowFileStorageService rawWindowFileStorageService;
    private final VibrationWindowRepository vibrationWindowRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AlarmHistoryRepository alarmHistoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final AiAnalysisClient aiAnalysisClient;

    public VibrationIngestionService(
            RawWindowFileStorageService rawWindowFileStorageService,
            VibrationWindowRepository vibrationWindowRepository,
            AnalysisResultRepository analysisResultRepository,
            AlarmHistoryRepository alarmHistoryRepository,
            EquipmentRepository equipmentRepository,
            AiAnalysisClient aiAnalysisClient
    ) {
        this.rawWindowFileStorageService = rawWindowFileStorageService;
        this.vibrationWindowRepository = vibrationWindowRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.alarmHistoryRepository = alarmHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.aiAnalysisClient = aiAnalysisClient;
    }

    public VibrationIngestionResult ingest(VibrationWindowMessage message, String rawPayload) {
        validate(message);

        LocalDateTime measuredAt = parseMeasuredAt(message.getTimestamp());
        ensureEquipmentExists(message.getEquipmentId());

        String rawFilePath = rawWindowFileStorageService.save(message, rawPayload, measuredAt);
        VibrationWindow vibrationWindow = saveVibrationWindow(message, rawFilePath, measuredAt);

        AnalyzeResponse analysis = aiAnalysisClient.analyze(message);
        AnalysisResult analysisResult = saveAnalysisResult(vibrationWindow, analysis);
        boolean alarmCreated = saveAlarmIfNeeded(analysisResult, analysis);

        return new VibrationIngestionResult(vibrationWindow, analysisResult, alarmCreated, rawFilePath, analysis);
    }

    private void validate(VibrationWindowMessage message) {
        if (message.getEquipmentId() == null || message.getEquipmentId().isBlank()) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (message.getSamplingRate() == null) {
            throw new IllegalArgumentException("samplingRate is required");
        }
        if (message.getWindowSize() == null) {
            throw new IllegalArgumentException("windowSize is required");
        }
        if (message.getWindowIndex() == null) {
            throw new IllegalArgumentException("windowIndex is required");
        }
        if (message.getValues() == null) {
            throw new IllegalArgumentException("values is required");
        }
    }

    private void ensureEquipmentExists(String equipmentCode) {
        if (equipmentRepository.existsByEquipmentCode(equipmentCode)) {
            return;
        }

        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(equipmentCode);
        equipment.setEquipmentName(equipmentCode);
        equipment.setLocation("auto-registered");
        equipmentRepository.save(equipment);
    }

    private VibrationWindow saveVibrationWindow(
            VibrationWindowMessage message,
            String rawFilePath,
            LocalDateTime measuredAt
    ) {
        VibrationWindow vibrationWindow = new VibrationWindow();
        vibrationWindow.setEquipmentCode(message.getEquipmentId());
        vibrationWindow.setMeasuredAt(measuredAt);
        vibrationWindow.setSamplingRate(message.getSamplingRate());
        vibrationWindow.setRpm(message.getRpm());
        vibrationWindow.setWindowSize(message.getWindowSize());
        vibrationWindow.setWindowIndex(message.getWindowIndex().longValue());
        vibrationWindow.setRawFilePath(rawFilePath);
        return vibrationWindowRepository.save(vibrationWindow);
    }

    private AnalysisResult saveAnalysisResult(VibrationWindow vibrationWindow, AnalyzeResponse analysis) {
        AnalysisFeatures features = analysis.getFeatures();

        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setVibrationWindow(vibrationWindow);
        analysisResult.setEquipmentCode(vibrationWindow.getEquipmentCode());
        analysisResult.setRms(features == null ? null : features.getRms());
        analysisResult.setPeakFrequency(features == null ? null : features.getPeakFrequency());
        analysisResult.setPeakToPeak(features == null ? null : features.getPeakToPeak());
        analysisResult.setCrestFactor(features == null ? null : features.getCrestFactor());
        analysisResult.setKurtosis(features == null ? null : features.getKurtosis());
        analysisResult.setPrediction(analysis.getPrediction());
        analysisResult.setConfidence(analysis.getConfidence());
        analysisResult.setModelVersion(analysis.getModelVersion());
        analysisResult.setModelInputType(analysis.getModelInputType());
        analysisResult.setModelInputSize(analysis.getModelInputSize());
        analysisResult.setModelExpectedInputSize(analysis.getModelExpectedInputSize());
        analysisResult.setModelInputStrategy(analysis.getModelInputStrategy());
        analysisResult.setModelStatus(analysis.getModelStatus());
        analysisResult.setAnomalyScore(analysis.getAnomalyScore());
        analysisResult.setAlarmLevel(analysis.getAlarmLevel());
        return analysisResultRepository.save(analysisResult);
    }

    private boolean saveAlarmIfNeeded(AnalysisResult analysisResult, AnalyzeResponse analysis) {
        if (!isAlarmLevel(analysis.getAlarmLevel())) {
            return false;
        }

        AlarmHistory alarm = new AlarmHistory();
        alarm.setEquipmentCode(analysisResult.getEquipmentCode());
        alarm.setAnalysisResult(analysisResult);
        alarm.setAlarmLevel(analysis.getAlarmLevel());
        alarm.setMessage(buildAlarmMessage(analysisResult, analysis));
        alarmHistoryRepository.save(alarm);
        return true;
    }

    private boolean isAlarmLevel(String alarmLevel) {
        if (alarmLevel == null) {
            return false;
        }
        String normalized = alarmLevel.toLowerCase(Locale.ROOT);
        return normalized.equals("warning") || normalized.equals("danger");
    }

    private String buildAlarmMessage(AnalysisResult analysisResult, AnalyzeResponse analysis) {
        return "Vibration anomaly detected: equipmentCode=%s, alarmLevel=%s, anomalyScore=%s, prediction=%s"
                .formatted(
                        analysisResult.getEquipmentCode(),
                        analysis.getAlarmLevel(),
                        analysis.getAnomalyScore(),
                        analysis.getPrediction()
                );
    }

    private LocalDateTime parseMeasuredAt(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
