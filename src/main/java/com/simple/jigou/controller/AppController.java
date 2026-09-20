package com.simple.jigou.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.simple.jigou.annotation.AuthCheck;
import com.simple.jigou.common.BaseResponse;
import com.simple.jigou.common.DeleteRequest;
import com.simple.jigou.common.ResultUtils;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.constant.UserConstant;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.model.dto.app.*;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.AppVisibilityEnum;
import com.simple.jigou.model.vo.AppVO;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author simple
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
        // 转为ServerSentEvent格式
        return contentFlux.map(chunk -> {
                    // 将内容转为json格式，避免空格丢失问题
                    Map<String, String> map = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(map);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件，主动告诉前端生成完成
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }


    // ==================== 用户接口 ====================

    /**
     * 创建应用（用户，须填写 initPrompt）
     * 未填写应用名称时由 AI 根据 initPrompt 生成，随应用记录一起入库
     *
     * @param appAddRequest 创建应用请求参数接收类
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 创建应用（名称生成等组装逻辑下沉至 service）
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 根据 id 修改自己的应用（支持修改应用名称、可见范围）
     *
     * @param appEditRequest 修改应用请求参数接收类
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editApp(@RequestBody AppEditRequest appEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appEditRequest == null || appEditRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long appId = appEditRequest.getId();
        // 判断应用是否存在
        App oldApp = appService.getById(appId);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验是否为当前用户的应用
        ThrowUtils.throwIf(!loginUser.getId().equals(oldApp.getUserId()), ErrorCode.NO_AUTH_ERROR, "无权限修改该应用");
        // 可见范围非空时校验取值合法性（仅支持 private 私有 / public 公开）
        String visibility = appEditRequest.getVisibility();
        ThrowUtils.throwIf(StrUtil.isNotBlank(visibility) && AppVisibilityEnum.getEnumByValue(visibility) == null,
                ErrorCode.PARAMS_ERROR, "可见范围取值不合法");
        App app = new App();
        BeanUtil.copyProperties(appEditRequest, app);
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 删除自己的应用
     *
     * @param deleteRequest 删除请求包装类
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 判断应用是否存在
        Long appId = deleteRequest.getId();
        App oldApp = appService.getById(appId);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验是否为当前用户的应用
        ThrowUtils.throwIf(!loginUser.getId().equals(oldApp.getUserId()), ErrorCode.NO_AUTH_ERROR, "无权限删除该应用");
        // 删除应用，并关联删除该应用下的所有对话历史
        boolean result = appService.deleteApp(appId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 查看应用详情（用户）
     * 公开应用所有登录用户均可查看，私有应用仅创建者与管理员可查看
     *
     * @param id 应用 id
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 需要登录
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验可见范围：私有应用仅创建者与管理员可查看
        appService.checkAppViewPermission(app, loginUser);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页查询自己的应用列表（支持根据名称查询，每页最多 20 个）
     *
     * @param appQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 每页最多查询20个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > AppConstant.MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "每页最多查询20个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前登录用户自己的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(toAppVOPage(appPage));
    }

    /**
     * 分页查询精选的应用列表（支持根据名称查询，每页最多 20 个）
     * 仅展示公开应用，私有应用即便被设置为精选也不会出现在列表中
     *
     * @param appQueryRequest 查询请求参数
     */
    @PostMapping("/list/featured/vo")
    public BaseResponse<Page<AppVO>> listFeaturedAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 需要登录
        userService.getLoginUser(request);
        // 每页最多查询20个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > AppConstant.MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "每页最多查询20个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        // 只查询公开应用，避免私有应用被精选列表泄露
        appQueryRequest.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询精选应用
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(toAppVOPage(appPage));
    }

    /**
     * 分页查询公开的应用列表（应用广场数据源，支持根据名称查询）
     * 强制只查询公开应用，避免通过请求参数绕过可见范围限制
     * 每页最多 20 个、最多翻 50 页，限制深分页防止爬虫批量抓取
     *
     * @param appQueryRequest 查询请求参数
     */
    @PostMapping("/list/public/vo")
    public BaseResponse<Page<AppVO>> listPublicAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 需要登录
        userService.getLoginUser(request);
        // 每页最多查询20个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > AppConstant.MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "每页最多查询20个应用");
        // 限制最大页号，避免被用于遍历全量公开数据
        long pageNum = appQueryRequest.getPageNum();
        ThrowUtils.throwIf(pageNum > AppConstant.MAX_PAGE_NUM, ErrorCode.PARAMS_ERROR,
                "最多查询" + AppConstant.MAX_PAGE_NUM + "页应用");
        // 强制只查询公开应用，且不限制精选优先级（广场展示全部公开应用）
        appQueryRequest.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        appQueryRequest.setPriority(null);
        // 广场不提供按创建者枚举，避免被用于抓取某个用户的全部应用
        appQueryRequest.setUserId(null);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(toAppVOPage(appPage));
    }

    // ==================== 管理员接口 ====================

    /**
     * 根据 id 删除任意应用（管理员）
     *
     * @param deleteRequest 删除请求包装类
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Long appId = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(appId);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.deleteApp(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 更新任意应用（管理员，支持更新应用名称、应用封面、优先级、可见范围）
     *
     * @param appUpdateRequest 更新应用请求参数接收类
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppUpdateRequest appUpdateRequest) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Long appId = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(appId);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 可见范围非空时校验取值合法性（仅支持 private 私有 / public 公开）
        String visibility = appUpdateRequest.getVisibility();
        ThrowUtils.throwIf(StrUtil.isNotBlank(visibility) && AppVisibilityEnum.getEnumByValue(visibility) == null,
                ErrorCode.PARAMS_ERROR, "可见范围取值不合法");
        App app = new App();
        BeanUtil.copyProperties(appUpdateRequest, app);
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页查询应用列表（管理员，支持根据除时间外的任何字段查询，每页数量不限）
     *
     * @param appQueryRequest 查询请求参数
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getQueryWrapper(appQueryRequest));
        return ResultUtils.success(toAppVOPage(appPage));
    }

    /**
     * 根据 id 查看应用详情（管理员）
     *
     * @param id 应用 id
     */
    @GetMapping("/admin/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 将应用分页数据转换为脱敏后的 VO 分页数据
     *
     * @param appPage 脱敏前应用分页数据
     * @return 脱敏后的应用分页数据
     */
    private Page<AppVO> toAppVOPage(Page<App> appPage) {
        Page<AppVO> appVOPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        appVOPage.setRecords(appService.getAppVOList(appPage.getRecords()));
        return appVOPage;
    }
}
