package com.simple.jigou.controller;

import com.simple.jigou.common.BaseResponse;
import com.simple.jigou.common.ResultUtils;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.model.dto.app.AppVersionCommitRequest;
import com.simple.jigou.model.dto.app.AppVersionRollbackRequest;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.vo.AppVersionDetailVO;
import com.simple.jigou.model.vo.AppVersionListVO;
import com.simple.jigou.service.AppVersionService;
import com.simple.jigou.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用版本 控制层。
 * <p>
 * 提供版本提交、版本列表、版本内容查看、版本回退能力，版本快照保存在文件系统，不新增数据表
 *
 * @author simple
 */
@RestController
@RequestMapping("/app/version")
public class AppVersionController {

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private UserService userService;

    /**
     * 提交版本：把应用当前工作区的代码固化为一个新版本
     *
     * @param appVersionCommitRequest 提交版本请求
     * @param request                 请求对象
     * @return 新版本号
     */
    @PostMapping("/commit")
    public BaseResponse<Integer> commitVersion(@RequestBody AppVersionCommitRequest appVersionCommitRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(appVersionCommitRequest == null || appVersionCommitRequest.getAppId() == null,
                ErrorCode.PARAMS_ERROR);
        Long appId = appVersionCommitRequest.getAppId();
        ThrowUtils.throwIf(appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.commitVersion(appId, loginUser));
    }

    /**
     * 查询应用的版本列表（创建者或管理员）
     *
     * @param appId   应用 id
     * @param request 请求对象
     * @return 版本列表（含当前版本号、工作区是否有未提交修改）
     */
    @GetMapping("/list")
    public BaseResponse<AppVersionListVO> listVersions(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.listVersions(appId, loginUser));
    }

    /**
     * 查看某个版本的代码内容（创建者或管理员，用于前端查看与版本对比）
     *
     * @param appId   应用 id
     * @param version 版本号
     * @param request 请求对象
     * @return 版本详情（文件名 -> 文件内容）
     */
    @GetMapping("/detail")
    public BaseResponse<AppVersionDetailVO> getVersionDetail(@RequestParam Long appId,
                                                             @RequestParam Integer version,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.getVersionDetail(appId, version, loginUser));
    }

    /**
     * 回退到指定版本：把该版本的代码恢复到工作区（仅应用创建者）
     *
     * @param appVersionRollbackRequest 回退版本请求
     * @param request                   请求对象
     * @return 是否回退成功
     */
    @PostMapping("/rollback")
    public BaseResponse<Boolean> rollbackVersion(@RequestBody AppVersionRollbackRequest appVersionRollbackRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(appVersionRollbackRequest == null
                        || appVersionRollbackRequest.getAppId() == null
                        || appVersionRollbackRequest.getVersion() == null,
                ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(appVersionRollbackRequest.getAppId() <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.rollbackVersion(
                appVersionRollbackRequest.getAppId(), appVersionRollbackRequest.getVersion(), loginUser));
    }
}
