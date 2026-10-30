package com.zyyqq.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 网关启动类：统一入口，按路径把请求转发到注册中心里的服务 */
@SpringBootApplication
public class GatewayApplication {

    /** 启动网关 */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
