package com.flashsale.mq;

import com.flashsale.constant.MQConstant;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.Order;
import com.flashsale.entity.Product;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.exception.BusinessException;
import com.flashsale.mapper.OrderMapper;
import com.flashsale.mapper.ProductMapper;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单消息消费者
 * 
 * 监听 MQ 消息，异步创建订单
 * 
 * 面试考点：
 * 1. 手动 ACK：保证消息不丢失
 * 2. 消息幂等：防止重复消费
 * 3. 异常处理：重试机制、死信队列
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderMapper orderMapper;

    private final ProductMapper productMapper;

    private final SeckillProductMapper seckillProductMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final OrderService orderService;

    /**
     * 监听普通订单队列
     * 
     * @param message 消息内容
     * @param channel 通道
     * @param deliveryTag 消息标签
     */
    @RabbitListener(queues = MQConstant.ORDER_QUEUE)
    public void consumeOrderMessage(Map<String, Object> message, Channel channel, 
                                    long deliveryTag) throws IOException {
        log.info("收到普通订单消息：{}", message);
        
        try {
            // 创建订单
            createOrderFromMessage(message, false);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("订单创建成功，消息 ACK");
        } catch (Exception e) {
            log.error("订单创建失败：", e);
            // 重试次数检查
            Integer retryCount = (Integer) message.get("retryCount");
            if (retryCount == null) {
                retryCount = 0;
            }
            retryCount++;

            if (retryCount < MQConstant.MESSAGE_RETRY_COUNT) {
                // 重试：重新发送消息
                message.put("retryCount", retryCount);
                channel.basicNack(deliveryTag, false, false);  // 不重新入队，避免死循环
                log.warn("订单创建失败，准备重试，重试次数：{}", retryCount);
            } else {
                // 超过重试次数，拒绝消息（进入死信队列）
                channel.basicNack(deliveryTag, false, false);
                log.error("订单创建失败，超过最大重试次数，消息进入死信队列");
            }
        }
    }

    /**
     * 监听秒杀订单队列
     * 
     * @param message 消息内容
     * @param channel 通道
     * @param deliveryTag 消息标签
     */
    @RabbitListener(queues = MQConstant.SECKILL_ORDER_QUEUE)
    public void consumeSeckillOrderMessage(Map<String, Object> message, Channel channel,
                                           long deliveryTag) throws IOException {
        log.info("收到秒杀订单消息：{}", message);
        
        try {
            // 创建秒杀订单
            createOrderFromMessage(message, true);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("秒杀订单创建成功，消息 ACK");
        } catch (Exception e) {
            log.error("秒杀订单创建失败：", e);
            // 秒杀订单失败，需要回滚 Redis 库存
            Long seckillId = Long.valueOf(message.get("seckillId").toString());
            Integer quantity = Integer.valueOf(message.get("quantity").toString());
            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + seckillId;
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.info("秒杀失败，已回滚 Redis 库存");

            // 拒绝消息，不重试
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听死信队列（订单超时取消）
     * 
     * @param message 消息内容
     * @param channel 通道
     * @param deliveryTag 消息标签
     */
    @RabbitListener(queues = MQConstant.ORDER_DLX_QUEUE)
    public void consumeTimeoutOrderMessage(Map<String, Object> message, Channel channel,
                                           long deliveryTag) throws IOException {
        log.info("收到超时订单消息：{}", message);
        
        try {
            String orderNo = message.get("orderNo").toString();
            // 处理超时订单
            orderService.handleTimeoutOrder(orderNo);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("超时订单处理成功，订单号：{}", orderNo);
        } catch (Exception e) {
            log.error("超时订单处理失败：", e);
            // 超时订单处理失败，拒绝消息
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 从消息创建订单
     * 
     * @param message 消息内容
     * @param isSeckill 是否秒杀订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderFromMessage(Map<String, Object> message, boolean isSeckill) {
        Long userId = Long.valueOf(message.get("userId").toString());
        Long productId = Long.valueOf(message.get("productId").toString());
        Integer quantity = Integer.valueOf(message.get("quantity").toString());
        String orderNo = message.get("orderNo").toString();
        Long seckillId = message.get("seckillId") != null ? 
            Long.valueOf(message.get("seckillId").toString()) : null;

        // 查询商品
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        // 计算订单金额
        BigDecimal totalAmount;
        if (isSeckill) {
            // 秒杀订单使用秒杀价格
            SeckillProduct seckillProduct = seckillProductMapper.selectById(seckillId);
            if (seckillProduct == null) {
                throw new BusinessException("秒杀活动不存在");
            }
            totalAmount = seckillProduct.getSeckillPrice()
                .multiply(new BigDecimal(quantity));
        } else {
            // 普通订单使用现价
            totalAmount = product.getCurrentPrice().multiply(new BigDecimal(quantity));
        }

        // 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setSeckillId(seckillId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setOrderStatus(SystemConstant.ORDER_STATUS_PENDING);  // 待支付
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        int rows = orderMapper.insert(order);
        if (rows == 0) {
            throw new BusinessException("订单创建失败");
        }

        log.info("订单创建成功，订单号：{}", orderNo);
    }
}
