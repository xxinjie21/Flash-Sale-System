package com.flashsale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.constant.RedisKeyConstant;
import com.flashsale.constant.SystemConstant;
import com.flashsale.entity.User;
import com.flashsale.exception.BusinessException;
import com.flashsale.exception.ErrorCode;
import com.flashsale.mapper.UserMapper;
import com.flashsale.service.UserService;
import com.flashsale.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 * 
 * @author XXJ
 * @since 2026-06-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final IdGenerator idGenerator;

    // BCrypt 密码加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(User user) {
        // 参数校验
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("密码不能为空");
        }

        // 检查用户名是否已存在
        User existUser = getUserByUsername(user.getUsername());
        if (existUser != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 生成用户 ID
        Long userId = idGenerator.nextId();
        user.setId(userId);

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 设置默认状态
        user.setStatus(SystemConstant.YES);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 插入数据库
        int rows = userMapper.insert(user);
        if (rows == 0) {
            throw new BusinessException("用户注册失败");
        }

        log.info("用户注册成功，userId={}, username={}", userId, user.getUsername());
        return userId;
    }

    @Override
    public String login(String username, String password) {
        // 参数校验
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new BusinessException("密码不能为空");
        }

        // 查询用户
        User user = getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 检查用户状态
        if (!SystemConstant.YES.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 生成 Token（使用雪花算法生成唯一 ID）
        String token = idGenerator.nextId().toString();

        // 将用户信息存入 Redis
        String userKey = RedisKeyConstant.USER_TOKEN_KEY + token;
        redisTemplate.opsForValue().set(
            userKey,
            user,
            SystemConstant.USER_TOKEN_EXPIRE_HOURS,
            TimeUnit.HOURS
        );

        log.info("用户登录成功，userId={}, username={}", user.getId(), username);
        return token;
    }

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            // 删除 Redis 中的 Token
            String userKey = RedisKeyConstant.USER_TOKEN_KEY + token;
            redisTemplate.delete(userKey);
            log.info("用户登出成功，token={}", token);
        }
    }
}
