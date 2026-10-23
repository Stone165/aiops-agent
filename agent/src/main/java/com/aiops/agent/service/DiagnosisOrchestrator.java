package com.aiops.agent.service;

import com.aiops.agent.domain.SnapshotMode;
import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.entity.DiagnosisRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 诊断编排：统一负责采集快照、调用诊断实现与保存诊断记录。
 *
 * <p>诊断开始时采集一次快照并 pin 到当前线程，所有工具调用复用同一份数据；
 * 保存失败不会丢弃已经生成的诊断结论，响应中 recordId 为 null。</p>
 */
@Slf4j
@Service
public class DiagnosisOrchestrator {

    private final DeepSeekAgentService agentService;
    private final DiagnosisService diagnosisService;
    private final DiagnosisRecordService recordService;
    private final LocalMonitorService monitorService;
    private final ObjectMapper objectMapper;

    public DiagnosisOrchestrator(
            DeepSeekAgentService agentService,
            DiagnosisService diagnosisService,
            DiagnosisRecordService recordService,
            LocalMonitorService monitorService,
            ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.diagnosisService = diagnosisService;
        this.recordService = recordService;
        this.monitorService = monitorService;
        this.objectMapper = objectMapper;
    }

    public DiagnosisResult runAgent(String deviceId, String description) {
        return runAgent(deviceId, description, SnapshotMode.LOCAL);
    }

    public DiagnosisResult runAgent(String deviceId,
                                    String description,
                                    SnapshotMode mode) {
        if (mode == SnapshotMode.TEXT) {
            return persist(agentService.diagnoseFromText(deviceId, description));
        }

        return withPinnedSnapshot(
                snapshot -> agentService.diagnose(deviceId, description, snapshot));
    }

    public DiagnosisResult runRule(String deviceId, String description) {
        return runRule(deviceId, description, SnapshotMode.LOCAL);
    }

    public DiagnosisResult runRule(String deviceId,
                                   String description,
                                   SnapshotMode mode) {
        if (mode == SnapshotMode.TEXT) {
            return persist(diagnosisService.diagnoseFromText(deviceId, description));
        }

        return withPinnedSnapshot(
                snapshot -> diagnosisService.diagnose(deviceId, description, snapshot));
    }

    private DiagnosisResult withPinnedSnapshot(
            Function<MonitorSnapshot, DiagnosisResult> action) {

        MonitorSnapshot snapshot = monitorService.captureFresh();
        monitorService.pin(snapshot);

        try {
            DiagnosisResult result = action.apply(snapshot).withSnapshot(snapshot);
            return persist(result);
        } finally {
            monitorService.unpin();
        }
    }

    private DiagnosisResult persist(DiagnosisResult result) {
        try {
            DiagnosisRecord record = new DiagnosisRecord();
            record.setDeviceId(result.deviceId());
            record.setDescription(result.description());
            record.setAnswer(result.answer());
            record.setToolCallCount(result.toolCallCount());
            record.setToolResults(serializeToolResults(result));
            record.setSimulated(result.simulated());

            DiagnosisRecord saved = recordService.save(record);
            return result.withRecord(saved.getId(), saved.getCreatedAt());
        } catch (Exception exception) {
            log.warn("保存诊断记录失败，deviceId={}", result.deviceId(), exception);
            return result;
        }
    }

    private String serializeToolResults(DiagnosisResult result)
            throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshot", result.monitorSnapshot());
        payload.put("toolCalls",
                result.toolCalls() == null ? List.of() : result.toolCalls());
        return objectMapper.writeValueAsString(payload);
    }
}
