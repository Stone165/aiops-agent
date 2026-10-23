package com.aiops.agent.controller;

import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.service.LocalMonitorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本机实时监控快照。
 */
@RestController
@RequestMapping("/api/monitor")
@Validated
public class MonitorController {

    private final LocalMonitorService monitorService;

    public MonitorController(LocalMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 前端轮询入口：每次返回一份最新的本机快照。
     */
    @GetMapping("/local")
    public MonitorSnapshot local() {
        return monitorService.captureFresh();
    }

    /**
     * 兼容旧路径。当前监控目标固定为本机，deviceId 仅用于兼容调用方。
     */
    @GetMapping("/{deviceId}")
    public MonitorSnapshot byDevice(
            @PathVariable
            @NotBlank(message = "设备标识不能为空")
            @Size(max = 100, message = "设备标识不能超过 100 个字符")
            String deviceId) {
        return monitorService.captureFresh();
    }
}
