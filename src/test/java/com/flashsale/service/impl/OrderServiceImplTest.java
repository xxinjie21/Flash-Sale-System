package com.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.entity.Order;
import com.flashsale.entity.Product;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.OrderMapper;
import com.flashsale.mapper.ProductMapper;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.mq.OrderMessageProducer;
import com.flashsale.util.IdGenerator;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MQ 消费建单单元测试
 *
 * 重点覆盖幂等：手动 ACK + 重投机制下同一条消息可能被消费多次，
 * 必须按 order_no 去重，不能重复建单。
 *
 * @author XXJ
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderServiceImpl MQ 消息建单")
class OrderServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long PRODUCT_ID = 3001L;
    private static final Long SECKILL_ID = 2001L;
    private static final String ORDER_NO = "2026092000000001";

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SeckillProductMapper seckillProductMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private OrderMessageProducer messageProducer;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Map<String, Object> message(Long seckillId, Integer quantity) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", USER_ID);
        message.put("productId", PRODUCT_ID);
        message.put("quantity", quantity);
        message.put("orderNo", ORDER_NO);
        message.put("seckillId", seckillId);
        return message;
    }

    private Product product(String currentPrice) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("测试商品");
        product.setOriginalPrice(new BigDecimal("99.00"));
        product.setCurrentPrice(new BigDecimal(currentPrice));
        product.setStock(100);
        return product;
    }

    private SeckillProduct seckillProduct(String seckillPrice) {
        SeckillProduct seckillProduct = new SeckillProduct();
        seckillProduct.setId(SECKILL_ID);
        seckillProduct.setProductId(PRODUCT_ID);
        seckillProduct.setSeckillPrice(new BigDecimal(seckillPrice));
        seckillProduct.setSeckillStock(10);
        return seckillProduct;
    }

    @Test
    @DisplayName("幂等：订单号已存在时直接跳过，不重复建单")
    void createOrderFromMessage_duplicateOrderNo_shouldSkip() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        orderService.createOrderFromMessage(message(null, 1), false);

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("普通订单：金额按现价计算，状态为待支付")
    void createOrderFromMessage_normalOrder_shouldUseCurrentPrice() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("50.00"));
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        orderService.createOrderFromMessage(message(null, 3), false);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSeckillId()).isNull();
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getTotalAmount())
            .as("50.00 × 3")
            .isEqualByComparingTo("150.00");
        assertThat(saved.getOrderStatus()).isZero();
    }

    @Test
    @DisplayName("秒杀订单：金额按秒杀价计算，并带上秒杀 ID")
    void createOrderFromMessage_seckillOrder_shouldUseSeckillPrice() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("99.00"));
        when(seckillProductMapper.selectById(SECKILL_ID)).thenReturn(seckillProduct("9.90"));
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        orderService.createOrderFromMessage(message(SECKILL_ID, 2), true);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getSeckillId()).isEqualTo(SECKILL_ID);
        assertThat(saved.getTotalAmount())
            .as("秒杀单必须用秒杀价 9.90，而不是现价 99.00")
            .isEqualByComparingTo("19.80");
    }

    @Test
    @DisplayName("商品不存在：抛「商品不存在」")
    void createOrderFromMessage_productMissing_shouldThrow() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

        Throwable thrown = catchThrowable(
            () -> orderService.createOrderFromMessage(message(null, 1), false));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getCode())
            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getCode());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("秒杀活动不存在：抛「秒杀活动不存在」")
    void createOrderFromMessage_seckillMissing_shouldThrow() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("99.00"));
        when(seckillProductMapper.selectById(SECKILL_ID)).thenReturn(null);

        Throwable thrown = catchThrowable(
            () -> orderService.createOrderFromMessage(message(SECKILL_ID, 1), true));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getCode())
            .isEqualTo(ErrorCode.SECKILL_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("插入影响行数为 0：抛「订单创建失败」")
    void createOrderFromMessage_insertFailed_shouldThrow() {
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product("50.00"));
        when(orderMapper.insert(any(Order.class))).thenReturn(0);

        Throwable thrown = catchThrowable(
            () -> orderService.createOrderFromMessage(message(null, 1), false));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getCode())
            .isEqualTo(ErrorCode.ORDER_CREATE_FAILED.getCode());
    }
}
