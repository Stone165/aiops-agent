package com.aiops.agent.service;

import com.aiops.agent.domain.SnapshotMode;
import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.dto.ToolCallResult;
import com.aiops.agent.entity.DiagnosisRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosisOrchestratorTest {

    private final DeepSeekAgentService agentService = mock(DeepSeekAgentService.class);
    private final DiagnosisService diagnosisService = mock(DiagnosisService.class);
    private final DiagnosisRecordService recordService = mock(DiagnosisRecordService.class);
    private final LocalMonitorService monitorService = mock(LocalMonitorService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final MonitorSnapshot snapshot = sampleSnapshot();
    private final DiagnosisOrchestrator orchestrator = new DiagnosisOrchestrator(
            agentService, diagnosisService, recordService, monitorService, objectMapper);

    @BeforeEach
    void setUp() {
        when(monitorService.captureFresh()).thenReturn(snapshot);
    }

    @Test
    void shouldPinSnapshotPersistResultAndReturnRecordId() {
        DiagnosisResult raw = sampleResult();
        when(agentService.diagnose("server-001", "接口超时", snapshot))
                .thenReturn(raw);

        DiagnosisRecord saved = new DiagnosisRecord();
        saved.setId(7L);
        saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(recordService.save(any(DiagnosisRecord.class))).thenReturn(saved);

        DiagnosisResult result = orchestrator.runAgent("server-001", "接口超时");

        assertThat(result.recordId()).isEqualTo(7L);
        assertThat(result.createdAt()).isEqualTo(saved.getCreatedAt());
        assertThat(result.answer()).isEqualTo("诊断结论");
        assertThat(result.monitorSnapshot()).isSameAs(snapshot);

        verify(monitorService).pin(snapshot);
        verify(monitorService).unpin();

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(recordService).save(captor.capture());

        DiagnosisRecord record = captor.getValue();
        assertThat(record.getDeviceId()).isEqualTo("server-001");
        assertThat(record.getToolCallCount()).isEqualTo(1);
        assertThat(record.getToolResults())
                .contains("\"snapshot\"")
                .contains("check_system_metrics");
        assertThat(record.getSimulated()).isFalse();
    }

    @Test
    void shouldRunTextModeWithoutLocalSnapshot() {
        DiagnosisResult textResult = new DiagnosisResult(
                "server-006",
                "CPU 92%",
                "文本结论",
                List.of("描述中 CPU 使用率为 92%"),
                List.of("检查高负载进程"),
                0,
                List.of(),
                false,
                null,
                null,
                null);
        when(agentService.diagnoseFromText("server-006", "CPU 92%"))
                .thenReturn(textResult);

        DiagnosisRecord saved = new DiagnosisRecord();
        saved.setId(8L);
        saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 5));
        when(recordService.save(any(DiagnosisRecord.class))).thenReturn(saved);

        DiagnosisResult result = orchestrator.runAgent(
                "server-006", "CPU 92%", SnapshotMode.TEXT);

        assertThat(result.recordId()).isEqualTo(8L);
        assertThat(result.monitorSnapshot()).isNull();
        assertThat(result.toolCalls()).isEmpty();

        verify(monitorService, never()).captureFresh();
        verify(monitorService, never()).pin(any());
        verify(agentService).diagnoseFromText("server-006", "CPU 92%");

        ArgumentCaptor<DiagnosisRecord> captor =
                ArgumentCaptor.forClass(DiagnosisRecord.class);
        verify(recordService).save(captor.capture());

        DiagnosisRecord record = captor.getValue();
        assertThat(record.getToolCallCount()).isZero();
        assertThat(record.getToolResults()).contains("\"snapshot\":null");
        assertThat(record.getSimulated()).isFalse();
    }

    @Test
    void shouldReturnUnsavedResultWhenPersistenceFails() {
        when(agentService.diagnose("server-001", "接口超时", snapshot))
                .thenReturn(sampleResult());
        when(recordService.save(any(DiagnosisRecord.class)))
                .thenThrow(new RuntimeException("database down"));

        DiagnosisResult result = orchestrator.runAgent("server-001", "接口超时");

        assertThat(result.recordId()).isNull();
        assertThat(result.answer()).isEqualTo("诊断结论");
        verify(monitorService).unpin();
    }

    private MonitorSnapshot sampleSnapshot() {
        return new MonitorSnapshot(
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
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    private DiagnosisResult sampleResult() {
        return new DiagnosisResult(
                "server-001",
                "接口超时",
                "诊断结论",
                List.of(),
                List.of(),
                1,
                List.of(new ToolCallResult(
                        "check_system_metrics",
                        Map.of("deviceId", "local-machine"),
                        Map.of("cpuUsage", 42.0, "simulated", false))),
                false,
                null,
                null,
                snapshot);
    }
}
