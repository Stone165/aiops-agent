package com.aiops.agent.service;

import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisServiceTest {

    private DiagnosisService diagnosisService;

    @BeforeEach
    void setUp() {
        diagnosisService = new DiagnosisService();
    }

    @Test
    void shouldDetectAllLocalAnomalies() {
        MonitorSnapshot snapshot = snapshot(
                92.0, true,
                88.0, true,
                95.0, true,
                false, false);

        DiagnosisResult result = diagnosisService.diagnose(
                "local-machine", "本机运行缓慢", snapshot);

        assertThat(result.deviceId()).isEqualTo("local-machine");
        assertThat(result.description()).isEqualTo("本机运行缓慢");
        assertThat(result.findings())
                .anyMatch(finding -> finding.contains("CPU 使用率偏高"))
                .anyMatch(finding -> finding.contains("内存使用率偏高"))
                .anyMatch(finding -> finding.contains("磁盘使用率偏高"))
                .anyMatch(finding -> finding.contains("网络不可达"))
                .anyMatch(finding -> finding.contains("未运行"));
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.toolCalls()).hasSize(3);
        assertThat(result.toolCallCount()).isEqualTo(3);
        assertThat(result.answer())
                .contains("诊断结论")
                .contains("处理建议")
                .contains("本机实时采集");
        assertThat(result.simulated()).isFalse();
        assertThat(result.monitorSnapshot()).isSameAs(snapshot);
        assertThat(result.recordId()).isNull();
    }

    @Test
    void shouldDetectHighMemoryOnly() {
        MonitorSnapshot snapshot = snapshot(
                10.0, false,
                90.0, true,
                20.0, false,
                true, true);

        DiagnosisResult result = diagnosisService.diagnose(
                "local-machine", "内存告警", snapshot);

        assertThat(result.findings())
                .containsExactly("内存使用率偏高（90.0%）");
    }

    @Test
    void shouldReportNoObviousAnomalyWhenAllHealthy() {
        MonitorSnapshot snapshot = snapshot(
                10.0, false,
                20.0, false,
                30.0, false,
                true, true);

        DiagnosisResult result = diagnosisService.diagnose(
                "local-machine", "例行检查", snapshot);

        assertThat(result.findings())
                .containsExactly("本机实时检测未发现明显异常");
        assertThat(result.suggestions()).isNotEmpty();
    }

    @Test
    void shouldAnalyzeIncidentTextWithoutLocalSnapshot() {
        DiagnosisResult result = diagnosisService.diagnoseFromText(
                "server-006",
                "CPU 92%，内存 88%，磁盘 95%，延迟 300ms，服务未运行");

        assertThat(result.findings())
                .anyMatch(finding -> finding.contains("CPU 使用率为 92%"))
                .anyMatch(finding -> finding.contains("内存使用率为 88%"))
                .anyMatch(finding -> finding.contains("磁盘使用率为 95%"))
                .anyMatch(finding -> finding.contains("网络延迟为 300ms"))
                .anyMatch(finding -> finding.contains("服务未运行"));
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.answer())
                .contains("文本诊断结论")
                .contains("未读取本机实时指标");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.monitorSnapshot()).isNull();
        assertThat(result.simulated()).isFalse();
    }

    @Test
    void shouldReportWhenTextHasNoQuantifiedMetrics() {
        DiagnosisResult result = diagnosisService.diagnoseFromText(
                "server-007", "页面打不开，用户反馈很慢");

        assertThat(result.findings())
                .containsExactly("描述中未识别到明确的可量化指标");
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.monitorSnapshot()).isNull();
    }

    private MonitorSnapshot snapshot(
            double cpuUsage, boolean cpuHigh,
            double memoryUsage, boolean memoryHigh,
            double diskUsage, boolean diskHigh,
            boolean reachable, boolean serviceRunning) {

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tool", "system_metrics");
        metrics.put("deviceId", "local-machine");
        metrics.put("cpuUsage", cpuUsage);
        metrics.put("memoryUsage", memoryUsage);
        metrics.put("diskUsage", diskUsage);
        metrics.put("cpuHigh", cpuHigh);
        metrics.put("memoryHigh", memoryHigh);
        metrics.put("diskHigh", diskHigh);
        metrics.put("simulated", false);

        Map<String, Object> network = new LinkedHashMap<>();
        network.put("tool", "network_check");
        network.put("deviceId", "local-machine");
        network.put("targetHost", "127.0.0.1");
        network.put("targetPort", 1);
        network.put("reachable", reachable);
        network.put("latencyMs", reachable ? 1 : -1);
        network.put("simulated", false);

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("tool", "service_status");
        service.put("deviceId", "local-machine");
        service.put("serviceName", "test-service");
        service.put("host", "127.0.0.1");
        service.put("port", 1);
        service.put("running", serviceRunning);
        service.put("simulated", false);

        return new MonitorSnapshot(
                "local-machine", metrics, network, service, false,
                LocalDateTime.now());
    }
}
