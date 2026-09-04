package com.example.phm.vibration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.config.StorageProperties;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawWindowFileStorageServiceTest {

    @Test
    void deletesOnlyFilesOlderThanRetention(@TempDir Path rawWindowDir) throws IOException {
        RawWindowFileStorageService storageService =
                new RawWindowFileStorageService(new StorageProperties(rawWindowDir.toString(), 7));

        Path expired = Path.of(storageService.save(message(1), payload(1), LocalDateTime.of(2026, 5, 1, 12, 0)));
        Path kept = Path.of(storageService.save(message(2), payload(2), LocalDateTime.of(2026, 5, 6, 12, 0)));
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(Duration.ofDays(10))));

        long deletedFileCount = storageService.deleteOlderThan(Duration.ofDays(7));

        assertThat(deletedFileCount).isEqualTo(1L);
        assertThat(expired).doesNotExist();
        assertThat(kept).exists();
        assertThat(expired.getParent()).doesNotExist();
        assertThat(rawWindowDir).exists();
    }

    @Test
    void keepsEveryFileWhenNothingIsOlderThanRetention(@TempDir Path rawWindowDir) {
        RawWindowFileStorageService storageService =
                new RawWindowFileStorageService(new StorageProperties(rawWindowDir.toString(), 7));

        Path saved = Path.of(storageService.save(message(1), payload(1), LocalDateTime.of(2026, 5, 1, 12, 0)));

        long deletedFileCount = storageService.deleteOlderThan(Duration.ofDays(7));

        assertThat(deletedFileCount).isZero();
        assertThat(saved).exists();
    }

    private VibrationWindowMessage message(int windowIndex) {
        return new VibrationWindowMessage(
                "MOTOR_001",
                "2026-05-06T12:00:00.000Z",
                16000,
                1200,
                3,
                windowIndex,
                List.of(0.1, 0.2, 0.3)
        );
    }

    private String payload(int windowIndex) {
        return "{\"windowIndex\":" + windowIndex + "}";
    }
}
