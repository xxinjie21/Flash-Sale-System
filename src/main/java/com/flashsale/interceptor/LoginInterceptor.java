package com.flashsale.interceptor;

import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.User;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 * 
 * 验证用户是否已登录，未登录则拦截请求
 * 
 * 面试考点：
 * 1. 拦截器 vs 过滤器：拦截器基于反射，过滤器基于函数回调
 * 2. Token 验证流程：客户端携带 Token→服务端验证→返回用户信息
 * 3. Token 续期：每次访问刷新过期时间（滑动窗口）
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取 Token（从请求头）
        String token = request.getHeader("X-Auth-Token");

        // 验证 Token 是否存在
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 从 Redis 中获取用户信息
        String userKey = RedisKeyConstant.USER_TOKEN_KEY + token;
        Object userObj = redisTemplate.opsForValue().get(userKey);

        if (userObj == null) {
            // Token 不存在或已过期
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        // 将用户信息转换为 User 对象
        User user;
        try {
            if (userObj instanceof User) {
                user = (User) userObj;
            } else {
                user = objectMapper.readValue(objectMapper.writeValueAsString(userObj), User.class);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 验证用户状态
        if (!SystemConstant.YES.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 将用户 ID 放入请求头，供后续使用
        request.setAttribute("userId", user.getId());
        request.setAttribute("userInfo", user);

        // Token 续期（滑动窗口）
        redisTemplate.expire(userKey, SystemConstant.USER_TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);

        return true;
    }
}
