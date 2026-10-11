package com.simple.jigou.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.simple.jigou.ai.AiAppNameService;
import com.simple.jigou.ai.AiCodeGeneratorService;
import com.simple.jigou.ai.AiCodeGeneratorServiceFactory;
import com.simple.jigou.ai.model.AppNameResult;
import com.simple.jigou.ai.model.HtmlCodeResult;
import com.simple.jigou.ai.model.MultiFileCodeResult;
import com.simple.jigou.ai.model.massage.AiResponseMessage;
import com.simple.jigou.ai.model.massage.ToolExecutedMessage;
import com.simple.jigou.ai.model.massage.ToolRequestMessage;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.core.parser.CodeParserExecutor;
import com.simple.jigou.core.saver.CodeFileSaverExecutor;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {


    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiAppNameService aiAppNameService;

    /**
     * AI 偶尔会把名称用成对的符号包裹，此处按成对符号去掉一层
     */
    private static final String[][] APP_NAME_WRAPPER_SYMBOLS = {
            {"\"", "\""}, {"'", "'"}, {"“", "”"}, {"‘", "’"},
            {"《", "》"}, {"【", "】"}, {"「", "」"}, {"（", "）"}, {"(", ")"}, {"`", "`"}
    };

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) throws BusinessException {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) throws BusinessException {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, CodeGenTypeEnum.VUE_PROJECT);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 根据应用初始需求生成应用名称
     * 命名属于增强能力，失败时返回 null 由调用方兜底，绝不影响应用创建
     *
     * @param initPrompt 应用初始需求
     * @return 清洗后的应用名称，失败时返回 null
     */
    public String generateAppName(String initPrompt) {
        if (StrUtil.isBlank(initPrompt)) {
            return null;
        }
        long startTime = System.currentTimeMillis();
        try {
            AppNameResult result = aiAppNameService.generateAppName(initPrompt);
            String appName = cleanAppName(result == null ? null : result.getAppName());
            if (StrUtil.isBlank(appName)) {
                log.warn("AI 未生成有效的应用名称，initPrompt：{}", initPrompt);
                return null;
            }
            log.info("AI 生成应用名称成功：{}，耗时 {} ms", appName, System.currentTimeMillis() - startTime);
            return appName;
        } catch (Exception e) {
            log.error("AI 生成应用名称失败，将使用兜底名称", e);
            return null;
        }
    }

    /**
     * 清洗AI生成的应用名称
     * 依次处理换行与多余空白、成对包裹符号、结尾标点、结构化 JSON 残留，最后限制长度
     *
     * @param rawAppName AI 返回的原始名称
     * @return 清洗后的名称，无效时返回 null
     */
    private String cleanAppName(String rawAppName) {
        if (StrUtil.isBlank(rawAppName)) {
            return null;
        }
        // 1. 换行、制表符与全角空格统一为空格，并压缩连续空白，避免 AI 输出的多行说明污染名称
        String appName = rawAppName.replaceAll("[\\r\\n\\t\\u3000]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
        // 2. 去掉成对包裹的引号、书名号等符号
        appName = stripWrapperSymbols(appName);
        // 3. 去掉结尾的语气标点，如「每日饮水打卡。」
        appName = appName.replaceAll("[。，,.;；!！?？]+$", "").trim();
        // 4. 模型输出格式异常时可能返回结构化 JSON 文本，这类内容直接视为无效
        if (appName.contains("{") || appName.contains("}")) {
            log.warn("AI 返回的应用名称格式异常：{}", appName);
            return null;
        }
        // 5. 限制长度，避免超长名称影响列表展示
        return StrUtil.sub(appName, 0, AppConstant.APP_NAME_MAX_LENGTH);
    }

    /**
     * 去掉AI用成对符号包裹名称的情况，如 `"名称"`、`《名称》`
     *
     * @param appName 待处理的名称
     * @return 去掉包裹符号后的名称
     */
    private String stripWrapperSymbols(String appName) {
        for (String[] wrapperSymbol : APP_NAME_WRAPPER_SYMBOLS) {
            String prefix = wrapperSymbol[0];
            String suffix = wrapperSymbol[1];
            if (appName.length() > prefix.length() + suffix.length()
                    && appName.startsWith(prefix) && appName.endsWith(suffix)) {
                return appName.substring(prefix.length(), appName.length() - suffix.length()).trim();
            }
        }
        return appName;
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream  TokenStream 对象
     * @param appId        应用 id
     * @param codeGenType  代码生成类型
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, CodeGenTypeEnum codeGenType) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolCall((PartialToolCall partialToolCall) -> {
                        try {
                            // 只透传碎片，绝对不要在这里解析 JSON！
                            ToolRequestMessage toolRequestMessage = new ToolRequestMessage(partialToolCall);
                            sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                        } catch (Exception e) {
                            // 兜底 1.19 依赖底层 ensureNotBlank 触发的空参报错，防止流式响应崩溃
                            log.warn("[appId: {}] 收到空的 ToolCall 片段，已跳过: {}", appId, e.getMessage());
                        }
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        log.error("[appId: {}] AI 流式生成失败，已中断本次生成", appId, error);
                        // 生成过程中断，会话记忆可能残留「有工具调用无工具结果」的非法序列，
                        // 失效 AI 服务实例缓存，下次请求会重建服务并从数据库历史重新加载记忆
                        aiCodeGeneratorServiceFactory.invalidate(appId, codeGenType);
                        sink.error(error);
                    })
                    .start();
        });
    }


}
