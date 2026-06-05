package com.flashsale.util;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Redisson 库存扣减工具类
 * 
 * 使用 Redisson 原子操作扣减库存，替代 Lua 脚本方案
 * 
 * 面试考点：
 * 1. 原子性保证：compareAndSet CAS 操作保证原子性
 * 2. 乐观锁机制：失败重试，避免超卖
 * 3. 性能优势：纯内存操作，性能极高
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Component
public class RedissonStockManager {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 扣减库存（原子操作）
     * 
     * @param stockKey 库存 key
     * @param quantity 扣减数量
     * @return 1-扣减成功，0-库存不足，-1-库存不存在
     */
    public int decreaseStock(String stockKey, int quantity) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(stockKey);
        
        // 检查库存是否存在
        long currentStock = atomicLong.get();
        if (currentStock < 0) {
            // Key 不存在
            return -1;
        }
        
        // CAS 乐观锁扣减库存
        while (currentStock >= quantity) {
            // 尝试原子扣减
            if (atomicLong.compareAndSet(currentStock, currentStock - quantity)) {
                return 1;  // 扣减成功
            }
            // 如果失败，重新获取当前值并重试
            currentStock = atomicLong.get();
        }
        
        // 库存不足
        return 0;
    }

    /**
     * 获取库存
     * 
     * @param stockKey 库存 key
     * @return 当前库存
     */
    public long getStock(String stockKey) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(stockKey);
        return atomicLong.get();
    }

    /**
     * 设置库存
     * 
     * @param stockKey 库存 key
     * @param stock 库存数量
     */
    public void setStock(String stockKey, long stock) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(stockKey);
        atomicLong.set(stock);
    }

    /**
     * 回滚库存（增加库存）
     * 
     * @param stockKey 库存 key
     * @param quantity 增加数量
     */
    public void rollbackStock(String stockKey, int quantity) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(stockKey);
        atomicLong.addAndGet(quantity);
    }

    /**
     * 删除库存
     * 
     * @param stockKey 库存 key
     */
    public void deleteStock(String stockKey) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(stockKey);
        atomicLong.delete();
    }
}
