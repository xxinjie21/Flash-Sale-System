package com.flashsale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 事务配置类
 * 
 * 启用 Spring 事务管理
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    // 事务管理器由 Spring Boot 自动配置
    // 使用 @Transactional 注解管理事务
    
}
