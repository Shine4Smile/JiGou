package com.simple.jigou.controller;

import com.mybatisflex.core.query.QueryWrapper;
import com.simple.jigou.constant.AppConstant;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.model.entity.App;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.service.AppService;
import com.simple.jigou.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/static")
public class StaticResourceController {
    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 预览目录名格式：{codeGenType}_{appId}（与前端 getPreviewUrl 保持一致）
     */
    private static final Pattern PREVIEW_DIR_PATTERN = Pattern.compile("^([a-zA-Z0-9_]+)_(\\d+)$");

    /**
     * 注意：本类同时使用 Spring 的 Resource（文件资源）与 jakarta 的 Resource（依赖注入），
     * 因此注入注解使用全限定名，避免单一类型导入冲突
     */
    @jakarta.annotation.Resource
    private AppService appService;

    @jakarta.annotation.Resource
    private UserService userService;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8181/api/static/{deployKey}[/{fileName}]
     * <p>
     * 访问前会校验资源所属应用的可见范围：私有应用仅创建者与管理员可访问，避免私有代码被直链预览
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 校验可见范围：私有应用仅创建者与管理员可访问
            checkViewPermission(deployKey, request);
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (BusinessException e) {
            // 无权限访问私有应用的静态资源：统一返回 403，不暴露资源是否存在
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 校验静态资源所属应用的可见范围
     * 公开应用允许访问；私有应用仅创建者与管理员可访问；无法定位到应用记录时沿用原有的文件存在性判断
     *
     * @param deployKey 预览目录名（{codeGenType}_{appId}）或部署标识
     * @param request   请求对象
     */
    private void checkViewPermission(String deployKey, HttpServletRequest request) {
        App app = resolveApp(deployKey);
        if (app == null) {
            return;
        }
        appService.checkAppViewPermission(app, getLoginUserQuietly(request));
    }

    /**
     * 解析资源目录对应的应用
     * 优先按预览目录名 {codeGenType}_{appId} 解析，其次按部署标识（deployKey）解析
     *
     * @param deployKey 预览目录名或部署标识
     * @return 应用信息（无法定位时返回 null）
     */
    private App resolveApp(String deployKey) {
        Matcher matcher = PREVIEW_DIR_PATTERN.matcher(deployKey);
        if (matcher.matches()) {
            try {
                long appId = Long.parseLong(matcher.group(2));
                App app = appService.getById(appId);
                // 目录名必须与应用的生成类型完全匹配，避免构造目录名绕过校验
                if (app != null && deployKey.equals(app.getCodeGenType() + "_" + app.getId())) {
                    return app;
                }
            } catch (NumberFormatException ignored) {
                // 超出 long 范围的数字，继续按部署标识查找
            }
        }
        return appService.getOne(QueryWrapper.create().eq("deployKey", deployKey));
    }

    /**
     * 获取当前登录用户，未登录时返回 null
     * 静态资源可能被公开应用匿名访问，因此这里不直接抛出未登录异常
     *
     * @param request 请求对象
     * @return 登录用户（未登录返回 null）
     */
    private User getLoginUserQuietly(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
