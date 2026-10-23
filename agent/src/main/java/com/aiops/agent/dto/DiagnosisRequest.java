package com.aiops.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiagnosisRequest {

    @NotBlank(message = "设备标识不能为空")
    @Size(max = 100, message = "设备标识不能超过 100 个字符")
    private String deviceId;

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 4000, message = "故障描述不能超过 4000 个字符")
    private String description;

    /**
     * LOCAL：使用本机实时快照；TEXT：只分析描述文本。
     */
    @Pattern(
            regexp = "(?i)LOCAL|TEXT",
            message = "无效数据来源，可选值：LOCAL、TEXT")
    private String snapshotMode;
}
