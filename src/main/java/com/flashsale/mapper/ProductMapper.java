package com.flashsale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper 接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣减库存
     * 使用 MyBatis 注解实现，无需 XML
     * 
     * @param productId 商品 ID
     * @param quantity 扣减数量
     * @return 影响行数
     */
    @Update("UPDATE product SET stock = stock - #{quantity}, update_time = NOW() " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 增加库存（回滚用）
     * 使用 MyBatis 注解实现，无需 XML
     * 
     * @param productId 商品 ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    @Update("UPDATE product SET stock = stock + #{quantity}, update_time = NOW() " +
            "WHERE id = #{productId}")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
