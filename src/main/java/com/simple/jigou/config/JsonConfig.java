package com.simple.jigou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 全局序列化配置类。
 *
 * <p>核心作用：
 * 将后端返回给前端的 Long / long 类型字段统一序列化为 JSON 字符串，
 * 避免前端 JavaScript 因 Number 精度限制（安全整数上限 2^53 - 1，约 9e15）
 * 导致雪花算法 ID 等 19 位大整数精度丢失。
 *
 * <p>生效方向：
 * 仅影响「序列化」过程，即 Java 对象 → JSON（后端返回给前端）。
 * 不影响「反序列化」过程，即 JSON → Java 对象（前端传参给后端），
 * 前端传字符串 "123" 或数字 123 均可被正常解析为 Long。
 *
 * <p>生效范围：
 * 全局生效。项目中所有 Long / long 类型字段（包括集合、Map 中的元素）
 * 都会被序列化为字符串。
 *
 * <p>注意事项：
 * 1. 只注册了 Serializer，未注册 Deserializer，因此不影响前端传参。
 * 2. Integer / int / String / LocalDateTime 等其他类型不受影响。
 * 3. 若某个 Long 字段前端期望收到数字而非字符串，
 * 可通过在该字段上单独添加 @JsonSerialize 或调整字段类型来覆盖全局配置。
 *
 * @author simple
 */
@Configuration
public class JsonConfig {

    /**
     * 注册 Long 转 String 的全局序列化模块。
     *
     * <p>Spring Boot 会自动发现该 SimpleModule Bean，
     * 并将其注册到全局的 ObjectMapper 中，无需手动调用 registerModule。
     *
     * <p>同时注册 Long.class 和 Long.TYPE（即 long.class），
     * 确保包装类型 Long 和基本类型 long 都能被正确处理。
     *
     * @return 配置好的 SimpleModule 实例
     */
    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
