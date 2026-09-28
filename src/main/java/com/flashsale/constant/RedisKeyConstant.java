package com.flashsale.constant;

/**
 * Redis Key 常量类
 * 
 * 设计规范：
 * 1. 格式：项目名：模块名：业务类型：唯一标识
 * 2. 所有 Redis Key 必须从此常量类获取，禁止硬编码
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public class RedisKeyConstant {

    private RedisKeyConstant() {
        throw new IllegalStateException("Constant class cannot be instantiated");
    }

    // 项目前缀
    public static final String PROJECT_PREFIX = "flash_sale:";

    // ==================== 1. 秒杀库存相关 Key ====================

    /**
     * 秒杀商品库存 Key
     * 格式：flash_sale:seckill:stock:{seckillId}
     * 用途：存储秒杀商品剩余库存，使用 Lua 脚本原子扣减
     * 数据类型：String (Integer)
     */
    public static final String SECKILL_STOCK_KEY = PROJECT_PREFIX + "seckill:stock:";

    /**
     * 秒杀商品库存锁定 Key
     * 格式：flash_sale:seckill:lock:{seckillId}:{userId}
     * 用途：防止用户重复锁定库存
     * 数据类型：String
     * 过期时间：5 分钟
     */
    public static final String SECKILL_STOCK_LOCK_KEY = PROJECT_PREFIX + "seckill:lock:";

    // ==================== 2. 分布式锁相关 Key ====================

    /**
     * 订单防重锁 Key
     * 格式：flash_sale:lock:order:{userId}:{seckillId}
     * 用途：防止用户重复下单
     * 数据类型：String (Redisson Lock)
     * 过期时间：30 秒
     */
    public static final String ORDER_LOCK_KEY = PROJECT_PREFIX + "lock:order:";

    // ==================== 3. 限流相关 Key ====================

    /**
     * 接口限流计数器 Key
     * 格式：flash_sale:ratelimit:{api}:{userId}
     * 用途：基于用户 ID 的接口访问频率限制
     * 数据类型：String (Counter)
     * 过期时间：1 秒
     */
    public static final String RATE_LIMIT_KEY = PROJECT_PREFIX + "ratelimit:";

    /**
     * 秒杀接口限流 Key
     * 格式：flash_sale:ratelimit:seckill:{seckillId}
     * 用途：秒杀活动总 QPS 限制
     * 数据类型：String (Counter)
     * 过期时间：1 秒
     */
    public static final String SECKILL_RATE_LIMIT_KEY = PROJECT_PREFIX + "ratelimit:seckill:";

    // ==================== 4. 订单相关 Key ====================

    /**
     * 失败订单消息 Key
     * 格式：flash_sale:order:fail:message
     * 用途：沉淀重试耗尽仍失败的订单消息，供人工排查补偿
     * 数据类型：List
     * 过期时间：7 天
     */
    public static final String ORDER_FAIL_MESSAGE_KEY = PROJECT_PREFIX + "order:fail:message";

    // ==================== 5. 商品缓存相关 Key ====================

    /**
     * 商品信息缓存 Key
     * 格式：flash_sale:product:{productId}
     * 用途：缓存商品详细信息
     * 数据类型：String (JSON)
     * 过期时间：10 分钟
     */
    public static final String PRODUCT_CACHE_KEY = PROJECT_PREFIX + "product:";

    /**
     * 秒杀商品缓存 Key
     * 格式：flash_sale:seckill:product:{seckillId}
     * 用途：缓存秒杀活动信息
     * 数据类型：String (JSON)
     * 过期时间：5 分钟
     */
    public static final String SECKILL_PRODUCT_CACHE_KEY = PROJECT_PREFIX + "seckill:product:";

    // ==================== 6. 用户相关 Key ====================

    /**
     * 用户 Token Key
     * 格式：flash_sale:user:token:{token}
     * 用途：存储用户登录 Token
     * 数据类型：String (JSON)
     * 过期时间：2 小时
     */
    public static final String USER_TOKEN_KEY = PROJECT_PREFIX + "user:token:";
}
