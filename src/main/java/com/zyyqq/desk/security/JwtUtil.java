package com.zyyqq.desk.security;

import tools.jackson.databind.ObjectMapper;
import com.zyyqq.desk.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** 轻量 JWT 工具（HS256），签发与校验登录凭证 */
@Component
public class JwtUtil {

    private final byte[] secret;
    private final long expireSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(AppProperties props) {
        this.secret = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.expireSeconds = props.getJwt().getExpireSeconds();
    }

    /** 生成 JWT token */
    public String create(long uid, String username, String displayName, String role, int status) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("username", username);
        payload.put("displayName", displayName);
        payload.put("role", role);
        payload.put("status", status);
        payload.put("exp", now + expireSeconds);
        payload.put("iat", now);

        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String body;
        try {
            body = b64(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 序列化失败", e);
        }
        String sign = b64(hmac(header + "." + body));
        return header + "." + body + "." + sign;
    }

    /** 校验并解析 JWT token，返回 payload */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("非法 token");
        }
        String expected = b64(hmac(parts[0] + "." + parts[1]));
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("签名校验失败");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Object exp = payload.get("exp");
            if (exp != null && Long.parseLong(String.valueOf(exp)) < System.currentTimeMillis() / 1000) {
                throw new IllegalArgumentException("token 已过期");
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("token 解析失败", e);
        }
    }

    /** 字节数组 Base64URL 编码 */
    private String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** 字符串 Base64URL 编码 */
    private String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 计算 HMAC-SHA256 签名 */
    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** 恒定时间比较（防时序攻击） */
    private boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < ba.length; i++) {
            r |= ba[i] ^ bb[i];
        }
        return r == 0;
    }
}
