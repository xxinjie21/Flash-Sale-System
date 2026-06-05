package com.flashsale.config;

import com.flashsale.constant.MQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 
 * 配置要点：
 * 1. 普通订单队列：异步下单，削峰填谷
 * 2. 秒杀订单队列：限流消费，保证数据库不雪崩
 * 3. 死信队列：订单超时 30 分钟自动取消
 * 
 * 面试考点：
 * - 死信队列触发条件：消息被拒绝、队列 TTL 到期、队列消息数超限
 * - 延迟队列实现：TTL + 死信队列
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器（JSON 格式）
     * 
     * @return MessageConverter
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate
     * 
     * @param connectionFactory 连接工厂
     * @return RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        // 开启消息确认机制（面试考点：可靠投递）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 消息发送失败，记录日志
                System.out.println("消息发送失败：" + cause);
            }
        });
        return rabbitTemplate;
    }

    /**
     * 配置消费者容器工厂
     * 
     * @param connectionFactory 连接工厂
     * @return SimpleRabbitListenerContainerFactory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // 预取数量=1，保证公平消费（面试考点：防止某个消费者积压）
        factory.setPrefetchCount(1);
        // 手动 ACK（面试考点：保证消息不丢失）
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    // ==================== 普通订单队列配置 ====================

    /**
     * 普通订单交换机（Direct 模式）
     */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(MQConstant.ORDER_EXCHANGE, true, false);
    }

    /**
     * 普通订单队列
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(MQConstant.ORDER_QUEUE).build();
    }

    /**
     * 普通订单队列绑定
     */
    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue)
            .to(orderExchange)
            .with(MQConstant.ORDER_ROUTING_KEY);
    }

    // ==================== 秒杀订单队列配置 ====================
    /**
     * 秒杀订单交换机（Direct 模式）
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(MQConstant.SECKILL_ORDER_EXCHANGE, true, false);
    }

    /**
     * 秒杀订单队列
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(MQConstant.SECKILL_ORDER_QUEUE).build();
    }

    /**
     * 秒杀订单队列绑定
     */
    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
            .to(seckillOrderExchange)
            .with(MQConstant.SECKILL_ORDER_ROUTING_KEY);
    }

    // ==================== 死信队列配置（订单超时） ====================

    /**
     * 订单 TTL 交换机（发送带 TTL 的消息）
     */
    @Bean
    public DirectExchange orderTTLExchange() {
        return new DirectExchange(MQConstant.ORDER_TTL_EXCHANGE, true, false);
    }

    /**
     * 订单 TTL 队列（延迟队列）
     * 配置：
     * - x-message-ttl: 30 分钟
     * - x-dead-letter-exchange: 死信交换机
     * - x-dead-letter-routing-key: 死信 routingKey
     */
    @Bean
    public Queue orderTTLQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", MQConstant.ORDER_TTL_MILLIS);  // 消息 TTL 30 分钟
        args.put("x-dead-letter-exchange", MQConstant.ORDER_DLX_EXCHANGE);  // 死信交换机
        args.put("x-dead-letter-routing-key", MQConstant.ORDER_DLX_ROUTING_KEY);  // 死信 routingKey
        return QueueBuilder.durable(MQConstant.ORDER_TTL_QUEUE)
            .withArguments(args)
            .build();
    }

    /**
     * 订单 TTL 队列绑定
     */
    @Bean
    public Binding orderTTLBinding(Queue orderTTLQueue, DirectExchange orderTTLExchange) {
        return BindingBuilder.bind(orderTTLQueue)
            .to(orderTTLExchange)
            .with(MQConstant.ORDER_TTL_ROUTING_KEY);
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange orderDLXExchange() {
        return new DirectExchange(MQConstant.ORDER_DLX_EXCHANGE, true, false);
    }

    /**
     * 死信队列（接收超时消息）
     */
    @Bean
    public Queue orderDLXQueue() {
        return QueueBuilder.durable(MQConstant.ORDER_DLX_QUEUE).build();
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding orderDLXBinding(Queue orderDLXQueue, DirectExchange orderDLXExchange) {
        return BindingBuilder.bind(orderDLXQueue)
            .to(orderDLXExchange)
            .with(MQConstant.ORDER_DLX_ROUTING_KEY);
    }
}
