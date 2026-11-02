package com.simple.jigou.config;

import com.mybatisflex.core.audit.AuditManager;
import com.mybatisflex.core.audit.ConsoleMessageCollector;
import com.mybatisflex.core.audit.MessageCollector;
import com.mybatisflex.core.query.QueryColumnBehavior;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 配置类
 * 负责全局 SQL 审计、条件忽略策略等初始化
 */
@Configuration
public class MyBatisFlexConfiguration {

    /**
     * 在 Spring 容器完成依赖注入后执行初始化
     */
    @PostConstruct
    public void init() {
        // 1. 全局配置：自动忽略 null 和空字符串 "" 的查询条件
        QueryColumnBehavior.setIgnoreFunction(o -> o == null || "".equals(o));

        // 2. 开启 SQL 审计功能
        AuditManager.setAuditEnable(false);

        // 3. 设置 SQL 审计收集器，将 SQL 日志输出到控制台
        MessageCollector collector = new ConsoleMessageCollector();
        AuditManager.setMessageCollector(collector);
    }
}