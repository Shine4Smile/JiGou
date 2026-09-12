package com.simple.jigou.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户编辑自己用户信息请求参数接收
 */
@Data
public class UserEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}
