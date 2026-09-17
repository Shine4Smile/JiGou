package com.simple.jigou.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.simple.jigou.constant.ChatHistoryConstant;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.mapper.AppMapper;
import com.simple.jigou.mapper.ChatHistoryMapper;
import com.simple.jigou.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.ChatHistory;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.ChatHistoryMessageTypeEnum;
import com.simple.jigou.model.vo.ChatHistoryPageVO;
import com.simple.jigou.model.vo.ChatHistoryVO;
import com.simple.jigou.model.vo.UserVO;
import com.simple.jigou.service.ChatHistoryService;
import com.simple.jigou.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 对话历史 服务层实现。
 *
 * <p>注意：这里依赖的是 UserService 与 AppMapper，而不是 AppService。
 * 因为 AppService 需要调用本服务保存对话消息、删除应用时关联删除对话历史，
 * 若本服务反向依赖 AppService 会形成循环依赖，因此关联查询应用信息时直接使用 AppMapper。
 *
 * @author simple
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private UserService userService;

    @Resource
    private AppMapper appMapper;

    /**
     * 添加对话消息
     *
     * @param appId       应用 id
     * @param userId      创建用户 id
     * @param message     消息内容
     * @param messageType 消息类型
     * @return 是否保存成功
     */
    @Override
    public boolean addChatMessage(Long appId, Long userId, String message, ChatHistoryMessageTypeEnum messageType) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(messageType == null, ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        // 2. 校验应用是否存在，避免应用已被删除时产生脏数据
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 构造入库对象并保存（创建时间等字段由数据库默认值补齐）
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setUserId(userId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageType.getValue());
        return this.save(chatHistory);
    }

    /**
     * 加载某个应用的一页对话历史（游标分页，支持向前加载更多）
     *
     * @param appId          应用 id
     * @param pageSize       每次加载条数
     * @param lastCreateTime 游标：上一页最早一条消息的创建时间
     * @param lastId         游标：上一页最早一条消息的 id
     * @return 游标分页结果
     */
    @Override
    public ChatHistoryPageVO listAppChatHistory(Long appId, Integer pageSize, LocalDateTime lastCreateTime, Long lastId) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 2. 处理每次加载条数：默认 10 条，最多 20 条
        int currentPageSize = (pageSize == null || pageSize <= 0)
                ? ChatHistoryConstant.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, ChatHistoryConstant.MAX_PAGE_SIZE);
        // 3. 构造查询条件：按创建时间倒序（同一秒的消息按 id 倒序），保证最新或更早的消息优先查出
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .orderBy("createTime", false)
                .orderBy("id", false)
                .limit(currentPageSize);
        // 4. 游标条件：创建时间更早，或创建时间相同但 id 更小
        // 数据库中的创建时间精确到秒，同一秒内可能有多条消息，只按时间过滤会漏查，因此用 id 兜底
        // 注意：and / or 存在 LambdaGetter 重载，这里显式声明为 Consumer 避免类型推断歧义
        if (lastCreateTime != null && lastId != null) {
            queryWrapper.and((Consumer<QueryWrapper>) wrapper -> wrapper
                    .lt("createTime", lastCreateTime)
                    .or((Consumer<QueryWrapper>) inner -> inner
                            .eq("createTime", lastCreateTime)
                            .lt("id", lastId)));
        } else if (lastCreateTime != null) {
            // 只传了时间游标：按创建时间向前加载
            queryWrapper.lt("createTime", lastCreateTime);
        } else if (lastId != null) {
            // 只传了 id 游标：chat_history.id 自增，可直接按 id 向前加载
            queryWrapper.lt("id", lastId);
        }
        // 5. 查询出一页数据（倒序），再反转成升序返回，便于前端顺序渲染、追加
        List<ChatHistory> chatHistoryList = this.list(queryWrapper);
        if (CollUtil.isEmpty(chatHistoryList)) {
            ChatHistoryPageVO emptyPage = new ChatHistoryPageVO();
            emptyPage.setRecords(new ArrayList<>());
            emptyPage.setHasMore(false);
            return emptyPage;
        }
        // 查满一页说明可能还有更早的历史消息
        boolean hasMore = chatHistoryList.size() >= currentPageSize;
        // 倒序的最后一条即本页中最早的一条消息，作为下一页的游标
        ChatHistory earliestChatHistory = chatHistoryList.get(chatHistoryList.size() - 1);
        Collections.reverse(chatHistoryList);
        // 6. 组装返回结果
        ChatHistoryPageVO chatHistoryPageVO = new ChatHistoryPageVO();
        chatHistoryPageVO.setRecords(this.getChatHistoryVOList(chatHistoryList));
        chatHistoryPageVO.setHasMore(hasMore);
        chatHistoryPageVO.setNextLastCreateTime(hasMore ? earliestChatHistory.getCreateTime() : null);
        chatHistoryPageVO.setNextLastId(hasMore ? earliestChatHistory.getId() : null);
        return chatHistoryPageVO;
    }

    /**
     * 分页查询所有应用的对话历史（管理员，按时间降序排序）
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 脱敏后的对话历史分页数据
     */
    @Override
    public Page<ChatHistoryVO> listChatHistoryVOByPage(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        Page<ChatHistory> chatHistoryPage = this.page(Page.of(pageNum, pageSize),
                this.getQueryWrapper(chatHistoryQueryRequest));
        // 数据脱敏 + 关联查询应用名称
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(pageNum, pageSize, chatHistoryPage.getTotalRow());
        chatHistoryVOPage.setRecords(this.getChatHistoryVOList(chatHistoryPage.getRecords()));
        return chatHistoryVOPage;
    }

    /**
     * 构造分页查询条件
     * 由于使用mybatis-flex，所以将查询请求转换成 QueryWrapper
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = chatHistoryQueryRequest.getId();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        String message = chatHistoryQueryRequest.getMessage();
        LocalDateTime createTimeStart = chatHistoryQueryRequest.getCreateTimeStart();
        LocalDateTime createTimeEnd = chatHistoryQueryRequest.getCreateTimeEnd();
        String sortField = chatHistoryQueryRequest.getSortField();
        boolean ascend = "ascend".equals(chatHistoryQueryRequest.getSortOrder());
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("messageType", messageType)
                .like("message", message)
                // 管理员支持按时间范围查询，为空时自动忽略该条件
                .ge("createTime", createTimeStart)
                .le("createTime", createTimeEnd)
                // 默认按创建时间倒序，最新的消息排在最前，便于内容监管
                .orderBy(StrUtil.isNotBlank(sortField) ? sortField : "createTime", ascend);
        // 未指定排序字段时追加主键兜底排序：创建时间精确到秒，同一秒内的消息按写入顺序稳定排列
        if (StrUtil.isBlank(sortField)) {
            queryWrapper.orderBy("id", ascend);
        }
        return queryWrapper;
    }

    /**
     * 关联查询应用名称与用户脱敏信息的对话历史列表
     *
     * @param chatHistoryList 脱敏前的对话历史列表
     * @return 脱敏后的对话历史列表
     */
    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList) {
        if (CollUtil.isEmpty(chatHistoryList)) {
            return new ArrayList<>();
        }
        // 批量获取应用名称与用户信息，避免 N + 1 查询
        Set<Long> appIds = chatHistoryList.stream().map(ChatHistory::getAppId).collect(Collectors.toSet());
        Map<Long, String> appNameMap = appMapper.selectListByIds(appIds).stream()
                .collect(Collectors.toMap(App::getId, app -> StrUtil.blankToDefault(app.getAppName(), "未命名应用")));
        Set<Long> userIds = chatHistoryList.stream().map(ChatHistory::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return chatHistoryList.stream().map(chatHistory -> {
            ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
            BeanUtil.copyProperties(chatHistory, chatHistoryVO);
            chatHistoryVO.setAppName(appNameMap.get(chatHistory.getAppId()));
            chatHistoryVO.setUserVO(userVOMap.get(chatHistory.getUserId()));
            return chatHistoryVO;
        }).collect(Collectors.toList());
    }
}
