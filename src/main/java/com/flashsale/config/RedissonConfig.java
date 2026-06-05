package com.flashsale.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类
 * 
 * Redisson 是 Redis 的 Java 客户端，提供分布式锁等功能
 * 
 * 面试考点：
 * 1. Redisson 锁的优势：
 *    - 自动续期（WatchDog 机制）
 *    - 可重入
 *    - 避免死锁
 * 2. 锁的粒度：锁粒度越小，并发性能越高
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    /**
     * 配置 RedissonClient
     * 
     * @return RedissonClient
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // 单机模式配置（生产环境建议使用集群或哨兵模式）
        String address = String.format("redis://%s:%d", redisHost, redisPort);
        config.useSingleServer()
            .setAddress(address)
            .setDatabase(redisDatabase);
        
        // 如果有密码，设置密码
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }
        
        // 连接池配置
        config.useSingleServer()
            .setConnectionMinimumIdleSize(10)
            .setConnectionPoolSize(50)
            .setIdleConnectionTimeout(30000)
            .setConnectTimeout(10000)
            .setRetryAttempts(3)
            .setRetryInterval(1500);
        
        return Redisson.create(config);
    }
}
