package com.aiops.agent.domain;

import java.util.Locale;

/**
 * 工单严重程度。
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM.name();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效严重程度：" + value);
        }
    }
}
