package com.simple.jigou.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.simple.jigou.ai.AiCodeGenTypeRoutingService;
import com.simple.jigou.ai.AiCodeGenTypeRoutingServiceFactory;
import com.simple.jigou.ai.AiCodeGeneratorServiceFactory;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.core.AiCodeGeneratorFacade;
import com.simple.jigou.core.AppStorageManager;
import com.simple.jigou.core.builder.VueProjectBuilder;
import com.simple.jigou.core.handler.StreamHandlerExecutor;
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
import com.simple.jigou.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

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
        // 5. 使用AI智能选择应用生成类型
        // 使用 AI 智能选择代码生成类型（多例模式）
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum codeGenTypeEnum = routingService.routeCodeGenType(appAddRequest.getInitPrompt());
        // 6. 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setAppName(appName);
        app.setCodeGenType(codeGenTypeEnum.getValue());
        app.setUserId(loginUser.getId());
        // 可见范围默认私有，用户后续可在应用详情中自行切换为公开
        app.setVisibility(AppVisibilityEnum.PRIVATE.getValue());
        // 7. 保存应用，名称与记录一起入库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("创建应用成功，应用 id：{}，应用名称：{}", app.getId(), app.getAppName());
        return app.getId();
    }

    /**
     * 部署应用
     * <p>
     * 部署来源有两种：不指定版本时部署工作区的最新代码，指定版本时部署版本库中该版本的代码快照。
     * 每次部署都会先清空部署目录，避免上一次部署中已被删除的文件残留在线上
     *
     * @param appId     应用id
     * @param loginUser 登录用户
     * @param version   部署来源版本号（为 null 表示部署工作区最新代码）
     * @return 部署后的访问地址
     */
    @Override
    public String deployApp(Long appId, User loginUser, Integer version) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        ThrowUtils.throwIf(version != null && version <= 0, ErrorCode.PARAMS_ERROR, "部署的版本号不合法");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 获取代码生成类型，确定部署来源目录
        String codeGenType = app.getCodeGenType();
        ThrowUtils.throwIf(StrUtil.isBlank(codeGenType), ErrorCode.SYSTEM_ERROR, "应用代码生成类型不存在");
        File sourceDir = version == null
                ? AppStorageManager.getWorkDir(codeGenType, appId)
                : AppStorageManager.getVersionDir(codeGenType, appId, version);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            if (version != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该版本不存在或已被清理");
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 5. Vue 项目特殊处理：执行构建，拿到最终待部署的内容目录
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        File buildDir = null;
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // 构建会在代码目录内写入 node_modules 与 dist，而版本目录是不可变的历史快照，
            // 因此部署历史版本时先复制到临时构建目录再构建，避免构建产物污染版本库
            File buildSourceDir = sourceDir;
            if (version != null) {
                buildDir = AppStorageManager.getBuildDir(codeGenType, appId);
                FileUtil.del(buildDir);
                AppStorageManager.copyCodeContent(sourceDir, buildDir);
                buildSourceDir = buildDir;
            }
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(buildSourceDir.getPath());
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(buildSourceDir.getPath(), "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        try {
            // 6. 复用已有的 deployKey，没有则生成 6 位 deployKey（大小写字母 + 数字），复杂场景还可考虑数据库查重逻辑
            String deployKey = app.getDeployKey();
            if (StrUtil.isBlank(deployKey)) {
                deployKey = RandomUtil.randomString(6);
            }
            // 7. 部署前先清空部署目录，再复制最新内容
            File deployDir = FileUtil.file(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
            FileUtil.del(deployDir);
            try {
                FileUtil.copyContent(sourceDir, deployDir, true);
            } catch (Exception e) {
                log.error("部署应用失败，appId = {}，部署目录 = {}", appId, deployDir.getAbsolutePath(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
            }
            // 8. 更新应用的部署信息：部署工作区代码时 deployedVersion 置空，表示线上内容跟随工作区
            updateDeployInfo(appId, deployKey, version);
            log.info("部署应用成功，appId = {}，部署标识 = {}，部署来源版本 = {}", appId, deployKey, version);
            // 9. 返回可访问的 URL
            String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
            // 10. 异步生成截图并更新应用封面
            generateAppScreenshotAsync(appId, appDeployUrl);
            return appDeployUrl;
        } finally {
            // 临时构建目录只在本次部署过程中使用，无论成功失败都立即清理，避免磁盘上堆积多份 node_modules
            if (buildDir != null) {
                FileUtil.del(buildDir);
            }
        }
    }

    /**
     * 取消部署（应用下线）
     * <p>
     * 删除部署目录让线上地址立即失效，并清空部署信息，使应用回到「未部署」状态
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 是否取消成功
     */
    @Override
    public boolean undeployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2. 仅应用创建者可以取消部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限取消部署该应用");
        }
        String deployKey = app.getDeployKey();
        ThrowUtils.throwIf(StrUtil.isBlank(deployKey), ErrorCode.OPERATION_ERROR, "该应用还未部署，无需取消部署");
        // 3. 删除部署目录，保证线上地址不再可访问
        File deployDir = FileUtil.file(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        if (deployDir.exists() && !FileUtil.del(deployDir)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消部署失败，请稍后重试");
        }
        // 4. 清空部署信息，应用回到未部署状态
        clearDeployInfo(appId);
        log.info("取消部署成功，appId = {}，原部署标识 = {}", appId, deployKey);
        return true;
    }

    /**
     * 更新应用的部署信息
     * <p>
     * 这里使用 UpdateChain 而不是 updateById：部署工作区最新代码时需要把 deployedVersion 显式写为 null，
     * 而 updateById 默认会忽略值为 null 的字段，无法表达「线上内容跟随工作区」的语义
     *
     * @param appId           应用 id
     * @param deployKey       部署标识
     * @param deployedVersion 部署来源版本号（为 null 表示部署工作区最新代码）
     */
    private void updateDeployInfo(Long appId, String deployKey, Integer deployedVersion) {
        LocalDateTime now = LocalDateTime.now();
        boolean updateResult = UpdateChain.of(this.getMapper())
                .set(App::getDeployKey, deployKey)
                .set(App::getDeployedTime, now)
                .set(App::getDeployedVersion, deployedVersion)
                .set(App::getEditTime, now)
                .where(App::getId).eq(appId)
                .update();
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
    }

    /**
     * 清空应用的部署信息（取消部署后调用）
     * <p>
     * deployKey / deployedTime / deployedVersion 都需要置为 null，同样使用 UpdateChain 保证 null 能写入
     *
     * @param appId 应用 id
     */
    private void clearDeployInfo(Long appId) {
        LocalDateTime now = LocalDateTime.now();
        boolean updateResult = UpdateChain.of(this.getMapper())
                .set(App::getDeployKey, null)
                .set(App::getDeployedTime, null)
                .set(App::getDeployedVersion, null)
                .set(App::getEditTime, now)
                .where(App::getId).eq(appId)
                .update();
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "清空应用部署信息失败");
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
        // 6. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
    }

    /**
     * 删除应用，并关联删除该应用下的所有对话历史与代码数据
     *
     * <p>加事务保证「删除对话历史」与「删除应用」两步要么都成功、要么都回滚，
     * 避免应用删除失败时对话历史已被删除，导致数据不一致
     *
     * <p>数据库之外的数据（工作区代码、历史版本快照、部署产物、redis 会话记忆、AI 服务实例缓存）
     * 统一在事务提交后清理：事务回滚时不会误删文件，应用删除后也不会残留无人可查的数据
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApp(Long appId) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 2. 查询应用信息：记录删除后读不到代码生成类型与部署标识，先取出用于后续清理数据库之外的数据
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 关联删除该应用下的所有对话历史（该应用没有对话历史时返回 false 属正常情况，因此不校验返回值）
        chatHistoryService.remove(QueryWrapper.create().eq("appId", appId));
        // 4. 逻辑删除应用本身（失败则抛出异常，由事务回滚上面的对话历史删除）
        boolean result = this.removeById(appId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        // 5. 事务提交后再清理代码文件、部署产物与 AI 会话缓存
        cleanupAppResourcesAfterCommit(app);
        return true;
    }

    /**
     * 注册应用资源清理任务（事务提交后执行）
     *
     * <p>没有活跃的事务同步时（如单元测试中直接调用）立即清理，保证清理逻辑不会漏执行
     *
     * @param app 被删除的应用（携带代码生成类型与部署标识）
     */
    private void cleanupAppResourcesAfterCommit(App app) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupAppResources(app);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupAppResources(app);
            }
        });
    }

    /**
     * 清理应用在数据库之外的全部数据
     *
     * <p>包含三部分：工作区代码与历史版本快照、部署产物、redis 会话记忆与缓存的 AI 服务实例
     *
     * <p>每部分单独兜底并记录日志：此时数据库事务已提交，清理失败不能影响删除结果，
     * 否则会出现「应用已删除但接口报错」的误导（残留数据可由运维按日志手动清理）
     *
     * @param app 被删除的应用
     */
    private void cleanupAppResources(App app) {
        Long appId = app.getId();
        String codeGenType = app.getCodeGenType();
        // 1. 工作区代码（AI 最新生成的代码）与版本库（历史版本快照）
        if (StrUtil.isNotBlank(codeGenType)) {
            try {
                AppStorageManager.deleteWorkDir(codeGenType, appId);
                AppStorageManager.deleteVersionRootDir(codeGenType, appId);
            } catch (Exception e) {
                log.error("清理应用代码文件失败，appId = {}，代码生成类型 = {}", appId, codeGenType, e);
            }
        }
        // 2. 部署产物：已部署的应用随应用一起下线，避免线上地址继续可访问
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            try {
                FileUtil.del(FileUtil.file(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
            } catch (Exception e) {
                log.error("清理应用部署产物失败，appId = {}，部署标识 = {}", appId, deployKey, e);
            }
        }
        // 3. redis 中的会话记忆与缓存的 AI 服务实例：避免已删除应用的上下文继续被使用
        try {
            aiCodeGeneratorServiceFactory.deleteChatMemory(appId);
            aiCodeGeneratorServiceFactory.invalidate(appId, CodeGenTypeEnum.getEnumByValue(codeGenType));
        } catch (Exception e) {
            log.error("清理应用会话缓存失败，appId = {}", appId, e);
        }
        log.info("应用数据清理完成，appId = {}，代码生成类型 = {}，部署标识 = {}", appId, codeGenType, deployKey);
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

    /**
     * 异步生成应用截图并更新封面
     * 当前方案暂将图片存储在本地，后续可以对接第三方存储服务，减轻本地存储压力
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = WebScreenshotUtils.saveWebPageScreenshot(appUrl);
            // 由于当前存储方案是存储在本地，利用nginx读取图片，所以将返回的图片本地路径转换为nginx访问链接
            String convertToHttpUrl = convertToHttpUrl(screenshotUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(convertToHttpUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    /**
     * 将本地绝对路径转换为 HTTP 访问路径
     * 例如：D:/project/.../tmp/screenshots/4ab36b54/1.jpg -> http://localhost/screenshots/4ab36b54/1.jpg
     *
     * @param localPath 原始本地路径
     * @return HTTP 路径
     */
    private static String convertToHttpUrl(String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) {
            return "";
        }

        // 1. 将 Windows 的反斜杠 \ 替换为正斜杠 / (兼容 Nginx 和 URL)
        String normalizedPath = localPath.replace("\\", "/");

        // 2. 截取 "screenshots/" 及其后面的内容
        String keyword = "screenshots/";
        int index = normalizedPath.indexOf(keyword);

        if (index != -1) {
            // 截取得到：screenshots/4ab36b54/67056_compressed.jpg
            String relativePath = normalizedPath.substring(index);
            // 拼接成 HTTP 地址
            return "http://localhost/" + relativePath;
        }

        // 兜底：如果路径中没有 screenshots，原样返回或做其他处理
        return normalizedPath;
    }
}
