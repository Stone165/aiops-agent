package com.aiops.agent.tool;

import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.service.LocalMonitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsToolRegistryTest {

    private OpsToolRegistry registry;

    @BeforeEach
    void setUp() {
        MonitorSnapshot snapshot = new MonitorSnapshot(
                "local-machine",
                Map.of(
                        "tool", "system_metrics",
                        "deviceId", "local-machine",
                        "cpuUsage", 42.0,
                        "simulated", false),
                Map.of(
                        "tool", "network_check",
                        "reachable", true,
                        "simulated", false),
                Map.of(
                        "tool", "service_status",
                        "running", true,
                        "simulated", false),
                false,
                LocalDateTime.now());

        LocalMonitorService monitorService = mock(LocalMonitorService.class);
        when(monitorService.capture()).thenReturn(snapshot);

        registry = new OpsToolRegistry(new ObjectMapper(), monitorService);
    }

    @Test
    void shouldExposeThreeFunctionDefinitions() {
        assertThat(registry.definitions()).hasSize(3);
        assertThat(registry.definitions().toString())
                .contains(OpsToolRegistry.TOOL_METRICS)
                .contains(OpsToolRegistry.TOOL_NETWORK)
                .contains(OpsToolRegistry.TOOL_SERVICE);
    }

    @Test
    void shouldExecuteKnownToolFromLocalSnapshot() {
        Map<String, Object> result = registry.execute(
                OpsToolRegistry.TOOL_METRICS, "local-machine");

        assertThat(result)
                .containsEntry("tool", "system_metrics")
                .containsEntry("deviceId", "local-machine")
                .containsEntry("cpuUsage", 42.0)
                .containsEntry("simulated", false);
    }

    @Test
    void shouldRejectUnknownTool() {
        Map<String, Object> result = registry.execute("rm -rf /", "local-machine");

        assertThat(result)
                .containsEntry("allowed", false)
                .containsKey("error");
        assertThat(String.valueOf(result.get("error"))).contains("不支持的工具");
        assertThat(registry.isSupported("rm -rf /")).isFalse();
    }
}
