package com.simple.jigou.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

/**
 * Redis 统一配置类。
 *
 * <p>作用：
 * <ol>
 *     <li>自定义 RedisTemplate 的序列化规则，解决 Redis 中数据乱码以及反序列化类型丢失的问题；</li>
 *     <li>定义 Spring Cache 使用的 RedisCacheManager，
 *         让 {@code @Cacheable} 缓存的数据与 RedisTemplate 使用同一套 JSON 格式。</li>
 * </ol>
 */
@Configuration
public class RedisConfig {

    /**
     * 通用 RedisTemplate。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 1. 配置 Key 的序列化器。
        // 使用 StringRedisSerializer，保证存入 Redis 的 Key 是纯字符串，方便在 redis-cli 中直接查看和排查。
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        // 2. 配置 Value 的序列化器。
        // 必须开启 DefaultTyping 写入 @class 类型信息，
        // 否则反序列化时 Long/Integer 会退化为 Integer、实体对象会退化为 LinkedHashMap。
        GenericJacksonJsonRedisSerializer valueSerializer = createJsonSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        // 3. 手动 new 出来的 RedisTemplate 必须调用此方法，序列化器才会生效。
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache 使用的 RedisCacheManager。
     * 与 RedisTemplate 共用同一套序列化规则，保证缓存数据可以直接用 RedisTemplate 读取排查。
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))   // 默认 30 分钟过期
                .disableCachingNullValues()         // 禁用 null 值缓存
                // key 使用 String 序列化器
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // value 使用与 RedisTemplate 一致的 JSON 序列化器
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(createJsonSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                // 针对 good_app_page 单独配置 5 分钟过期
                .withCacheConfiguration("featured_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    /**
     * 构建 Jackson 3 的 JSON 序列化器。
     *
     * <p>Spring Data Redis 4 基于 Jackson 3，使用 GenericJacksonJsonRedisSerializer。
     * Jackson 3 的 JsonMapper 已内置 Java Time 支持，无需再注册 JavaTimeModule。
     *
     * <p>默认不写入类型信息，这里开启 DefaultTyping 写入 @class，
     * 避免反序列化后 Long/Integer 变成 String、实体对象变成 LinkedHashMap。
     *
     * <p>生产环境建议把 allowIfSubType(Object.class) 收紧为具体包名白名单，
     * 例如 com.simple.jigou.、java.util.、java.time.、java.lang.Long、java.lang.Double，
     * 防止反序列化漏洞。
     */
    private GenericJacksonJsonRedisSerializer createJsonSerializer() {

        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                // 开发环境为了方便，允许所有 Object 的子类型
                .allowIfSubType(Object.class)
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator) // 开启类型信息，写入 @class
                .build();
    }
}
