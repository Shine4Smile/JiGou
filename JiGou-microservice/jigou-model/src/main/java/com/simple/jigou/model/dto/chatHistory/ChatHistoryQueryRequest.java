package com.simple.jigou.model.dto.chatHistory;

import com.simple.jigou.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分页查询对话历史请求参数接收
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用 id（查询某个应用的对话历史时必填）
     */
    private Long appId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 消息类型：user/ai/error（对应 ChatHistoryMessageTypeEnum）
     */
    private String messageType;

    /**
     * 消息内容（支持模糊查询）
     */
    private String message;

    /**
     * 游标：上一页最早一条消息的创建时间
     * 不传表示加载最新的一页，传入表示向前（更早）加载更多历史记录
     */
    private LocalDateTime lastCreateTime;

    /**
     * 游标：上一页最早一条消息的 id
     * 创建时间精确到秒，同一秒内可能有多条消息，用 id 兜底避免漏查
     */
    private Long lastId;

    /**
     * 起始创建时间（管理员按时间范围查询）
     */
    private LocalDateTime createTimeStart;

    /**
     * 结束创建时间（管理员按时间范围查询）
     */
    private LocalDateTime createTimeEnd;

    private static final long serialVersionUID = 1L;
}
