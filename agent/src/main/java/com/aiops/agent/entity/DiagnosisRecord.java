package com.aiops.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("diagnosis_records")
public class DiagnosisRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String description;

    private String answer;

    private Integer toolCallCount;

    /**
     * 保存 CPU、内存、网络、服务等工具的执行结果。
     * 使用 JSON 字符串存储。
     */
    private String toolResults;

    private Boolean simulated;

    private LocalDateTime createdAt;
}
