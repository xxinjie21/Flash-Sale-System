package com.flashsale.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存扣减并发测试（需要本地 Redis，未启动时自动跳过）
 *
 * 重点验证两件事：
 * 1. 高并发下库存不超卖（项目最核心的承诺）
 * 2. 库存 key 不存在时返回 -1，而不是被当成「库存为 0 已售罄」
 *
 * @author XXJ
 */
@DisplayName("RedissonStockManager 库存并发扣减")
class RedissonStockManagerTest {

    private static final String REDIS_ADDRESS =
        System.getProperty("test.redis.address", "redis://127.0.0.1:6379");

    private static final String REDIS_PASSWORD =
        System.getProperty("test.redis.password", "123456");

    private static RedissonClient redissonClient;

    private static String skipReason;

    private RedissonStockManager stockManager;

    @BeforeAll
    static void initRedis() {
        try {
            Config config = new Config();
            config.useSingleServer()
                .setAddress(REDIS_ADDRESS)
                .setPassword(REDIS_PASSWORD.isEmpty() ? null : REDIS_PASSWORD);
            redissonClient = Redisson.create(config);
            // 触发一次真实网络调用，确认 Redis 真的可用
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
        stockManager = new RedissonStockManager();
        ReflectionTestUtils.setField(stockManager, "redissonClient", redissonClient);
    }

    private String newKey(String tag) {
        return "test:stock:" + tag + ":" + System.nanoTime();
    }

    @Test
    @DisplayName("200 线程并发抢 100 件库存：恰好 100 次成功，剩余库存为 0")
    void concurrentDecrease_shouldNeverOversell() throws Exception {
        String key = newKey("oversell");
        int stock = 100;
        int threads = 200;
        stockManager.setStock(key, stock);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        int result = stockManager.decreaseStock(key, 1);
                        if (result == 1) {
                            success.incrementAndGet();
                        } else if (result == 0) {
                            soldOut.incrementAndGet();
                        } else {
                            unexpected.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(doneGate.await(30, TimeUnit.SECONDS))
                .as("并发任务应在 30 秒内全部完成")
                .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).as("扣减成功次数").isEqualTo(stock);
        assertThat(soldOut.get()).as("库存不足次数").isEqualTo(threads - stock);
        assertThat(unexpected.get()).as("非预期返回值次数").isZero();
        assertThat(stockManager.getStock(key)).as("剩余库存").isZero();

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("库存 key 不存在时返回 -1，而不是伪装成售罄（回归：isExists 判断）")
    void decreaseStock_whenKeyMissing_shouldReturnMinusOne() {
        String key = newKey("missing");

        int result = stockManager.decreaseStock(key, 1);

        assertThat(result)
            .as("key 不存在代表库存未预热，属于系统故障，必须区别于「售罄」")
            .isEqualTo(-1);
    }

    @Test
    @DisplayName("库存为 0 时返回 0（售罄），与 key 不存在区分开")
    void decreaseStock_whenStockIsZero_shouldReturnZero() {
        String key = newKey("zero");
        stockManager.setStock(key, 0);

        assertThat(stockManager.decreaseStock(key, 1)).isEqualTo(0);

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("单次扣减多件：库存不足时整笔失败，不会部分扣减")
    void decreaseStock_multiQuantity_shouldBeAllOrNothing() {
        String key = newKey("multi");
        stockManager.setStock(key, 10);

        assertThat(stockManager.decreaseStock(key, 5)).isEqualTo(1);
        assertThat(stockManager.getStock(key)).isEqualTo(5);

        // 剩余 5 件，想买 6 件应当整体失败，且库存不能被改动
        assertThat(stockManager.decreaseStock(key, 6)).isEqualTo(0);
        assertThat(stockManager.getStock(key)).as("失败时库存不应被改动").isEqualTo(5);

        redissonClient.getKeys().delete(key);
    }

    @Test
    @DisplayName("回滚库存后可以继续扣减")
    void rollbackStock_shouldRestoreStock() {
        String key = newKey("rollback");
        stockManager.setStock(key, 3);

        assertThat(stockManager.decreaseStock(key, 3)).isEqualTo(1);
        assertThat(stockManager.getStock(key)).isZero();

        stockManager.rollbackStock(key, 2);

        assertThat(stockManager.getStock(key)).isEqualTo(2);
        assertThat(stockManager.decreaseStock(key, 2)).isEqualTo(1);
        assertThat(stockManager.getStock(key)).isZero();

        redissonClient.getKeys().delete(key);
    }
}
