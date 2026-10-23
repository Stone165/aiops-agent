package com.aiops.agent.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本机实时监控快照。metrics/network/service 均来自本机采集。
 */
public record MonitorSnapshot(
        String deviceId,
        Map<String, Object> metrics,
        Map<String, Object> network,
        Map<String, Object> service,
        boolean simulated,
        LocalDateTime collectedAt) {
}
