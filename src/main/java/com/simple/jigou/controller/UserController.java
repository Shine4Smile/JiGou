package com.simple.jigou.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.simple.jigou.annotation.AuthCheck;
import com.simple.jigou.common.BaseResponse;
import com.simple.jigou.common.DeleteRequest;
import com.simple.jigou.common.ResultUtils;
import com.simple.jigou.constant.UserConstant;
import com.simple.jigou.exception.BusinessException;
import com.simple.jigou.exception.ErrorCode;
import com.simple.jigou.exception.ThrowUtils;
import com.simple.jigou.model.dto.user.*;
import com.simple.jigou.model.entity.User;
import com.simple.jigou.model.vo.LoginUserVO;
import com.simple.jigou.model.vo.UserVO;
import com.simple.jigou.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 控制层。
 *
 * @author simple
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;


    /**
     * 用户注册
     *
     * @param registerRequest 用户注册请求参数接收类
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest registerRequest) {
        ThrowUtils.throwIf(registerRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = registerRequest.getUserAccount();
        String userName = registerRequest.getUserName();
        String userPassword = registerRequest.getUserPassword();
        String checkPassword = registerRequest.getCheckPassword();
        long res = userService.userRegister(userAccount, userName, userPassword, checkPassword);
        return ResultUtils.success(res);
    }

    /**
     * 用户登录
     *
     * @param loginRequest 用户登录请求参数接收类
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest loginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(loginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = loginRequest.getUserAccount();
        String userPassword = loginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取登录用户信息（脱敏后）
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 注销登录
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 用户编辑自己用户信息
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editUser(@RequestBody UserEditRequest userEditRequest) {
        if (userEditRequest == null || userEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userEditRequest, user);
        user.setEditTime(LocalDateTime.now());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 创建用户（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户（管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }


}
