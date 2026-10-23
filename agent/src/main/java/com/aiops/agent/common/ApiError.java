package com.aiops.agent.common;

import java.time.Instant;

/**
 * 统一错误响应结构。
 */
public record ApiError(
        int status,
        String message,
        Instant timestamp,
        String path) {
}
