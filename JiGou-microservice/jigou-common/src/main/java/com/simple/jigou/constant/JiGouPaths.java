package com.simple.jigou.constant;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一解析「本机存储目录」。
 *
 * <p>微服务化后各服务可能部署在不同机器上，文件不共享，因此这里只解析
 * <b>当前进程所在机器</b>的存储根目录；跨服务、跨浏览器一律走 {@link JiGouUrls}。
 *
 * <p>存储根目录解析优先级：
 * <ol>
 *     <li>JVM 系统属性 {@code -Djigou.storage.root=...}</li>
 *     <li>环境变量 {@code JIGOU_STORAGE_ROOT}</li>
 *     <li>自动向上查找包含 {@code .git} 的目录（本地开发时兼容 IDE 以模块目录为工作目录启动）</li>
 *     <li>兜底 {@code user.dir}</li>
 * </ol>
 *
 * <p>字段均为 {@code static final}，每个进程只解析一次，因此各服务可以互不相同。
 *
 * @author simple
 */
public final class JiGouPaths {

    private static final Logger log = LoggerFactory.getLogger(JiGouPaths.class);

    /**
     * 本机存储根目录（等价于单体时代的项目根目录），使用正斜杠且不带结尾斜杠
     */
    public static final String STORAGE_ROOT = resolveStorageRoot();

    /**
     * 应用工作区：AI 每次生成就地覆盖，预览与部署均基于该目录（仅 jigou-app 使用）
     */
    public static final String CODE_OUTPUT_ROOT_DIR = child("tmp", "code_output");

    /**
     * 版本库：用户提交版本时固化的代码快照（仅 jigou-app 使用）
     */
    public static final String CODE_VERSION_ROOT_DIR = child("tmp", "code_versions");

    /**
     * 部署产物目录：由 Nginx 托管（仅 jigou-app 使用）
     */
    public static final String CODE_DEPLOY_ROOT_DIR = child("tmp", "code_deploy");

    /**
     * 截图临时目录：由 Nginx 托管（仅 jigou-screenshot 使用）
     */
    public static final String SCREENSHOT_ROOT_DIR = child("tmp", "screenshots");

    static {
        log.info("JiGou 本机存储根目录：{}", STORAGE_ROOT);
    }

    private JiGouPaths() {
    }

    /**
     * 校验并解析「存储根目录下的相对路径」，阻止 ../ 越权访问
     *
     * @param relativePath 相对路径（可带前导斜杠）
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 路径越出存储根目录时抛出
     */
    public static Path resolveUnderRoot(String relativePath) {
        Path root = Paths.get(STORAGE_ROOT).toAbsolutePath().normalize();
        String safePath = StrUtil.removePrefix(StrUtil.nullToEmpty(relativePath), "/");
        Path target = root.resolve(safePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的资源路径：" + relativePath);
        }
        return target;
    }

    // ==================== 内部工具（同包 JiGouUrls 复用） ====================

    private static String resolveStorageRoot() {
        String configured = firstNotBlank(
                System.getProperty("jigou.storage.root"),
                System.getenv("JIGOU_STORAGE_ROOT"));
        if (configured != null) {
            return trimTrailingSlash(Paths.get(configured).toAbsolutePath().normalize().toString());
        }
        return trimTrailingSlash(detectProjectRoot(System.getProperty("user.dir")));
    }

    /**
     * 从起始目录向上查找项目根（包含 .git 的目录），仅用于本地开发时以模块目录为工作目录启动的场景
     */
    private static String detectProjectRoot(String startDir) {
        try {
            Path dir = Paths.get(startDir).toAbsolutePath().normalize();
            while (dir != null) {
                if (Files.isDirectory(dir.resolve(".git"))) {
                    return dir.toString();
                }
                dir = dir.getParent();
            }
            return Paths.get(startDir).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            log.warn("自动探测项目根目录失败，回退到 user.dir：{}", startDir, e);
            return startDir;
        }
    }

    private static String child(String... parts) {
        Path path = Paths.get(STORAGE_ROOT);
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path.toString().replace('\\', '/');
    }

    static String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    static String trimTrailingSlash(String value) {
        String result = value.replace('\\', '/');
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
