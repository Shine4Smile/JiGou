package com.simple.jigou.ai;

import com.simple.jigou.ai.model.HtmlCodeResult;
import com.simple.jigou.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult res = aiCodeGeneratorService.generateHtmlCode("生成一个时间戳转换工具");
        Assertions.assertNotNull(res);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult res = aiCodeGeneratorService.generateMultiFileCode("生成一个每日计划打卡页面");
        Assertions.assertNotNull(res);
    }
}