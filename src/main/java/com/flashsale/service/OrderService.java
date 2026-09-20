package com.flashsale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.entity.Order;
import com.flashsale.entity.dto.OrderDTO;

import java.util.List;
import java.util.Map;

/**
 * 订单服务接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public interface OrderService {

    /**
     * 创建订单
     * 
     * @param userId 用户 ID
     * @param productId 商品 ID
     * @param quantity 数量
     * @param seckillId 秒杀 ID（如果是秒杀订单）
     * @return 订单信息
     */
    OrderDTO createOrder(Long userId, Long productId, Integer quantity, Long seckillId);

    /**
     * 获取订单详情
     * 
     * @param orderId 订单 ID
     * @return 订单详情
     */
    OrderDTO getOrderDetail(Long orderId);

    /**
     * 获取订单详情（根据订单号）
     * 
     * @param orderNo 订单号
     * @return 订单详情
     */
    OrderDTO getOrderByOrderNo(String orderNo);

    /**
     * 获取用户订单列表（分页）
     * 
     * @param userId 用户 ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 订单列表
     */
    Page<OrderDTO> getUserOrderList(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 取消订单
     * 
     * @param orderId 订单 ID
     * @param userId 用户 ID
     */
    void cancelOrder(Long orderId, Long userId);

    /**
     * 支付订单
     * 
     * @param orderId 订单 ID
     * @param userId 用户 ID
     */
    void payOrder(Long orderId, Long userId);

    /**
     * 处理超时订单（取消订单 + 回滚库存）
     * 
     * @param orderNo 订单号
     */
    void handleTimeoutOrder(String orderNo);

    /**
     * 根据 MQ 消息创建订单（供消费者调用）
     *
     * 必须放在 Service 层：消费者内部自调用会使 @Transactional 代理失效，
     * 通过接口调用才能让事务真正生效。
     * 方法内部按 orderNo 做幂等校验，重复投递不会重复建单。
     *
     * @param message 订单消息
     * @param isSeckill 是否秒杀订单
     */
    void createOrderFromMessage(Map<String, Object> message, boolean isSeckill);
}
