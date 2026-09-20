package com.simple.jigou.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用提交版本请求参数接收
 */
@Data
public class AppVersionCommitRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}
