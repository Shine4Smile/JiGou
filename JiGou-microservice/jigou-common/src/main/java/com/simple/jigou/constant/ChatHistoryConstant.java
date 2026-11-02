package com.simple.jigou.constant;

/**
 * 对话历史相关常量
 */
public interface ChatHistoryConstant {

    /**
     * 应用对话历史默认加载条数（类似聊天软件，首次加载最新 10 条消息）
     */
    Integer DEFAULT_PAGE_SIZE = 10;

    /**
     * 应用对话历史每次最多加载条数
     */
    Integer MAX_PAGE_SIZE = 20;
}
