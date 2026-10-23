package com.aiops.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 诊断历史记录响应，tool_calls 与监控快照已反序列化为结构化数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisRecordResponse(
        Long id,
        String deviceId,
        String description,
        String answer,
        int toolCallCount,
        List<ToolCallResult> toolCalls,
        MonitorSnapshot monitorSnapshot,
        boolean simulated,
        LocalDateTime createdAt) {
}
