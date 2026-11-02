package com.simple.jigou.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用回退版本请求参数接收
 */
@Data
public class AppVersionRollbackRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 要回退到的版本号
     */
    private Integer version;

    private static final long serialVersionUID = 1L;
}
