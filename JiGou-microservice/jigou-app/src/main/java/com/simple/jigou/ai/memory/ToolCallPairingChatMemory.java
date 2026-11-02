package com.simple.jigou.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

/**
 * 工具调用配对的会话记忆装饰器
 * <p>
 * 只在读取记忆（组装请求发送给模型）时修复「assistant 工具调用消息缺少结果消息」的脏数据，
 * 读写其余行为全部委托给原始记忆。
 * <p>
 * 之所以装饰 {@link ChatMemory} 而不是底层的 ChatMemoryStore：
 * 记忆的写入是「读 -> 追加 -> 整体回写」，如果在校验层补齐消息，
 * 正向写入工具结果的过程中就会把补齐出来的占位结果一起写回存储，
 * 从而产生重复的 tool_call_id 结果；而装饰记忆只在请求组装阶段生效，不会污染写入链路。
 */
public class ToolCallPairingChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    public ToolCallPairingChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public void add(Iterable<ChatMessage> messages) {
        delegate.add(messages);
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        delegate.set(messages);
    }

    @Override
    public List<ChatMessage> messages() {
        return ToolCallMessagePairer.repair(delegate.messages());
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
