package com.aiops.agent.controller;

import com.aiops.agent.common.PageResponse;
import com.aiops.agent.common.ResourceNotFoundException;
import com.aiops.agent.dto.DiagnosisRecordResponse;
import com.aiops.agent.dto.DiagnosisRequest;
import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.domain.SnapshotMode;
import com.aiops.agent.entity.DiagnosisRecord;
import com.aiops.agent.service.DiagnosisOrchestrator;
import com.aiops.agent.service.DiagnosisRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnosis")
@Validated
public class DiagnosisController {

    private final DiagnosisOrchestrator orchestrator;
    private final DiagnosisRecordService recordService;

    public DiagnosisController(
            DiagnosisOrchestrator orchestrator,
            DiagnosisRecordService recordService) {
        this.orchestrator = orchestrator;
        this.recordService = recordService;
    }

    /**
     * AI 工具调用诊断。
     */
    @PostMapping("/agent")
    public DiagnosisResult agent(@Valid @RequestBody DiagnosisRequest request) {
        return orchestrator.runAgent(
                request.getDeviceId().trim(),
                request.getDescription().trim(),
                SnapshotMode.from(request.getSnapshotMode()));
    }

    /**
     * 规则诊断。保留 /run 作为旧接口兼容别名。
     */
    @PostMapping({"/rule", "/run"})
    public DiagnosisResult rule(@Valid @RequestBody DiagnosisRequest request) {
        return orchestrator.runRule(
                request.getDeviceId().trim(),
                request.getDescription().trim(),
                SnapshotMode.from(request.getSnapshotMode()));
    }

    @GetMapping("/records")
    public PageResponse<DiagnosisRecordResponse> records(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码不能小于 1") int pageNum,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页最多查询 100 条") int pageSize,

            @RequestParam(required = false)
            @Size(max = 100, message = "设备标识不能超过 100 个字符")
            String deviceId) {

        return recordService.page(pageNum, pageSize, deviceId);
    }

    @GetMapping("/records/{id}")
    public DiagnosisRecordResponse record(
            @PathVariable
            @Positive(message = "记录 ID 必须为正数") Long id) {

        DiagnosisRecord record = recordService.getById(id);
        if (record == null) {
            throw new ResourceNotFoundException("诊断记录不存在");
        }
        return recordService.toResponse(record);
    }
}
