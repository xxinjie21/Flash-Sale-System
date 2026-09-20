package com.flashsale.interceptor;

import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.util.RedissonRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 限流拦截器
 * 
 * 基于 Redisson 原生 RRateLimiter 实现令牌桶限流
 * 
 * 面试考点：
 * 1. 限流算法：令牌桶、漏桶、计数器、滑动窗口
 * 2. 为什么用 Redisson？取令牌与扣减在 Redis 端原子完成，天然无竞态
 * 3. 限流维度：用户维度、IP 维度、接口维度
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonRateLimiter redissonRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取用户 ID（从请求头或 Token 中）
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            // 如果没有用户 ID，使用 IP 地址作为限流维度
            userId = request.getRemoteAddr();
        }

        // 构建限流 Key
        String uri = request.getRequestURI();
        String limitKey = RedisKeyConstant.RATE_LIMIT_KEY + uri + ":" + userId;

        // 使用 Redisson 原生令牌桶限流
        boolean allowed = redissonRateLimiter.tryAcquire(
            limitKey,
            SystemConstant.RATE_LIMIT_PER_SECOND,  // 每秒最大请求数
            1  // 1 秒窗口
        );

        // 判断是否超过限制
        if (!allowed) {
            // 超过限流阈值
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return true;
    }
}
