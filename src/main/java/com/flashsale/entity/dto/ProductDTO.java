package com.flashsale.entity.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 DTO
 * 
 * 用于返回商品信息给前端
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Data
public class ProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品 ID
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 原价
     */
    private BigDecimal originalPrice;

    /**
     * 现价
     */
    private BigDecimal currentPrice;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品图片 URL
     */
    private String imageUrl;

    /**
     * 状态：0-下架，1-上架
     */
    private Integer status;

    /**
     * 是否参与秒杀
     */
    private Boolean isSeckill;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 秒杀开始时间
     */
    private LocalDateTime seckillStartTime;

    /**
     * 秒杀结束时间
     */
    private LocalDateTime seckillEndTime;
}
