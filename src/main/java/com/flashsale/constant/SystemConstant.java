package com.flashsale.constant;

/**
 * 系统常量类
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public class SystemConstant {

    private SystemConstant() {
        throw new IllegalStateException("Constant class cannot be instantiated");
    }

    // ==================== 分页参数 ====================

    /**
     * 默认页码
     */
    public static final Integer DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页数量
     */
    public static final Integer DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页数量
     */
    public static final Integer MAX_PAGE_SIZE = 100;

    // ==================== 订单状态 ====================

    /**
     * 订单状态：待支付
     */
    public static final Integer ORDER_STATUS_PENDING = 0;

    /**
     * 订单状态：已支付
     */
    public static final Integer ORDER_STATUS_PAID = 1;

    /**
     * 订单状态：已取消
     */
    public static final Integer ORDER_STATUS_CANCELLED = 2;

    /**
     * 订单状态：已完成
     */
    public static final Integer ORDER_STATUS_COMPLETED = 3;

    // ==================== 秒杀活动状态 ====================

    /**
     * 秒杀状态：未开始
     */
    public static final Integer SECKILL_STATUS_NOT_START = 0;

    /**
     * 秒杀状态：进行中
     */
    public static final Integer SECKILL_STATUS_IN_PROGRESS = 1;

    /**
     * 秒杀状态：已结束
     */
    public static final Integer SECKILL_STATUS_ENDED = 2;

    // ==================== 限流参数 ====================

    /**
     * 接口限流：每秒最大请求数
     */
    public static final Integer RATE_LIMIT_PER_SECOND = 100;

    /**
     * 秒杀限流：每秒最大请求数
     */
    public static final Integer SECKILL_RATE_LIMIT_PER_SECOND = 1000;

    /**
     * 令牌桶容量
     */
    public static final Integer TOKEN_BUCKET_CAPACITY = 100;

    /**
     * 令牌桶每秒填充速率
     */
    public static final Double TOKEN_BUCKET_REFILL_RATE = 100.0;

    // ==================== 分布式锁参数 ====================

    /**
     * 分布式锁默认等待时间（秒）
     */
    public static final Long LOCK_WAIT_TIME = 5L;

    /**
     * 分布式锁默认持有时间（秒）
     */
    public static final Long LOCK_LEASE_TIME = 30L;

    /**
     * 秒杀锁等待时间（秒）
     */
    public static final Long SECKILL_LOCK_WAIT_TIME = 3L;

    /**
     * 秒杀锁持有时间（秒）
     */
    public static final Long SECKILL_LOCK_LEASE_TIME = 10L;

    // ==================== Redis 过期时间 ====================

    /**
     * 秒杀库存锁定过期时间（分钟）
     */
    public static final Long STOCK_LOCK_EXPIRE_MINUTES = 5L;

    /**
     * 用户订单锁过期时间（秒）
     */
    public static final Long ORDER_LOCK_EXPIRE_SECONDS = 30L;

    /**
     * 商品信息缓存过期时间（分钟）
     */
    public static final Long PRODUCT_CACHE_EXPIRE_MINUTES = 10L;

    /**
     * 秒杀商品缓存过期时间（分钟）
     */
    public static final Long SECKILL_PRODUCT_CACHE_EXPIRE_MINUTES = 5L;

    /**
     * 用户 Token 过期时间（小时）
     */
    public static final Long USER_TOKEN_EXPIRE_HOURS = 2L;

    // ==================== 其他常量 ====================

    /**
     * 是/否：是
     */
    public static final Integer YES = 1;

    /**
     * 是/否：否
     */
    public static final Integer NO = 0;

    /**
     * 成功标识
     */
    public static final String SUCCESS = "success";

    /**
     * 失败标识
     */
    public static final String ERROR = "error";
}
