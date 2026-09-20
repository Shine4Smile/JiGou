package com.simple.jigou.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用版本信息返回包装类
 */
@Data
public class AppVersionVO implements Serializable {

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 提交时间
     */
    private LocalDateTime commitTime;

    /**
     * 是否为当前版本（应用当前指针指向的版本）
     */
    private Boolean current;

    private static final long serialVersionUID = 1L;
}
