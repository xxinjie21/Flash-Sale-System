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
 * 1. 接口令牌桶限流（防刷）
 * 2. Redis 分布式锁（防重复抢购）
 * 3. Redisson 原子操作扣库存（防超卖，替代 Lua 脚本）
 * 4. MQ 异步下单（削峰填谷）
 * 5. 快速返回结果（不阻塞 DB）
 * 
 * 面试考点：
 * 1. 为什么用 Redis 扣库存？性能高，支持高并发
 * 2. 为什么用 Redisson？原子操作保证线程安全，无需 Lua 脚本，代码更简洁
 * 3. 为什么用 MQ？削峰填谷，保护数据库
 * 4. 分布式锁的作用？防止用户重复下单
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

        // ==================== 第二步：接口限流（令牌桶算法） ====================
        // 面试考点：令牌桶 vs 漏桶 vs 计数器
        // 令牌桶：允许一定程度的突发流量，更灵活
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
            checkUserSeckillLimit(userId, seckillId);

            // ==================== 第四步：Redisson 原子操作扣库存（防超卖） ====================
            // 面试考点：为什么用 Redisson 替代 Lua 脚本？
            // 1. 原子性：Redisson 的 CAS 操作保证原子性，无需 Lua 脚本
            // 2. 代码简洁：纯 Java 代码，更易维护
            // 3. 性能优势：直接原子操作，性能更高
            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + seckillId;
            int decreaseResult = redissonStockManager.decreaseStock(stockKey, quantity);

            if (decreaseResult == -1) {
                log.error("库存扣减异常：seckillId={}, result={}", seckillId, decreaseResult);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }

            if (decreaseResult == 0) {
                // 库存不足
                log.info("秒杀商品已售罄：seckillId={}", seckillId);
                throw new BusinessException(ErrorCode.SECKILL_OUT_OF_STOCK);
            }

            // ==================== 第五步：发送 MQ 消息（异步下单） ====================
            // 面试考点：为什么用 MQ？
            // 1. 削峰填谷：控制消费速率，保护数据库
            // 2. 异步解耦：主线程快速返回，不阻塞用户
            // 3. 流量控制：消费者按数据库处理能力消费
            String orderNo = idGenerator.generateOrderNo();
            Map<String, Object> message = buildOrderMessage(userId, seckillProduct, quantity, orderNo);
            messageProducer.sendSeckillOrderMessage(message);

            log.info("秒杀成功，订单号：{}", orderNo);
            return orderNo;

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
     * 接口限流（使用 Redisson 原子计数器）
     */
    private void rateLimit(Long seckillId, Long userId) {
        // 用户维度限流（1 秒窗口，自动过期）
        String userLimitKey = RedisKeyConstant.RATE_LIMIT_KEY + "seckill:" + userId;
        long userCount = redissonStockManager.getStock(userLimitKey);
        if (userCount >= SystemConstant.RATE_LIMIT_PER_SECOND) {
            log.warn("用户限流：userId={}", userId);
            throw new BusinessException(ErrorCode.REQUEST_TOO_FREQUENT);
        }
        long newVal = redissonStockManager.incrementAndGet(userLimitKey);
        if (newVal == 1) {
            // 首次设置，添加 1 秒过期
            redissonStockManager.expireKey(userLimitKey, 1, TimeUnit.SECONDS);
        }

        // 秒杀活动维度限流（1 秒窗口，自动过期）
        String seckillLimitKey = RedisKeyConstant.SECKILL_RATE_LIMIT_KEY + seckillId;
        long currentCount = redissonStockManager.getStock(seckillLimitKey);
        if (currentCount >= SystemConstant.SECKILL_RATE_LIMIT_PER_SECOND) {
            log.warn("秒杀活动限流：seckillId={}", seckillId);
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        long newVal2 = redissonStockManager.incrementAndGet(seckillLimitKey);
        if (newVal2 == 1) {
            redissonStockManager.expireKey(seckillLimitKey, 1, TimeUnit.SECONDS);
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
