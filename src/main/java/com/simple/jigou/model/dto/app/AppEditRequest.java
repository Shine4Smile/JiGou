package com.simple.jigou.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户修改自己的应用请求参数接收（目前仅支持修改应用名称）
 */
@Data
public class AppEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}
