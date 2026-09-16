package com.simple.jigou.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.simple.jigou.model.dto.app.AppQueryRequest;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.vo.AppVO;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author simple
 */
public interface AppService extends IService<App> {

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
