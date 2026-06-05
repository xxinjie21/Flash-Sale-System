package com.flashsale.util;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 限流工具类
 * 
 * 使用 Redisson 原子计数器实现限流，替代 Lua 脚本方案
 * 
 * 面试考点：
 * 1. 原子性保证：Redisson 的原子操作保证线程安全
 * 2. 性能优势：无需执行 Lua 脚本，直接原子操作
 * 3. 实现简单：代码更清晰，易于维护
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
     * @param maxCount 最大请求数
     * @param expireSeconds 过期时间（秒）
     * @return true-允许访问，false-超过限制
     */
    public boolean tryAcquire(String key, long maxCount, long expireSeconds) {
        // 获取原子计数器
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        
        // 原子递增并获取当前值
        long currentCount = atomicLong.incrementAndGet();
        
        // 如果是第一次访问，设置过期时间
        if (currentCount == 1) {
            atomicLong.expireAsync(expireSeconds, TimeUnit.SECONDS);
        }
        
        // 判断是否超过限制
        if (currentCount > maxCount) {
            return false;
        }
        
        return true;
    }

    /**
     * 获取当前计数
     * 
     * @param key 限流 key
     * @return 当前计数值
     */
    public long getCount(String key) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.get();
    }

    /**
     * 重置计数器
     * 
     * @param key 限流 key
     */
    public void reset(String key) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        atomicLong.set(0);
    }
}
