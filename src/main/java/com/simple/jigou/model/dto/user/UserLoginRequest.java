package com.simple.jigou.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 接受用户登录请求参数
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 964003059984250803L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
