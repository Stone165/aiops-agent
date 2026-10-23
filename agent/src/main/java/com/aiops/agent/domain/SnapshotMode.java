package com.aiops.agent.domain;

import java.util.Locale;

/**
 * 诊断数据来源。
 *
 * <p>LOCAL：诊断开始时采集本机实时快照，工具返回本机指标。
 * TEXT：不读取本机指标，仅分析工单/用户提供的文字描述。</p>
 */
public enum SnapshotMode {
    LOCAL,
    TEXT;

    public static SnapshotMode from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效数据来源：" + value);
        }
    }
}
