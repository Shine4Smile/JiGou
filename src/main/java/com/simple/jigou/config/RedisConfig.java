package com.simple.jigou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * 通用 Redis 配置类。
 * 作用：自定义 RedisTemplate 的序列化规则，解决 Redis 中数据乱码以及反序列化类型丢失的问题。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 1. 配置 Key 的序列化器。
        // 使用 StringRedisSerializer，保证存入 Redis 的 Key 是纯字符串，方便在 redis-cli 中直接查看和排查。
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 2. 配置 Value 的序列化器。
        // Spring Boot 4 默认使用 Jackson 3，采用 Builder 模式构建序列化器。
        // 核心痛点：Jackson 3 的 GenericJacksonJsonRedisSerializer 默认不写入类型信息。
        // 如果不写入类型信息，反序列化时 Long、Integer 等类型会退化为 String 或 LinkedHashMap，导致强制转换异常。
        // 解决方案：通过 PolymorphicTypeValidator（多态类型验证器）开启默认类型化，在 JSON 中写入 @class 字段。
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                // 开发环境为了方便，允许所有 Object 的子类型。
                // 生产环境建议收紧：.allowIfBaseType("com.simple.jigou.").allowIfBaseType("java.util.")
                .allowIfSubType(Object.class)
                .build();

        GenericJacksonJsonRedisSerializer jsonSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(typeValidator) // 开启类型信息，写入 @class
                        .build();

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 3. 初始化 RedisTemplate。
        // 手动 new 出来的 RedisTemplate 必须调用此方法，以确保配置的序列化器生效。
        template.afterPropertiesSet();
        return template;
    }
}