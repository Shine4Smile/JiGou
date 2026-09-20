package com.simple.jigou.service;

import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.vo.AppVersionDetailVO;
import com.simple.jigou.model.vo.AppVersionListVO;

/**
 * 应用版本 服务层。
 *
 * @author simple
 */
public interface AppVersionService {

    /**
     * 提交版本：把应用工作区（最新代码）固化为一个新版本
     * <p>
     * 版本号 = 当前最大版本号 + 1，历史版本不可变，仅应用创建者可操作
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 新版本号
     */
    Integer commitVersion(Long appId, User loginUser);

    /**
     * 查询应用的版本列表（含当前版本号、工作区是否有未提交修改）
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 版本列表信息
     */
    AppVersionListVO listVersions(Long appId, User loginUser);

    /**
     * 查询某个版本的代码内容（用于前端查看与版本对比）
     *
     * @param appId     应用 id
     * @param version   版本号
     * @param loginUser 登录用户
     * @return 版本详情（文件内容映射）
     */
    AppVersionDetailVO getVersionDetail(Long appId, Integer version, User loginUser);

    /**
     * 回退版本：把指定版本的代码恢复到应用工作区，历史版本本身不受影响
     * <p>
     * 回退后工作区内容 = 该版本内容，应用当前版本号同步更新为该版本号，仅应用创建者可操作
     *
     * @param appId     应用 id
     * @param version   要回退到的版本号
     * @param loginUser 登录用户
     * @return 是否回退成功
     */
    boolean rollbackVersion(Long appId, Integer version, User loginUser);
}
