package com.simple.jigou.ai.memory;

import cn.hutool.core.collection.CollUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用消息配对器
 * <p>
 * 模型服务端要求 assistant 的每个 tool_calls 后面必须紧跟对应 tool_call_id 的结果消息，
 * 否则整个请求会被直接拒绝（如 DeepSeek 返回 400 invalid_request_error）。
 * <p>
 * 而会话记忆是「先写入带工具调用的 assistant 消息，再逐个写入工具结果」的，
 * 中间任意环节被中断（工具参数解析失败、工具轮次超限、模型流超时、用户停止、连接断开），
 * 都会在记忆中永久残留「有 tool_calls 无 tool 结果」的非法序列。
 * <p>
 * 本类负责在把记忆交给模型之前修复这类脏数据：
 * <ul>
 *     <li>缺失的工具结果按 tool_calls 顺序补齐占位结果（标记为错误，提示模型重新生成）；</li>
 *     <li>没有前置工具调用的孤立工具结果直接丢弃；</li>
 *     <li>已经是合法配对的记忆原样返回。</li>
 * </ul>
 */
@Slf4j
public final class ToolCallMessagePairer {

    /**
     * 工具调用被中断时，用于补齐缺失结果的占位内容
     */
    public static final String INTERRUPTED_TOOL_RESULT = "工具调用未完成（生成过程被中断），请基于已有信息重新生成。";

    private ToolCallMessagePairer() {
    }

    /**
     * 修复记忆中的工具调用消息配对关系
     *
     * @param messages 记忆中的原始消息
     * @return 修复后的消息列表，可安全发送给模型
     */
    public static List<ChatMessage> repair(List<ChatMessage> messages) {
        if (CollUtil.isEmpty(messages)) {
            return new ArrayList<>();
        }
        List<ChatMessage> repaired = new ArrayList<>(messages.size());
        int size = messages.size();
        for (int i = 0; i < size; i++) {
            ChatMessage message = messages.get(i);
            // 1. 孤立的工具结果：没有前置的带工具调用 assistant 消息，直接丢弃，否则同样会被模型拒绝
            if (message instanceof ToolExecutionResultMessage resultMessage) {
                log.warn("检测到没有对应工具调用的工具结果消息，已丢弃，toolCallId: {}", resultMessage.id());
                continue;
            }
            repaired.add(message);
            // 2. 普通消息无需处理
            if (!(message instanceof AiMessage aiMessage) || !aiMessage.hasToolExecutionRequests()) {
                continue;
            }
            // 3. 带工具调用的 assistant 消息：紧跟其后的工具结果按 toolCallId 建立索引
            Map<String, ToolExecutionResultMessage> followingResults = new LinkedHashMap<>();
            int next = i + 1;
            while (next < size && messages.get(next) instanceof ToolExecutionResultMessage resultMessage) {
                followingResults.putIfAbsent(resultMessage.id(), resultMessage);
                next++;
            }
            // 4. 按工具调用顺序补齐结果，缺失的结果用占位内容填充
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutionResultMessage followingResult = followingResults.get(toolExecutionRequest.id());
                if (followingResult != null) {
                    repaired.add(followingResult);
                    continue;
                }
                log.warn("检测到工具调用缺少结果消息，已自动补齐占位结果，toolCallId: {}, toolName: {}",
                        toolExecutionRequest.id(), toolExecutionRequest.name());
                repaired.add(ToolExecutionResultMessage.builder()
                        .id(toolExecutionRequest.id())
                        .toolName(toolExecutionRequest.name())
                        .text(INTERRUPTED_TOOL_RESULT)
                        .isError(true)
                        .build());
            }
            // 5. 原列表中的工具结果已在上一步按工具调用顺序补齐，跳过避免重复
            i = next - 1;
        }
        return repaired;
    }
}
