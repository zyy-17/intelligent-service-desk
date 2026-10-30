package com.zyyqq.desk.security;

/** 认证上下文：基于 ThreadLocal 保存当前请求的登录用户 */
public class AuthContext {
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    /** 设置当前登录用户 */
    public static void set(UserInfo info) {
        HOLDER.set(info);
    }

    /** 获取当前登录用户（可能为 null） */
    public static UserInfo get() {
        return HOLDER.get();
    }

    /** 获取当前登录用户，未登录则抛 401 */
    public static UserInfo require() {
        UserInfo u = HOLDER.get();
        if (u == null) {
            throw new com.zyyqq.desk.common.BizException(401, "未登录");
        }
        return u;
    }

    /** 清除当前线程的用户信息 */
    public static void clear() {
        HOLDER.remove();
    }

    /** 登录用户信息记录 */
    public record UserInfo(long id, String username, String displayName, String role) {
    }
}
