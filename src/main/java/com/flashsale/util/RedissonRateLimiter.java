package com.flashsale.util;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redisson 限流工具类
 *
 * 使用 Redisson 原生 {@link RRateLimiter} 实现令牌桶限流：
 * 每个时间窗口向桶中注入 maxCount 个令牌，请求取走 1 个，取不到即被限流。
 * 相比固定窗口计数器，令牌桶允许一定程度的突发流量，且窗口边界不会出现 2 倍放行。
 *
 * 面试考点：
 * 1. 为什么用 RRateLimiter 而不是自己写计数器？
 *    - 令牌桶语义由 Redisson 保证，窗口边界平滑，不会出现 2 倍突发
 *    - 取令牌的判定与扣减在 Redis 端一次原子脚本内完成，天然无竞态
 * 2. trySetRate 只在限流器不存在时生效，因此可以安全地每次调用：
 *    既不会重置已消耗的令牌，又能保证限流器过期重建后速率参数依然正确
 * 3. TTL 兜底：限流 key 按「用户 / 活动」维度生成，数量随维度增长，
 *    用 expireIfNotSet 保证 key 最终被回收，避免 Redis 内存无限增长
 *
 * @author XXJ
 * @since 2026-06-05
 */
@Component
public class RedissonRateLimiter {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 尝试获取访问权限（限流）
     *
     * @param key 限流 key
     * @param maxCount 窗口内最大请求数（桶容量 / 每窗口注入的令牌数）
     * @param expireSeconds 窗口大小（秒）
     * @return true-允许访问，false-超过限制
     */
    public boolean tryAcquire(String key, long maxCount, long expireSeconds) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 仅当限流器不存在时才会生效：重复调用不会重置已消耗的令牌
        rateLimiter.trySetRate(RateType.OVERALL, maxCount, expireSeconds, RateIntervalUnit.SECONDS);

        // 兜底 TTL，避免限流 key 常驻内存。
        // TTL 取窗口的 2 倍：窗口到期时桶本就已重新注满，不影响稳态速率
        rateLimiter.expireIfNotSet(Duration.ofSeconds(expireSeconds * 2));

        // 非阻塞获取 1 个令牌
        return rateLimiter.tryAcquire();
    }
}
