package com.example.phm.vibration.service;

import java.time.Duration;

import com.example.phm.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RawWindowRetentionService {

    private static final Logger log = LoggerFactory.getLogger(RawWindowRetentionService.class);

    private final RawWindowFileStorageService rawWindowFileStorageService;
    private final Duration retention;

    public RawWindowRetentionService(
            RawWindowFileStorageService rawWindowFileStorageService,
            StorageProperties storageProperties
    ) {
        this.rawWindowFileStorageService = rawWindowFileStorageService;
        this.retention = Duration.ofDays(storageProperties.retentionDays());
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void purgeExpiredRawWindows() {
        try {
            long deletedFileCount = rawWindowFileStorageService.deleteOlderThan(retention);
            log.info(
                    "Raw window retention completed: deletedFiles={}, retentionDays={}",
                    deletedFileCount,
                    retention.toDays()
            );
        } catch (RuntimeException exception) {
            log.warn("Raw window retention failed: {}", exception.getMessage(), exception);
        }
    }
}
