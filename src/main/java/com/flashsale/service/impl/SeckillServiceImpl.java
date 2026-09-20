package com.flashsale.service.impl;

import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.entity.dto.SeckillRequest;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.mq.OrderMessageProducer;
import com.flashsale.service.SeckillService;
import com.flashsale.util.IdGenerator;
import com.flashsale.util.RedisLockUtil;
import com.flashsale.util.RedissonRateLimiter;
import com.flashsale.util.RedissonStockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务实现类
 *
 * 核心流程：
 * 1. 接口限流（固定窗口原子计数器，防刷）
 * 2. Redis 分布式锁（防重复抢购）
 * 3. Redisson 原子操作扣库存（防超卖）
 * 4. MQ 异步下单 + 延迟消息（削峰填谷 + 超时取消）
 * 5. 快速返回结果（不阻塞 DB）
 *
 * 面试考点：
 * 1. 为什么用 Redis 扣库存？性能高，支持高并发
 * 2. 为什么用 Redisson？原子操作保证线程安全，无需 Lua 脚本，代码更简洁
 * 3. 为什么用 MQ？削峰填谷，保护数据库
 * 4. 分布式锁的作用？防止用户重复下单
 * 5. 失败补偿：任何一步失败都必须归还已占用的库存和限购标记，
 *    否则会出现「用户没抢到却被锁 5 分钟」或「库存被吞」的问题
 *
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillProductMapper seckillProductMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonStockManager redissonStockManager;

    private final RedissonRateLimiter redissonRateLimiter;

    private final RedisLockUtil redisLockUtil;

    private final OrderMessageProducer messageProducer;

    private final IdGenerator idGenerator;

    @Override
    public String executeSeckill(SeckillRequest request) {
        Long userId = request.getUserId();
        Long seckillId = request.getSeckillId();
        Integer quantity = request.getQuantity();

        log.info("秒杀请求：userId={}, seckillId={}, quantity={}", userId, seckillId, quantity);

        // ==================== 第一步：验证秒杀活动 ====================
        SeckillProduct seckillProduct = validateSeckill(seckillId);

        // ==================== 第二步：接口限流（令牌桶） ====================
        rateLimit(seckillId, userId);

        // ==================== 第三步：分布式锁（防止用户重复下单） ====================
        // 面试考点：Redisson 锁的优势（自动续期、可重入、避免死锁）
        String lockKey = RedisKeyConstant.ORDER_LOCK_KEY + userId + ":" + seckillId;
        boolean locked = false;
        try {
            locked = redisLockUtil.tryLock(
                lockKey,
                SystemConstant.SECKILL_LOCK_WAIT_TIME,
                SystemConstant.SECKILL_LOCK_LEASE_TIME,
                TimeUnit.SECONDS
            );

            if (!locked) {
                log.warn("获取锁失败，用户可能正在秒杀：userId={}, seckillId={}", userId, seckillId);
                throw new BusinessException(ErrorCode.SECKILL_LOCK_FAILED);
            }

            // 检查用户是否已参与过该秒杀（每人限购 1 件）
            // 锁已按 userId + seckillId 粒度串行化，此处 check-then-act 不存在竞态
            checkUserSeckillLimit(userId, seckillId);

            // ==================== 第四步：扣库存 + 异步下单 ====================
            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + seckillId;
            boolean stockDeducted = false;
            boolean orderDispatched = false;
            try {
                // 面试考点：为什么用 Redisson 替代 Lua 脚本？
                // 1. 原子性：Redisson 的 CAS 操作保证原子性，无需 Lua 脚本
                // 2. 代码简洁：纯 Java 代码，更易维护
                // 3. 性能优势：直接原子操作，性能更高
                int decreaseResult = redissonStockManager.decreaseStock(stockKey, quantity);

                if (decreaseResult == -1) {
                    // 库存 key 不存在：说明库存未预热（Redis 重启 / 预热失败），属于系统故障
                    log.error("库存未预热或已丢失：seckillId={}, stockKey={}", seckillId, stockKey);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }

                if (decreaseResult == 0) {
                    log.info("秒杀商品已售罄：seckillId={}", seckillId);
                    throw new BusinessException(ErrorCode.SECKILL_OUT_OF_STOCK);
                }
                stockDeducted = true;

                // 面试考点：为什么用 MQ？
                // 1. 削峰填谷：控制消费速率，保护数据库
                // 2. 异步解耦：主线程快速返回，不阻塞用户
                // 3. 流量控制：消费者按数据库处理能力消费
                String orderNo = idGenerator.generateOrderNo();
                Map<String, Object> message = buildOrderMessage(userId, seckillProduct, quantity, orderNo);
                messageProducer.sendSeckillOrderMessage(message);

                // 延迟消息：30 分钟未支付则自动取消订单并回滚库存
                // 注意：秒杀单与普通单都必须投递延迟消息，否则订单永远不会超时取消
                messageProducer.sendDelayOrderMessage(message);
                orderDispatched = true;

                log.info("秒杀成功，订单号：{}", orderNo);
                return orderNo;
            } finally {
                // 秒杀未真正成功时，把已占用的库存和限购标记全部归还，
                // 避免「用户什么都没抢到却被锁 5 分钟」以及「库存被吞」
                if (!orderDispatched) {
                    if (stockDeducted) {
                        redissonStockManager.rollbackStock(stockKey, quantity);
                        log.warn("秒杀失败，已回滚 Redis 库存：seckillId={}, quantity={}", seckillId, quantity);
                    }
                    releaseUserSeckillLimit(userId, seckillId);
                }
            }

        } catch (BusinessException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("秒杀失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            // ==================== 第六步：释放锁 ====================
            if (locked) {
                redisLockUtil.unlock(lockKey);
            }
        }
    }

    /**
     * 验证秒杀活动
     */
    private SeckillProduct validateSeckill(Long seckillId) {
        // 先从缓存获取
        String cacheKey = RedisKeyConstant.SECKILL_PRODUCT_CACHE_KEY + seckillId;
        SeckillProduct cached = (SeckillProduct) redisTemplate.opsForValue().get(cacheKey);

        if (cached == null) {
            // 缓存未命中，查询数据库
            cached = seckillProductMapper.selectById(seckillId);
            if (cached == null) {
                throw new BusinessException(ErrorCode.SECKILL_NOT_FOUND);
            }
            // 写入缓存
            redisTemplate.opsForValue().set(
                cacheKey,
                cached,
                SystemConstant.SECKILL_PRODUCT_CACHE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
            );
        }

        // 检查秒杀状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(cached.getSeckillStartTime())) {
            throw new BusinessException(ErrorCode.SECKILL_NOT_STARTED);
        }
        if (now.isAfter(cached.getSeckillEndTime())) {
            throw new BusinessException(ErrorCode.SECKILL_ENDED);
        }

        return cached;
    }

    /**
     * 接口限流（Redisson 原生令牌桶 RRateLimiter）
     *
     * 令牌桶由 Redisson 在 Redis 端原子完成「取令牌 + 扣减」，
     * 不存在「先读后判再自增」的 TOCTOU 问题，窗口边界也不会 2 倍放行。
     * 限流 key 由 expireIfNotSet 兜底 TTL，不会因 key 残留导致永久限流。
     */
    private void rateLimit(Long seckillId, Long userId) {
        // 用户维度限流（1 秒窗口）
        String userLimitKey = RedisKeyConstant.RATE_LIMIT_KEY + "seckill:" + userId;
        if (!redissonRateLimiter.tryAcquire(
                userLimitKey, SystemConstant.RATE_LIMIT_PER_SECOND, 1)) {
            log.warn("用户限流：userId={}", userId);
            throw new BusinessException(ErrorCode.REQUEST_TOO_FREQUENT);
        }

        // 秒杀活动维度限流（1 秒窗口）
        String seckillLimitKey = RedisKeyConstant.SECKILL_RATE_LIMIT_KEY + seckillId;
        if (!redissonRateLimiter.tryAcquire(
                seckillLimitKey, SystemConstant.SECKILL_RATE_LIMIT_PER_SECOND, 1)) {
            log.warn("秒杀活动限流：seckillId={}", seckillId);
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    /**
     * 检查用户秒杀限制（每人限购 1 件）
     * 使用 setIfAbsent 保证原子性，避免 TOCTOU 竞态
     */
    private void checkUserSeckillLimit(Long userId, Long seckillId) {
        String lockKey = RedisKeyConstant.SECKILL_STOCK_LOCK_KEY + seckillId + ":" + userId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
            lockKey,
            "1",
            SystemConstant.STOCK_LOCK_EXPIRE_MINUTES,
            TimeUnit.MINUTES
        );
        if (!Boolean.TRUE.equals(success)) {
            log.info("用户已参与过该秒杀：userId={}, seckillId={}", userId, seckillId);
            throw new BusinessException(ErrorCode.SECKILL_LIMIT_EXCEEDED);
        }
    }

    /**
     * 释放用户限购标记
     *
     * 秒杀流程中途失败时必须调用，否则用户会被白白锁定
     * STOCK_LOCK_EXPIRE_MINUTES 分钟却什么也没抢到。
     */
    private void releaseUserSeckillLimit(Long userId, Long seckillId) {
        String lockKey = RedisKeyConstant.SECKILL_STOCK_LOCK_KEY + seckillId + ":" + userId;
        redisTemplate.delete(lockKey);
        log.info("已释放用户限购标记：userId={}, seckillId={}", userId, seckillId);
    }

    /**
     * 构建订单消息
     */
    private Map<String, Object> buildOrderMessage(Long userId, SeckillProduct seckillProduct,
                                                   Integer quantity, String orderNo) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("productId", seckillProduct.getProductId());
        message.put("seckillId", seckillProduct.getId());
        message.put("quantity", quantity);
        message.put("orderNo", orderNo);
        return message;
    }
}
