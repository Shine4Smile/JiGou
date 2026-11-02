package com.simple.jigou.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 应用版本列表返回包装类
 */
@Data
public class AppVersionListVO implements Serializable {

    /**
     * 当前版本号（0 表示从未提交版本）
     */
    private Integer currentVersion;

    /**
     * 工作区是否存在代码（用于前端判断「提交版本」是否可用）
     */
    private Boolean hasCode;

    /**
     * 工作区是否存在未提交的修改（内容与当前版本不一致）
     */
    private Boolean uncommitted;

    /**
     * 版本列表（按版本号降序）
     */
    private List<AppVersionVO> versionList;

    private static final long serialVersionUID = 1L;
}
