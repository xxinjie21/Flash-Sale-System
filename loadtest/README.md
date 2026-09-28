# 秒杀接口压测报告

本目录是 `/product/seckill/execute` 的真实压测脚本与实测结果。
**报告中所有数字都来自本页描述的可复现测试，没有估算值。**

---

## 1. 测试目标

验证四件事，而不是单纯"跑个 TPS"：

1. **零超卖**：并发远超库存时，成交数是否严格等于库存数
2. **真实吞吐与延迟**：单机单实例下的容量边界
3. **数据库压力削减**：一万次请求里有多少次真正落到 MySQL
4. **链路可靠性**：消息是否丢失、失败是否被兜住

---

## 2. 测试环境

| 项目 | 配置 |
|------|------|
| 机器 | Windows 11（build 22631），32 逻辑核 |
| JDK | OpenJDK 17.0.18 |
| 应用 | Spring Boot 2.7.15，单实例，`java -jar`，默认 JVM 堆 |
| Redis | 7.4（Docker 容器，端口 6379） |
| RabbitMQ | 3.13.7（Docker 容器，端口 5672） |
| MySQL | 9.7.2（Docker 容器，端口 3306） |
| 压测工具 | Apache JMeter 5.6.3（非 GUI 模式，`-Xms1g -Xmx2g`） |
| 网络 | 全部在 localhost，排除网络带宽影响 |

> 注意：**压测客户端与应用跑在同一台机器上**，JMeter 自身会争抢 CPU，
> 因此这里的数字是"同一台机器自测"的下限，不代表生产容量。

---

## 3. 测试设计

模拟真实秒杀场景：**大量用户同时抢极少量库存**。

- **5 个秒杀活动**，库存分别为 10 / 5 / 20 / 15 / 8，**合计 58 件**
- **2000 个真实注册用户**，每人登录拿到真实 Token
- 每个用户对 5 个活动各发起 1 次请求 → **合计 10000 次请求**
- 2000 线程，ramp-up 10s，每个虚拟用户带独立的 `X-Forwarded-For`
  （模拟不同客户端 IP，避免单 IP 限流掩盖服务端容量）
- 每个请求携带真实 `X-Auth-Token`，走完整鉴权链路

预期：58 件库存，**只应有 58 笔订单**，其余请求被"已售罄 / 重复下单"拒绝。

---

## 4. 如何复现

### 4.1 准备数据

```bash
# 1) 启动依赖（Redis / RabbitMQ / MySQL）
docker start windows-redis windows-rabbitmq windows-mysql

# 2) 初始化库表
docker exec -i windows-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 \
  -e "CREATE DATABASE IF NOT EXISTS flash_sale DEFAULT CHARACTER SET utf8mb4;"
docker exec -i windows-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 flash_sale \
  < src/main/resources/db/schema.sql
docker exec -i windows-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 flash_sale \
  < src/main/resources/db/test_data.sql

# 3) 延长活动时间窗并重置库存（test_data.sql 的活动只有 2 小时）
docker exec windows-mysql mysql -uroot -p<密码> --default-character-set=utf8mb4 flash_sale -e "
  UPDATE seckill_product SET
    seckill_stock = CASE id WHEN 1 THEN 10 WHEN 2 THEN 5 WHEN 3 THEN 20 WHEN 4 THEN 15 WHEN 5 THEN 8 END,
    seckill_start_time = NOW() - INTERVAL 5 MINUTE,
    seckill_end_time   = NOW() + INTERVAL 4 HOUR,
    status = 1;"

# 4) 清空订单与 MQ 队列
docker exec windows-mysql mysql ... -e "DELETE FROM \`order\`;"
docker exec windows-rabbitmq sh -c 'for q in flash_sale.order.queue flash_sale.seckill.order.queue \
  flash_sale.order.dlx.queue flash_sale.order.fail.queue; do rabbitmqctl purge_queue $q; done'
```

### 4.2 启动应用

