package com.flashsale.exception;

import lombok.Getter;

/**
 * 错误码枚举类
 * 
 * 错误码设计规范：
 * 1. 200: 成功
 * 2. 4xx: 客户端错误
 * 3. 5xx: 服务端错误
 * 4. 业务错误码：4 位数字，第一位表示业务类型
 *    - 1xxx: 通用错误
 *    - 2xxx: 用户相关
 *    - 3xxx: 商品相关
 *    - 4xxx: 订单相关
 *    - 5xxx: 秒杀相关
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Getter
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS(200, "操作成功"),

    // ==================== 通用错误 (1xxx) ====================
    SYSTEM_ERROR(1001, "系统繁忙，请稍后再试"),
    PARAM_ERROR(1002, "参数错误"),
    DATA_NOT_FOUND(1003, "数据不存在"),
    DATA_ALREADY_EXISTS(1004, "数据已存在"),
    UNAUTHORIZED(1005, "未授权，请先登录"),
    FORBIDDEN(1006, "无权限访问"),
    RATE_LIMIT_EXCEEDED(1007, "访问过于频繁，请稍后再试"),
    REQUEST_TOO_FREQUENT(1008, "请求过于频繁"),

    // ==================== 用户相关错误 (2xxx) ====================
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_DISABLED(2003, "用户已被禁用"),
    USER_ALREADY_EXISTS(2004, "用户已存在"),
    TOKEN_INVALID(2005, "Token 无效或已过期"),
    TOKEN_EXPIRED(2006, "Token 已过期"),

    // ==================== 商品相关错误 (3xxx) ====================
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_OUT_OF_STOCK(3002, "商品库存不足"),
    PRODUCT_STATUS_ERROR(3003, "商品状态异常"),
    PRODUCT_NOT_ON_SALE(3004, "商品未上架"),

    // ==================== 订单相关错误 (4xxx) ====================
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_STATUS_ERROR(4002, "订单状态异常"),
    ORDER_CREATE_FAILED(4003, "订单创建失败"),
    ORDER_PAYMENT_FAILED(4004, "订单支付失败"),
    ORDER_CANCELLED(4005, "订单已取消"),
    ORDER_EXPIRED(4006, "订单已超时"),
    ORDER_ALREADY_PAID(4007, "订单已支付"),

    // ==================== 秒杀相关错误 (5xxx) ====================
    SECKILL_NOT_FOUND(5001, "秒杀活动不存在"),
    SECKILL_NOT_STARTED(5002, "秒杀活动尚未开始"),
    SECKILL_ENDED(5003, "秒杀活动已结束"),
    SECKILL_OUT_OF_STOCK(5004, "秒杀商品已抢光"),
    SECKILL_LIMIT_EXCEEDED(5005, "每人限购 1 件"),
    SECKILL_REPEAT(5006, "您已参与过该秒杀活动"),
    SECKILL_LOCK_FAILED(5007, "获取锁失败，请稍后重试"),
    SECKILL_STOCK_LOCK_FAILED(5008, "库存锁定失败"),
    SECKILL_QUEUE_FULL(5009, "排队人数过多，请稍后再试");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
