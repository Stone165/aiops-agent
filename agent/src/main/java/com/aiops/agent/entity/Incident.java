package com.aiops.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data 
@TableName ("incidents")
public class Incident {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String deviceId;

    private String severity;

    private String status;

    private String rootCause;

    private String solution;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
