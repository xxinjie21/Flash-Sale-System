package com.flashsale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper 接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

}
