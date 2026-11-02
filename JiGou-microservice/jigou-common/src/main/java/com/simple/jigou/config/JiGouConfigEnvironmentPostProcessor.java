package com.simple.jigou.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 把 application(-dev).yml 中的 jigou.* 提前写入系统属性，
 * 使 {@link com.simple.jigou.constant.JiGouPaths} / {@link com.simple.jigou.constant.JiGouUrls}
 * 这类「类加载期解析」的静态常量也能读到配置。
 *
 * <p>优先级：命令行 {@code -D} / 环境变量（本来就是系统属性，不会被覆盖）> yml > 代码默认值。
 * <p>注意：Spring Boot 4 起接口已从 {@code org.springframework.boot.env} 迁移到 {@code org.springframework.boot}。
 *
 * @author simple
 */
public class JiGouConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String[] KEYS = {
            "jigou.storage.root",
            "jigou.deploy.base-url",
            "jigou.screenshot.base-url"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String key : KEYS) {
            String value = environment.getProperty(key);
            if (StrUtil.isNotBlank(value) && StrUtil.isBlank(System.getProperty(key))) {
                System.setProperty(key, value.trim());
            }
        }
    }

    @Override
    public int getOrder() {
        // 尽量晚执行：确保 application.yml / application-{profile}.yml 已加载完成
        return Ordered.LOWEST_PRECEDENCE;
    }
}
