package com.zyyqq.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 客户端 IP 透传：把真实来源 IP 写入 X-Real-IP，避免开启 X-Forwarded 支持 */
@Component
public class ClientIpFilter implements GlobalFilter, Ordered {

    /** 转发前把 TCP 对端地址写入 X-Real-IP，并清掉客户端伪造的同名头 */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 1. 取连接的对端地址（此时还没被任何反向代理污染）
        String ip = "unknown";
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            ip = request.getRemoteAddress().getAddress().getHostAddress();
        }
        // 2. 覆盖式写入：先移除再设置，防止外部传入伪造值
        String clientIp = ip;
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove("X-Real-IP");
                    headers.set("X-Real-IP", clientIp);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 最先执行，保证后续过滤器看到的是净化后的请求头 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
