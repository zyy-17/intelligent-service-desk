package com.zyyqq.desk.common;

import lombok.Data;

/** 统一 API 响应体（code/message/data/requestId） */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;

    /** 构造成功响应（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    /** 构造成功响应（无数据） */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /** 构造错误响应 */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
