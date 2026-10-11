package com.simple.jigou.core.handler;

import cn.hutool.json.JSONUtil;
import com.simple.jigou.ai.model.massage.AiResponseMessage;
import com.simple.jigou.ai.model.massage.StreamMessageTypeEnum;
import com.simple.jigou.ai.model.massage.ToolExecutedMessage;
import com.simple.jigou.ai.model.massage.ToolRequestMessage;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.service.ChatHistoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON 消息流处理器测试（不启动 Spring 容器）
 * <p>
 * 重点覆盖「[选择工具] 写入文件」这一步：它既要实时发给前端，也要写进对话历史，
 * 否则刷新页面重新渲染这条 AI 消息时会少一步，出现「刚生成看得到、刷新后消失」的展示不一致
 */
class JsonMessageStreamHandlerTest {

    private static final long APP_ID = 1L;
    private static final long USER_ID = 9L;
    private static final String SELECT_TOOL_LINE = "[选择工具] 写入文件";

    @Test
    void shouldSendAndPersistSelectToolLineOncePerTool() {
        List<String> recorded = new ArrayList<>();

        List<String> chunks = handle(recorded, Flux.just(
                aiResponseJson("我来创建 index.html"),
                // 真实流式输出：同一个工具会推来多个参数碎片，碎片之间 id 相同
                toolRequestJson(0, "call_1", "{\"relative"),
                toolRequestJson(0, "call_1", "FilePath\":\"index.html\"}"),
                toolExecutedJson("call_1", "index.html", "<h1>hi</h1>"),
                aiResponseJson("文件已创建")
        ));

        // 前端实时看到的内容：文本 -> 选择工具（只一次）-> 工具调用 + 代码块 -> 文本
        Assertions.assertEquals(4, chunks.size());
        Assertions.assertEquals(1, countOccurrences(chunks, SELECT_TOOL_LINE));
        Assertions.assertEquals(SELECT_TOOL_LINE, chunks.get(1).trim());
        Assertions.assertTrue(chunks.get(2).contains("[工具调用] 写入文件 index.html"));
        Assertions.assertTrue(chunks.get(2).contains("```html"));
        Assertions.assertTrue(chunks.get(2).contains("<h1>hi</h1>"));
        Assertions.assertEquals("文件已创建", chunks.get(3));

        // 落库的历史内容与前端看到的一致，刷新页面后同样能渲染出这一步
        Assertions.assertEquals(1, recorded.size());
        String history = recorded.get(0);
        Assertions.assertTrue(history.startsWith("我来创建 index.html"));
        Assertions.assertEquals(1, countOccurrences(List.of(history), SELECT_TOOL_LINE));
        Assertions.assertTrue(history.indexOf(SELECT_TOOL_LINE)
                < history.indexOf("[工具调用] 写入文件 index.html"));
    }

    @Test
    void shouldSendSelectToolLineForEachToolInOrder() {
        List<String> recorded = new ArrayList<>();

        List<String> chunks = handle(recorded, Flux.just(
                aiResponseJson("开始生成两个文件"),
                toolRequestJson(0, "call_1", "{\"relativeFilePath\":\"index.html\"}"),
                toolExecutedJson("call_1", "index.html", "<h1>hi</h1>"),
                toolRequestJson(1, "call_2", "{\"relativeFilePath\":\"src/App.vue\"}"),
                toolExecutedJson("call_2", "src/App.vue", "<template><div /></template>")
        ));

        // 每个工具都会有自己的一次「选择工具」，且紧跟在对应的「工具调用」之前
        Assertions.assertEquals(2, countOccurrences(chunks, SELECT_TOOL_LINE));
        int firstSelect = indexOf(chunks, SELECT_TOOL_LINE, 0);
        int secondSelect = indexOf(chunks, SELECT_TOOL_LINE, firstSelect + 1);
        int firstCall = indexOf(chunks, "[工具调用] 写入文件 index.html", 0);
        int secondCall = indexOf(chunks, "[工具调用] 写入文件 src/App.vue", 0);
        Assertions.assertTrue(firstSelect < firstCall);
        Assertions.assertTrue(firstSelect < secondSelect);
        Assertions.assertTrue(secondSelect < secondCall);

        String history = recorded.get(0);
        Assertions.assertEquals(2, countOccurrences(List.of(history), SELECT_TOOL_LINE));
        Assertions.assertTrue(history.indexOf(SELECT_TOOL_LINE)
                < history.indexOf("[工具调用] 写入文件 index.html"));
        Assertions.assertTrue(history.indexOf("[工具调用] 写入文件 index.html")
                < history.lastIndexOf(SELECT_TOOL_LINE));
    }

