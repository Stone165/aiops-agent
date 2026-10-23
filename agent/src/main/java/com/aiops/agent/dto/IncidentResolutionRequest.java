package com.aiops.agent.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑工单的根因、解决方案与状态，至少需要提交一个有效字段。
 */
@Data
public class IncidentResolutionRequest {

    @Size(max = 5000, message = "根因不能超过 5000 个字符")
    private String rootCause;

    @Size(max = 5000, message = "解决方案不能超过 5000 个字符")
    private String solution;

    @Pattern(
            regexp = "(?i)OPEN|IN_PROGRESS|RESOLVED|CLOSED",
            message = "无效状态，可选值：OPEN、IN_PROGRESS、RESOLVED、CLOSED")
    private String status;
}
