package com.simple.jigou.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 为后续微服务做准备，所以将项目下载单独封装成一个通用服务
 */
public interface ProjectDownloadService {
    /**
     * 下载项目为压缩包
     *
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);

}