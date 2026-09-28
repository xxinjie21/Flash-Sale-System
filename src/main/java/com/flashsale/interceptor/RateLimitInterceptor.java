package com.flashsale.interceptor;

import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.util.RedissonRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 限流拦截器
 *
 * 基于 Redisson 原生 RRateLimiter 实现令牌桶限流。
 *
 * 面试考点：
 * 1. 限流算法：令牌桶、漏桶、计数器、滑动窗口
 * 2. 为什么用 Redisson？取令牌与扣减在 Redis 端原子完成，天然无竞态
 * 3. 限流维度怎么选（见下方说明）
 *
 * ⚠️ 两个容易踩的坑：
 * 1. **绝不能信任客户端传来的身份标识**。早期实现直接取 `X-User-Id` 请求头当限流
 *    维度，攻击者只要改一下这个头就能给自己换一个桶，限流形同虚设。
 *    现在只信任「上游拦截器写入的 request attribute」，否则退回客户端 IP。
 * 2. **必须支持反向代理场景**。若部署在 Nginx / 网关之后，`getRemoteAddr()`
 *    拿到的是网关 IP，所有用户会共用同一个桶，整站被限到阈值以下。
 *    因此优先解析 `X-Forwarded-For` 的第一段。
 *
 * @author XXJ
 * @since 2026-06-05
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonRateLimiter redissonRateLimiter;

    /**
     * 单维度每秒最大请求数，可用 flashsale.ratelimit.per-second 覆盖
     */
    @Value("${flashsale.ratelimit.per-second:100}")
    private long rateLimitPerSecond;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String dimension = resolveDimension(request);
        String limitKey = RedisKeyConstant.RATE_LIMIT_KEY + request.getRequestURI() + ":" + dimension;

        if (!redissonRateLimiter.tryAcquire(limitKey, rateLimitPerSecond, 1)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return true;
    }

    /**
     * 解析限流维度
     *
     * 优先使用上游拦截器写入的已认证用户（request attribute，客户端无法伪造）；
     * 未认证时退回客户端 IP。
     */
    private String resolveDimension(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return "u:" + userId;
        }
        return "ip:" + resolveClientIp(request);
    }

    /**
     * 解析客户端真实 IP（兼容反向代理）
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
