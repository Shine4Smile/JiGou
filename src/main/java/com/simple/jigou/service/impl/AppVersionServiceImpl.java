package com.simple.jigou.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.core.AppStorageManager;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.enums.UserRoleEnum;
import com.simple.jigou.model.vo.AppVersionDetailVO;
import com.simple.jigou.model.vo.AppVersionListVO;
import com.simple.jigou.model.vo.AppVersionVO;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.AppVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用版本 服务层实现。
 * <p>
 * 版本快照保存在文件系统中（tmp/code_versions/{codeGenType}_{appId}/v{n}），
 * 数据库 app 表仅保存「当前版本号」指针，因此本需求无需新增数据表
 *
 * @author simple
 */
@Slf4j
@Service
public class AppVersionServiceImpl implements AppVersionService {

    @Resource
    private AppService appService;

    @Override
    public Integer commitVersion(Long appId, User loginUser) {
        App app = getOwnedApp(appId, loginUser);
        String codeGenType = app.getCodeGenType();
        // 1. 把工作区代码复制为一个新版本（版本号只增不减，历史版本永不被覆盖）
        int newVersion = AppStorageManager.commitVersion(codeGenType, appId);
        // 2. 应用当前版本号指向新版本
        updateCurrentVersion(appId, newVersion);
        // 3. 淘汰超出上限的历史版本：保留最新的 N 个版本，当前版本与线上正在部署的版本永远保留
        AppStorageManager.evictOldVersions(codeGenType, appId, AppConstant.MAX_VERSION_COUNT,
                buildProtectedVersions(newVersion, app.getDeployedVersion()));
        return newVersion;
    }

    /**
     * 构造淘汰历史版本时需要保护的版本号集合
     * <p>
     * 除了本次新提交的版本，如果应用已经部署，线上正在使用的版本也不能被清理，否则线上会取不到代码
     *
     * @param newVersion      本次提交的新版本号
     * @param deployedVersion 线上正在部署的版本号（null 表示部署的是工作区最新内容）
     * @return 需要保护的版本号集合
     */
    private Set<Integer> buildProtectedVersions(int newVersion, Integer deployedVersion) {
        Set<Integer> protectedVersions = new LinkedHashSet<>();
        protectedVersions.add(newVersion);
        if (deployedVersion != null && deployedVersion > 0) {
            protectedVersions.add(deployedVersion);
        }
        return protectedVersions;
    }

    @Override
    public AppVersionListVO listVersions(Long appId, User loginUser) {
        App app = getAccessibleApp(appId, loginUser);
        String codeGenType = app.getCodeGenType();
        Integer currentVersion = getCurrentVersion(app);
        List<Integer> versionList = AppStorageManager.listVersions(codeGenType, appId);
        AppVersionListVO appVersionListVO = new AppVersionListVO();
        appVersionListVO.setCurrentVersion(currentVersion);
        appVersionListVO.setHasCode(AppStorageManager.hasCode(codeGenType, appId));
        appVersionListVO.setUncommitted(AppStorageManager.hasUncommittedChanges(codeGenType, appId, currentVersion));
        appVersionListVO.setVersionList(versionList.stream()
                .map(version -> {
                    AppVersionVO appVersionVO = new AppVersionVO();
                    appVersionVO.setVersion(version);
                    appVersionVO.setCommitTime(AppStorageManager.getCommitTime(codeGenType, appId, version));
                    appVersionVO.setCurrent(version.equals(currentVersion));
                    return appVersionVO;
                })
                .collect(Collectors.toList()));
        return appVersionListVO;
    }

    @Override
    public AppVersionDetailVO getVersionDetail(Long appId, Integer version, User loginUser) {
        App app = getAccessibleApp(appId, loginUser);
        ThrowUtils.throwIf(version == null || version <= 0, ErrorCode.PARAMS_ERROR, "版本号不能为空");
        String codeGenType = app.getCodeGenType();
        File versionDir = AppStorageManager.getVersionDir(codeGenType, appId, version);
        ThrowUtils.throwIf(!versionDir.exists(), ErrorCode.NOT_FOUND_ERROR, "该版本不存在或已被清理");
        AppVersionDetailVO appVersionDetailVO = new AppVersionDetailVO();
        appVersionDetailVO.setVersion(version);
        appVersionDetailVO.setCommitTime(AppStorageManager.getCommitTime(codeGenType, appId, version));
        appVersionDetailVO.setFileMap(readVersionFiles(versionDir));
        return appVersionDetailVO;
    }

    @Override
    public boolean rollbackVersion(Long appId, Integer version, User loginUser) {
        App app = getOwnedApp(appId, loginUser);
        ThrowUtils.throwIf(version == null || version <= 0, ErrorCode.PARAMS_ERROR, "版本号不能为空");
        // 1. 把指定版本的内容恢复到工作区（历史版本目录保持不可变）
        AppStorageManager.restoreVersion(app.getCodeGenType(), appId, version);
        // 2. 应用当前版本号指向被回退的版本
        updateCurrentVersion(appId, version);
        return true;
    }

    /**
     * 读取版本目录下的所有文件内容
     *
     * @param versionDir 版本目录
     * @return 文件名（相对路径）-> 文件内容
     */
    private Map<String, String> readVersionFiles(File versionDir) {
        List<File> fileList = FileUtil.loopFiles(versionDir);
        fileList.sort(Comparator.comparing(File::getAbsolutePath));
        Map<String, String> fileMap = new LinkedHashMap<>();
        for (File file : fileList) {
            String fileName = versionDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            fileMap.put(fileName, FileUtil.readString(file, StandardCharsets.UTF_8));
        }
        return fileMap;
    }

    /**
     * 获取应用并校验操作权限：仅应用创建者可以提交 / 回退版本
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 应用信息
     */
    private App getOwnedApp(Long appId, User loginUser) {
        App app = getApp(appId);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限管理该应用的版本");
        return app;
    }

    /**
     * 获取应用并校验查看权限：应用创建者或管理员可以查看版本列表与版本内容
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 应用信息
     */
    private App getAccessibleApp(Long appId, User loginUser) {
        App app = getApp(appId);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该应用的版本");
        return app;
    }

    /**
     * 查询应用并校验基础信息
     *
     * @param appId 应用 id
     * @return 应用信息
     */
    private App getApp(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(StrUtil.isBlank(app.getCodeGenType()), ErrorCode.SYSTEM_ERROR, "应用代码生成类型不存在");
        return app;
    }

    /**
     * 获取应用当前版本号（历史数据为 null，统一按 0 处理，表示从未提交版本）
     *
     * @param app 应用信息
     * @return 当前版本号
     */
    private Integer getCurrentVersion(App app) {
        return app.getVersion() == null ? 0 : app.getVersion();
    }

    /**
     * 更新应用当前版本号
     *
     * @param appId   应用 id
     * @param version 版本号
     */
    private void updateCurrentVersion(Long appId, Integer version) {
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setVersion(version);
        updateApp.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用版本号失败");
    }
}

