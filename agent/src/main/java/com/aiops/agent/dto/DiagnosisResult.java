package com.aiops.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 诊断结果。AI 诊断与规则诊断共用同一结构。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosisResult(
        String deviceId,
        String description,
        String answer,
        List<String> findings,
        List<String> suggestions,
        int toolCallCount,
        List<ToolCallResult> toolCalls,
        boolean simulated,
        Long recordId,
        LocalDateTime createdAt,
        MonitorSnapshot monitorSnapshot) {

    public DiagnosisResult withRecord(Long id, LocalDateTime createdTime) {
        return new DiagnosisResult(
                deviceId,
                description,
                answer,
                findings,
                suggestions,
                toolCallCount,
                toolCalls,
                simulated,
                id,
                createdTime,
                monitorSnapshot);
    }

    public DiagnosisResult withSnapshot(MonitorSnapshot snapshot) {
        return new DiagnosisResult(
                deviceId,
                description,
                answer,
                findings,
                suggestions,
                toolCallCount,
                toolCalls,
                simulated,
                recordId,
                createdAt,
                snapshot);
    }
}
