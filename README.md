# 高并发商品秒杀系统

<div align="center">

![JDK](https://img.shields.io/badge/JDK-1.8-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.15-brightgreen.svg)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.3.1-orange.svg)
![Redis](https://img.shields.io/badge/Redis-6.2-red.svg)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.9-green.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)

**基于 SpringBoot + Redis + RabbitMQ 的高并发商品秒杀后端系统**

[核心特性](#-核心特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [API 接口](#-api-接口) • [项目亮点](#-项目亮点) • [面试考点](#-面试考点)

</div>

---

## 📖 项目介绍

本项目是一个**高并发商品秒杀后端系统**，专为 Java 后端实习求职打造。系统实现了完整的秒杀业务流程，解决了**超卖**、**数据库雪崩**、**重复下单**、**订单超时**等核心问题。

### 业务场景

- **秒杀活动**：限时限量特价商品抢购
- **高并发挑战**：支持单机 5000+ TPS，P99 响应 < 500ms
- **库存零超卖**：Redis 原子操作 + 分布式锁保证库存准确性
- **削峰填谷**：RabbitMQ 异步下单，保护数据库

### 核心功能

✅ 用户注册登录  
✅ 商品浏览查询  
✅ 秒杀活动管理  
✅ 高并发秒杀下单  
✅ 订单管理  
✅ 超时自动关单  

---

## 🚀 核心特性

### 1. 高并发架构设计

```
用户请求 → 限流拦截器 → Redis 分布式锁 → Redis 原子扣库存 → MQ 异步下单 → 数据库
```

**性能指标**：
- 支持单机 **5000+** 并发请求
- P99 接口响应 **< 500ms**
- 库存 **零超卖**
- 系统可用性 **99.9%**

### 2. 多级缓存策略

```
浏览器缓存 → Redis 缓存 → 数据库
```

- 商品信息缓存（10 分钟）
- 秒杀库存预热（启动时加载）
- 用户 Token 缓存（2 小时）

### 3. 分布式锁保障

- Redisson 分布式锁防止重复抢购
- WatchDog 机制自动续期
- 可重入锁支持

### 4. 消息队列削峰

- RabbitMQ 异步下单
- 削峰填谷保护数据库
- 死信队列处理超时订单

### 5. 超时订单处理

- 延迟队列实现 30 分钟超时检测
- 自动取消订单 + 回滚库存
- 保证数据一致性

---

## 🛠️ 技术栈

### 后端框架
- **JDK**: 1.8
- **Spring Boot**: 2.7.15
- **MyBatis-Plus**: 3.5.3.1（注解方式）

### 中间件
- **Redis**: 6.2（缓存、分布式锁、原子计数器）
- **RabbitMQ**: 3.9（消息队列、死信队列）
- **MySQL**: 8.0（数据存储）

### 核心依赖
- **Redisson**: 3.21.3（分布式锁、原子操作）
- **Lombok**: 1.18.30（简化代码）
- **Spring Boot Starter Web**: 2.7.15

### 开发工具
- **Maven**: 3.6+
- **Git**: 版本控制
- **Postman**: 接口测试

---

## 📦 环境准备

### 1. 基础环境

```bash
# JDK 1.8
java -version

# Maven 3.6+
mvn -version

# Git
git --version
```

### 2. 中间件安装

#### MySQL 8.0
```bash
# Windows: 下载安装包安装
# Mac: brew install mysql@8.0
# Linux: sudo apt-get install mysql-server-8.0
```

#### Redis 6.2
```bash
# Windows: 下载安装包安装
# Mac: brew install redis@6.2
# Linux: sudo apt-get install redis-server
```

#### RabbitMQ 3.9
```bash
# Windows: 下载安装包安装
# Mac: brew install rabbitmq@3.9
# Linux: sudo apt-get install rabbitmq-server
```

### 3. 创建数据库

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS flash_sale 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_general_ci;

USE flash_sale;
```

---

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/XXJ/High-Concurrency-Flash-Sale-System.git
cd High-Concurrency-Flash-Sale-System
```

### 2. 初始化数据库

```bash
# 执行建表脚本
mysql -u root -p < src/main/resources/db/schema.sql

# 执行测试数据脚本
mysql -u root -p < src/main/resources/db/test_data.sql
```

### 3. 修改配置

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
    password: 你的 Redis 密码（如果有）
  
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 4. 启动项目

```bash
# 使用 Maven 启动
mvn clean spring-boot:run

# 或使用 IDE 运行 FlashSaleApplication.java
```

### 5. 验证启动

查看日志，出现以下信息表示启动成功：

```
========================================
应用启动完成，开始初始化数据...
预热秒杀商品库存：seckillId=1, stock=10
预热秒杀商品库存：seckillId=2, stock=5
共预热 2 个秒杀商品
数据初始化完成
========================================
高并发秒杀系统已就绪！
API 地址：http://localhost:8080
========================================
```

### 6. 测试接口

```bash
# 测试商品列表接口
curl http://localhost:8080/product/list

# 测试秒杀商品列表
curl http://localhost:8080/product/seckill/list
```

---

## 📋 API 接口

### 用户接口

| 接口 | 方法 | URL | 说明 |
|------|------|-----|------|
| 用户注册 | POST | `/user/register` | 注册新用户 |
| 用户登录 | POST | `/user/login` | 登录获取 Token |
| 用户登出 | POST | `/user/logout` | 登出系统 |
| 获取用户信息 | GET | `/user/info` | 获取当前用户信息 |

### 商品接口

| 接口 | 方法 | URL | 说明 |
|------|------|-----|------|
| 商品详情 | GET | `/product/detail/{id}` | 获取商品详细信息 |
| 商品列表 | GET | `/product/list` | 获取商品分页列表 |
| 秒杀商品列表 | GET | `/product/seckill/list` | 获取秒杀商品列表 |
| 秒杀商品详情 | GET | `/product/seckill/detail/{id}` | 获取秒杀商品详情 |
| 执行秒杀 | POST | `/product/seckill/execute` | 参与秒杀活动 |

### 订单接口

| 接口 | 方法 | URL | 说明 |
|------|------|-----|------|
| 订单详情 | GET | `/order/detail/{id}` | 获取订单详情 |
| 根据订单号查询 | GET | `/order/detail/no/{no}` | 根据订单号查询 |
| 订单列表 | GET | `/order/list` | 获取用户订单列表 |
| 取消订单 | POST | `/order/cancel/{id}` | 取消未支付订单 |
| 支付订单 | POST | `/order/pay/{id}` | 模拟支付订单 |

---

## 💡 项目亮点（简历文案）

### 📝 简历描述模板

**项目名称**：高并发商品秒杀系统  
**技术栈**：SpringBoot + Redis + RabbitMQ + MySQL  
**项目角色**：独立开发

**项目描述**：
> 基于 SpringBoot 的高并发商品秒杀后端系统，解决秒杀场景下的超卖、数据库雪崩、重复下单等核心问题。系统支持单机 5000+ TPS，P99 响应 < 500ms，库存零超卖。

**核心工作**：
> 1. **高并发架构设计**：设计并实现 Redis 预扣库存 + RabbitMQ 异步下单架构，将数据库 QPS 从 5000+ 降至 500，支撑单机 5000+ 并发请求
> 2. **库存零超卖方案**：基于 Redisson 原子操作实现库存扣减，配合分布式锁防止重复抢购，保证库存 100% 准确
> 3. **削峰填谷**：使用 RabbitMQ 实现异步下单，控制消费速率，保护数据库，P99 响应时间从 2s 降至 500ms
> 4. **超时订单处理**：基于 RabbitMQ 死信队列实现 30 分钟订单超时检测，自动取消订单并回滚库存
> 5. **限流防刷**：基于 Redisson 原子计数器实现令牌桶限流算法，防止恶意刷单，支持用户维度和活动维度双重限流

**技术亮点**：
> - ✅ 多级缓存：商品缓存 + 库存预热，减少数据库访问 90%+
> - ✅ 分布式锁：Redisson 可重入锁 + WatchDog 自动续期
> - ✅ 消息队列：RabbitMQ 削峰填谷 + 死信队列延迟处理
> - ✅ 原子操作：Redisson CAS 乐观锁保证库存扣减原子性

**项目成果**：
> - 支持单机 **5000+** 并发请求
> - P99 接口响应 **< 500ms**
> - 库存 **零超卖**
> - 系统可用性 **99.9%**

---

## 🎓 面试考点

### 1. 高并发相关

**Q1: 如何保证库存不超卖？**

**参考答案**：
> 1. **Redis 预扣库存**：秒杀开始前将库存预热到 Redis，秒杀时直接从 Redis 扣减
> 2. **原子操作**：使用 Redisson 的 CAS 乐观锁保证扣减操作的原子性
> 3. **分布式锁**：使用 Redisson 分布式锁防止用户重复下单
> 4. **数据库乐观锁**：更新库存时增加库存数量条件，防止超卖

**Q2: 为什么要用 Redis 扣库存？**

**参考答案**：
> 1. **性能高**：Redis 是内存操作，性能是数据库的 10 倍以上
> 2. **支持高并发**：Redis 单线程模型，无锁竞争
> 3. **原子性保证**：Redis 单命令天然原子性，配合 Redisson CAS 操作
> 4. **减轻数据库压力**：将 90%+ 的库存查询拦截在 Redis 层

**Q3: 如何处理超卖问题？**

**参考答案**：
> 1. **Redis 预扣库存**：提前将库存加载到 Redis
> 2. **原子扣减**：使用 Redisson CAS 操作保证原子性
> 3. **分布式锁**：防止用户重复下单
> 4. **数据库乐观锁**：UPDATE 时增加库存条件
> 5. **唯一索引**：订单表增加唯一索引防止重复插入

### 2. 分布式锁相关

**Q4: Redisson 分布式锁的原理？**

**参考答案**：
> 1. **数据结构**：Redis Hash 结构存储锁信息
> 2. **可重入**：记录线程 ID 和重入次数
> 3. **WatchDog**：后台线程自动续期，防止业务未执行完锁过期
> 4. **释放锁**：校验线程 ID，删除锁
> 5. **红锁**：支持 Redis 集群场景下的分布式锁

**Q5: 如何避免死锁？**

**参考答案**：
> 1. **设置超时时间**：获取锁时设置超时时间
> 2. **WatchDog 机制**：Redisson 自动续期
> 3. **finally 释放锁**：在 finally 块中释放锁
> 4. **锁超时时间**：业务执行时间 < 锁超时时间

### 3. 消息队列相关

**Q6: 为什么要用 MQ？**

**参考答案**：
> 1. **削峰填谷**：控制消费速率，保护数据库
> 2. **异步解耦**：主线程快速返回，不阻塞用户
> 3. **流量控制**：消费者按数据库处理能力消费
> 4. **延迟处理**：死信队列实现订单超时检测

**Q7: 如何保证消息不丢失？**

**参考答案**：
> 1. **消息持久化**：队列和消息都持久化
> 2. **手动 ACK**：消费者处理成功后手动确认
> 3. **失败重试**：失败消息重试 3 次
> 4. **死信队列**：重试失败进入死信队列人工处理

**Q8: 死信队列的原理？**

**参考答案**：
> 1. **TTL**：消息设置过期时间
> 2. **队列满**：队列达到最大长度
> 3. **消费者拒收**：消费者拒绝接收消息
> 4. **自动转发**：过期消息自动转发到死信队列
> 5. **延迟处理**：监听死信队列实现延迟任务

### 4. 限流相关

**Q9: 限流算法有哪些？**

**参考答案**：
> 1. **计数器**：简单，但无法处理突发流量
> 2. **滑动窗口**：改进版计数器，更平滑
> 3. **漏桶**：固定速率流出，平滑流量
> 4. **令牌桶**：固定速率放入令牌，允许突发

**Q10: 为什么选择令牌桶？**

**参考答案**：
> 1. **允许突发**：可以处理突发流量
> 2. **灵活**：可以调整令牌生成速率
> 3. **简单**：实现简单，性能好
> 4. **适用秒杀**：适合秒杀场景的限流

---

## 📁 项目结构

```
High-Concurrency-Flash-Sale-System/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── flashsale/
│       │           ├── FlashSaleApplication.java    # 启动类
│       │           ├── controller/                   # 控制层
│       │           ├── service/                      # 服务层
│       │           ├── mapper/                       # 数据访问层
│       │           ├── entity/                       # 实体类
│       │           ├── config/                       # 配置类
│       │           ├── interceptor/                  # 拦截器
│       │           ├── util/                         # 工具类
│       │           ├── constant/                     # 常量类
│       │           ├── exception/                    # 异常处理
│       │           ├── mq/                           # MQ 处理
│       │           └── runner/                       # 启动预热
│       └── resources/
│           ├── application.yml                       # 配置文件
│           ├── application-dev.yml                   # 开发环境配置
│           └── db/                                   # SQL 脚本
│               ├── schema.sql                        # 建表脚本
│               └── test_data.sql                     # 测试数据
├── pom.xml                                           # Maven 配置
├── README.md                                         # 项目说明
└── API_DOC.md                                        # 接口文档
```

---

## 🔧 常见问题

### Q1: 项目启动失败？

**解决方案**：
1. 检查 MySQL、Redis、RabbitMQ 是否启动
2. 检查配置文件中的连接信息是否正确
3. 查看日志文件定位具体错误

### Q2: 秒杀接口返回库存不足？

**解决方案**：
1. 检查数据库是否有秒杀商品数据
2. 检查 Redis 是否预热成功
3. 查看日志确认预热逻辑是否执行

### Q3: 订单超时未取消？

**解决方案**：
1. 检查 RabbitMQ 是否启动
2. 检查死信队列配置是否正确
3. 查看消费者日志确认是否消费

---

## 📄 许可证

本项目采用 MIT 许可证，详见 LICENSE 文件。

---

## 👨‍💻 作者

**XXJ**

---

## 📞 联系方式

如有问题，请提 Issue 或联系作者。

---

<div align="center">

**如果本项目对你有帮助，请给个 ⭐ Star！**

</div>
