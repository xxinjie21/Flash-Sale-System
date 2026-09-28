package com.flashsale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.entity.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 秒杀商品 Mapper 接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Mapper
public interface SeckillProductMapper extends BaseMapper<SeckillProduct> {

    /**
     * 扣减秒杀商品库存（数据库侧乐观锁）
     *
     * `AND seckill_stock >= #{quantity}` 是乐观锁条件：并发下只有库存足够的
     * 请求才能更新成功，返回 0 行说明数据库库存已不足。
     *
     * 数据库这份库存是 Redis 之外的兜底账本：
     * 1. 应用重启时预热 Redis 的数据源（因此必须与真实销量同步）
     * 2. Redis 数据丢失时的对账依据
     *
     * @param seckillId 秒杀商品 ID
     * @param quantity 扣减数量
     * @return 影响行数，0 表示库存不足
     */
    @Update("UPDATE seckill_product SET seckill_stock = seckill_stock - #{quantity}, " +
            "update_time = NOW() WHERE id = #{seckillId} AND seckill_stock >= #{quantity}")
    int decreaseStock(@Param("seckillId") Long seckillId, @Param("quantity") Integer quantity);

    /**
     * 增加秒杀商品库存（取消/超时订单回滚用）
     *
     * @param seckillId 秒杀商品 ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    @Update("UPDATE seckill_product SET seckill_stock = seckill_stock + #{quantity}, " +
            "update_time = NOW() WHERE id = #{seckillId}")
    int increaseStock(@Param("seckillId") Long seckillId, @Param("quantity") Integer quantity);
}
