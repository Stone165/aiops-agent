package com.aiops.agent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("status", "UP");
        result.put("message", "智能运维平台运行正常");
        result.put("service", "aiops-agent");
        result.put("time", LocalDateTime.now());
        return result;
    }
}
