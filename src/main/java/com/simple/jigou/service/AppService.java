package com.simple.jigou.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.simple.jigou.model.dto.app.AppAddRequest;
import com.simple.jigou.model.dto.app.AppQueryRequest;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author simple
 */
public interface AppService extends IService<App> {
    /**
     * 创建应用
     * 应用名称未填写时由 AI 根据初始需求生成并随记录一起入库
     *
     * @param appAddRequest 创建应用请求参数
     * @param loginUser     登录用户
     * @return 新应用 id
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 部署应用
     * <p>
     * 不指定版本时部署工作区的最新代码，指定版本时部署该历史版本的代码快照
     *
     * @param appId     应用id
     * @param loginUser 登录用户
     * @param version   部署来源版本号（为 null 表示部署工作区最新代码）
     * @return 部署后的访问地址
     */
    String deployApp(Long appId, User loginUser, Integer version);

    /**
     * 取消部署：下线应用，删除部署产物并清空部署信息
     *
     * @param appId     应用 id
     * @param loginUser 登录用户
     * @return 是否取消成功
     */
    boolean undeployApp(Long appId, User loginUser);

    /**
     * 将AI回复的代码信息提取出来存入文件
     *
     * @param appId     应用id
     * @param message   AI回复信息
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 构造分页查询条件
     * 由于使用mybatis-flex，所以将查询请求转换成 QueryWrapper
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取脱敏后的应用列表
     *
     * @param appList 脱敏前应用列表
     * @return 脱敏后的应用列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 获取脱敏后的应用信息
     *
     * @param app 脱敏前的应用信息
     * @return 脱敏后的应用信息
     */
    AppVO getAppVO(App app);

    /**
     * 校验用户是否有权查看应用
     * 公开应用所有用户均可查看；私有应用仅创建者与管理员可查看
     *
     * @param app       应用信息
     * @param loginUser 登录用户（未登录时传 null）
     */
    void checkAppViewPermission(App app, User loginUser);

    /**
     * 删除应用，并关联删除该应用下的所有对话历史（避免产生冗余数据）
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    boolean deleteApp(Long appId);
}
