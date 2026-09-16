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

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

}
