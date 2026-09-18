package com.simple.jigou.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 聊天记忆存储配置。
 * 从 spring.data.redis.* 读取连接信息，并注册 RedisChatMemoryStore。
 * 当前仅映射了 host、port、password、ttl；还可设置更多参数（如 database、timeout、
 * 连接池、SSL、sentinel 等）。
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    /**
     * Redis 主机地址
     */
    private String host;

    /**
     * Redis 端口
     */
    private int port;

    /**
     * Redis 密码，无密码可空
     */
    private String password;

    /**
     * 聊天记忆过期时间（秒）
     */
    private long ttl;

    /**
     * 构建 RedisChatMemoryStore Bean，供 AI 对话记忆使用
     */
    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .ttl(ttl)
                .build();
    }
}
