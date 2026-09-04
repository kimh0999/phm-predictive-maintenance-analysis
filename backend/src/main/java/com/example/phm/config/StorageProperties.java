package com.example.phm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "phm.storage")
public record StorageProperties(String rawWindowDir, Integer retentionDays) {

    public StorageProperties {
        rawWindowDir = rawWindowDir == null || rawWindowDir.isBlank() ? "../data/raw_windows" : rawWindowDir;
        retentionDays = retentionDays == null || retentionDays < 1 ? 7 : retentionDays;
    }
}
