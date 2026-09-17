package com.simple.jigou.controller;

import com.mybatisflex.core.paginate.Page;
import com.simple.jigou.annotation.AuthCheck;
import com.simple.jigou.common.BaseResponse;
import com.simple.jigou.common.ResultUtils;
import com.simple.jigou.constant.UserConstant;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.UserRoleEnum;
import com.simple.jigou.model.vo.ChatHistoryPageVO;
import com.simple.jigou.model.vo.ChatHistoryVO;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.ChatHistoryService;
import com.simple.jigou.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话历史 控制层。
 *
 * @author simple
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    /**
     * 加载某个应用的对话历史（仅应用创建者和管理员可见）
     *
     * <p>游标分页，类似聊天软件的消息加载机制：
     * 不传游标时加载最新的一页消息（默认 10 条），
     * 传入上一页返回的 nextLastCreateTime、nextLastId 时向前加载更早的历史记录。
     *
     * @param chatHistoryQueryRequest 查询请求（appId 必填，pageSize、lastCreateTime、lastId 可选）
     * @param request                 请求对象
     */
    @PostMapping("/app/list/page/vo")
    public BaseResponse<ChatHistoryPageVO> listAppChatHistory(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = chatHistoryQueryRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 1. 校验应用是否存在
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2. 校验权限，仅应用创建者和管理员可以查看该应用的对话历史（避免浏览他人应用时触发对话）
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该应用的对话历史");
        // 3. 游标分页查询
        ChatHistoryPageVO chatHistoryPageVO = chatHistoryService.listAppChatHistory(appId,
                chatHistoryQueryRequest.getPageSize(),
                chatHistoryQueryRequest.getLastCreateTime(),
                chatHistoryQueryRequest.getLastId());
        return ResultUtils.success(chatHistoryPageVO);
    }

    /**
     * 分页查询所有应用的对话历史（管理员，按时间降序排序，便于内容监管）
     *
     * @param chatHistoryQueryRequest 查询请求（支持按应用、用户、消息类型、消息内容、时间范围查询）
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryVOByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(chatHistoryService.listChatHistoryVOByPage(chatHistoryQueryRequest));
    }
}
