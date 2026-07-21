# 高并发商品秒杀系统

<div align="center">

![JDK](https://img.shields.io/badge/JDK-1.8-blue.svg?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.15-brightgreen.svg?style=flat-square)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.3.1-orange.svg?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-6.2-red.svg?style=flat-square)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.9-green.svg?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg?style=flat-square)
![Redisson](https://img.shields.io/badge/Redisson-3.21.3-critical.svg?style=flat-square)

**基于 Spring Boot + Redis + RabbitMQ + Redisson 的高并发商品秒杀后端系统**

[核心特性](#-核心特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [API 接口](#-api-接口) • [项目结构](#-项目结构) • [面试考点](#-面试考点)

</div>

---

## 项目介绍

本项目是一个 **高并发商品秒杀后端系统**，专为 Java 后端实习求职打造。系统实现了完整的秒杀业务流程，解决了 **超卖**、**数据库雪崩**、**重复下单**、**订单超时** 等核心问题。

### 核心理念

**高并发场景下的库存准确性和系统稳定性。**

- Redis 原子扣库存 + RabbitMQ 异步下单
- Redisson 分布式锁防重复抢购
- 死信队列 TTL 实现 30 分钟超时订单自动取消
- 雪花算法生成全局唯一 ID

---

## 核心特性

### 1. 高并发秒杀流程

```
请求 → 限流拦截器(Redisson 原子计数器) → 登录拦截器(Token 校验)
     → 分布式锁(用户+活动粒度) → Redis CAS 原子扣库存
     → MQ 异步创建订单 → 死信队列延迟 30 分钟超时检测
```

- 秒杀活动维度 + 用户维度双重限流（1000 QPS / 100 QPS）
- Redisson `setIfAbsent` 保证每人限购 1 件
- CAS 重试循环保证库存扣减原子性
- MQ 手动 ACK + 重试 3 次，失败回滚 Redis 库存

### 2. 死信队列超时处理

```
订单消息 → TTL 队列(30分钟) → DLX 死信队列 → 消费者取消订单 + 回滚库存
```

- RabbitMQ 原生 TTL + 死信交换机实现延迟队列
- 无需额外定时任务扫描
- 自动取消未支付订单 + 回滚 Redis/DB 库存

### 3. 多级缓存策略

| 缓存 | Key 模式 | TTL | 说明 |
|------|---------|-----|------|
| 商品详情 | `flash_sale:product:{id}` | 10 分钟 | JSON 缓存 |
| 秒杀商品 | `flash_sale:seckill:product:{id}` | 5 分钟 | JSON 缓存 |
| 用户 Token | `flash_sale:user:token:{token}` | 2 小时 | 滑动窗口续期 |
| 用户信息 | `flash_sale:user:info:{id}` | 30 分钟 | JSON 缓存 |

### 4. 启动预热

- `DataWarmUpRunner`（CommandLineRunner）启动时从 DB 加载库存到 Redis
- `flash_sale:seckill:stock:{seckillId}` 原子计数器

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8 | Java 开发环境 |
| Spring Boot | 2.7.15 | 快速开发框架 |
| MyBatis-Plus | 3.5.3.1 | ORM 持久层框架 |
| Redis | 6.2 | 缓存、分布式锁、原子计数器 |
| RabbitMQ | 3.9 | 消息队列、死信队列 |
| MySQL | 8.0 | 关系型数据库 |
| Redisson | 3.21.3 | 分布式锁、CAS 原子操作 |
| FastJSON2 | 2.0.32 | JSON 序列化 |
| Guava | 32.1.3-jre | 工具类 |
| Lombok | 1.18.30 | 简化代码 |

---

## 快速开始

### 1. 环境准备

```bash
# JDK 1.8
java -version

# Maven 3.6+
mvn -v
```

需要安装以下中间件：
- MySQL 8.0
- Redis 6.2
- RabbitMQ 3.9

### 2. 克隆项目

```bash
git clone https://github.com/xxinjie21/High-Concurrency-Flash-Sale-System.git
cd High-Concurrency-Flash-Sale-System
```

### 3. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p < src/main/resources/db/test_data.sql
```

### 4. 修改配置

编辑 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flash_sale?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的 MySQL 密码
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 5. 启动项目

```bash
mvn clean spring-boot:run
```

### 6. 验证启动

```
========================================
应用启动完成，开始初始化数据...
预热秒杀商品库存：seckillId=1, stock=10
共预热 2 个秒杀商品
========================================
高并发秒杀系统已就绪！
API 地址：http://localhost:8080
========================================
```

---

## 数据库设计

### 表结构（4 张表）

#### user 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | PK，雪花算法 |
| `username` | VARCHAR(50) | 唯一索引 |
| `password` | VARCHAR(100) | BCrypt 加密 |
| `email` | VARCHAR(100) | |
| `phone` | VARCHAR(20) | 唯一索引 |
| `status` | TINYINT | 0=禁用 1=正常 |
| `create_time` | DATETIME | 索引 |

#### product 商品表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | PK |
| `name` | VARCHAR(200) | 商品名称 |
| `original_price` | DECIMAL(10,2) | 原价 |
| `current_price` | DECIMAL(10,2) | 现价 |
| `stock` | INT | 库存 |
| `category_id` | BIGINT | 分类索引 |
| `status` | TINYINT | 0=下架 1=上架 |

#### seckill_product 秒杀商品表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | PK |
| `product_id` | BIGINT | 唯一索引 |
| `seckill_price` | DECIMAL(10,2) | 秒杀价 |
| `seckill_stock` | INT | 秒杀库存 |
| `seckill_start_time` | DATETIME | 复合索引 |
| `seckill_end_time` | DATETIME | 复合索引 |
| `status` | TINYINT | 0=未开始 1=进行中 2=已结束 |

#### order 订单表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | PK |
| `order_no` | VARCHAR(64) | 唯一索引，格式 yyyyMMdd+8位序列 |
| `user_id` | BIGINT | 索引 |
| `product_id` | BIGINT | 索引 |
| `seckill_id` | BIGINT | 索引 |
| `quantity` | INT | 数量 |
| `total_amount` | DECIMAL(10,2) | 总金额 |
| `order_status` | TINYINT | 0=待支付 1=已支付 2=已取消 3=已完成 |

---

## API 接口

### 用户接口（4 个）

| 方法 | URL | 权限 | 说明 |
|------|-----|------|------|
| POST | `/user/register` | 公开 | 注册新用户 |
| POST | `/user/login` | 公开 | 登录获取 Token |
| POST | `/user/logout` | 需登录 | 登出系统 |
| GET | `/user/info` | 需登录 | 获取当前用户信息 |

### 商品接口（5 个）

| 方法 | URL | 权限 | 说明 |
|------|-----|------|------|
| GET | `/product/detail/{productId}` | 公开 | 获取商品详情（Redis 缓存 10min） |
| GET | `/product/list` | 公开 | 分页查询商品列表 |
| GET | `/product/seckill/list` | 公开 | 秒杀商品列表 |
| GET | `/product/seckill/detail/{seckillId}` | 公开 | 秒杀商品详情（Redis 缓存 5min） |
| POST | `/product/seckill/execute` | 需登录 | **执行秒杀**（核心接口） |

### 订单接口（5 个）

| 方法 | URL | 权限 | 说明 |
|------|-----|------|------|
| GET | `/order/detail/{orderId}` | 需登录 | 订单详情 |
| GET | `/order/detail/no/{orderNo}` | 需登录 | 按订单号查询 |
| GET | `/order/list` | 需登录 | 用户订单分页列表 |
| POST | `/order/cancel/{orderId}` | 需登录 | 取消订单 |
| POST | `/order/pay/{orderId}` | 需登录 | 支付订单 |

---

## Redis Key 设计

| Key | 类型 | TTL | 说明 |
|-----|------|-----|------|
| `flash_sale:seckill:stock:{seckillId}` | String | 无 | 秒杀库存原子计数器 |
| `flash_sale:seckill:lock:{seckillId}:{userId}` | String | 5min | 防重复库存锁定 |
| `flash_sale:lock:seckill:{seckillId}` | Redisson Lock | - | 秒杀活动级分布式锁 |
| `flash_sale:lock:order:{userId}:{seckillId}` | Redisson Lock | 30s | 用户订单去重锁 |
| `flash_sale:ratelimit:{api}:{userId}` | String | 1s | 用户级限流 |
| `flash_sale:ratelimit:seckill:{seckillId}` | String | 1s | 活动级限流 |
| `flash_sale:order:timeout:{orderId}` | String | 30min | 订单超时检测 |
| `flash_sale:product:{productId}` | String | 10min | 商品详情缓存 |
| `flash_sale:seckill:product:{seckillId}` | String | 5min | 秒杀商品缓存 |
| `flash_sale:user:token:{token}` | String | 2h | 用户登录 Token |
| `flash_sale:user:info:{userId}` | String | 30min | 用户信息缓存 |

---

## MQ 队列设计

| 交换机 | 队列 | Routing Key | 说明 |
|--------|------|-------------|------|
| `flash_sale.order.exchange` | `flash_sale.order.queue` | `order.create` | 普通订单创建 |
| `flash_sale.seckill.order.exchange` | `flash_sale.seckill.order.queue` | `seckill.order.create` | 秒杀订单创建 |
| `flash_sale.order.ttl.exchange` | `flash_sale.order.ttl.queue` | `order.ttl` | TTL 30min → 死信 |
| `flash_sale.order.dlx.exchange` | `flash_sale.order.dlx.queue` | `order.dead` | 超时订单取消 |

- 消息格式：Jackson JSON
- 消费模式：手动 ACK，prefetch=1
- 重试上限：3 次，失败进入死信队列

---

## 项目结构

```
High-Concurrency-Flash-Sale-System/
├── src/main/java/com/flashsale/
│   ├── FlashSaleApplication.java        # 启动类(@EnableAsync, @EnableScheduling)
│   ├── config/
│   │   ├── MybatisPlusConfig.java       # 分页拦截器
│   │   ├── RabbitMQConfig.java          # 交换机/队列/绑定
│   │   ├── RedisConfig.java             # RedisTemplate + @EnableCaching
│   │   ├── RedissonConfig.java          # RedissonClient 单机模式
│   │   ├── TransactionConfig.java       # @EnableTransactionManagement
│   │   └── WebMvcConfig.java            # 拦截器注册 + CORS
│   ├── constant/
│   │   ├── MQConstant.java              # MQ 交换机/队列名称
│   │   ├── RedisKeyConstant.java        # 14 种 Redis Key 模式
│   │   └── SystemConstant.java          # 状态码、限流阈值、超时时间
│   ├── controller/                       # 3 个 Controller，14 个接口
│   ├── entity/dto/                      # 4 实体 + 3 DTO
│   ├── exception/
│   │   ├── ErrorCode.java               # 28 个错误码（5 大类）
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── interceptor/
│   │   ├── LoginInterceptor.java        # Token 校验 + 滑动窗口续期
│   │   └── RateLimitInterceptor.java    # Redisson 原子计数器限流
│   ├── mapper/                           # 4 个 Mapper
│   ├── mq/
│   │   ├── OrderMessageConsumer.java    # 3 个 @RabbitListener + 手动 ACK
│   │   └── OrderMessageProducer.java    # 3 个发送方法
│   ├── runner/
│   │   └── DataWarmUpRunner.java        # 启动预热库存到 Redis
│   ├── service/impl/                    # 4 个 Service + 实现
│   └── util/
│       ├── IdGenerator.java             # 雪花算法 ID 生成
│       ├── RedisLockUtil.java           # Redisson 分布式锁封装
│       ├── RedissonRateLimiter.java     # 原子计数器限流器
│       ├── RedissonStockManager.java    # CAS 原子库存管理
│       └── Result.java                  # 统一响应封装
├── src/main/resources/
│   ├── application.yml / application-dev.yml / application-prod.yml
│   └── db/  schema.sql + test_data.sql
├── API_DOC.md                            # 完整接口文档（827行）
├── DEPLOY.md                             # 部署指南（373行）
└── PROJECT_HIGHLIGHTS.md                 # 面试亮点文档（447行）
```

---

## 技术特点

| 特点 | 说明 |
|------|------|
| **Redis CAS 原子扣库存** | `RedissonStockManager` CAS 重试循环，零超卖 |
| **双重限流** | 活动级（1000 QPS）+ 用户级（100 QPS），1秒自动过期 |
| **分布式锁** | 用户+活动粒度，3s 等待 / 10s 租约，防重复抢购 |
| **死信队列延迟** | TTL 30min → DLX 自动取消超时订单 + 回滚库存 |
| **雪花算法** | 全局唯一 ID，订单号格式 yyyyMMdd + 8 位序列号 |
| **滑动窗口续期** | Token 每次访问自动续期 2 小时 |
| **手动 ACK** | MQ 消费手动确认，失败重试 3 次后进死信 |
| **28 个错误码** | 5 大分类（通用/用户/商品/订单/秒杀），业务异常规范化 |

---

## 面试考点

### 1. 高并发相关

**Q1: 如何保证库存不超卖？**

**参考答案**：
> 1. **Redis 预扣库存**：启动时 `DataWarmUpRunner` 预热到 Redis
> 2. **CAS 原子操作**：`RedissonStockManager.decreaseStock()` 乐观锁重试循环
> 3. **分布式锁**：`RedisLockUtil.tryLock()` 用户+活动粒度防并发
> 4. **DB 乐观锁**：`ProductMapper` UPDATE 带 stock 条件兜底

**Q2: 为什么用 RabbitMQ 而不是直接写 DB？**

**参考答案**：
> 1. **削峰填谷**：秒杀 QPS 远超 DB 承载，MQ 控制消费速率
> 2. **异步解耦**：Redis 扣库存后立即返回，不阻塞用户
> 3. **超时处理**：TTL + 死信队列原生实现延迟取消，无需扫描

### 2. 分布式锁相关

**Q3: Redisson 分布式锁如何避免死锁？**

**参考答案**：
> 1. **租约时间**：获取锁时设置 10 秒 leaseTime
> 2. **WatchDog**：后台线程自动续期（默认 30s）
> 3. **tryLock 超时**：3 秒等待时间，超时放弃
> 4. **finally 释放**：`RedisLockUtil.unlock()` 确保释放

### 3. 消息队列相关

**Q4: 订单超时如何实现？**

**参考答案**：
> 1. **TTL 队列**：消息设置 30 分钟 TTL
> 2. **死信交换机**：TTL 到期自动路由到 DLX 队列
> 3. **消费者处理**：`OrderMessageConsumer` 取消订单 + 回滚库存
> 4. **无需定时任务**：纯 MQ 原生延迟，架构简洁

---

## 常见问题

### Q: 项目启动失败？

检查 MySQL、Redis、RabbitMQ 是否启动，检查 `application-dev.yml` 配置。

### Q: 秒杀接口返回库存不足？

检查 `test_data.sql` 是否执行成功，检查 Redis 预热日志。

### Q: 订单超时未取消？

检查 RabbitMQ 是否启动，检查死信队列配置（TTL + DLX）。

---

## 许可证

MIT License

---

<div align="center">

**如果本项目对你有帮助，请给个 Star 支持！**

</div>
