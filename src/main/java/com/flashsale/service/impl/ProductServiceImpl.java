package com.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.Product;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.entity.dto.ProductDTO;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.ProductMapper;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    private final SeckillProductMapper seckillProductMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public ProductDTO getProductDetail(Long productId) {
        // 先从缓存获取
        String cacheKey = RedisKeyConstant.PRODUCT_CACHE_KEY + productId;
        ProductDTO cached = (ProductDTO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，查询数据库
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 检查是否参与秒杀
        SeckillProduct seckillProduct = getSeckillProductByProductId(productId);
        ProductDTO productDTO = convertToDTO(product);
        if (seckillProduct != null) {
            productDTO.setIsSeckill(true);
            productDTO.setSeckillPrice(seckillProduct.getSeckillPrice());
            productDTO.setSeckillStartTime(seckillProduct.getSeckillStartTime());
            productDTO.setSeckillEndTime(seckillProduct.getSeckillEndTime());
        } else {
            productDTO.setIsSeckill(false);
        }

        // 写入缓存
        redisTemplate.opsForValue().set(
            cacheKey,
            productDTO,
            SystemConstant.PRODUCT_CACHE_EXPIRE_MINUTES,
            TimeUnit.MINUTES
        );

        return productDTO;
    }

    @Override
    public Page<ProductDTO> getProductList(Integer pageNum, Integer pageSize) {
        // 参数校验
        if (pageNum == null || pageNum < 1) {
            pageNum = SystemConstant.DEFAULT_PAGE_NUM;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = SystemConstant.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > SystemConstant.MAX_PAGE_SIZE) {
            pageSize = SystemConstant.MAX_PAGE_SIZE;
        }

        // 分页查询
        Page<Product> productPage = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, SystemConstant.YES);  // 只查询上架商品
        wrapper.orderByDesc(Product::getCreateTime);  // 按创建时间倒序

        Page<Product> page = productMapper.selectPage(productPage, wrapper);

        // 转换为 DTO
        List<ProductDTO> dtoList = page.getRecords().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        Page<ProductDTO> resultPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    public List<ProductDTO> getSeckillProductList() {
        // 查询进行中的秒杀活动
        LambdaQueryWrapper<SeckillProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillProduct::getStatus, SystemConstant.SECKILL_STATUS_IN_PROGRESS);
        wrapper.le(SeckillProduct::getSeckillStartTime, LocalDateTime.now());  // 已开始
        wrapper.ge(SeckillProduct::getSeckillEndTime, LocalDateTime.now());    // 未结束

        List<SeckillProduct> seckillProducts = seckillProductMapper.selectList(wrapper);

        return seckillProducts.stream()
            .map(sp -> {
                ProductDTO dto = getProductDetail(sp.getProductId());
                dto.setIsSeckill(true);
                dto.setSeckillPrice(sp.getSeckillPrice());
                dto.setSeckillStartTime(sp.getSeckillStartTime());
                dto.setSeckillEndTime(sp.getSeckillEndTime());
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Override
    public ProductDTO getSeckillProductDetail(Long seckillId) {
        // 查询秒杀商品
        SeckillProduct seckillProduct = seckillProductMapper.selectById(seckillId);
        if (seckillProduct == null) {
            throw new BusinessException(ErrorCode.SECKILL_NOT_FOUND);
        }

        // 获取关联的普通商品信息
        ProductDTO productDTO = getProductDetail(seckillProduct.getProductId());
        productDTO.setIsSeckill(true);
        productDTO.setSeckillPrice(seckillProduct.getSeckillPrice());
        productDTO.setSeckillStartTime(seckillProduct.getSeckillStartTime());
        productDTO.setSeckillEndTime(seckillProduct.getSeckillEndTime());

        return productDTO;
    }

    @Override
    public void warmUpSeckillStock() {
        log.info("开始预热秒杀商品库存到 Redis...");

        // 查询所有进行中的秒杀活动
        LambdaQueryWrapper<SeckillProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillProduct::getStatus, SystemConstant.SECKILL_STATUS_IN_PROGRESS);

        List<SeckillProduct> seckillProducts = seckillProductMapper.selectList(wrapper);

        for (SeckillProduct sp : seckillProducts) {
            // 将库存预热到 Redis
            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + sp.getId();
            redisTemplate.opsForValue().set(stockKey, sp.getSeckillStock());

            log.info("秒杀商品 ID={}, 库存={} 已预热到 Redis", sp.getId(), sp.getSeckillStock());
        }

        log.info("秒杀商品库存预热完成，共预热 {} 个商品", seckillProducts.size());
    }

    @Override
    public void decreaseStock(Long productId, Integer quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        // 扣减库存
        int rows = productMapper.decreaseStock(productId, quantity);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }

    /**
     * 根据商品 ID 查询秒杀信息
     */
    private SeckillProduct getSeckillProductByProductId(Long productId) {
        LambdaQueryWrapper<SeckillProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillProduct::getProductId, productId);
        wrapper.eq(SeckillProduct::getStatus, SystemConstant.SECKILL_STATUS_IN_PROGRESS);
        return seckillProductMapper.selectOne(wrapper);
    }

    /**
     * 转换为 DTO
     */
    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(product, dto);
        return dto;
    }
}
