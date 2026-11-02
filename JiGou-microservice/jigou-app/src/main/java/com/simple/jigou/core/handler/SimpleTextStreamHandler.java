package com.simple.jigou.core.handler;

import cn.hutool.core.util.StrUtil;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.ChatHistoryMessageTypeEnum;
import com.simple.jigou.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, loginUser.getId(), aiResponse, ChatHistoryMessageTypeEnum.AI);
                })
                .doOnError(error -> {
                    // AI 生成失败也要记录，但类型标记为 ERROR：前端按失败样式展示，加载会话记忆时也会跳过
                    String errorMessage = "AI回复失败: " + StrUtil.blankToDefault(error.getMessage(), error.getClass().getSimpleName());
                    chatHistoryService.addChatMessage(appId, loginUser.getId(), errorMessage, ChatHistoryMessageTypeEnum.ERROR);
                });
    }
}
