package com.simple.jigou.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 为将AI输出内容结构化，创建应用名称结果类，用于封装AI返回内容
 */
@Description("根据应用需求生成的应用名称结果")
@Data
public class AppNameResult {

    @Description("应用名称，2~12 个字符，以中文为主，不含引号、标点与空格")
    private String appName;
}
