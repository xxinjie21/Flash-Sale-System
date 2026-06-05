package com.flashsale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
