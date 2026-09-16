package com.simple.jigou.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
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
}
