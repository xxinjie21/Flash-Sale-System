package com.flashsale.mq;

import com.flashsale.constant.MQConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单消息生产者
 * 
 * 负责发送订单创建消息到 MQ
 * 
 * 面试考点：
 * 1. 消息可靠投递：Confirm 确认、Return 回调、持久化
 * 2. 为什么用 MQ？削峰填谷、异步解耦、流量控制
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送普通订单消息
     * 
     * @param message 订单消息
     */
    public void sendOrderMessage(Map<String, Object> message) {
        log.info("发送普通订单消息：{}", message);
        rabbitTemplate.convertAndSend(
            MQConstant.ORDER_EXCHANGE,
            MQConstant.ORDER_ROUTING_KEY,
            message
        );
    }

    /**
     * 发送秒杀订单消息
     * 
     * @param message 订单消息
     */
    public void sendSeckillOrderMessage(Map<String, Object> message) {
        log.info("发送秒杀订单消息：{}", message);
        rabbitTemplate.convertAndSend(
            MQConstant.SECKILL_ORDER_EXCHANGE,
            MQConstant.SECKILL_ORDER_ROUTING_KEY,
            message
        );
    }

    /**
     * 重投订单消息（消费失败重试）
     *
     * 消费失败时由消费者调用：把携带了 retryCount 的消息重新投递到原队列，
     * 然后 ACK 掉原消息。不能依赖 basicNack(requeue=true)，
     * 那样失败消息会立刻重回队头形成热循环。
     *
     * @param message 订单消息（retryCount 已由调用方写入）
     * @param isSeckill 是否秒杀订单
     */
    public void resendOrderMessage(Map<String, Object> message, boolean isSeckill) {
        if (isSeckill) {
            sendSeckillOrderMessage(message);
        } else {
            sendOrderMessage(message);
        }
    }

    /**
     * 发送延迟订单消息（用于超时取消）
     * 
     * @param message 订单消息
     */
    public void sendDelayOrderMessage(Map<String, Object> message) {
        log.info("发送延迟订单消息：{}", message);
        
        // 设置消息过期时间（30 分钟）
        rabbitTemplate.convertAndSend(
            MQConstant.ORDER_TTL_EXCHANGE,
            MQConstant.ORDER_TTL_ROUTING_KEY,
            message,
            msg -> {
                msg.getMessageProperties().setExpiration(
                    String.valueOf(MQConstant.ORDER_TTL_MILLIS)
                );
                return msg;
            }
        );
    }
}
