package com.flashsale.service;

import com.flashsale.entity.dto.SeckillRequest;

/**
 * 秒杀服务接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public interface SeckillService {

    /**
     * 执行秒杀下单
     * 
     * 核心流程：
     * 1. 接口令牌桶限流
     * 2. Redis 分布式锁防重复
     * 3. Lua 脚本原子扣库存
     * 4. 发送 MQ 消息异步下单
     * 5. 快速返回结果
     * 
     * @param request 秒杀请求
     * @return 订单号
     */
    String executeSeckill(SeckillRequest request);
}
