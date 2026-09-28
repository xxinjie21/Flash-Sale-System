package com.flashsale.util;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
 * 3. ⚠️ 千万不要给 RRateLimiter 的 key 设置 TTL（包括 expireIfNotSet）：
 *    RRateLimiter 的状态跨多个 key（主 Hash + {key}:value + {key}:permits），
 *    只给主 Hash 加 TTL 会周期性地把它清掉，而 Lua 脚本随后会对 nil 做算术，
 *    抛出 `attempt to perform arithmetic on a nil value` /
 *    `RateLimiter is not initialized`，限流器直接失效并让请求 500。
 *    在 2000 并发压测下这个问题会被放大到约 30% 请求失败。
 *    代价是限流 key 不会自动回收，需要按维度数量评估内存，或另配清理任务。
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

        // 非阻塞获取 1 个令牌
        return rateLimiter.tryAcquire();
    }
}
