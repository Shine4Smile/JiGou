package com.simple.jigou.innerservice;

/**
 * 定义截图相关内部接口
 */
public interface InnerScreenshotService {
    String generateAndUploadScreenshot(String webUrl);
}
