package com.flashsale.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 令牌桶限流测试（需要本地 Redis，未启动时自动跳过）
 *
 * @author XXJ
 */
@DisplayName("RedissonRateLimiter 令牌桶限流")
class RedissonRateLimiterTest {

    private static final String REDIS_ADDRESS =
        System.getProperty("test.redis.address", "redis://127.0.0.1:6379");

    private static final String REDIS_PASSWORD =
        System.getProperty("test.redis.password", "123456");

    private static RedissonClient redissonClient;

    private static String skipReason;

    private RedissonRateLimiter rateLimiter;

    @BeforeAll
    static void initRedis() {
        try {
            Config config = new Config();
            config.useSingleServer()
                .setAddress(REDIS_ADDRESS)
                .setPassword(REDIS_PASSWORD.isEmpty() ? null : REDIS_PASSWORD);
            redissonClient = Redisson.create(config);
            redissonClient.getKeys().count();
        } catch (Exception e) {
            redissonClient = null;
            skipReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        Assumptions.assumeTrue(redissonClient != null,
            "本地 Redis 不可用，跳过集成测试（" + skipReason + "）；"
                + "可用 -Dtest.redis.address / -Dtest.redis.password 覆盖");
    }

    @AfterAll
    static void closeRedis() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        rateLimiter = new RedissonRateLimiter();
        ReflectionTestUtils.setField(rateLimiter, "redissonClient", redissonClient);
    }

    private String newKey(String tag) {
        return "test:rate:" + tag + ":" + System.nanoTime();
    }

    @Test
    @DisplayName("窗口内放行次数恰好等于桶容量，其余全部拒绝")
    void tryAcquire_shouldAllowExactlyMaxCountWithinWindow() {
        String key = newKey("limit");
        long maxCount = 5;

        // 用 60 秒长窗口，保证循环期间不会补充令牌
        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (rateLimiter.tryAcquire(key, maxCount, 60)) {
                allowed++;
            }
        }

        assertThat(allowed).as("放行次数应恰好等于桶容量").isEqualTo((int) maxCount);

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("窗口结束后令牌重新注满")
    void tryAcquire_shouldRefillAfterWindow() throws InterruptedException {
        String key = newKey("refill");

        assertThat(rateLimiter.tryAcquire(key, 2, 1)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 2, 1)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 2, 1)).as("桶已空，应被限流").isFalse();

        Thread.sleep(1200);

        assertThat(rateLimiter.tryAcquire(key, 2, 1)).as("新窗口开始后应重新放行").isTrue();

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("限流 key 会被设置 TTL，避免按维度增长的 key 常驻内存")
    void tryAcquire_shouldSetTtlOnLimiterKey() {
        String key = newKey("ttl");
        long windowSeconds = 10;

        rateLimiter.tryAcquire(key, 100, windowSeconds);

        long ttl = redissonClient.getRateLimiter(key).remainTimeToLive();
        assertThat(ttl)
            .as("TTL 应已设置（窗口的 2 倍，单位毫秒）")
            .isGreaterThan(0L)
            .isLessThanOrEqualTo(windowSeconds * 2 * 1000 + 1000);

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("重复调用不会重置已消耗的令牌（必须用 trySetRate 而非 setRate）")
    void tryAcquire_shouldNotResetTokensOnRepeatedCalls() {
        String key = newKey("noreset");
        long maxCount = 3;

        assertThat(rateLimiter.tryAcquire(key, maxCount, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, maxCount, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, maxCount, 60)).isTrue();

        // 第 4 次调用仍会执行 trySetRate，但不能把令牌重置回 3 个
        assertThat(rateLimiter.tryAcquire(key, maxCount, 60))
            .as("若此处返回 true，说明限流被 trySetRate 意外重置，限流形同虚设")
            .isFalse();

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("不同 key 之间的限流互不影响（用户维度 / 活动维度隔离）")
    void tryAcquire_shouldIsolateDifferentKeys() {
        String userKey = newKey("user");
        String seckillKey = newKey("seckill");

        // 把活动维度桶打空
        assertThat(rateLimiter.tryAcquire(seckillKey, 1, 60)).isTrue();
        assertThat(rateLimiter.tryAcquire(seckillKey, 1, 60)).isFalse();

        // 用户维度仍应有独立配额
        assertThat(rateLimiter.tryAcquire(userKey, 1, 60)).as("用户维度不应受活动维度影响").isTrue();

        redissonClient.getKeys().delete(userKey, seckillKey);
    }
}
