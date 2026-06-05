-- ============================================
-- 高并发秒杀系统 - 测试数据初始化脚本
-- ============================================
-- 数据库：MySQL 8.0
-- 作者：XXJ
-- 创建日期：2026-06-05
-- ============================================

USE flash_sale;

-- ============================================
-- 1. 插入测试用户
-- ============================================
-- 密码：123456 (BCrypt 加密后)
INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `status`) VALUES
(1, 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8QeIe6k7uOCQb376NoUnuTJ8QeIe', 'test@example.com', '13800138000', 1),
(2, 'user1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8QeIe6k7uOCQb376NoUnuTJ8QeIe', 'user1@example.com', '13800138001', 1),
(3, 'user2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8QeIe6k7uOCQb376NoUnuTJ8QeIe', 'user2@example.com', '13800138002', 1);

-- ============================================
-- 2. 插入测试商品
-- ============================================
INSERT INTO `product` (`id`, `name`, `description`, `original_price`, `current_price`, `stock`, `status`) VALUES
(1, 'iPhone 15 Pro', '苹果最新旗舰手机，A17 芯片，钛金属边框', 8999.00, 7999.00, 100, 1),
(2, 'MacBook Pro 14', '高性能笔记本电脑，M3 Pro 芯片', 14999.00, 13999.00, 50, 1),
(3, 'AirPods Pro 2', '主动降噪无线耳机', 1899.00, 1699.00, 200, 1),
(4, 'iPad Air 5', '轻薄平板电脑，M1 芯片', 4799.00, 4399.00, 80, 1),
(5, 'Apple Watch Ultra', '智能手表，户外运动版', 6299.00, 5999.00, 60, 1);

-- ============================================
-- 3. 插入测试秒杀商品
-- ============================================
-- 设置秒杀时间为当前时间的前后 1 小时
-- 注意：实际使用时需要根据当前时间调整
INSERT INTO `seckill_product` (`id`, `product_id`, `seckill_price`, `seckill_stock`, `seckill_start_time`, `seckill_end_time`, `status`) VALUES
(1, 1, 5999.00, 10, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1),
(2, 2, 9999.00, 5, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1),
(3, 3, 999.00, 20, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1),
(4, 4, 2999.00, 15, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1),
(5, 5, 3999.00, 8, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1);

-- ============================================
-- 4. 查询验证数据
-- ============================================
SELECT '用户数据：' AS info;
SELECT id, username, email, phone FROM `user`;

SELECT '商品数据：' AS info;
SELECT id, name, current_price, stock FROM `product`;

SELECT '秒杀商品数据：' AS info;
SELECT 
  sp.id,
  p.name AS product_name,
  sp.seckill_price,
  sp.seckill_stock,
  sp.seckill_start_time,
  sp.seckill_end_time,
  sp.status
FROM `seckill_product` sp
JOIN `product` p ON sp.product_id = p.id;

-- ============================================
-- 5. 补充说明
-- ============================================
-- 密码说明：
-- 测试用户密码均为：123456
-- BCrypt 加密后：$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8QeIe6k7uOCQb376NoUnuTJ8QeIe
--
-- 秒杀时间说明：
-- 默认设置为当前时间前后 1 小时，确保测试时秒杀活动正在进行中
-- 如需调整，可修改 DATE_ADD 函数中的时间间隔
--
-- 库存说明：
-- 普通商品库存充足，秒杀商品库存有限
-- 秒杀商品库存会在项目启动时预热到 Redis

-- ============================================
-- 6. 可选：清空所有数据
-- ============================================
-- 注意：执行前请谨慎考虑，会删除所有测试数据
-- DELETE FROM `order`;
-- DELETE FROM `seckill_product`;
-- DELETE FROM `product`;
-- DELETE FROM `user`;
