package com.flashsale.mq;

import com.flashsale.constant.MQConstant;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 订单消息消费者
 *
 * 监听 MQ 消息，异步创建订单
 *
 * 面试考点：
 * 1. 手动 ACK：保证消息不丢失
 * 2. 消息幂等：由 OrderService.createOrderFromMessage 按订单号去重
 * 3. 失败重试：重新投递并携带 retryCount，不能依赖 requeue=true
 * 4. 死信队列：重试耗尽的普通订单 + 秒杀失败订单统一沉淀，不静默丢弃
 *
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    private final OrderService orderService;

    private final OrderMessageProducer messageProducer;

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
            // 通过 Service 接口调用，保证 @Transactional 生效
            orderService.createOrderFromMessage(message, false);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("订单创建成功，消息 ACK");
        } catch (Exception e) {
            handleConsumeFailure(message, channel, deliveryTag, e);
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
            // 通过 Service 接口调用，保证 @Transactional 生效
            orderService.createOrderFromMessage(message, true);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("秒杀订单创建成功，消息 ACK");
        } catch (Exception e) {
            // 秒杀下单失败：Redis 库存与用户限购标记必须归还给用户。
            // 归还之后不能再重试，否则会出现「库存已退还却仍然建单」的超卖，
            // 因此这里直接拒绝消息，由队列 DLX 转入失败死信队列。
            rollbackSeckill(message);
            log.error("秒杀订单创建失败，已回滚库存并释放限购标记，消息进入失败死信队列：{}", message, e);
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
            String orderNo = parseString(message.get("orderNo"));
            // 处理超时订单
            orderService.handleTimeoutOrder(orderNo);
            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("超时订单处理成功，订单号：{}", orderNo);
        } catch (Exception e) {
            log.error("超时订单处理失败，消息转入失败死信队列：{}", message, e);
            // 拒绝消息，不重新入队，避免无限重投；由 DLX 转入失败队列
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听失败死信队列
     *
     * 重试耗尽或秒杀回滚后的消息会沉淀到这里，同时落一份到 Redis List，
     * 供人工排查与补偿，避免消息被静默丢弃。
     *
     * @param message 消息内容
     * @param channel 通道
     * @param deliveryTag 消息标签
     */
    @RabbitListener(queues = MQConstant.ORDER_FAIL_QUEUE)
    public void consumeFailOrderMessage(Map<String, Object> message, Channel channel,
                                        long deliveryTag) throws IOException {
        log.error("订单消息处理失败，进入失败队列，需人工介入：{}", message);
        try {
            redisTemplate.opsForList().rightPush(RedisKeyConstant.ORDER_FAIL_MESSAGE_KEY, message);
            redisTemplate.expire(RedisKeyConstant.ORDER_FAIL_MESSAGE_KEY, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("失败消息落盘 Redis 异常：{}", message, e);
        }
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理普通订单消费失败
     *
     * 未超过重试上限：把 retryCount 已 +1 的消息重新投递到原队列，然后 ACK 原消息。
     * 不能使用 basicNack(requeue=true)，那样失败消息会立刻回到队头形成热循环。
     * 超过上限：basicNack(requeue=false)，由队列 DLX 转入失败死信队列。
     */
    private void handleConsumeFailure(Map<String, Object> message, Channel channel,
                                      long deliveryTag, Exception cause) throws IOException {
        int retryCount = message.get("retryCount") == null
            ? 0 : Integer.parseInt(message.get("retryCount").toString());
        retryCount++;

        if (retryCount <= MQConstant.MESSAGE_RETRY_COUNT) {
            message.put("retryCount", retryCount);
            try {
                messageProducer.resendOrderMessage(message, false);
                channel.basicAck(deliveryTag, false);
                log.warn("订单创建失败，第 {} 次重投，订单号：{}",
                    retryCount, message.get("orderNo"), cause);
            } catch (Exception resendEx) {
                // 重投本身失败：让原消息重回队列，避免丢失
                log.error("重投失败，原消息重新入队：{}", message, resendEx);
                channel.basicNack(deliveryTag, false, true);
            }
        } else {
            log.error("订单消息重试 {} 次仍失败，转入失败死信队列：{}",
                MQConstant.MESSAGE_RETRY_COUNT, message, cause);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 回滚秒杀资源（Redis 库存 + 用户限购标记）
     *
     * 本方法自身不抛异常：任何解析失败都只记录日志，
     * 保证调用方后续的 basicNack 一定执行，否则消息会长期 unacked 并被无限重投。
     */
    private void rollbackSeckill(Map<String, Object> message) {
        try {
            Object seckillIdValue = message.get("seckillId");
            Object quantityValue = message.get("quantity");
            Object userIdValue = message.get("userId");

            if (seckillIdValue == null || quantityValue == null) {
                log.error("秒杀消息缺少 seckillId/quantity，无法回滚库存，需人工介入：{}", message);
                return;
            }

            Long seckillId = Long.valueOf(seckillIdValue.toString());
            int quantity = Integer.parseInt(quantityValue.toString());

            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + seckillId;
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.info("秒杀库存已回滚，秒杀 ID={}, 数量={}", seckillId, quantity);

            if (userIdValue != null) {
                String limitKey = RedisKeyConstant.SECKILL_STOCK_LOCK_KEY + seckillId + ":" + userIdValue;
                redisTemplate.delete(limitKey);
                log.info("用户限购标记已释放，userId={}, seckillId={}", userIdValue, seckillId);
            }
        } catch (Exception e) {
            log.error("回滚秒杀资源失败，需人工介入：{}", message, e);
        }
    }

    /**
     * 安全解析字符串字段
     */
    private String parseString(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("订单号缺失，消息内容异常");
        }
        return value.toString();
    }
}
