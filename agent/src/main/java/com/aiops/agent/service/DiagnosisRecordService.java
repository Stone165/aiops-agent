package com.aiops.agent.service;

import com.aiops.agent.common.PageResponse;
import com.aiops.agent.dto.DiagnosisRecordResponse;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.dto.ToolCallResult;
import com.aiops.agent.entity.DiagnosisRecord;
import com.aiops.agent.mapper.DiagnosisRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DiagnosisRecordService {

    private static final TypeReference<List<ToolCallResult>> TOOL_CALLS_TYPE =
            new TypeReference<>() {
            };

    private final DiagnosisRecordMapper diagnosisRecordMapper;
    private final ObjectMapper objectMapper;

    public DiagnosisRecordService(
            DiagnosisRecordMapper diagnosisRecordMapper,
            ObjectMapper objectMapper) {
        this.diagnosisRecordMapper = diagnosisRecordMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存一次诊断记录，并重新查询数据库默认生成的时间字段。
     */
    public DiagnosisRecord save(DiagnosisRecord record) {
        record.setId(null);

        if (record.getToolCallCount() == null) {
            record.setToolCallCount(0);
        }

        if (record.getSimulated() == null) {
            record.setSimulated(true);
        }

        diagnosisRecordMapper.insert(record);

        DiagnosisRecord saved = diagnosisRecordMapper.selectById(record.getId());
        return saved == null ? record : saved;
    }

    public DiagnosisRecord getById(Long id) {
        return diagnosisRecordMapper.selectById(id);
    }

    public PageResponse<DiagnosisRecordResponse> page(
            int pageNum, int pageSize, String deviceId) {

        LambdaQueryWrapper<DiagnosisRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(deviceId != null && !deviceId.isBlank(),
                DiagnosisRecord::getDeviceId, deviceId);
        wrapper.orderByDesc(DiagnosisRecord::getCreatedAt);

        Page<DiagnosisRecord> page = new Page<>(pageNum, pageSize);
        Page<DiagnosisRecord> result =
                diagnosisRecordMapper.selectPage(page, wrapper);

        List<DiagnosisRecordResponse> records = result.getRecords()
                .stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.of(records, result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    public DiagnosisRecordResponse toResponse(DiagnosisRecord record) {
        ParsedToolResults parsed = parseToolResults(record.getToolResults());

        return new DiagnosisRecordResponse(
                record.getId(),
                record.getDeviceId(),
                record.getDescription(),
                record.getAnswer(),
                record.getToolCallCount() == null ? 0 : record.getToolCallCount(),
                parsed.toolCalls(),
                parsed.snapshot(),
                Boolean.TRUE.equals(record.getSimulated()),
                record.getCreatedAt());
    }

    private ParsedToolResults parseToolResults(String toolResults) {
        if (toolResults == null || toolResults.isBlank()) {
            return new ParsedToolResults(null, List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(toolResults);

            // 兼容旧格式：tool_results 直接是 toolCalls 数组。
            if (root.isArray()) {
                return new ParsedToolResults(
                        null,
                        objectMapper.convertValue(root, TOOL_CALLS_TYPE));
            }

            if (root.isObject()) {
                MonitorSnapshot snapshot = root.hasNonNull("snapshot")
                        ? objectMapper.treeToValue(
                                root.get("snapshot"), MonitorSnapshot.class)
                        : null;

                List<ToolCallResult> toolCalls = root.has("toolCalls")
                        ? objectMapper.convertValue(
                                root.get("toolCalls"), TOOL_CALLS_TYPE)
                        : List.of();

                return new ParsedToolResults(snapshot, toolCalls);
            }

            return new ParsedToolResults(null, List.of());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("诊断记录中的工具结果不是合法 JSON，id 可能为空", exception);
            return new ParsedToolResults(null, List.of());
        }
    }

    private record ParsedToolResults(
            MonitorSnapshot snapshot,
            List<ToolCallResult> toolCalls) {
    }
}
