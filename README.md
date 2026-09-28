# 高并发商品秒杀系统

<div align="center">

![JDK](https://img.shields.io/badge/JDK-17-blue.svg?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.15-brightgreen.svg?style=flat-square)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.3.1-orange.svg?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-6.2-red.svg?style=flat-square)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.9-green.svg?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg?style=flat-square)
![Redisson](https://img.shields.io/badge/Redisson-3.21.3-critical.svg?style=flat-square)

**基于 Spring Boot + Redis + RabbitMQ + Redisson 的高并发商品秒杀后端系统**

[核心特性](#-核心特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [API 接口](#-api-接口) • [项目结构](#-项目结构)

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
请求 → 限流拦截器(Redisson 令牌桶) → 登录拦截器(Token 校验)
     → 分布式锁(用户+活动粒度) → Redis CAS 原子扣库存
     → MQ 异步创建订单 → 死信队列延迟 30 分钟超时检测
```

- 秒杀活动维度 + 用户维度双重限流（1000 QPS / 100 QPS）
- Redisson `setIfAbsent` 保证每人限购 1 件
- CAS 重试循环保证库存扣减原子性
- MQ 手动 ACK；普通订单重投 3 次，失败进死信；秒杀订单失败立即回滚库存 + 释放限购标记后进死信

### 2. 死信队列超时处理

```
订单消息 → TTL 队列(30分钟) → DLX 死信队列 → 消费者取消订单 + 回滚库存
```

- RabbitMQ 原生 TTL + 死信交换机实现延迟队列
- 无需额外定时任务扫描
- 自动取消未支付订单 + 回滚 Redis/DB 库存

### 3. 缓存策略

统一使用 Redis 作为缓存层（手写 `RedisTemplate`，非注解式缓存）：

| 缓存 | Key 模式 | TTL | 说明 |
|------|---------|-----|------|
| 商品详情 | `flash_sale:product:{id}` | 10 分钟 | JSON 缓存，回源后写回 |
| 秒杀商品 | `flash_sale:seckill:product:{id}` | 5 分钟 | JSON 缓存，回源后写回 |
| 用户 Token | `flash_sale:user:token:{token}` | 2 小时 | 滑动窗口续期 |

> 说明：本项目**没有**本地（JVM 内）缓存层，也没有使用 `@Cacheable`，
> 所有缓存都是显式调用 RedisTemplate 完成的，刻意保持简单可控。

### 4. 启动预热

- `DataWarmUpRunner`（CommandLineRunner）启动时从 DB 加载库存到 Redis
- `flash_sale:seckill:stock:{seckillId}` 原子计数器

---

### 5. 性能实测

压测脚本 [`loadtest/seckill-load-test.jmx`](loadtest/seckill-load-test.jmx)，
完整报告 [`loadtest/README.md`](loadtest/README.md)。全部为 Apache JMeter 实测。

| 指标 | 场景 A（2000 并发） | 场景 B（5000 并发） |
|------|-------------------|-------------------|
| 总请求数 | 10,000 | 10,000 |
| 吞吐 | 1002 TPS | 1079 TPS |
| 平均延迟 | 552 ms | 2009 ms |
| P99 | 1347 ms | 2723 ms |
| 客户端失败 | 0.86% | 0.00% |
| **成交订单** | **58** | **58** |

**10000 次并发请求抢 58 件库存，两组梯度都恰好成交 58 单**，Redis 与 MySQL
库存同时归零。并发从 2000 提到 5000 吞吐只涨 7.7%，说明单机容量已在
1000~1100 TPS 附近饱和。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | Java 开发环境 |
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
# JDK 17
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
| `flash_sale:seckill:stock:{seckillId}` | String | 无 | 秒杀实时剩余库存（唯一权威来源） |
| `flash_sale:seckill:lock:{seckillId}:{userId}` | String | 5min | 每人限购 1 件的占位标记，流程失败时释放 |
| `flash_sale:lock:order:{userId}:{seckillId}` | Redisson Lock | 10s | 用户+活动粒度的订单去重锁 |
| `flash_sale:ratelimit:seckill:user:{userId}` | RRateLimiter | 2s | 用户级令牌桶限流 |
| `flash_sale:ratelimit:seckill:{seckillId}` | RRateLimiter | 2s | 活动级令牌桶限流 |
| `flash_sale:product:{productId}` | String | 10min | 商品详情缓存 |
| `flash_sale:seckill:product:{seckillId}` | String | 5min | 秒杀商品缓存 |
| `flash_sale:user:token:{token}` | String | 2h | 用户登录 Token |
| `flash_sale:order:fail:message` | List | 7d | 失败订单消息沉淀，供人工排查补偿 |

> 以上为 `RedisKeyConstant` 中实际被引用的全部 key，文档与代码保持一致。

---

## MQ 队列设计

| 交换机 | 队列 | Routing Key | 说明 |
|--------|------|-------------|------|
| `flash_sale.order.exchange` | `flash_sale.order.queue` | `order.create` | 普通订单创建 |
| `flash_sale.seckill.order.exchange` | `flash_sale.seckill.order.queue` | `seckill.order.create` | 秒杀订单创建 |
| `flash_sale.order.ttl.exchange` | `flash_sale.order.ttl.queue` | `order.ttl` | TTL 30min → 死信 |
| `flash_sale.order.dlx.exchange` | `flash_sale.order.dlx.queue` | `order.dead` | 超时订单取消 |
| `flash_sale.order.fail.exchange` | `flash_sale.order.fail.queue` | `order.fail` | 重试耗尽 / 秒杀失败消息沉淀 |

- 消息格式：Jackson JSON
- 消费模式：手动 ACK，prefetch=1
- 普通订单：失败后携带 `retryCount` 重新投递，重投 3 次仍失败则 nack 进失败队列（队列已配置 `x-dead-letter-exchange`，不会静默丢消息）
- 秒杀订单：失败即回滚 Redis 库存并释放用户限购标记，随后进失败队列（不再重试，避免"库存已退还却仍建单"的超卖）
- 幂等：消费端按 `order_no` 去重，重复投递不会重复建单

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
│   │   └── RateLimitInterceptor.java    # Redisson 令牌桶限流
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
│       ├── RedissonRateLimiter.java     # RRateLimiter 令牌桶限流器
│       ├── RedissonStockManager.java    # CAS 原子库存管理
│       └── Result.java                  # 统一响应封装
├── src/main/resources/
│   ├── application.yml / application-dev.yml / application-prod.yml
│   └── db/  schema.sql + test_data.sql
├── loadtest/                             # JMeter 压测脚本与实测报告
│   ├── seckill-load-test.jmx
│   └── README.md
├── API_DOC.md                            # 完整接口文档
├── DEPLOY.md                             # 部署指南（373行）
└── PROJECT_HIGHLIGHTS.md                 # 面试亮点文档（447行）
```

---

## 技术特点

| 特点 | 说明 |
|------|------|
| **Redis CAS 原子扣库存** | `RedissonStockManager` CAS 重试循环，零超卖；库存 key 缺失时明确报错而非伪装成售罄 |
| **双重限流** | 活动级（1000 QPS）+ 用户级（100 QPS），Redisson 原生 `RRateLimiter` 令牌桶，取令牌与扣减在 Redis 端原子完成 |
| **分布式锁** | 用户+活动粒度，3s 等待 / 10s 租约，防重复抢购 |
| **死信队列延迟** | TTL 30min → DLX 自动取消超时订单 + 回滚库存，秒杀单与普通单均投递延迟消息 |
| **失败补偿** | 秒杀流程任一步失败都会回滚已扣库存并释放限购标记，不会出现"没抢到却被锁" |
| **雪花算法** | 全局唯一 ID，订单号格式 yyyyMMdd + 8 位序列号 |
| **滑动窗口续期** | Token 每次访问自动续期 2 小时 |
| **手动 ACK + 幂等** | 消费手动确认，按订单号去重；普通订单重投 3 次，耗尽后进失败死信队列 |
| **28 个错误码** | 5 大分类（通用/用户/商品/订单/秒杀），业务异常规范化 |

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