    @Test
    void shouldIgnoreToolRequestWithoutId() {
        List<String> recorded = new ArrayList<>();

        // 没有 id 的碎片（例如某些网关不返回 tool_call_id）不产生提示，避免同一个工具被反复提示
        List<String> chunks = handle(recorded, Flux.just(
                aiResponseJson("开始生成"),
                toolRequestJson(0, null, "{\"relative"),
                toolRequestJson(0, null, "FilePath\":\"index.html\"}"),
                toolExecutedJson("call_1", "index.html", "<h1>hi</h1>")
        ));

        Assertions.assertEquals(0, countOccurrences(chunks, SELECT_TOOL_LINE));
        Assertions.assertEquals(1, recorded.size());
        String history = recorded.get(0);
        Assertions.assertFalse(history.contains(SELECT_TOOL_LINE));
        Assertions.assertTrue(history.contains("[工具调用] 写入文件 index.html"));
    }

    /**
     * 走完整个流，返回前端可见的分片
     *
     * @param recorded   接收落库内容的列表
     * @param originFlux 原始 JSON 消息流
     */
    private static List<String> handle(List<String> recorded, Flux<String> originFlux) {
        User loginUser = new User();
        loginUser.setId(USER_ID);
        List<String> chunks = new JsonMessageStreamHandler()
                .handle(originFlux, recordingHistoryService(recorded), APP_ID, loginUser)
                .collectList()
                .block();
        Assertions.assertNotNull(chunks);
        return chunks;
    }

    /** AI 文本消息 */
    private static String aiResponseJson(String data) {
        return JSONUtil.toJsonStr(new AiResponseMessage(data));
    }

    /** 工具调用请求：真实流式里参数是一小段一小段推来的 */
    private static String toolRequestJson(Integer index, String id, String arguments) {
        ToolRequestMessage message = new ToolRequestMessage();
        message.setType(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        message.setIndex(index);
        message.setId(id);
        message.setName("writeFile");
        message.setArguments(arguments);
        return JSONUtil.toJsonStr(message);
    }

    /** 工具执行结果：写入某个文件 */
    private static String toolExecutedJson(String id, String relativeFilePath, String content) {
        ToolExecutedMessage message = new ToolExecutedMessage();
        message.setType(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        message.setId(id);
        message.setName("writeFile");
        message.setArguments(JSONUtil.createObj()
                .set("relativeFilePath", relativeFilePath)
                .set("content", content)
                .toString());
        message.setResult("写入成功");
        return JSONUtil.toJsonStr(message);
    }

    /** 查找包含指定文本的分片下标，找不到返回 -1 */
    private static int indexOf(List<String> chunks, String text, int startIndex) {
        for (int i = startIndex; i < chunks.size(); i++) {
            if (chunks.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    /** 统计全文出现次数（同一个分片里出现多次也要算上） */
    private static int countOccurrences(List<String> chunks, String text) {
        int count = 0;
        for (String chunk : chunks) {
            int fromIndex = 0;
            while (true) {
                int index = chunk.indexOf(text, fromIndex);
                if (index < 0) {
                    break;
                }
                count++;
                fromIndex = index + text.length();
            }
        }
        return count;
    }

    /**
     * 只记录 addChatMessage 的动态代理桩，避免为接口写一堆空实现
     */
    private static ChatHistoryService recordingHistoryService(List<String> recorded) {
        return (ChatHistoryService) Proxy.newProxyInstance(
                ChatHistoryService.class.getClassLoader(),
                new Class<?>[]{ChatHistoryService.class},
                (proxy, method, args) -> {
                    // Object 的方法不能走业务桩，否则日志、断言等场景会出错
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ChatHistoryServiceStub";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("addChatMessage".equals(method.getName())) {
                        recorded.add((String) args[2]);
                        return true;
                    }
                    return defaultValue(method);
                });
    }

    /** 基本类型返回类型的默认值，避免代理返回 null 时抛空指针 */
    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        // 剩余 int 与 void：void 方法返回值会被忽略，按 0 处理即可
        return 0;
    }
}
