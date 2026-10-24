package com.zyyqq.desk.security;

public class AuthContext {
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static void set(UserInfo info) {
        HOLDER.set(info);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static UserInfo require() {
        UserInfo u = HOLDER.get();
        if (u == null) {
            throw new com.zyyqq.desk.common.BizException(401, "未登录");
        }
        return u;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record UserInfo(long id, String username, String displayName, String role) {
    }
}
