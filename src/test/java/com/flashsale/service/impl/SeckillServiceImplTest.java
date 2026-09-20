package com.flashsale.service.impl;

import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.entity.dto.SeckillRequest;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.mq.OrderMessageProducer;
import com.flashsale.util.IdGenerator;
import com.flashsale.util.RedisLockUtil;
import com.flashsale.util.RedissonRateLimiter;
import com.flashsale.util.RedissonStockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀核心流程单元测试
 *
 * 重点覆盖失败补偿：秒杀中途失败时，必须把已扣的库存和限购标记还回去，
 * 否则会出现「用户什么都没抢到却被锁 5 分钟」以及「库存被吞」。
 *
 * @author XXJ
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SeckillServiceImpl 秒杀流程")
class SeckillServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long SECKILL_ID = 2001L;
    private static final Long PRODUCT_ID = 3001L;
    private static final String ORDER_NO = "2026092000000001";

    private static final String STOCK_KEY = RedisKeyConstant.SECKILL_STOCK_KEY + SECKILL_ID;
    private static final String CACHE_KEY = RedisKeyConstant.SECKILL_PRODUCT_CACHE_KEY + SECKILL_ID;
    private static final String LIMIT_MARK_KEY = RedisKeyConstant.SECKILL_STOCK_LOCK_KEY + SECKILL_ID + ":" + USER_ID;

    @Mock
    private SeckillProductMapper seckillProductMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedissonStockManager redissonStockManager;

    @Mock
    private RedissonRateLimiter redissonRateLimiter;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private OrderMessageProducer messageProducer;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private SeckillServiceImpl seckillService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 测试夹具 ====================

    private SeckillRequest request() {
        SeckillRequest request = new SeckillRequest();
        request.setUserId(USER_ID);
        request.setSeckillId(SECKILL_ID);
        request.setQuantity(1);
        return request;
    }

    private SeckillProduct activeSeckillProduct() {
        SeckillProduct product = new SeckillProduct();
        product.setId(SECKILL_ID);
        product.setProductId(PRODUCT_ID);
        product.setSeckillPrice(new BigDecimal("9.90"));
        product.setSeckillStock(10);
        product.setSeckillStartTime(LocalDateTime.now().minusHours(1));
        product.setSeckillEndTime(LocalDateTime.now().plusHours(1));
        return product;
    }

    /** 前置条件：活动进行中、限流放行、拿到锁、限购标记未被占用 */
    private void givenReadyToSeckill() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(activeSeckillProduct());
        when(redissonRateLimiter.tryAcquire(anyString(), anyLong(), anyLong())).thenReturn(true);
        when(redisLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(valueOperations.setIfAbsent(eq(LIMIT_MARK_KEY), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);
    }

    private void assertBusinessCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getCode()).isEqualTo(expected.getCode());
    }

    // ==================== 成功路径 ====================

    @Test
    @DisplayName("秒杀成功：扣库存 + 投递秒杀订单消息 + 投递延迟消息")
    void executeSeckill_success_shouldDispatchOrderAndDelayMessage() {
        givenReadyToSeckill();
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(1);
        when(idGenerator.generateOrderNo()).thenReturn(ORDER_NO);

        String orderNo = seckillService.executeSeckill(request());

        assertThat(orderNo).isEqualTo(ORDER_NO);

        ArgumentCaptor<Map<String, Object>> orderMsg = ArgumentCaptor.forClass(Map.class);
        verify(messageProducer).sendSeckillOrderMessage(orderMsg.capture());
        assertThat(orderMsg.getValue())
            .containsEntry("userId", USER_ID)
            .containsEntry("productId", PRODUCT_ID)
            .containsEntry("seckillId", SECKILL_ID)
            .containsEntry("quantity", 1)
            .containsEntry("orderNo", ORDER_NO);

        ArgumentCaptor<Map<String, Object>> delayMsg = ArgumentCaptor.forClass(Map.class);
        verify(messageProducer).sendDelayOrderMessage(delayMsg.capture());
        assertThat(delayMsg.getValue())
            .as("秒杀订单也必须投递延迟消息，否则永远不会超时取消、库存被永久占用")
            .containsEntry("orderNo", ORDER_NO);

        verify(redissonStockManager, never()).rollbackStock(anyString(), anyInt());
        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== 失败补偿 ====================

    @Test
    @DisplayName("库存不足：抛「已抢光」并释放限购标记，不发消息、不回滚库存")
    void executeSeckill_outOfStock_shouldReleaseLimitMark() {
        givenReadyToSeckill();
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(0);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_OUT_OF_STOCK);

        verify(redisTemplate).delete(LIMIT_MARK_KEY);
        verify(redissonStockManager, never()).rollbackStock(anyString(), anyInt());
        verify(messageProducer, never()).sendSeckillOrderMessage(any());
    }

    @Test
    @DisplayName("库存 key 缺失（未预热）：抛系统错误并释放限购标记，不伪装成售罄")
    void executeSeckill_stockKeyMissing_shouldReportSystemError() {
        givenReadyToSeckill();
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(-1);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SYSTEM_ERROR);

        verify(redisTemplate).delete(LIMIT_MARK_KEY);
        verify(redissonStockManager, never()).rollbackStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("投递 MQ 失败：回滚已扣库存 + 释放限购标记")
    void executeSeckill_mqFailure_shouldRollbackStockAndLimitMark() {
        givenReadyToSeckill();
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(1);
        when(idGenerator.generateOrderNo()).thenReturn(ORDER_NO);
        doThrow(new RuntimeException("MQ 不可用"))
            .when(messageProducer).sendSeckillOrderMessage(any());

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SYSTEM_ERROR);

        verify(redissonStockManager).rollbackStock(STOCK_KEY, 1);
        verify(redisTemplate).delete(LIMIT_MARK_KEY);
    }

    @Test
    @DisplayName("投递延迟消息失败：同样回滚库存 + 释放限购标记")
    void executeSeckill_delayMessageFailure_shouldRollbackStockAndLimitMark() {
        givenReadyToSeckill();
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(1);
        when(idGenerator.generateOrderNo()).thenReturn(ORDER_NO);
        doThrow(new RuntimeException("延迟队列不可用"))
            .when(messageProducer).sendDelayOrderMessage(any());

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SYSTEM_ERROR);

        verify(redissonStockManager).rollbackStock(STOCK_KEY, 1);
        verify(redisTemplate).delete(LIMIT_MARK_KEY);
    }

    // ==================== 前置校验 ====================

    @Test
    @DisplayName("用户维度被限流：直接拒绝，不扣库存、不加限购标记")
    void executeSeckill_rateLimited_shouldNotTouchStock() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(activeSeckillProduct());
        when(redissonRateLimiter.tryAcquire(anyString(), anyLong(), anyLong())).thenReturn(false);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.REQUEST_TOO_FREQUENT);

        verify(redissonStockManager, never()).decreaseStock(anyString(), anyInt());
        verify(redisLockUtil, never()).tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("用户已参与过该秒杀：抛限购异常，不扣库存")
    void executeSeckill_alreadyParticipated_shouldReject() {
        givenReadyToSeckill();
        when(valueOperations.setIfAbsent(eq(LIMIT_MARK_KEY), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.FALSE);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_LIMIT_EXCEEDED);

        verify(redissonStockManager, never()).decreaseStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("获取分布式锁失败：抛锁异常，不扣库存")
    void executeSeckill_lockFailed_shouldReject() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(activeSeckillProduct());
        when(redissonRateLimiter.tryAcquire(anyString(), anyLong(), anyLong())).thenReturn(true);
        when(redisLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_LOCK_FAILED);

        verify(redissonStockManager, never()).decreaseStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("活动未开始：抛「尚未开始」")
    void executeSeckill_notStarted_shouldReject() {
        SeckillProduct notStarted = activeSeckillProduct();
        notStarted.setSeckillStartTime(LocalDateTime.now().plusHours(1));
        when(valueOperations.get(CACHE_KEY)).thenReturn(notStarted);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_NOT_STARTED);

        verify(redissonStockManager, never()).decreaseStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("活动已结束：抛「已结束」")
    void executeSeckill_ended_shouldReject() {
        SeckillProduct ended = activeSeckillProduct();
        ended.setSeckillEndTime(LocalDateTime.now().minusMinutes(1));
        when(valueOperations.get(CACHE_KEY)).thenReturn(ended);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_ENDED);

        verify(redissonStockManager, never()).decreaseStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("缓存未命中：回源查库并写入缓存")
    void executeSeckill_cacheMiss_shouldLoadFromDbAndCache() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(seckillProductMapper.selectById(SECKILL_ID)).thenReturn(activeSeckillProduct());
        when(redissonRateLimiter.tryAcquire(anyString(), anyLong(), anyLong())).thenReturn(true);
        when(redisLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(valueOperations.setIfAbsent(eq(LIMIT_MARK_KEY), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(Boolean.TRUE);
        when(redissonStockManager.decreaseStock(STOCK_KEY, 1)).thenReturn(1);
        when(idGenerator.generateOrderNo()).thenReturn(ORDER_NO);

        String orderNo = seckillService.executeSeckill(request());

        assertThat(orderNo).isEqualTo(ORDER_NO);
        verify(seckillProductMapper).selectById(SECKILL_ID);
        verify(valueOperations).set(eq(CACHE_KEY), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("秒杀活动不存在：抛「活动不存在」")
    void executeSeckill_notFound_shouldReject() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(seckillProductMapper.selectById(SECKILL_ID)).thenReturn(null);

        Throwable thrown = catchThrowable(() -> seckillService.executeSeckill(request()));
        assertBusinessCode(thrown, ErrorCode.SECKILL_NOT_FOUND);
    }
}
