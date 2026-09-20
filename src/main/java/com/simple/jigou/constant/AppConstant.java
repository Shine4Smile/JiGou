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
     * 应用生成目录（同时作为应用的「工作区」目录，存放应用最新代码，预览与部署均基于该目录）
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用版本库目录（存放用户提交的历史版本快照，与工作区隔离，避免污染预览与部署产物）
     */
    String CODE_VERSION_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_versions";

    /**
     * 单个应用最多保留的历史版本数量
     * 超出后淘汰最旧的版本，当前版本与部署版本永远不会被淘汰
     */
    Integer MAX_VERSION_COUNT = 20;

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

}
