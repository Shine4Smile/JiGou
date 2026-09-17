package com.simple.jigou.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用对话历史游标分页返回包装类
 * 用于聊天式加载：首次加载最新的一页消息，之后带上游标向前加载更多历史记录
 */
@Data
public class ChatHistoryPageVO implements Serializable {

    /**
     * 消息列表（按创建时间升序返回，前端可直接顺序渲染）
     */
    private List<ChatHistoryVO> records;

    /**
     * 是否还有更早的历史消息（为 true 时前端可展示「加载更多」）
     */
    private Boolean hasMore;

    /**
     * 下一页游标：本页最早一条消息的创建时间（没有更多消息时为 null）
     */
    private LocalDateTime nextLastCreateTime;

    /**
     * 下一页游标：本页最早一条消息的 id（没有更多消息时为 null）
     */
    private Long nextLastId;

    private static final long serialVersionUID = 1L;
}
