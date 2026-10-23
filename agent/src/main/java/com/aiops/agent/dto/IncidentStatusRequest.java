package com.aiops.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IncidentStatusRequest {

    @NotBlank(message = "工单状态不能为空")
    @Pattern(
            regexp = "(?i)OPEN|IN_PROGRESS|RESOLVED|CLOSED",
            message = "无效状态，可选值：OPEN、IN_PROGRESS、RESOLVED、CLOSED")
    private String status;
}
