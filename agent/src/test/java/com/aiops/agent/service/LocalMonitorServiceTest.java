package com.aiops.agent.service;

import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.config.MonitorProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMonitorServiceTest {

    @Test
    void shouldCaptureLocalSnapshot() {
        MonitorProperties properties = new MonitorProperties();
        properties.setNetworkHost("127.0.0.1");
        properties.setNetworkPort(1);
        properties.setNetworkTimeoutMs(100);
        properties.setServiceName("test-service");
        properties.setServiceHost("127.0.0.1");
        properties.setServicePort(1);
        properties.setServiceTimeoutMs(100);

        LocalMonitorService service = new LocalMonitorService(properties);
        MonitorSnapshot snapshot = service.captureFresh();

        assertThat(snapshot.deviceId()).isNotBlank();
        assertThat(snapshot.simulated()).isFalse();
        assertThat(snapshot.metrics()).containsKeys(
                "cpuUsage", "memoryUsage", "diskUsage",
                "cpuHigh", "memoryHigh", "diskHigh");
        assertThat((Double) snapshot.metrics().get("cpuUsage"))
                .isBetween(0.0, 100.0);
        assertThat((Double) snapshot.metrics().get("memoryUsage"))
                .isBetween(0.0, 100.0);
        assertThat((Double) snapshot.metrics().get("diskUsage"))
                .isBetween(0.0, 100.0);
        assertThat(snapshot.network()).containsEntry("reachable", false);
        assertThat(snapshot.service()).containsEntry("running", false);
    }

    @Test
    void shouldPinSnapshotForDiagnosisTools() {
        MonitorProperties properties = new MonitorProperties();
        properties.setNetworkHost("127.0.0.1");
        properties.setNetworkPort(1);
        properties.setNetworkTimeoutMs(100);
        properties.setServiceHost("127.0.0.1");
        properties.setServicePort(1);
        properties.setServiceTimeoutMs(100);

        LocalMonitorService service = new LocalMonitorService(properties);
        MonitorSnapshot pinned = service.captureFresh();
        service.pin(pinned);

        try {
            assertThat(service.capture()).isSameAs(pinned);
            assertThat(service.capture().metrics()).isSameAs(pinned.metrics());
        } finally {
            service.unpin();
        }
    }
}
