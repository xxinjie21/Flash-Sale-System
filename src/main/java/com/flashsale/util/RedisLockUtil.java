package com.flashsale.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具类
 * 
 * 基于 Redisson 实现分布式锁，用于解决分布式环境下的并发控制问题
 * 
 * 面试考点：
 * 1. 分布式锁应用场景：秒杀库存扣减、防止重复下单
 * 2. Redisson 锁的优势：自动续期（WatchDog 机制）、可重入、避免死锁
 * 3. 锁粒度控制：锁粒度越小，并发性能越高
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final RedissonClient redissonClient;

    /**
     * 尝试获取锁
     * 
     * @param lockKey 锁的 key
     * @param waitTime 等待时间
     * @param leaseTime 锁持有时间
     * @param timeUnit 时间单位
     * @return true-获取成功，false-获取失败
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 尝试获取锁（默认等待时间和持有时间）
     * 
     * @param lockKey 锁的 key
     * @return true-获取成功，false-获取失败
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, 5, 30, TimeUnit.SECONDS);
    }

    /**
     * 获取锁（阻塞式，直到获取到锁为止）
     * 
     * @param lockKey 锁的 key
     * @param leaseTime 锁持有时间
     * @param timeUnit 时间单位
     */
    public void lock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, timeUnit);
    }

    /**
     * 获取锁（默认持有时间）
     * 
     * @param lockKey 锁的 key
     */
    public void lock(String lockKey) {
        lock(lockKey, 30, TimeUnit.SECONDS);
    }

    /**
     * 释放锁
     * 
     * @param lockKey 锁的 key
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 检查锁是否被持有
     * 
     * @param lockKey 锁的 key
     * @return true-锁被持有，false-锁未被持有
     */
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * 检查当前线程是否持有锁
     * 
     * @param lockKey 锁的 key
     * @return true-当前线程持有锁，false-当前线程未持有锁
     */
    public boolean isHeldByCurrentThread(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isHeldByCurrentThread();
    }
}
