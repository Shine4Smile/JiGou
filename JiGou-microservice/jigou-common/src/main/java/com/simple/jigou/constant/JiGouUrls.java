package com.simple.jigou.constant;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一解析「对外可访问地址」。
 *
 * <p>微服务可能部署在不同机器上，服务之间、浏览器与服务之间只能通过 URL 交互，
 * 因此所有对外地址都在这里集中解析，<b>禁止在业务代码里再硬编码 http://localhost</b>。
 *
 * <p>部署根地址解析优先级：
 * {@code -Djigou.deploy.base-url} → {@code JIGOU_DEPLOY_BASE_URL} → {@code http://localhost}。
 * <p>截图根地址解析优先级：
 * {@code -Djigou.screenshot.base-url} → {@code JIGOU_SCREENSHOT_BASE_URL} → 部署根地址。
 *
 * @author simple
 */
public final class JiGouUrls {

    private static final Logger log = LoggerFactory.getLogger(JiGouUrls.class);

    /**
     * 已部署应用的对外根地址（不带结尾斜杠）。
     * <p>必须同时满足：① 浏览器能访问；② jigou-screenshot 所在机器能访问（它要用 Chrome 打开该地址截图）。
     */
    public static final String DEPLOY_BASE_URL = JiGouPaths.trimTrailingSlash(JiGouPaths.firstNotBlank(
            System.getProperty("jigou.deploy.base-url"),
            System.getenv("JIGOU_DEPLOY_BASE_URL"),
            "http://localhost"));

    /**
     * 截图资源的对外根地址（不带结尾斜杠）。
     * <p>默认与部署根地址一致；截图服务单独部署时配置成截图机上的 HTTP 地址即可。
     */
    public static final String SCREENSHOT_BASE_URL = JiGouPaths.trimTrailingSlash(JiGouPaths.firstNotBlank(
            System.getProperty("jigou.screenshot.base-url"),
            System.getenv("JIGOU_SCREENSHOT_BASE_URL"),
            DEPLOY_BASE_URL));

    static {
        log.info("JiGou 部署根地址：{}，截图根地址：{}", DEPLOY_BASE_URL, SCREENSHOT_BASE_URL);
    }

    private JiGouUrls() {
    }

    /**
     * 已部署应用的访问地址：{@code {deploy-base}/{deployKey}/}
     */
    public static String deployUrl(String deployKey) {
        return DEPLOY_BASE_URL + "/" + deployKey + "/";
    }

    /**
     * 判断是否已经是 http(s) 直链（如对象存储地址）
     */
    public static boolean isAbsoluteUrl(String value) {
        return StrUtil.startWithAny(StrUtil.nullToEmpty(value), "http://", "https://");
    }

    /**
     * 把截图文件路径转换为对外可访问 URL。
     * <p>入参已是 http(s) 直链时原样返回（对象存储场景）。
     * <p>否则按 {@code screenshots/} 截取相对部分，再拼到 {@link #SCREENSHOT_BASE_URL} 上；
     * 只依赖「相对部分」，因此即使路径来自另一台机器的截图服务也能正确转换。
     */
    public static String screenshotUrl(String localPath) {
        if (StrUtil.isBlank(localPath)) {
            return "";
        }
        String normalized = localPath.replace('\\', '/').trim();
        if (isAbsoluteUrl(normalized)) {
            return normalized;
        }
        String relative = null;
        // 1. 路径落在本机截图目录下：去掉根目录前缀
        String rootPrefix = JiGouPaths.SCREENSHOT_ROOT_DIR + "/";
        if (normalized.startsWith(rootPrefix)) {
            relative = normalized.substring(rootPrefix.length());
        } else {
            // 2. 兜底：按 screenshots/ 截取相对部分（路径来自其他机器或其他根目录）
            int index = normalized.indexOf("screenshots/");
            if (index >= 0) {
                relative = normalized.substring(index + "screenshots/".length());
            }
        }
        if (StrUtil.isBlank(relative)) {
            return normalized;
        }
        return SCREENSHOT_BASE_URL + "/screenshots/" + relative;
    }
}
