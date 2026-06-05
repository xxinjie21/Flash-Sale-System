-- ============================================
-- 高并发秒杀系统 - 数据库建表脚本
-- ============================================
-- 数据库：MySQL 8.0
-- 字符集：utf8mb4
-- 作者：XXJ
-- 创建日期：2026-06-05
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS flash_sale 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_general_ci;

USE flash_sale;

-- ============================================
-- 1. 用户表
-- ============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT(20) NOT NULL COMMENT '用户 ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码 (加密存储)',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT(4) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. 商品表 (普通商品)
-- ============================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id` BIGINT(20) NOT NULL COMMENT '商品 ID',
  `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '商品描述',
  `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
  `current_price` DECIMAL(10,2) NOT NULL COMMENT '现价',
  `stock` INT(11) NOT NULL DEFAULT 0 COMMENT '库存数量',
  `category_id` BIGINT(20) DEFAULT NULL COMMENT '分类 ID',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '商品图片 URL',
  `status` TINYINT(4) NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================
-- 3. 秒杀商品表
-- ============================================
DROP TABLE IF EXISTS `seckill_product`;
CREATE TABLE `seckill_product` (
  `id` BIGINT(20) NOT NULL COMMENT '秒杀商品 ID',
  `product_id` BIGINT(20) NOT NULL COMMENT '关联商品 ID',
  `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
  `seckill_stock` INT(11) NOT NULL COMMENT '秒杀库存',
  `seckill_start_time` DATETIME NOT NULL COMMENT '秒杀开始时间',
  `seckill_end_time` DATETIME NOT NULL COMMENT '秒杀结束时间',
  `status` TINYINT(4) NOT NULL DEFAULT 1 COMMENT '状态：0-未开始，1-进行中，2-已结束',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product` (`product_id`),
  KEY `idx_time` (`seckill_start_time`, `seckill_end_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- ============================================
-- 4. 订单表
-- ============================================
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id` BIGINT(20) NOT NULL COMMENT '订单 ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单编号 (业务唯一键)',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户 ID',
  `product_id` BIGINT(20) NOT NULL COMMENT '商品 ID',
  `seckill_id` BIGINT(20) DEFAULT NULL COMMENT '秒杀活动 ID(如果是秒杀订单)',
  `quantity` INT(11) NOT NULL DEFAULT 1 COMMENT '购买数量',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
  `order_status` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已完成',
  `payment_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_seckill_id` (`seckill_id`),
  KEY `idx_status` (`order_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================
-- 数据清理脚本（可选）
-- ============================================
-- TRUNCATE TABLE `order`;
-- TRUNCATE TABLE `seckill_product`;
-- TRUNCATE TABLE `product`;
-- TRUNCATE TABLE `user`;
