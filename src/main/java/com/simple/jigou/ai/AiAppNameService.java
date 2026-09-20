package com.simple.jigou.ai;

import com.simple.jigou.ai.model.AppNameResult;
import dev.langchain4j.service.SystemMessage;

/**
 * AI 应用名称生成服务
 * 仅在创建应用时根据初始需求生成名称，属于单轮调用，因此不关联会话记忆
 */
public interface AiAppNameService {

    /**
     * 根据应用初始需求生成应用名称
     *
     * @param userMessage 应用初始需求（initPrompt）
     * @return 结构化的应用名称结果
     */
    @SystemMessage(fromResource = "prompt/app-name-system-prompt.txt")
    AppNameResult generateAppName(String userMessage);
}
