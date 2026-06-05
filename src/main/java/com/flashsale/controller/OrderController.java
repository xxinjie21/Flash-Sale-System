package com.flashsale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.entity.dto.OrderDTO;
import com.flashsale.service.OrderService;
import com.flashsale.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;

/**
 * 订单 Controller
 * 
 * 负责处理订单相关的 HTTP 请求
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 获取订单详情
     * 
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @GetMapping("/detail/{orderId}")
    public Result<OrderDTO> getOrderDetail(@PathVariable Long orderId) {
        OrderDTO dto = orderService.getOrderDetail(orderId);
        return Result.success(dto);
    }

    /**
     * 根据订单号查询订单
     * 
     * @param orderNo 订单号
     * @return 订单详情
     */
    @GetMapping("/detail/no/{orderNo}")
    public Result<OrderDTO> getOrderByOrderNo(@PathVariable String orderNo) {
        OrderDTO dto = orderService.getOrderByOrderNo(orderNo);
        return Result.success(dto);
    }

    /**
     * 获取用户订单列表（分页）
     * 
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param httpRequest HTTP 请求（用于获取用户 ID）
     * @return 订单列表
     */
    @GetMapping("/list")
    public Result<Page<OrderDTO>> getUserOrderList(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Page<OrderDTO> page = orderService.getUserOrderList(userId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 取消订单
     * 
     * @param orderId 订单 ID
     * @param httpRequest HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping("/cancel/{orderId}")
    public Result<Void> cancelOrder(
        @PathVariable Long orderId,
        HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        orderService.cancelOrder(orderId, userId);
        return Result.success();
    }

    /**
     * 支付订单
     * 
     * @param orderId 订单 ID
     * @param httpRequest HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping("/pay/{orderId}")
    public Result<Void> payOrder(
        @PathVariable Long orderId,
        HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        orderService.payOrder(orderId, userId);
        return Result.success();
    }
}
