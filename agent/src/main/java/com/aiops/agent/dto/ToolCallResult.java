package com.aiops.agent.dto;

import java.util.Map;

/**
 * 一次工具调用的参数与结果。
 */
public record ToolCallResult(
        String tool,
        Map<String, Object> arguments,
        Map<String, Object> result) {
}
