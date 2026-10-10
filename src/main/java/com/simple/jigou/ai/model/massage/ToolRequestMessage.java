package com.simple.jigou.ai.model.massage;

import dev.langchain4j.model.chat.response.PartialToolCall;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {
    /**
     * 用于区分单次响应中的多个工具调用。
     * 早期版本直接提供了完整的 ToolExecutionRequest。
     * 但新版本后参数变为流式碎片，必须依赖该 index 将碎片拼接到对应的工具上。老版本写法如下
     * .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
     * ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
     * sink.next(JSONUtil.toJsonStr(toolRequestMessage));
     * })
     **/
    private Integer index;

    private String id;

    private String name;

    private String arguments;

    public ToolRequestMessage(PartialToolCall partialToolCall) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.index = partialToolCall.index();
        this.id = partialToolCall.id();
        this.name = partialToolCall.name();
        this.arguments = partialToolCall.partialArguments();
    }
}
