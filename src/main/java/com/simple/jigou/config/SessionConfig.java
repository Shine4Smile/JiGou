package com.simple.jigou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Spring Session 配置类。
 * 作用：专门定制 Spring Session 在 Redis 中存取数据时的序列化规则。
 */
@Configuration
public class SessionConfig {

    /**
     * 注意：Bean 名称必须严格为 "springSessionDefaultRedisSerializer"。
     * Spring Session 自动配置类会通过这个 Bean 名称来查找并覆盖默认的 JDK 序列化器。
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {

        // 同样构建多态类型验证器，解决 Session 属性（如 Long 类型的创建时间、超时时间）的反序列化类型丢失问题。
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        // 返回启用了默认类型化（DefaultTyping）的 Jackson 3 序列化器。
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }
}