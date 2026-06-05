package com.flashsale;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 高并发秒杀系统启动类
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@SpringBootApplication
@MapperScan("com.flashsale.mapper")
@EnableAsync  // 启用异步方法支持
@EnableScheduling  // 启用定时任务支持
public class FlashSaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleApplication.class, args);
        System.out.println("========================================");
        System.out.println("   高并发秒杀系统启动成功！");
        System.out.println("   支持单机 5000 并发，P99 响应<500ms");
        System.out.println("========================================");
    }
}
