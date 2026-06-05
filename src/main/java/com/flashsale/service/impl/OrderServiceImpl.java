package com.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.Order;
import com.flashsale.entity.Product;
import com.flashsale.entity.SeckillProduct;
import com.flashsale.entity.dto.OrderDTO;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.OrderMapper;
import com.flashsale.mapper.ProductMapper;
import com.flashsale.mapper.SeckillProductMapper;
import com.flashsale.mq.OrderMessageProducer;
import com.flashsale.service.OrderService;
import com.flashsale.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private final ProductMapper productMapper;

    private final SeckillProductMapper seckillProductMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final OrderMessageProducer messageProducer;

    private final IdGenerator idGenerator;

    @Override
    public OrderDTO createOrder(Long userId, Long productId, Integer quantity, Long seckillId) {
        // 生成订单号
        String orderNo = idGenerator.generateOrderNo();

        // 构建消息
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("productId", productId);
        message.put("quantity", quantity);
        message.put("orderNo", orderNo);
        message.put("seckillId", seckillId);

        // 发送 MQ 消息
        if (seckillId != null) {
            // 秒杀订单
            messageProducer.sendSeckillOrderMessage(message);
        } else {
            // 普通订单
            messageProducer.sendOrderMessage(message);
        }

        // 发送延迟消息（30 分钟后超时取消）
        messageProducer.sendDelayOrderMessage(message);

        // 快速返回订单号
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(orderNo);
        dto.setUserId(userId);
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        dto.setOrderStatus(SystemConstant.ORDER_STATUS_PENDING);
        dto.setOrderStatusDesc("待支付");
        return dto;
    }

    @Override
    public OrderDTO getOrderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return convertToDTO(order);
    }

    @Override
    public OrderDTO getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return convertToDTO(order);
    }

    @Override
    public Page<OrderDTO> getUserOrderList(Long userId, Integer pageNum, Integer pageSize) {
        // 参数校验
        if (pageNum == null || pageNum < 1) {
            pageNum = SystemConstant.DEFAULT_PAGE_NUM;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = SystemConstant.DEFAULT_PAGE_SIZE;
        }

        // 分页查询
        Page<Order> orderPage = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> page = orderMapper.selectPage(orderPage, wrapper);

        // 转换为 DTO
        List<OrderDTO> dtoList = page.getRecords().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        Page<OrderDTO> resultPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 验证订单归属
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查订单状态
        if (!SystemConstant.ORDER_STATUS_PENDING.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }

        // 更新订单状态
        order.setOrderStatus(SystemConstant.ORDER_STATUS_CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 回滚库存
        rollbackStock(order);

        log.info("订单已取消，订单号：{}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 验证订单归属
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查订单状态
        if (!SystemConstant.ORDER_STATUS_PENDING.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }

        // 更新订单状态为已支付
        order.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
        order.setPaymentTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("订单已支付，订单号：{}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTimeoutOrder(String orderNo) {
        // 查询订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);

        if (order == null) {
            log.warn("订单不存在，订单号：{}", orderNo);
            return;
        }

        // 只处理待支付订单
        if (!SystemConstant.ORDER_STATUS_PENDING.equals(order.getOrderStatus())) {
            log.info("订单状态不是待支付，无需处理，订单号：{}, 状态：{}", orderNo, order.getOrderStatus());
            return;
        }

        // 更新订单状态为已取消
        order.setOrderStatus(SystemConstant.ORDER_STATUS_CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 回滚库存
        rollbackStock(order);

        log.info("超时订单已取消，订单号：{}", orderNo);
    }

    /**
     * 回滚库存
     */
    private void rollbackStock(Order order) {
        Long productId = order.getProductId();
        Integer quantity = order.getQuantity();
        Long seckillId = order.getSeckillId();

        if (seckillId != null) {
            // 秒杀订单：回滚 Redis 库存
            String stockKey = RedisKeyConstant.SECKILL_STOCK_KEY + seckillId;
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.info("秒杀库存已回滚，秒杀 ID={}, 数量={}", seckillId, quantity);
        } else {
            // 普通订单：回滚数据库库存
            productMapper.increaseStock(productId, quantity);
            log.info("商品库存已回滚，商品 ID={}, 数量={}", productId, quantity);
        }
    }

    /**
     * 转换为 DTO
     */
    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        BeanUtils.copyProperties(order, dto);

        // 设置订单状态描述
        switch (order.getOrderStatus()) {
            case 0:
                dto.setOrderStatusDesc("待支付");
                break;
            case 1:
                dto.setOrderStatusDesc("已支付");
                break;
            case 2:
                dto.setOrderStatusDesc("已取消");
                break;
            case 3:
                dto.setOrderStatusDesc("已完成");
                break;
            default:
                dto.setOrderStatusDesc("未知");
        }

        // 格式化时间
        if (order.getCreateTime() != null) {
            dto.setCreateTime(order.getCreateTime().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            ));
        }

        // 查询商品名称
        Product product = productMapper.selectById(order.getProductId());
        if (product != null) {
            dto.setProductName(product.getName());
        }

        return dto;
    }
}
