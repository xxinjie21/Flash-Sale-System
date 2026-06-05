package com.flashsale.runner;

import com.flashsale.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动数据预热
 * 
 * 使用 CommandLineRunner 实现数据预热
 * 项目启动完成后自动执行初始化操作
 * 
 * 面试考点：
 * 1. CommandLineRunner vs ApplicationListener<ApplicationReadyEvent>
 *    - CommandLineRunner: Spring Boot 提供，更简洁，专用于启动后初始化
 *    - ApplicationListener: Spring 事件机制，更通用但较繁琐
 * 2. 执行时机：Spring Boot 启动完成后立即执行
 * 3. 应用场景：数据预热、缓存加载、定时任务启动等
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataWarmUpRunner implements CommandLineRunner {

    private final ProductService productService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("应用启动完成，开始初始化数据...");
        
        try {
            // 预热秒杀商品库存到 Redis
            // 将数据库中的秒杀商品库存数据加载到 Redis 缓存
            // 目的：秒杀开始时直接从 Redis 扣减库存，避免数据库压力
            productService.warmUpSeckillStock();
            log.info("数据初始化完成");
        } catch (Exception e) {
            log.error("数据初始化失败：", e);
            // 预热失败不阻止应用启动，但记录错误日志
        }
        
        log.info("========================================");
        log.info("高并发秒杀系统已就绪！");
        log.info("API 地址：http://localhost:8080");
        log.info("========================================");
    }
}
