package com.flashsale.config;

import com.flashsale.interceptor.LoginInterceptor;
import com.flashsale.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * 
 * 配置要点：
 * 1. 拦截器配置：登录拦截、限流拦截
 * 2. 跨域配置：支持前后端分离
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    /**
     * 添加拦截器
     * 
     * 拦截器执行顺序：
     * 1. RateLimitInterceptor: 限流（最先执行，快速失败）
     * 2. LoginInterceptor: 登录验证
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流拦截器（全局）
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/**")
            .order(1);

        // 登录拦截器
        registry.addInterceptor(loginInterceptor)
            .addPathPatterns("/**")  // 拦截所有请求
            .excludePathPatterns(
                "/user/login",              // 排除登录接口
                "/user/register",           // 排除注册接口
                "/product/list",            // 排除商品列表
                "/product/detail/**",       // 排除商品详情（注意必须是 /** 才会匹配 /detail/{id}）
                "/product/seckill/list",    // 排除秒杀商品列表
                "/product/seckill/detail/**" // 排除秒杀商品详情
            )
            .order(2);
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")  // 允许所有来源
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);  // 预检请求缓存 1 小时
    }
}
