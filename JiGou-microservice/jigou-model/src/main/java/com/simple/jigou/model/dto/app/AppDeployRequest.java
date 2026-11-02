package com.simple.jigou.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 部署来源版本号：不传表示部署工作区最新代码，传入则表示部署该历史版本
     */
    private Integer version;

    private static final long serialVersionUID = 1L;
}
