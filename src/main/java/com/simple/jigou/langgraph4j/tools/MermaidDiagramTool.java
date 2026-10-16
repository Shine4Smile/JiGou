package com.simple.jigou.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.langgraph4j.model.ImageResource;
import com.simple.jigou.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class MermaidDiagramTool {


    /**
     * 将 Mermaid 文本代码渲染为 SVG 架构图，并将其持久化到本地，最终返回可通过 Nginx 访问的 HTTP URL。
     * 注：与生成应用截图并更新封面功能相同，本打算对接COS进行图片存储，考虑个人学习项目暂且存放本地，通过nginx访问
     *
     * @param mermaidCode Mermaid 图表代码（如 flowchart TD...）
     * @param description 架构图的描述信息，用于前端展示或 AI 上下文
     * @return 包含单个 ImageResource 的列表；如果生成失败或代码为空，则返回空列表
     */
    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                                      @P("架构图描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        try {
            // 转换为SVG图片
            File diagramFile = convertMermaidToSvg(mermaidCode);
            // 创建临时目录，将生成的架构图移动到指定的tmp目录下
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "MermaidDiagram"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            File saveDir = new File(rootPath);
            if (!saveDir.exists()) {
                saveDir.mkdirs(); // 确保目录存在
            }
            File destFile = new File(saveDir, diagramFile.getName());
            FileUtil.copy(diagramFile, destFile, true);
            // 清理临时文件
            FileUtil.del(diagramFile);
            // 将本地绝对路径转换为 Nginx 访问的 URL
            String httpUrl = convertMermaidUrlToHttpUrl(destFile.getAbsolutePath());
            if (StrUtil.isNotBlank(httpUrl)) {
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(httpUrl)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * 将Mermaid代码转换为SVG图片
     */
    private File convertMermaidToSvg(String mermaidCode) {
        // 创建临时输入文件
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        // 创建临时输出文件
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        // 根据操作系统选择命令
        String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        // 构建命令
        String cmdLine = String.format("%s -i %s -o %s -b transparent",
                command,
                tempInputFile.getAbsolutePath(),
                tempOutputFile.getAbsolutePath()
        );
        // 执行命令
        RuntimeUtil.execForStr(cmdLine);
        // 检查输出文件
        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        // 清理输入文件，保留输出文件供上传使用
        FileUtil.del(tempInputFile);
        return tempOutputFile;
    }

    /**
     * 将本地绝对路径转换为 HTTP 访问路径（针对 Mermaid 图片）
     * 例如：D:/project/studyProject/JiGou/tmp/MermaidDiagram/a1b2c3d4/1.svg
     * 转换为：http://localhost/MermaidDiagram/a1b2c3d4/1.svg
     */
    private static String convertMermaidUrlToHttpUrl(String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) {
            return "";
        }

        // 1. 统一斜杠，兼容 Windows 和 Linux
        String normalizedPath = localPath.replace("\\", "/");

        // 2. 截取 "MermaidDiagram/" 及其后面的内容（跳过前面的 tmp/）
        String keyword = "MermaidDiagram/";
        int index = normalizedPath.indexOf(keyword);

        if (index != -1) {
            // 截取得到：MermaidDiagram/a1b2c3d4/1.svg
            String relativePath = normalizedPath.substring(index);
            // 拼接成不带 tmp 的 HTTP 地址
            return "http://localhost/" + relativePath;
        }

        // 兜底：如果路径异常，原样返回
        return normalizedPath;
    }
}
