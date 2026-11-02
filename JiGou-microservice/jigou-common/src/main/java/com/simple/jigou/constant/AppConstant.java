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
     * 应用广场等公开列表的最大页号（限制深分页，防止爬虫拉取全量公开数据）
     */
    Integer MAX_PAGE_NUM = 50;

    /**
     * 应用生成目录（同时作为应用的「工作区」目录，存放应用最新代码，预览与部署均基于该目录）
     * <p>由 {@link JiGouPaths} 统一解析（本机路径），保证 app / screenshot 进程与 Nginx 指向同一目录
     */
    String CODE_OUTPUT_ROOT_DIR = JiGouPaths.CODE_OUTPUT_ROOT_DIR;

    /**
     * 应用版本库目录（存放用户提交的历史版本快照，与工作区隔离）
     */
    String CODE_VERSION_ROOT_DIR = JiGouPaths.CODE_VERSION_ROOT_DIR;

    /**
     * 应用部署目录（由 Nginx 托管）
     */
    String CODE_DEPLOY_ROOT_DIR = JiGouPaths.CODE_DEPLOY_ROOT_DIR;

    /**
     * 应用部署域名（对外地址，不含结尾斜杠）
     * <p>由 {@link JiGouUrls} 统一解析，禁止在业务代码里再硬编码 localhost
     */
    String CODE_DEPLOY_HOST = JiGouUrls.DEPLOY_BASE_URL;


    /**
     * 单个应用最多保留的历史版本数量
     * 超出后淘汰最旧的版本，当前版本与部署版本永远不会被淘汰
     */
    Integer MAX_VERSION_COUNT = 20;

    /**
     * 应用名称最大长度（AI 生成或用户输入超长时统一截断）
     */
    Integer APP_NAME_MAX_LENGTH = 50;

    /**
     * 应用名称兜底长度（AI 生成失败时截取 initPrompt 的字符数，沿用历史行为）
     */
    Integer DEFAULT_APP_NAME_MAX_LENGTH = 12;

}
