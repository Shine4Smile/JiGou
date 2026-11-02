package com.simple.jigou.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用版本详情返回包装类（用于前端查看与对比版本代码）
 */
@Data
public class AppVersionDetailVO implements Serializable {

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 提交时间
     */
    private LocalDateTime commitTime;

    /**
     * 文件内容：文件名（相对路径）-> 文件内容
     */
    private Map<String, String> fileMap;

    private static final long serialVersionUID = 1L;
}
