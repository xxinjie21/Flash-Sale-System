package com.flashsale.service;

import com.flashsale.entity.User;

/**
 * 用户服务接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public interface UserService {

    /**
     * 用户注册
     * 
     * @param user 用户信息
     * @return 用户 ID
     */
    Long register(User user);

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @return Token
     */
    String login(String username, String password);

    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);

    /**
     * 根据 ID 查询用户
     * 
     * @param id 用户 ID
     * @return 用户信息
     */
    User getUserById(Long id);

    /**
     * 用户登出
     * 
     * @param token 用户 Token
     */
    void logout(String token);
}
