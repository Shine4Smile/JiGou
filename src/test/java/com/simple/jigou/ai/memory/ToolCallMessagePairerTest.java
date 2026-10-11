package com.simple.jigou.ai.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 工具调用消息配对器测试
 */
class ToolCallMessagePairerTest {

    private static final long APP_ID = 1L;

    private static ToolExecutionRequest toolRequest(String id, String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{\"relativePath\":\"index.html\"}")
                .build();
    }

    @Test
    void repairShouldKeepPairedMessagesUnchanged() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我生成一个博客首页"),
                AiMessage.from(toolRequest("call_1", "writeFile")),
                ToolExecutionResultMessage.from("call_1", "writeFile", "写入成功"),
                AiMessage.from("博客首页已生成完成")
        );
        List<ChatMessage> repaired = ToolCallMessagePairer.repair(messages);
        Assertions.assertEquals(messages, repaired);
    }

    @Test
    void repairShouldFillMissingToolResult() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我生成一个博客首页"),
                AiMessage.from(toolRequest("call_1", "writeFile"))
        );
        List<ChatMessage> repaired = ToolCallMessagePairer.repair(messages);
        Assertions.assertEquals(3, repaired.size());
        ToolExecutionResultMessage filled = (ToolExecutionResultMessage) repaired.get(2);
        Assertions.assertEquals("call_1", filled.id());
        Assertions.assertEquals("writeFile", filled.toolName());
        Assertions.assertEquals(Boolean.TRUE, filled.isError());
        Assertions.assertEquals(ToolCallMessagePairer.INTERRUPTED_TOOL_RESULT, filled.text());
    }

    @Test
    void repairShouldFillMissingResultBeforeFollowingUserMessage() {
        // 真实故障现场：上一次生成被中断，记忆里只留下带工具调用的 assistant 消息，紧接着就是新一轮的用户消息
        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我生成一个博客首页"),
                AiMessage.from(toolRequest("call_1", "writeFile")),
                UserMessage.from("把标题改成我的博客")
        );
        List<ChatMessage> repaired = ToolCallMessagePairer.repair(messages);
        Assertions.assertEquals(4, repaired.size());
        Assertions.assertTrue(repaired.get(2) instanceof ToolExecutionResultMessage);
        Assertions.assertEquals("call_1", ((ToolExecutionResultMessage) repaired.get(2)).id());
        Assertions.assertEquals(messages.get(2), repaired.get(3));
    }

    @Test
    void repairShouldFillOnlyMissingResultsInToolCallOrder() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我生成一个博客首页"),
                AiMessage.from(toolRequest("call_1", "writeFile"), toolRequest("call_2", "writeFile")),
                // 第二个工具的结果先落库，第一个工具的结果因为中断丢失
                ToolExecutionResultMessage.from("call_2", "writeFile", "写入成功")
        );
        List<ChatMessage> repaired = ToolCallMessagePairer.repair(messages);
        Assertions.assertEquals(4, repaired.size());
        ToolExecutionResultMessage first = (ToolExecutionResultMessage) repaired.get(2);
        ToolExecutionResultMessage second = (ToolExecutionResultMessage) repaired.get(3);
        // 结果必须与工具调用顺序一致
        Assertions.assertEquals("call_1", first.id());
        Assertions.assertEquals(ToolCallMessagePairer.INTERRUPTED_TOOL_RESULT, first.text());
        Assertions.assertEquals("call_2", second.id());
        Assertions.assertEquals("写入成功", second.text());
    }

    @Test
    void repairShouldDropOrphanToolResult() {
        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call_9", "writeFile", "游离的工具结果"),
                UserMessage.from("帮我生成一个博客首页"),
                AiMessage.from("博客首页已生成完成")
        );
        List<ChatMessage> repaired = ToolCallMessagePairer.repair(messages);
        Assertions.assertEquals(2, repaired.size());
        Assertions.assertEquals(messages.get(1), repaired.get(0));
        Assertions.assertEquals(messages.get(2), repaired.get(1));
    }

    @Test
    void repairShouldMakeInterruptedMemoryReadableWithoutPollutingStore() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        MessageWindowChatMemory rawMemory = MessageWindowChatMemory.builder()
                .id(APP_ID)
                .chatMemoryStore(store)
                .maxMessages(20)
                .build();
        ChatMemory chatMemory = new ToolCallPairingChatMemory(rawMemory);
        rawMemory.add(UserMessage.from("帮我生成一个博客首页"));
        // 模拟工具调用被中断：只写入了带工具调用的 assistant 消息，工具结果消息还没写入
        rawMemory.add(AiMessage.from(toolRequest("call_1", "writeFile")));

        List<ChatMessage> repaired = chatMemory.messages();
        Assertions.assertEquals(3, repaired.size());
        Assertions.assertTrue(repaired.get(2) instanceof ToolExecutionResultMessage);
        // 修复只发生在读取阶段，存储中的原始消息不能被改写
        Assertions.assertEquals(2, store.getMessages(APP_ID).size());

        // 用户重新发起生成后，工具结果正常写入，不会出现重复的 tool_call_id
        rawMemory.add(ToolExecutionResultMessage.from("call_1", "writeFile", "写入成功"));
        List<ChatMessage> afterAppend = chatMemory.messages();
        Assertions.assertEquals(3, afterAppend.size());
        Assertions.assertEquals(3, store.getMessages(APP_ID).size());
        long toolResultCount = afterAppend.stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .count();
        Assertions.assertEquals(1, toolResultCount);
        Assertions.assertEquals("写入成功", ((ToolExecutionResultMessage) afterAppend.get(2)).text());
    }
}