`application-dev.yml` 已把 Redis / RabbitMQ 凭据改为环境变量：

```bash
export REDIS_PASSWORD=<你的 Redis 密码>
export RABBITMQ_USER=flashsale
export RABBITMQ_PASSWORD=<你的 RabbitMQ 密码>
java -jar target/high-concurrency-flash-sale-1.0.0.jar
```

> 若 RabbitMQ 跑在 Docker 里，`guest` 账号会因 loopback 限制被拒
> （`ACCESS_REFUSED`），需要另建用户：
> ```bash
> docker exec windows-rabbitmq rabbitmqctl add_user flashsale <密码>
> docker exec windows-rabbitmq rabbitmqctl set_user_tags flashsale administrator
> docker exec windows-rabbitmq rabbitmqctl set_permissions -p / flashsale ".*" ".*" ".*"
> ```

### 4.3 生成压测用户与 Token

```bash
python seed_users.py 2000      # 批量注册 + 登录，导出 tokens.csv
```

> ⚠️ 造数据时会被限流拦截器按 IP 限速（默认 100 req/s），脚本内已带退避重试。
> 同理，**不要在压测前清空 `flash_sale:user:token:*`**，否则所有 Token 失效，
> 请求会全部停在鉴权环节（表现为 HTTP 200 + `code 2006 Token 已过期`）。

### 4.4 执行压测

```bash
java -Xms1g -Xmx2g -jar apache-jmeter-5.6.3/bin/ApacheJMeter.jar \
  -n -t loadtest/seckill-load-test.jmx \
  -l result.jtl -e -o report \
  -Jthreads=2000 -Jramp=10 -Jloops=5 \
  -Jtokens=D:/fs-tools/tokens.csv \
  -Jhost=127.0.0.1 -Jport=8080
```

参数均可用 `-J` 覆盖：`threads` / `ramp` / `loops` / `tokens` / `host` / `port`。

---

## 5. 实测结果

共跑了两组并发梯度，**两组都精确成交 58 单**。

### 5.0 两组梯度对比

| 指标 | 场景 A（2000 并发） | 场景 B（5000 并发） |
|------|-------------------|-------------------|
| 线程数 / 每线程轮次 | 2000 × 5 | 5000 × 2 |
| 总请求数 | 10,000 | 10,000 |
| ramp-up | 10 s | 5 s |
| 吞吐 | 1002.4 TPS | 1079.0 TPS |
| 平均延迟 | 552 ms | 2009 ms |
| P95 | 794 ms | 2683 ms |
| P99 | 1347 ms | 2723 ms |
| **客户端失败** | 86（0.86%） | **0（0.00%）** |
| **成交订单** | **58** | **58** |

**解读**：并发从 2000 提到 5000，吞吐只从 1002 涨到 1079 TPS
（+7.7%），说明**单机容量在 1000~1100 TPS 附近已经饱和**；
多出来的并发全部转化成了排队等待（平均延迟 552ms → 2009ms）。
这是典型的"吞吐见顶、延迟劣化"拐点，继续加并发不会再提升吞吐。

### 5.1 场景 A（2000 并发）吞吐与延迟

| 指标 | 数值 |
|------|------|
| 总请求数 | 10,000 |
| 时间跨度 | 9.98 s |
| **吞吐** | **1002.4 TPS** |
| 平均延迟 | 552 ms |
| P50 | 531 ms |
| P90 | 716 ms |
| P95 | 794 ms |
| **P99** | **1347 ms** |
| 最小 / 最大 | 9 ms / 1642 ms |
| 客户端连接失败 | 86（0.86%，JMeter 侧 `HttpHostConnectException`） |

### 5.2 业务结果分布（服务端日志实测）

场景 A（本次运行的增量）：

