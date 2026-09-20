package com.simple.jigou.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.core.AiCodeGeneratorFacade;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.mapper.AppMapper;
import com.simple.jigou.model.dto.app.AppAddRequest;
import com.simple.jigou.model.dto.app.AppQueryRequest;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.AppVisibilityEnum;
import com.simple.jigou.model.enums.ChatHistoryMessageTypeEnum;
import com.simple.jigou.model.enums.CodeGenTypeEnum;
import com.simple.jigou.model.enums.UserRoleEnum;
import com.simple.jigou.model.vo.AppVO;
import com.simple.jigou.model.vo.UserVO;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.ChatHistoryService;
import com.simple.jigou.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author simple
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    /**
     * 创建应用
     * 应用名称未填写时由 AI 根据初始需求生成，生成失败则兜底截取 initPrompt 的前 12 个字符
     *
     * @param appAddRequest 创建应用请求参数
     * @param loginUser     登录用户
     * @return 新应用 id
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "initPrompt 不能为空");
        // 2. 确定应用名称：用户已填写则尊重用户输入，未填写才调用 AI 生成，避免多余的模型调用
        String appName = appAddRequest.getAppName();
        if (StrUtil.isBlank(appName)) {
            appName = aiCodeGeneratorFacade.generateAppName(initPrompt);
        }
        // 3. AI 生成失败时兜底截取 initPrompt 前 12 个字符，保证应用一定创建成功
        appName = StrUtil.blankToDefault(appName, StrUtil.sub(initPrompt, 0, AppConstant.DEFAULT_APP_NAME_MAX_LENGTH));
        // 4. 统一限制名称长度
        appName = StrUtil.sub(appName, 0, AppConstant.APP_NAME_MAX_LENGTH);
        // 5. 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setAppName(appName);
        app.setCodeGenType(StrUtil.blankToDefault(appAddRequest.getCodeGenType(), CodeGenTypeEnum.MULTI_FILE.getValue()));
        app.setUserId(loginUser.getId());
        // 可见范围默认私有，用户后续可在应用详情中自行切换为公开
        app.setVisibility(AppVisibilityEnum.PRIVATE.getValue());
        // 6. 保存应用，名称与记录一起入库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("创建应用成功，应用 id：{}，应用名称：{}", app.getId(), app.getAppName());
        return app.getId();
    }

    /**
     * 部署应用
     *
     * @param appId     应用id
     * @param loginUser 登录用户
     * @return
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字），复杂场景还可考虑数据库查重逻辑
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }


    /**
     * 将AI回复的代码信息提取出来存入文件
     *
     * @param appId     应用id
     * @param message   用户消息
     * @param loginUser 登录用户
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 保存用户消息（在流式返回前落库，保证对话历史完整）
        Long userId = loginUser.getId();
        boolean saved = chatHistoryService.addChatMessage(appId, userId, message, ChatHistoryMessageTypeEnum.USER);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "保存用户消息失败");
        // 6. 调用 AI 生成代码，并实时收集 AI 回复内容用于落库
        StringBuilder aiMessageBuilder = new StringBuilder();
        // 标记本次生成是否已结束（成功或失败），避免连接断开时重复记录中断信息
        AtomicBoolean finished = new AtomicBoolean(false);
        Flux<String> codeStream;
        try {
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        } catch (Exception e) {
            // 生成入口就抛异常（没有返回流）：同样记录错误信息，保证对话记录完整
            finished.set(true);
            saveCodeGenErrorChatHistory(appId, userId, aiMessageBuilder,
                    StrUtil.format("AI 生成失败：{}", StrUtil.blankToDefault(e.getMessage(), "未知异常")));
            throw e;
        }
        return codeStream
                .doOnNext(aiMessageBuilder::append)
                .doOnComplete(() -> {
                    // AI 生成成功：保存完整的 AI 回复
                    finished.set(true);
                    try {
                        String aiMessage = aiMessageBuilder.toString();
                        if (StrUtil.isNotBlank(aiMessage)) {
                            chatHistoryService.addChatMessage(appId, userId, aiMessage, ChatHistoryMessageTypeEnum.AI);
                        }
                    } catch (Exception e) {
                        // 落库失败不影响已经返回给前端的生成结果，仅记录日志
                        log.error("保存 AI 消息失败，appId = {}", appId, e);
                    }
                })
                .doOnError(throwable -> {
                    // AI 生成失败：已生成的内容与错误信息都落库，保证对话历史完整、便于排查问题
                    finished.set(true);
                    saveCodeGenErrorChatHistory(appId, userId, aiMessageBuilder,
                            StrUtil.format("AI 生成失败：{}", StrUtil.blankToDefault(throwable.getMessage(), "未知异常")));
                })
                .doOnCancel(() -> {
                    // 用户点了「停止生成」或连接断开：已生成的内容与中断信息都落库，
                    // 避免对话历史里只剩用户消息，导致对话记录不完整
                    if (finished.get()) {
                        return;
                    }
                    saveCodeGenErrorChatHistory(appId, userId, aiMessageBuilder,
                            "AI 生成已中断（用户停止生成或连接断开）");
                });
    }

    /**
     * 保存 AI 生成失败 / 中断的对话历史（含已生成的部分内容）
     * 保证即使 AI 回复失败，对话记录也是完整的，同时便于排查问题
     *
     * @param appId            应用 id
     * @param userId           用户 id
     * @param aiMessageBuilder AI 已生成的内容
     * @param errorMessage     错误信息
     */
    private void saveCodeGenErrorChatHistory(Long appId, Long userId, StringBuilder aiMessageBuilder, String errorMessage) {
        try {
            String partialMessage = aiMessageBuilder.toString();
            if (StrUtil.isNotBlank(partialMessage)) {
                chatHistoryService.addChatMessage(appId, userId, partialMessage, ChatHistoryMessageTypeEnum.AI);
            }
            chatHistoryService.addChatMessage(appId, userId, errorMessage, ChatHistoryMessageTypeEnum.ERROR);
        } catch (Exception e) {
            // 落库失败不影响已经返回给前端的生成结果，仅记录日志
            log.error("保存 AI 错误消息失败，appId = {}", appId, e);
        }
    }

    /**
     * 删除应用，并关联删除该应用下的所有对话历史
     *
     * <p>加事务保证「删除对话历史」与「删除应用」两步要么都成功、要么都回滚，
     * 避免应用删除失败时对话历史已被删除，导致数据不一致
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApp(Long appId) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 2. 关联删除该应用下的所有对话历史（该应用没有对话历史时返回 false 属正常情况，因此不校验返回值）
        chatHistoryService.remove(QueryWrapper.create().eq("appId", appId));
        // 3. 删除应用本身（失败则抛出异常，由事务回滚上面的对话历史删除）
        boolean result = this.removeById(appId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        return true;
    }

    /**
     * 构造分页查询条件
     * 由于使用mybatis-flex，所以将查询请求转换成 QueryWrapper
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        String visibility = appQueryRequest.getVisibility();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("visibility", visibility)
                .eq("priority", priority)
                .eq("userId", userId)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .orderBy(StrUtil.isNotBlank(sortField) ? sortField : "createTime",
                        "ascend".equals(sortOrder));
    }

    /**
     * 应用列表关联查询应用创建用户脱敏信息列表
     *
     * @param appList 脱敏前应用列表
     * @return 脱敏后的应用列表
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，考虑到性能先获取用户ID列表，根据用户id集合查询用户信息构建map映射关系userid->userVO，最后组装到apVO
        Set<Long> userIds = appList.stream().map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
                    AppVO appVO = new AppVO();
                    BeanUtil.copyProperties(app, appVO);
                    UserVO userVO = userVOMap.get(appVO.getUserId());
                    appVO.setUserVO(userVO);
                    return appVO;
                }
        ).collect(Collectors.toList());
    }

    /**
     * 应用信息获取创建用户脱敏后的信息
     *
     * @param app 脱敏前的应用信息
     * @return 脱敏后的应用信息
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUserVO(userVO);
        }
        return appVO;
    }

    /**
     * 校验用户是否有权查看应用
     * 公开应用所有用户均可查看；私有应用仅创建者与管理员可查看
     *
     * @param app       应用信息
     * @param loginUser 登录用户（未登录时传 null）
     */
    @Override
    public void checkAppViewPermission(App app, User loginUser) {
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 1. 公开应用：所有用户均可查看
        if (AppVisibilityEnum.isPublic(app.getVisibility())) {
            return;
        }
        // 2. 私有应用：必须先登录
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "该应用未公开，请先登录");
        // 3. 私有应用仅创建者与管理员可查看
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !loginUser.getId().equals(app.getUserId()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该应用");
    }
}
