-- AIOps Agent 数据库初始化脚本
-- 说明：本脚本可重复执行，不会清空已有数据。
-- 使用方式：mysql -u root -p < sql/schema.sql

CREATE DATABASE IF NOT EXISTS aiops_agent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE aiops_agent;

CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
    title VARCHAR(200) NOT NULL COMMENT '故障标题',
    description TEXT NOT NULL COMMENT '故障描述',
    device_id VARCHAR(100) DEFAULT NULL COMMENT '设备标识',
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '工单状态',
    root_cause TEXT DEFAULT NULL COMMENT '故障原因',
    solution TEXT DEFAULT NULL COMMENT '解决方案',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_incidents_status (status),
    INDEX idx_incidents_severity (severity),
    INDEX idx_incidents_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='故障工单表';

CREATE TABLE IF NOT EXISTS diagnosis_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '诊断记录ID',
    device_id VARCHAR(100) NOT NULL COMMENT '设备标识',
    description TEXT NOT NULL COMMENT '故障描述',
    answer TEXT COMMENT 'AI诊断结论',
    tool_call_count INT NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    tool_results JSON COMMENT '工具执行结果',
    simulated BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否使用模拟数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '诊断时间',
    INDEX idx_diagnosis_device (device_id),
    INDEX idx_diagnosis_created_at (created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='AI故障诊断记录表';
