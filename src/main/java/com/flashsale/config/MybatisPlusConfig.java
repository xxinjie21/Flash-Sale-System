package com.flashsale.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 配置类
 * 
 * 配置要点：
 * 1. 分页插件：支持物理分页
 * 2. MapperScan: 扫描 Mapper 接口
 * 3. 使用注解方式，无需 XML 配置文件
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Configuration
@EnableTransactionManagement  // 启用事务管理
@MapperScan("com.flashsale.mapper")  // 扫描 Mapper 接口
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器
     * 
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
