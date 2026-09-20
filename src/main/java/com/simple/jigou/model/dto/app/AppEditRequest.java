package com.simple.jigou.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户修改自己的应用请求参数接收（目前支持修改应用名称、可见范围）
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

    /**
     * 可见范围：private 私有（默认）/ public 公开
     */
    private String visibility;

    private static final long serialVersionUID = 1L;
}
