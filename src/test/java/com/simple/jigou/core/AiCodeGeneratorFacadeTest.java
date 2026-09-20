package com.simple.jigou.core;

import com.simple.jigou.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("每日计划打卡网站", CodeGenTypeEnum.MULTI_FILE, 123L);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("库存管理网站", CodeGenTypeEnum.MULTI_FILE, 123L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }

    @Test
    void generateAppName() {
        String appName = aiCodeGeneratorFacade.generateAppName("帮我做一个记录每日饮水量的打卡工具，可以设置目标杯数并查看一周统计");
        // 命名属于增强能力，正常情况应返回清洗后的名称
        Assertions.assertNotNull(appName, "AI 生成的应用名称不能为空");
        Assertions.assertFalse(appName.isBlank(), "应用名称不能为空白字符");
        // 名称需为已清洗的纯文本：不含引号、换行与结构化 JSON 残留
        Assertions.assertFalse(appName.contains("\"") || appName.contains("{") || appName.contains("\n"),
                "应用名称应为清洗后的纯文本：" + appName);
    }
}
