package com.flashsale.entity.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 秒杀请求 DTO
 * 
 * 用于接收前端秒杀下单请求参数
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Data
public class SeckillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 秒杀商品 ID
     */
    @NotNull(message = "秒杀商品 ID 不能为空")
    private Long seckillId;

    /**
     * 购买数量
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity = 1;

    /**
     * 用户 ID（从 Token 中解析）
     */
    private Long userId;
}
