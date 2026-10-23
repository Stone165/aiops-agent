package com.aiops.agent.domain;

import java.util.Locale;

/**
 * 工单状态。
 */
public enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("工单状态不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效状态：" + value);
        }
    }
}
