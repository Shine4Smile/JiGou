package com.simple.jigou.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史信息返回包装类（关联查询应用名称与用户脱敏信息）
 */
@Data
public class ChatHistoryVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 消息
     */
    private String message;

    /**
     * 消息类型：user/ai/error（对应 ChatHistoryMessageTypeEnum）
     */
    private String messageType;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 应用名称（关联查询，便于管理员按应用监管对话内容）
     */
    private String appName;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建用户的用户信息（脱敏后）
     */
    private UserVO userVO;

    private static final long serialVersionUID = 1L;
}
