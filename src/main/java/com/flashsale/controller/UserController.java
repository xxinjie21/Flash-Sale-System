package com.flashsale.controller;

import com.flashsale.entity.User;
import com.flashsale.service.UserService;
import com.flashsale.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 用户 Controller
 * 
 * 负责处理用户相关的 HTTP 请求
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     * 
     * @param user 用户信息
     * @return 用户 ID
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody User user) {
        Long userId = userService.register(user);
        return Result.success(userId);
    }

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @return Token
     */
    @PostMapping("/login")
    public Result<String> login(
        @RequestParam String username,
        @RequestParam String password
    ) {
        String token = userService.login(username, password);
        return Result.success(token);
    }

    /**
     * 用户登出
     * 
     * @param request HTTP 请求
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("X-Auth-Token");
        if (token != null && !token.isEmpty()) {
            // 登出时删除 Redis 中的 Token
            userService.logout(token);
        }
        return Result.success();
    }

    /**
     * 获取当前用户信息
     * 
     * @param request HTTP 请求
     * @return 用户信息
     */
    @GetMapping("/info")
    public Result<User> getUserInfo(HttpServletRequest request) {
        User user = (User) request.getAttribute("userInfo");
        return Result.success(user);
    }
}
