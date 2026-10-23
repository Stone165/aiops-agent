package com.aiops.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IncidentCreateRequest {

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "工单标题不能超过 200 个字符")
    private String title;

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 5000, message = "故障描述不能超过 5000 个字符")
    private String description;

    @Size(max = 100, message = "设备标识不能超过 100 个字符")
    private String deviceId;

    @Pattern(
            regexp = "(?i)LOW|MEDIUM|HIGH|CRITICAL",
            message = "无效严重程度，可选值：LOW、MEDIUM、HIGH、CRITICAL")
    private String severity;
}
