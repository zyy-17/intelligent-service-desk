package com.zyyqq.desk.common;

/** 业务异常（携带业务错误码） */
public class BizException extends RuntimeException {
    private final int code;

    /** 默认 400 业务异常 */
    public BizException(String message) {
        this(400, message);
    }

    /** 指定错误码的业务异常 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 获取错误码 */
    public int getCode() {
        return code;
    }

    /** 401 未授权异常 */
    public static BizException unauthorized(String msg) {
        return new BizException(401, msg);
    }

    /** 403 无权限异常 */
    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }

    /** 404 不存在异常 */
    public static BizException notFound(String msg) {
        return new BizException(404, msg);
    }
}
