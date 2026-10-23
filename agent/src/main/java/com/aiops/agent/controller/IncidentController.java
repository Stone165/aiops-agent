package com.aiops.agent.controller;

import com.aiops.agent.common.PageResponse;
import com.aiops.agent.common.ResourceNotFoundException;
import com.aiops.agent.dto.IncidentCreateRequest;
import com.aiops.agent.dto.IncidentResolutionRequest;
import com.aiops.agent.dto.IncidentStats;
import com.aiops.agent.dto.IncidentStatusRequest;
import com.aiops.agent.entity.Incident;
import com.aiops.agent.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
@Validated
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<Incident> create(
            @Valid @RequestBody IncidentCreateRequest request) {

        Incident incident = new Incident();
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident.setDeviceId(request.getDeviceId());
        incident.setSeverity(request.getSeverity());

        Incident created = incidentService.create(incident);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public Incident getById(
            @PathVariable
            @Positive(message = "工单 ID 必须为正数") Long id) {

        Incident incident = incidentService.getById(id);
        if (incident == null) {
            throw new ResourceNotFoundException("工单不存在");
        }
        return incident;
    }

    @GetMapping
    public List<Incident> listAll() {
        return incidentService.listAll();
    }

    @GetMapping("/stats")
    public IncidentStats stats() {
        return incidentService.stats();
    }

    @GetMapping("/page")
    public PageResponse<Incident> page(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码不能小于 1") int pageNum,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页最多查询 100 条") int pageSize) {

        return PageResponse.of(incidentService.page(pageNum, pageSize));
    }

    @GetMapping("/search")
    public PageResponse<Incident> search(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码不能小于 1") int pageNum,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页最多查询 100 条") int pageSize,

            @RequestParam(required = false)
            @Pattern(
                    regexp = "(?i)OPEN|IN_PROGRESS|RESOLVED|CLOSED",
                    message = "无效状态，可选值：OPEN、IN_PROGRESS、RESOLVED、CLOSED")
            String status,

            @RequestParam(required = false)
            @Pattern(
                    regexp = "(?i)LOW|MEDIUM|HIGH|CRITICAL",
                    message = "无效严重程度，可选值：LOW、MEDIUM、HIGH、CRITICAL")
            String severity,

            @RequestParam(required = false)
            @Size(max = 100, message = "关键词不能超过 100 个字符")
            String keyword) {

        return PageResponse.of(incidentService.pageByCondition(
                pageNum, pageSize, status, severity, keyword));
    }

    @PutMapping("/{id}/status")
    public Map<String, Object> updateStatus(
            @PathVariable
            @Positive(message = "工单 ID 必须为正数") Long id,
            @Valid @RequestBody IncidentStatusRequest request) {

        Incident updated = incidentService.updateStatus(id, request.getStatus());
        return Map.of(
                "message", "工单状态更新成功",
                "incident", updated);
    }

    @PutMapping("/{id}/resolution")
    public Map<String, Object> updateResolution(
            @PathVariable
            @Positive(message = "工单 ID 必须为正数") Long id,
            @Valid @RequestBody IncidentResolutionRequest request) {

        Incident updated = incidentService.updateResolution(id, request);
        return Map.of(
                "message", "工单处理信息更新成功",
                "incident", updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable
            @Positive(message = "工单 ID 必须为正数") Long id) {

        if (!incidentService.delete(id)) {
            throw new ResourceNotFoundException("工单不存在");
        }
        return ResponseEntity.noContent().build();
    }
}