| 结果 | 次数 |
|------|------|
| 进入秒杀业务逻辑 | 9914 |
| **秒杀成功** | **58** |
| 已售罄拒绝 | 9805 |
| 重复下单拒绝（每人限购 1 件） | 51 |
| 限流拒绝 | 0 |
| 获取锁失败 | 0 |
| 系统异常 | 0 |

场景 B 同样为：进入业务逻辑 10000 次、**秒杀成功 58**、已售罄 9885、重复下单 57、系统异常 0。

### 5.3 终态一致性校验

| 校验项 | 结果 |
|--------|------|
| MySQL 订单数 | **58** |
| MySQL `seckill_stock` | 0 / 0 / 0 / 0 / 0 |
| Redis 剩余库存 | 0 / 0 / 0 / 0 / 0 |
| Redis 与 MySQL 库存是否一致 | ✅ 一致 |
| 失败死信队列堆积 | 0 |
| 普通/秒杀订单队列堆积 | 0 |

按活动拆分的成交明细：

| seckill_id | 初始库存 | 成交订单 | 成交金额 |
|-----------|---------|---------|---------|
| 1 | 10 | 10 | 59,990.00 |
| 2 | 5 | 5 | 49,995.00 |
| 3 | 20 | 20 | 19,980.00 |
| 4 | 15 | 15 | 44,985.00 |
| 5 | 8 | 8 | 31,992.00 |
| **合计** | **58** | **58** | 206,942.00 |

---

## 6. 结论

### 6.1 零超卖（本报告最重要的一条）

> **10000 次并发请求抢 58 件库存，最终恰好产生 58 笔订单，Redis 与 MySQL 库存同时归零，两边完全一致。**

这不是推算，是可复现的实测：多一件就是超卖，少一件就是少卖，实际是精确的 58。

### 6.2 数据库压力削减（可直接用于面试）

请求量与数据库写入量的对比：

| 指标 | 数值 |
|------|------|
| 秒杀请求数 | 10,000 |
| 实际落到 MySQL 的写操作 | 58 |
| **数据库写压力削减** | **99.42%** |
| 落到 MySQL 的比例 | 0.58% |

也就是说：**99.4% 的请求被 Redis 层拦下**，MySQL 只承担了最终成交的那 58 次插入。
这正是"Redis 预扣库存 + MQ 异步下单"架构要达成的效果，且现在是可量化的。

### 6.3 链路可靠性

- 失败死信队列 **0 条**：没有消息在重试耗尽后丢失
- 订单队列 / 秒杀队列 **0 堆积**：消费能力跟得上生产速度
- 系统异常 **0 次**：稳态下没有未捕获异常

### 6.4 一个反直觉的观察

成功路径的平均延迟（552ms）远高于纯拒绝路径。原因是**失败得越早越快**：
库存售罄后，请求在 Redis CAS 扣减那一步就返回，无需占用后续资源；
而 58 个成功请求要额外走分布式锁、发 MQ、等消费建单。
所以高并发下限流/快速失败的设计，本身就是延迟优化手段。

---

## 7. 局限与注意事项

诚实说明，避免把数字当成生产容量：

1. **客户端与应用同机**：JMeter 与 JVM 争抢 32 核 CPU，真实容量应更高。
2. **0.86% 的客户端连接失败**：`HttpHostConnectException` 来自 JMeter 侧
   （2000 线程瞬时建连超出客户端/系统 backlog），不是服务端报错。
   服务端统计的"系统异常"为 0。
3. **单机单实例**：没有集群、没有读写分离，不代表分布式部署下的表现。
4. **限流维度影响**：单机压测所有请求同源，若不注入 `X-Forwarded-For`，
   会被按 IP 的 100 req/s 限流挡住，测出来的就不是服务端容量。
5. **`expireIfNotSet` 教训**：早期版本给 `RRateLimiter` 的 key 加过兜底 TTL，
   在 2000 并发下约有 30% 请求直接 500。已修复并有回归测试守护
   （详见 `RedissonRateLimiter` 类注释与 `RedissonRateLimiterTest`）。
