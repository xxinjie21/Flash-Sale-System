package com.flashsale.constant;

/**
 * RabbitMQ 常量类
 * 
 * 设计规范：
 * 1. 交换机、队列、RoutingKey 统一在此定义
 * 2. 禁止在代码中硬编码字符串
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public class MQConstant {

    private MQConstant() {
        throw new IllegalStateException("Constant class cannot be instantiated");
    }

    // ==================== 1. 普通订单队列配置 ====================

    /**
     * 订单交换机 - 直连模式
     * 用途：接收下单请求，异步处理订单创建逻辑
     */
    public static final String ORDER_EXCHANGE = "flash_sale.order.exchange";

    /**
     * 普通订单队列
     * 用途：存储普通下单消息
     */
    public static final String ORDER_QUEUE = "flash_sale.order.queue";

    /**
     * 普通订单 Routing Key
     */
    public static final String ORDER_ROUTING_KEY = "order.create";

    // ==================== 2. 秒杀订单队列配置 ====================

    /**
     * 秒杀订单交换机
     * 用途：接收秒杀下单请求，削峰填谷
     */
    public static final String SECKILL_ORDER_EXCHANGE = "flash_sale.seckill.order.exchange";

    /**
     * 秒杀订单队列
     * 用途：存储秒杀下单消息，控制消费速率
     */
    public static final String SECKILL_ORDER_QUEUE = "flash_sale.seckill.order.queue";

    /**
     * 秒杀订单 Routing Key
     */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";

    // ==================== 3. 死信队列配置 ====================

    /**
     * 订单 TTL 交换机
     * 用途：发送带 TTL 的订单消息
     */
    public static final String ORDER_TTL_EXCHANGE = "flash_sale.order.ttl.exchange";

    /**
     * 订单 TTL 队列 (延迟队列)
     * 用途：存储待支付订单，设置消息 TTL 为 30 分钟
     */
    public static final String ORDER_TTL_QUEUE = "flash_sale.order.ttl.queue";

    /**
     * 订单 TTL Routing Key
     */
    public static final String ORDER_TTL_ROUTING_KEY = "order.ttl";

    /**
     * 死信交换机
     * 用途：接收 TTL 队列过期的消息
     */
    public static final String ORDER_DLX_EXCHANGE = "flash_sale.order.dlx.exchange";

    /**
     * 死信队列
     * 用途：存储超时未支付的订单消息，触发取消逻辑
     */
    public static final String ORDER_DLX_QUEUE = "flash_sale.order.dlx.queue";

    /**
     * 死信 Routing Key
     */
    public static final String ORDER_DLX_ROUTING_KEY = "order.dead";

    // ==================== 4. 延迟队列参数配置 ====================

    /**
     * 订单超时时间 (30 分钟)
     * 用途：用户下单后 30 分钟内未支付，自动取消订单
     */
    public static final long ORDER_TTL_MILLIS = 30 * 60 * 1000L;

    /**
     * 消息投递重试次数
     */
    public static final int MESSAGE_RETRY_COUNT = 3;
}
