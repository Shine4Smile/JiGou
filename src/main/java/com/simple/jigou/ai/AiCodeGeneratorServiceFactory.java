package com.simple.jigou.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.simple.jigou.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 创建工厂类初始化AI服务
 */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    /**
     * redis记忆存储
     */
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<Long, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 为保证和引入redis记忆组件之前代码兼容，保留一个默认 Bean
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 应用命名 AI 服务（单轮调用，不关联会话记忆，与 appId 无关可复用单例）
     */
    @Bean
    public AiAppNameService aiAppNameService() {
        return AiServices.builder(AiAppNameService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }

    /**
     * 失效指定应用的 AI 服务实例缓存
     * <p>
     * 应用被删除后调用：缓存中的实例持有该应用的会话记忆，继续保留既占用内存，语义上也已无归属
     *
     * @param appId 应用 id
     */
    public void invalidate(long appId) {
        serviceCache.invalidate(appId);
        log.info("已失效应用 AI 服务实例缓存，appId: {}", appId);
    }

    /**
     * 删除指定应用在 redis 中的会话记忆
     * <p>
     * 应用被删除后调用：对话历史已随应用一起删除，redis 中缓存的上下文也必须清理
     *
     * @param appId 应用 id
     */
    public void deleteChatMemory(long appId) {
        redisChatMemoryStore.deleteMessages(appId);
        log.info("已删除应用会话记忆，appId: {}", appId);
    }

    /**
     * 方案一：内置机制隔离。给AI服务方法增加@MemoryId int memoryId注解和参数，通过chatMemoryProvider为每个appId分配会话记忆
     * 方案二：AI service隔离。根据appId获取服务，给每个应用分配一个专属的AI service，每个AI service绑定独立的会话记忆
     * 这里采用方案二
     *
     * @return
     */
    public AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }
}
