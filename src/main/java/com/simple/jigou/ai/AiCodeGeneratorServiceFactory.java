package com.simple.jigou.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.simple.jigou.ai.memory.ToolCallPairingChatMemory;
import com.simple.jigou.ai.tools.ToolManager;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.model.enums.CodeGenTypeEnum;
import com.simple.jigou.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
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
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

    /**
     * redis记忆存储
     */
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
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
//    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
//        return serviceCache.get(appId, this::createAiCodeGeneratorService);
//    }

    /**
     * 根据 appId 获取服务（带缓存）这个方法是为了兼容历史逻辑
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 和代码生成类型获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 失效指定应用的 AI 服务实例缓存
     * <p>
     * 两种调用场景：
     * 1. 应用被删除：缓存中的实例持有该应用的会话记忆，继续保留既占用内存，语义上也已无归属
     * 2. 流式生成中断：会话记忆可能残留「有工具调用无工具结果」的非法序列，失效后下次请求会重建服务，
     * 并从数据库历史重新加载记忆，让记忆回到干净状态
     *
     * @param appId       应用 id
     * @param codeGenType 代码生成类型
     */
    public void invalidate(long appId, CodeGenTypeEnum codeGenType) {
        String key = buildCacheKey(appId, codeGenType);
        serviceCache.invalidate(key);
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
     * 构建应用的会话记忆
     * <p>
     * 记忆写入被中断时可能在 redis 中残留「有工具调用无工具结果」的非法序列，
     * 这里用 {@link ToolCallPairingChatMemory} 装饰，读取记忆交给模型前自动修复，保证请求始终合法
     *
     * @param appId 应用 id
     * @return 带工具调用修复能力的会话记忆
     */
    private ChatMemory buildChatMemory(long appId) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(40)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return new ToolCallPairingChatMemory(chatMemory);
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
        ChatMemory chatMemory = buildChatMemory(appId);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(openAiStreamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * 创建新的 AI 服务实例，区分不同代码生成类型使用不同的模型
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        ChatMemory chatMemory = buildChatMemory(appId);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目生成使用推理模型
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(toolManager.getAllTools())
                    //幻觉工具名称名称策略，处理AI出现幻觉找不到工具的情况
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .build();
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

}
