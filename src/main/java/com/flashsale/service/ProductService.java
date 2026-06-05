package com.flashsale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.entity.Product;
import com.flashsale.entity.dto.ProductDTO;

import java.util.List;

/**
 * 商品服务接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
public interface ProductService {

    /**
     * 获取商品详情
     * 
     * @param productId 商品 ID
     * @return 商品详情
     */
    ProductDTO getProductDetail(Long productId);

    /**
     * 获取商品列表（分页）
     * 
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 商品列表
     */
    Page<ProductDTO> getProductList(Integer pageNum, Integer pageSize);

    /**
     * 获取秒杀商品列表
     * 
     * @return 秒杀商品列表
     */
    List<ProductDTO> getSeckillProductList();

    /**
     * 获取秒杀商品详情
     * 
     * @param seckillId 秒杀商品 ID
     * @return 秒杀商品详情
     */
    ProductDTO getSeckillProductDetail(Long seckillId);

    /**
     * 预热秒杀商品库存到 Redis
     * 项目启动时调用
     */
    void warmUpSeckillStock();

    /**
     * 扣减商品库存
     * 
     * @param productId 商品 ID
     * @param quantity 数量
     */
    void decreaseStock(Long productId, Integer quantity);
}
