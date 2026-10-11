package com.simple.jigou.controller;

import cn.hutool.json.JSONUtil;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

/**
 * 应用聊天生成代码接口的流式响应测试
 * <p>
 * 重点覆盖「生成中途失败」：流式响应一旦开始就无法再修改 HTTP 状态码，
 * 失败原因必须通过 SSE 的 error 事件传给前端，并且失败后仍然发送 done 事件收尾，
 * 避免前端把断流当成「生成完成」
 */
class AppControllerChatToGenCodeTest {

    private AppController appController;

    /**
     * 用例指定的代码生成流
     */
    private Flux<String> codeStream;

    @BeforeEach
    void setUp() {
        appController = new AppController();
        setField(appController, "appService", stub(AppService.class,
                method -> "chatToGenCode".equals(method.getName()) ? codeStream : defaultValue(method)));
        setField(appController, "userService", stub(UserService.class, method -> {
            if ("getLoginUser".equals(method.getName())) {
                User loginUser = new User();
                loginUser.setId(1L);
                return loginUser;
            }
            return defaultValue(method);
        }));
    }

    @Test
    void shouldSendContentFramesAndDoneEvent() {
        codeStream = Flux.just("hello", " world");

        List<ServerSentEvent<String>> events = collectEvents();

        Assertions.assertEquals(3, events.size());
        Assertions.assertEquals("hello", JSONUtil.parseObj(events.get(0).data()).getStr("d"));
        Assertions.assertEquals(" world", JSONUtil.parseObj(events.get(1).data()).getStr("d"));
        Assertions.assertEquals("done", events.get(2).event());
    }

    @Test
    void shouldSendErrorEventAndDoneEventWhenStreamFails() {
        codeStream = Flux.concat(Flux.just("part"), Flux.<String>error(new RuntimeException("模型调用失败")));

        List<ServerSentEvent<String>> events = collectEvents();

        // 事件顺序：已生成的内容 -> 错误事件 -> 结束事件
        Assertions.assertEquals(3, events.size());
        Assertions.assertEquals("part", JSONUtil.parseObj(events.get(0).data()).getStr("d"));
        ServerSentEvent<String> errorEvent = events.get(1);
        Assertions.assertEquals("error", errorEvent.event());
        Assertions.assertEquals("模型调用失败", JSONUtil.parseObj(errorEvent.data()).getStr("message"));
        Assertions.assertEquals("done", events.get(2).event());
    }

    @Test
    void shouldCarryErrorCodeWhenBusinessExceptionHappens() {
        codeStream = Flux.error(new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录"));

        List<ServerSentEvent<String>> events = collectEvents();

        Assertions.assertEquals(2, events.size());
        ServerSentEvent<String> errorEvent = events.get(0);
        Assertions.assertEquals("error", errorEvent.event());
        Assertions.assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), JSONUtil.parseObj(errorEvent.data()).getInt("code"));
        Assertions.assertEquals("用户未登录", JSONUtil.parseObj(errorEvent.data()).getStr("message"));
        Assertions.assertEquals("done", events.get(1).event());
    }

    @Test
    void shouldUseDefaultMessageWhenErrorHasNoMessage() {
        codeStream = Flux.<String>error(new RuntimeException());

        List<ServerSentEvent<String>> events = collectEvents();

        ServerSentEvent<String> errorEvent = events.get(0);
        Assertions.assertEquals("error", errorEvent.event());
        Assertions.assertEquals("生成失败，请稍后重试", JSONUtil.parseObj(errorEvent.data()).getStr("message"));
        // 非业务异常不携带错误码
        Assertions.assertNull(JSONUtil.parseObj(errorEvent.data()).get("code"));
        Assertions.assertEquals("done", events.get(1).event());
    }

    /**
     * 调用接口并收集全部 SSE 事件
     */
    private List<ServerSentEvent<String>> collectEvents() {
        List<ServerSentEvent<String>> events = appController.chatToGenCode(1L, "生成一个页面", null).collectList().block();
        Assertions.assertNotNull(events);
        return events;
    }

    /**
     * 通过反射注入被 @Resource 标注的依赖（本测试不启动 Spring 容器）
     */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入字段失败: " + fieldName, e);
        }
    }

    /**
     * 创建接口的动态代理桩对象
     *
     * @param type    接口类型
     * @param handler 根据方法返回桩值
     */
    private static <T> T stub(Class<T> type, Function<Method, Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            // Object 的方法不能走业务桩，否则日志、断言等场景会出错
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return handler.apply(method);
        }));
    }

    /**
     * 基本类型返回类型的默认值，避免代理返回 null 时抛空指针
     */
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
