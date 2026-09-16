package com.simple.jigou.constant;

/**
 * 应用相关常量
 */
public interface AppConstant {

    /**
     * 应用默认优先级（0 表示普通应用，未上精选）
     */
    Integer DEFAULT_APP_PRIORITY = 0;
    /**
     * 精选应用优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 用户分页查询应用列表时，每页最多展示数量
     */
    Integer MAX_PAGE_SIZE = 20;
}
