package com.simple.jigou.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.simple.jigou.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.simple.jigou.model.entity.ChatHistory;
import com.simple.jigou.model.enums.ChatHistoryMessageTypeEnum;
import com.simple.jigou.model.vo.ChatHistoryPageVO;
import com.simple.jigou.model.vo.ChatHistoryVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层。
 *
 * @author simple
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话消息
     * 用户消息在发送时落库，AI 消息在生成完成后落库，生成失败时落库错误信息，保证对话记录完整
     *
     * @param appId       应用 id
     * @param userId      创建用户 id
     * @param message     消息内容
     * @param messageType 消息类型
     * @return 是否保存成功
     */
    boolean addChatMessage(Long appId, Long userId, String message, ChatHistoryMessageTypeEnum messageType);

    /**
     * 加载某个应用的一页对话历史（游标分页，支持向前加载更多）
     *
     * @param appId          应用 id
     * @param pageSize       每次加载条数（为空取默认 10 条，最多 20 条）
     * @param lastCreateTime 游标：上一页最早一条消息的创建时间，为空表示加载最新的一页
     * @param lastId         游标：上一页最早一条消息的 id
     * @return 游标分页结果（消息按创建时间升序 + 是否还有更早的消息 + 下一页游标）
     */
    ChatHistoryPageVO listAppChatHistory(Long appId, Integer pageSize, LocalDateTime lastCreateTime, Long lastId);

    /**
     * 分页查询所有应用的对话历史（管理员，按时间降序排序，便于内容监管）
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 脱敏后的对话历史分页数据
     */
    Page<ChatHistoryVO> listChatHistoryVOByPage(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 构造分页查询条件
     * 由于使用mybatis-flex，所以将查询请求转换成 QueryWrapper
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 关联查询应用名称与用户脱敏信息的对话历史列表
     *
     * @param chatHistoryList 脱敏前的对话历史列表
     * @return 脱敏后的对话历史列表
     */
    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList);
}
