package com.simple.jigou.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * langchain4j 的 HTTP 客户端配置
 * <p>
 * langchain4j 的 OpenAi Starter 默认使用 Spring RestClient，其底层为 Apache HttpClient 5（由 WebDriverManager 传递引入）。
 * 实测该客户端读取 DeepSeek 的 chunked 响应时只会拿到 1 个字节（响应体被截断），
 * 导致同步模型调用抛 JsonEOFException，应用命名、代码生成类型智能路由等依赖同步 ChatModel 的能力全部失败。
 * <p>
 * 这里使用 langchain4j 预留的扩展点：自动配置 {@code OpenAiAutoConfiguration} 中的
 * {@code openAiChatModelHttpClientBuilder}、{@code openAiStreamingChatModelHttpClientBuilder}
 * 都标注了 {@code @ConditionalOnMissingBean(name = ...)}，因此只要提供**同名** Bean 就能覆盖默认实现。
 * 注意：Bean 名称必须与自动配置中的常量完全一致，不能随意改名，否则覆盖不生效。
 * <p>
 * 统一替换为 JDK 自带的 {@link java.net.http.HttpClient} 实现，规避 Apache HttpClient 5 的响应截断问题。
 * 说明：同时引入 langchain4j-http-client-jdk 后类路径上会存在两个 HttpClientBuilderFactory 实现，
 * 若后续新增「未显式指定 httpClientBuilder」的模型，需要在构建时显式传入
 * {@code JdkHttpClient.builder()}，或设置系统属性
 * {@code langchain4j.http.clientBuilderFactory=dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory}，
 * 否则 langchain4j 会抛出「multiple HTTP clients have been found」异常。
 */
@Configuration
public class LangChain4jHttpClientConfig {

    /**
     * 同步 ChatModel 使用的 HTTP 客户端
     * <p>
     * Bean 名称必须与 langchain4j 自动配置中的 {@code openAiChatModelHttpClientBuilder} 保持一致
     */
    @Bean
    public HttpClientBuilder openAiChatModelHttpClientBuilder() {
        return JdkHttpClient.builder();
    }

    /**
     * 流式 StreamingChatModel 使用的 HTTP 客户端
     * <p>
     * Bean 名称必须与 langchain4j 自动配置中的 {@code openAiStreamingChatModelHttpClientBuilder} 保持一致
     */
    @Bean
    public HttpClientBuilder openAiStreamingChatModelHttpClientBuilder() {
        return JdkHttpClient.builder();
    }
}
