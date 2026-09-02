package com.cqu.service;

import com.cqu.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author
 * @since 2026-06-29
 */
public interface IUsersService extends IService<Users> {

    /**
     * 用户注册
     * @param users 用户信息（username, password）
     * @return 注册成功的用户实体
     */
    Users register(Users users);

    /**
     * 用户登录
     * @param username 用户名
     * @param password 明文密码
     * @return 登录成功的用户实体
     */
    Users login(String username, String password);
}
