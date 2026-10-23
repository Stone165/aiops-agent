package com.aiops.agent.dto;

/**
 * 工单统计。
 */
public record IncidentStats(
        long total,
        long open,
        long inProgress,
        long resolved,
        long closed,
        long critical,
        long high) {
}
