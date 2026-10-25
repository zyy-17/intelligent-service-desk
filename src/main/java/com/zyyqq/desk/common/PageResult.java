package com.zyyqq.desk.common;

import lombok.Data;

/** 分页结果封装（列表/总数/页码/总页数） */
@Data
public class PageResult<T> {
    private java.util.List<T> list;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    /** 构造分页结果并计算总页数 */
    public static <T> PageResult<T> of(java.util.List<T> list, long total, int page, int pageSize) {
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        r.totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return r;
    }
}
