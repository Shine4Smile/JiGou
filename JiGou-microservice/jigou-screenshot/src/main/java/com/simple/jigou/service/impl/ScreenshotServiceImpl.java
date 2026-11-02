package com.simple.jigou.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.simple.jigou.constant.JiGouUrls;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.manager.CosManager;
import com.simple.jigou.service.ScreenshotService;
import com.simple.jigou.utils.WebScreenshotUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    // 注入时允许为空（如果未配置COS参数，CosManager不会创建）
    @Autowired(required = false)
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图的网址不能为空");
        log.info("开始生成网页截图，URL：{}", webUrl);
        // 本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "生成网页截图失败");
        if (cosManager != null) {
            // 上传图片到 COS
            try {
                String cosUrl = uploadScreenshotToCos(localScreenshotPath);
                ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "上传截图到对象存储失败");
                log.info("截图上传成功，URL：{}", cosUrl);
                // 清理本地文件
                cleanupLocalFile(localScreenshotPath);
                return cosUrl;
            } catch (Exception e) {
                // 捕获异常（如密钥错误、网络不通），走降级逻辑
                log.error("截图上传 COS 失败，降级使用本地路径。错误：{}", e.getMessage());
            }
        } else {
            log.warn("未检测到 CosManager 或 COS 配置不全，直接使用本地路径");
        }
        // 降级：本机磁盘 + Nginx 托管，对外返回 URL（截图服务与应用服务可能不在同一台机器上）
        String screenshotUrl = JiGouUrls.screenshotUrl(localScreenshotPath);
        log.info("截图保存到本地：{}，对外访问地址：{}", localScreenshotPath, screenshotUrl);
        return screenshotUrl;

    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            FileUtil.del(localFile);
            log.info("清理本地文件成功: {}", localFilePath);
        }
    }
}