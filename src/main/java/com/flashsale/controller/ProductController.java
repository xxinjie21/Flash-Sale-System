package com.flashsale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.entity.dto.ProductDTO;
import com.flashsale.entity.dto.SeckillRequest;
import com.flashsale.service.ProductService;
import com.flashsale.service.SeckillService;
import com.flashsale.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 商品 Controller
 * 
 * 负责处理商品相关的 HTTP 请求
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final SeckillService seckillService;

    /**
     * 获取商品详情
     * 
     * @param productId 商品 ID
     * @return 商品详情
     */
    @GetMapping("/detail/{productId}")
    public Result<ProductDTO> getProductDetail(@PathVariable Long productId) {
        ProductDTO dto = productService.getProductDetail(productId);
        return Result.success(dto);
    }

    /**
     * 获取商品列表（分页）
     * 
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 商品列表
     */
    @GetMapping("/list")
    public Result<Page<ProductDTO>> getProductList(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        Page<ProductDTO> page = productService.getProductList(pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 获取秒杀商品列表
     * 
     * @return 秒杀商品列表
     */
    @GetMapping("/seckill/list")
    public Result<List<ProductDTO>> getSeckillProductList() {
        List<ProductDTO> list = productService.getSeckillProductList();
        return Result.success(list);
    }

    /**
     * 获取秒杀商品详情
     * 
     * @param seckillId 秒杀商品 ID
     * @return 秒杀商品详情
     */
    @GetMapping("/seckill/detail/{seckillId}")
    public Result<ProductDTO> getSeckillProductDetail(@PathVariable Long seckillId) {
        ProductDTO dto = productService.getSeckillProductDetail(seckillId);
        return Result.success(dto);
    }

    /**
     * 执行秒杀下单
     * 
     * @param request 秒杀请求
     * @param httpRequest HTTP 请求（用于获取用户 ID）
     * @return 订单号
     */
    @PostMapping("/seckill/execute")
    public Result<String> executeSeckill(
        @Valid @RequestBody SeckillRequest request,
        HttpServletRequest httpRequest
    ) {
        // 从请求属性中获取用户 ID（登录拦截器已设置）
        Long userId = (Long) httpRequest.getAttribute("userId");
        request.setUserId(userId);

        String orderNo = seckillService.executeSeckill(request);
        return Result.success(orderNo);
    }
}
