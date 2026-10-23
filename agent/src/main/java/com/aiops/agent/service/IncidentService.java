package com.aiops.agent.service;

import com.aiops.agent.common.ResourceNotFoundException;
import com.aiops.agent.domain.IncidentSeverity;
import com.aiops.agent.domain.IncidentStatus;
import com.aiops.agent.dto.IncidentResolutionRequest;
import com.aiops.agent.dto.IncidentStats;
import com.aiops.agent.entity.Incident;
import com.aiops.agent.mapper.IncidentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class IncidentService {

    private final IncidentMapper incidentMapper;

    public IncidentService(IncidentMapper incidentMapper) {
        this.incidentMapper = incidentMapper;
    }

    public Incident create(Incident incident) {
        incident.setId(null);
        incident.setTitle(incident.getTitle().trim());
        incident.setDescription(incident.getDescription().trim());
        incident.setDeviceId(trimToNull(incident.getDeviceId()));
        incident.setSeverity(IncidentSeverity.normalize(incident.getSeverity()));
        incident.setStatus(IncidentStatus.OPEN.name());
        incident.setRootCause(null);
        incident.setSolution(null);
        incident.setCreatedAt(null);
        incident.setUpdatedAt(null);

        incidentMapper.insert(incident);
        return incidentMapper.selectById(incident.getId());
    }

    public Incident getById(Long id) {
        return incidentMapper.selectById(id);
    }

    public Incident require(Long id) {
        Incident incident = incidentMapper.selectById(id);
        if (incident == null) {
            throw new ResourceNotFoundException("工单不存在");
        }
        return incident;
    }

    public List<Incident> listAll() {
        LambdaQueryWrapper<Incident> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Incident::getCreatedAt);
        return incidentMapper.selectList(wrapper);
    }

    public Incident updateStatus(Long id, String status) {
        Incident incident = require(id);
        incident.setStatus(IncidentStatus.normalize(status));
        incidentMapper.updateById(incident);
        return incidentMapper.selectById(id);
    }

    public Incident updateResolution(Long id, IncidentResolutionRequest request) {
        Incident incident = require(id);
        boolean changed = false;

        if (request.getRootCause() != null) {
            incident.setRootCause(trimToNull(request.getRootCause()));
            changed = true;
        }

        if (request.getSolution() != null) {
            incident.setSolution(trimToNull(request.getSolution()));
            changed = true;
        }

        if (StringUtils.hasText(request.getStatus())) {
            incident.setStatus(IncidentStatus.normalize(request.getStatus()));
            changed = true;
        }

        if (!changed) {
            throw new IllegalArgumentException("至少提交一个要更新的字段");
        }

        incidentMapper.updateById(incident);
        return incidentMapper.selectById(id);
    }

    public boolean delete(Long id) {
        return incidentMapper.deleteById(id) > 0;
    }

    public Page<Incident> page(int pageNum, int pageSize) {
        LambdaQueryWrapper<Incident> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Incident::getCreatedAt);

        Page<Incident> page = new Page<>(pageNum, pageSize);
        return incidentMapper.selectPage(page, wrapper);
    }

    public Page<Incident> pageByCondition(
            int pageNum,
            int pageSize,
            String status,
            String severity,
            String keyword) {

        LambdaQueryWrapper<Incident> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(Incident::getStatus, IncidentStatus.normalize(status));
        }

        if (StringUtils.hasText(severity)) {
            wrapper.eq(Incident::getSeverity, IncidentSeverity.normalize(severity));
        }

        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword.trim());
            wrapper.and(condition -> condition
                    .like(Incident::getTitle, escaped)
                    .or()
                    .like(Incident::getDescription, escaped)
                    .or()
                    .like(Incident::getDeviceId, escaped));
        }

        wrapper.orderByDesc(Incident::getCreatedAt);

        Page<Incident> page = new Page<>(pageNum, pageSize);
        return incidentMapper.selectPage(page, wrapper);
    }

    public IncidentStats stats() {
        return new IncidentStats(
                count(null),
                countByStatus(IncidentStatus.OPEN),
                countByStatus(IncidentStatus.IN_PROGRESS),
                countByStatus(IncidentStatus.RESOLVED),
                countByStatus(IncidentStatus.CLOSED),
                countBySeverity(IncidentSeverity.CRITICAL),
                countBySeverity(IncidentSeverity.HIGH));
    }

    private long countByStatus(IncidentStatus status) {
        return count(new LambdaQueryWrapper<Incident>()
                .eq(Incident::getStatus, status.name()));
    }

    private long countBySeverity(IncidentSeverity severity) {
        return count(new LambdaQueryWrapper<Incident>()
                .eq(Incident::getSeverity, severity.name()));
    }

    private long count(LambdaQueryWrapper<Incident> wrapper) {
        Long total = incidentMapper.selectCount(wrapper);
        return total == null ? 0 : total;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
