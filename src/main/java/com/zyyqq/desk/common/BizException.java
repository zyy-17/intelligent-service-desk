package com.zyyqq.desk.common;

public class BizException extends RuntimeException {
    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException unauthorized(String msg) {
        return new BizException(401, msg);
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg);
    }
}
