package com.aiops.agent.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 统一分页响应，避免直接暴露 MyBatis-Plus 内部字段。
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        long pageNum,
        long pageSize,
        long pages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getRecords(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages());
    }

    public static <T> PageResponse<T> of(
            List<T> records, long total, long pageNum, long pageSize) {
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResponse<>(records, total, pageNum, pageSize, pages);
    }
}
