package com.zyyqq.desk.common;

import lombok.Data;

@Data
public class PageResult<T> {
    private java.util.List<T> list;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

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
