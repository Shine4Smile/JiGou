package com.simple.jigou.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.simple.jigou.mapper.UserMapper;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户 服务层实现。
 *
 * @author simple
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
