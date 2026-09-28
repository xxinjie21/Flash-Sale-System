package com.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.mapper.ProductMapper;
import com.flashsale.mapper.SeckillProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动库存预热测试
 *
 * 重点覆盖「重启安全」：Redis 中的库存是秒杀过程中实时扣减的，
 * 预热若无条件覆盖，重启就会把已售出的库存重置回数据库里的初始值，直接超卖。
 *
 * @author XXJ
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductServiceImpl 启动库存预热")
class ProductServiceImplTest {

    private static final Long SECKILL_ID = 2001L;
    private static final String STOCK_KEY = RedisKeyConstant.SECKILL_STOCK_KEY + SECKILL_ID;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SeckillProductMapper seckillProductMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SeckillProduct seckillProduct(int stock) {
        SeckillProduct sp = new SeckillProduct();
        sp.setId(SECKILL_ID);
        sp.setProductId(3001L);
        sp.setSeckillStock(stock);
        sp.setStatus(1);
        return sp;
    }

    @Test
    @DisplayName("Redis 中无该 key 时，写入数据库库存完成预热")
    void warmUp_whenKeyAbsent_shouldWriteStock() {
        when(seckillProductMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(seckillProduct(100)));
        when(valueOperations.setIfAbsent(eq(STOCK_KEY), any())).thenReturn(Boolean.TRUE);

        productService.warmUpSeckillStock();

        verify(valueOperations).setIfAbsent(STOCK_KEY, 100);
    }

    @Test
    @DisplayName("Redis 中已有库存时绝不覆盖（回归：重启把已售库存重置回初始值会导致超卖）")
    void warmUp_whenKeyPresent_shouldNotOverwrite() {
        when(seckillProductMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(seckillProduct(100)));
        // 模拟 Redis 中已是实时库存（例如已售罄为 0），setIfAbsent 返回 false
        when(valueOperations.setIfAbsent(eq(STOCK_KEY), any())).thenReturn(Boolean.FALSE);

        productService.warmUpSeckillStock();

        verify(valueOperations).setIfAbsent(STOCK_KEY, 100);
        // 关键断言：不能走 set() 覆盖掉 Redis 里的实时库存
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("没有进行中的秒杀活动时不写入任何 key")
    void warmUp_whenNoActiveSeckill_shouldDoNothing() {
        when(seckillProductMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        productService.warmUpSeckillStock();

        verify(valueOperations, never()).setIfAbsent(anyString(), any());
    }
}
