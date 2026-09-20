package com.simple.jigou.ai;

import com.simple.jigou.ai.model.AppNameResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiAppNameServiceTest {

    @Resource
    private AiAppNameService aiAppNameService;

    @Test
    void generateAppName() {
        AppNameResult result = aiAppNameService.generateAppName("帮我做一个记录每日饮水量的打卡工具，可以设置目标杯数并查看一周统计");
        Assertions.assertNotNull(result, "AI 返回结果不能为空");
        String appName = result.getAppName();
        Assertions.assertTrue(appName != null && !appName.isBlank(), "AI 生成的应用名称不能为空");
        // 名称需符合提示词约束：长度适中、不含引号与换行
        Assertions.assertTrue(appName.length() <= 12, "应用名称长度应不超过 12：" + appName);
        Assertions.assertFalse(appName.contains("\"") || appName.contains("\n"), "应用名称不应包含引号或换行：" + appName);
    }
}
