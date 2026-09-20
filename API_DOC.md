# API 接口文档

<div align="center">

**高并发商品秒杀系统 - RESTful API 接口文档**

版本：v1.1 | 更新时间：2026-09-20

</div>

---

## 接口说明

### 基础信息

- **基础路径**: `http://localhost:8080`
- **数据格式**: JSON
- **字符编码**: UTF-8
- **认证方式**: Header 中携带 `X-Auth-Token`

### 统一返回格式

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**失败响应**：
```json
{
  "code": 400,
  "message": "错误信息",
  "data": null
}
```

### 错误码说明

错误码定义以 `com.flashsale.exception.ErrorCode` 为准，四类业务码首位为业务类型：1xxx 通用 / 2xxx 用户 / 3xxx 商品 / 4xxx 订单 / 5xxx 秒杀。

**通用（1xxx）**

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 200 | SUCCESS | 成功 |
| 1001 | SYSTEM_ERROR | 系统繁忙，请稍后再试 |
| 1002 | PARAM_ERROR | 参数错误 |
| 1003 | DATA_NOT_FOUND | 数据不存在 |
| 1004 | DATA_ALREADY_EXISTS | 数据已存在 |
| 1005 | UNAUTHORIZED | 未授权，请先登录 |
| 1006 | FORBIDDEN | 无权限访问 |
| 1007 | RATE_LIMIT_EXCEEDED | 访问过于频繁，请稍后再试 |
| 1008 | REQUEST_TOO_FREQUENT | 请求过于频繁 |

**用户（2xxx）**

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 2001 | USER_NOT_FOUND | 用户不存在 |
| 2002 | USER_PASSWORD_ERROR | 用户名或密码错误 |
| 2003 | USER_DISABLED | 用户已被禁用 |
| 2004 | USER_ALREADY_EXISTS | 用户已存在 |
| 2005 | TOKEN_INVALID | Token 无效或已过期 |
| 2006 | TOKEN_EXPIRED | Token 已过期 |

**商品（3xxx）**

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 3001 | PRODUCT_NOT_FOUND | 商品不存在 |
| 3002 | PRODUCT_OUT_OF_STOCK | 商品库存不足 |
| 3003 | PRODUCT_STATUS_ERROR | 商品状态异常 |
| 3004 | PRODUCT_NOT_ON_SALE | 商品未上架 |

**订单（4xxx）**

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 4001 | ORDER_NOT_FOUND | 订单不存在 |
| 4002 | ORDER_STATUS_ERROR | 订单状态异常 |
| 4003 | ORDER_CREATE_FAILED | 订单创建失败 |
| 4004 | ORDER_PAYMENT_FAILED | 订单支付失败 |
| 4005 | ORDER_CANCELLED | 订单已取消 |
| 4006 | ORDER_EXPIRED | 订单已超时 |
| 4007 | ORDER_ALREADY_PAID | 订单已支付 |

**秒杀（5xxx）**

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 5001 | SECKILL_NOT_FOUND | 秒杀活动不存在 |
| 5002 | SECKILL_NOT_STARTED | 秒杀活动尚未开始 |
| 5003 | SECKILL_ENDED | 秒杀活动已结束 |
| 5004 | SECKILL_OUT_OF_STOCK | 秒杀商品已抢光 |
| 5005 | SECKILL_LIMIT_EXCEEDED | 每人限购 1 件 |
| 5006 | SECKILL_REPEAT | 您已参与过该秒杀活动 |
| 5007 | SECKILL_LOCK_FAILED | 获取锁失败，请稍后重试 |
| 5008 | SECKILL_STOCK_LOCK_FAILED | 库存锁定失败 |
| 5009 | SECKILL_QUEUE_FULL | 排队人数过多，请稍后再试 |

---

## 一、用户接口

### 1.1 用户注册

**接口地址**: `POST /user/register`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名（3-20 位） |
| password | String | 是 | 密码（6-20 位） |
| email | String | 否 | 邮箱 |
| phone | String | 否 | 手机号 |

**请求示例**：
```json
{
  "username": "testuser",
  "password": "123456",
  "email": "test@example.com",
  "phone": "13800138000"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 1234567890,
    "username": "testuser"
  }
}
```

---

### 1.2 用户登录

**接口地址**: `POST /user/login`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**请求示例**：
```
POST /user/login?username=testuser&password=123456
```

**响应示例**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1234567890,
    "username": "testuser",
    "expireTime": 7200
  }
}
```

**说明**：
- Token 有效期为 2 小时
- 后续请求需在 Header 中携带：`X-Auth-Token: {token}`

---

### 1.3 用户登出

**接口地址**: `POST /user/logout`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**响应示例**：
```json
{
  "code": 200,
  "message": "登出成功",
  "data": null
}
```

---

### 1.4 获取用户信息

**接口地址**: `GET /user/info`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1234567890,
    "username": "testuser",
    "email": "test@example.com",
    "phone": "13800138000",
    "status": 1
  }
}
```

---

## 二、商品接口

### 2.1 商品详情

**接口地址**: `GET /product/detail/{id}`

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 商品 ID |

**请求示例**：
```
GET /product/detail/1
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "iPhone 15 Pro",
    "description": "苹果最新旗舰手机",
    "originalPrice": 8999.00,
    "currentPrice": 7999.00,
    "stock": 100,
    "imageUrl": "https://example.com/iphone15pro.jpg",
    "status": 1
  }
}
```

---

### 2.2 商品列表

**接口地址**: `GET /product/list`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页数量（默认 10） |
| categoryId | Long | 否 | 分类 ID |
| status | Integer | 否 | 商品状态 |

**请求示例**：
```
GET /product/list?pageNum=1&pageSize=10
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "iPhone 15 Pro",
        "currentPrice": 7999.00,
        "stock": 100,
        "imageUrl": "https://example.com/iphone15pro.jpg"
      },
      {
        "id": 2,
        "name": "MacBook Pro 14",
        "currentPrice": 13999.00,
        "stock": 50,
        "imageUrl": "https://example.com/macbookpro14.jpg"
      }
    ],
    "total": 20,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 2
  }
}
```

---

### 2.3 秒杀商品列表

**接口地址**: `GET /product/seckill/list`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页数量（默认 10） |

**请求示例**：
```
GET /product/seckill/list?pageNum=1&pageSize=10
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "productId": 1,
        "productName": "iPhone 15 Pro",
        "seckillPrice": 5999.00,
        "seckillStock": 10,
        "seckillStartTime": "2026-06-10 10:00:00",
        "seckillEndTime": "2026-06-10 10:30:00",
        "status": 1
      },
      {
        "id": 2,
        "productId": 2,
        "productName": "MacBook Pro 14",
        "seckillPrice": 9999.00,
        "seckillStock": 5,
        "seckillStartTime": "2026-06-10 14:00:00",
        "seckillEndTime": "2026-06-10 14:30:00",
        "status": 1
      }
    ],
    "total": 2,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

### 2.4 秒杀商品详情

**接口地址**: `GET /product/seckill/detail/{id}`

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 秒杀商品 ID |

**请求示例**：
```
GET /product/seckill/detail/1
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "productId": 1,
    "productName": "iPhone 15 Pro",
    "productImage": "https://example.com/iphone15pro.jpg",
    "originalPrice": 8999.00,
    "seckillPrice": 5999.00,
    "seckillStock": 10,
    "seckillStartTime": "2026-06-10 10:00:00",
    "seckillEndTime": "2026-06-10 10:30:00",
    "status": 1,
    "currentTime": "2026-06-10 09:55:00",
    "seckillStatus": 0
  }
}
```

**说明**：
- `seckillStatus`: 0-未开始，1-进行中，2-已结束

---

### 2.5 执行秒杀

**接口地址**: `POST /product/seckill/execute`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| seckillId | Long | 是 | 秒杀商品 ID |
| quantity | Integer | 否 | 购买数量（默认 1） |

**请求示例**：
```json
{
  "seckillId": 1,
  "quantity": 1
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "秒杀成功",
  "data": {
    "orderId": 1234567890,
    "orderNo": "SK20260610100001",
    "userId": 1234567890,
    "productId": 1,
    "seckillId": 1,
    "quantity": 1,
    "totalAmount": 5999.00,
    "orderStatus": 0,
    "createTime": "2026-06-10 10:00:00"
  }
}
```

**错误响应**：
```json
{
  "code": 1001,
  "message": "库存不足",
  "data": null
}
```

```json
{
  "code": 1002,
  "message": "秒杀活动已结束",
  "data": null
}
```

```json
{
  "code": 1003,
  "message": "每人限购 1 件",
  "data": null
}
```

```json
{
  "code": 1004,
  "message": "请求过于频繁，请稍后再试",
  "data": null
}
```

---

## 三、订单接口

### 3.1 订单详情

**接口地址**: `GET /order/detail/{id}`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求示例**：
```
GET /order/detail/1234567890
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1234567890,
    "orderNo": "SK20260610100001",
    "userId": 1234567890,
    "productId": 1,
    "productName": "iPhone 15 Pro",
    "productImage": "https://example.com/iphone15pro.jpg",
    "quantity": 1,
    "totalAmount": 5999.00,
    "orderStatus": 0,
    "paymentTime": null,
    "createTime": "2026-06-10 10:00:00"
  }
}
```

**说明**：
- `orderStatus`: 0-待支付，1-已支付，2-已取消，3-已完成

---

### 3.2 根据订单号查询

**接口地址**: `GET /order/detail/no/{no}`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| no | String | 订单编号 |

**请求示例**：
```
GET /order/detail/no/SK20260610100001
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1234567890,
    "orderNo": "SK20260610100001",
    "userId": 1234567890,
    "productId": 1,
    "productName": "iPhone 15 Pro",
    "quantity": 1,
    "totalAmount": 5999.00,
    "orderStatus": 0,
    "createTime": "2026-06-10 10:00:00"
  }
}
```

---

### 3.3 订单列表

**接口地址**: `GET /order/list`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页数量（默认 10） |
| orderStatus | Integer | 否 | 订单状态 |

**请求示例**：
```
GET /order/list?pageNum=1&pageSize=10&orderStatus=0
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1234567890,
        "orderNo": "SK20260610100001",
        "productName": "iPhone 15 Pro",
        "productImage": "https://example.com/iphone15pro.jpg",
        "quantity": 1,
        "totalAmount": 5999.00,
        "orderStatus": 0,
        "createTime": "2026-06-10 10:00:00"
      },
      {
        "id": 1234567891,
        "orderNo": "SK20260610100002",
        "productName": "MacBook Pro 14",
        "productImage": "https://example.com/macbookpro14.jpg",
        "quantity": 1,
        "totalAmount": 9999.00,
        "orderStatus": 1,
        "createTime": "2026-06-10 14:00:00"
      }
    ],
    "total": 2,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

---

### 3.4 取消订单

**接口地址**: `POST /order/cancel/{id}`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求示例**：
```
POST /order/cancel/1234567890
```

**响应示例**：
```json
{
  "code": 200,
  "message": "订单已取消",
  "data": null
}
```

**错误响应**：
```json
{
  "code": 400,
  "message": "订单已支付，无法取消",
  "data": null
}
```

---

### 3.5 支付订单

**接口地址**: `POST /order/pay/{id}`

**请求头**：
```
X-Auth-Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求示例**：
```
POST /order/pay/1234567890
```

**响应示例**：
```json
{
  "code": 200,
  "message": "支付成功",
  "data": {
    "orderId": 1234567890,
    "orderNo": "SK20260610100001",
    "paymentTime": "2026-06-10 10:05:00",
    "orderStatus": 1
  }
}
```

**错误响应**：
```json
{
  "code": 400,
  "message": "订单状态异常",
  "data": null
}
```

---

## 四、限流说明

### 限流策略

系统采用 Redisson 原生 `RRateLimiter` 令牌桶算法，防止恶意刷单。
取令牌与扣减在 Redis 端一次原子脚本内完成，不存在并发竞态；
限流 key 设有兜底 TTL，不会因 key 残留导致该维度被永久限流。

**限流维度**：
1. **用户维度**：每个用户每秒最多 100 次请求，超出返回 `1008`
2. **活动维度**：每个秒杀活动每秒最多 1000 次请求，超出返回 `1007`

阈值定义在 `com.flashsale.constant.SystemConstant` 的
`RATE_LIMIT_PER_SECOND` 与 `SECKILL_RATE_LIMIT_PER_SECOND`。

**限流响应**：

用户维度超限：
```json
{
  "code": 1008,
  "message": "请求过于频繁",
  "data": null
}
```

活动维度超限：
```json
{
  "code": 1007,
  "message": "访问过于频繁，请稍后再试",
  "data": null
}
```

---

## 五、超时订单

### 超时规则

- **超时时间**：订单创建后 30 分钟
- **自动取消**：超时未支付订单自动取消
- **库存回滚**：取消订单后库存自动回滚

### 超时处理流程

```
订单创建 → 发送延迟消息 → 30 分钟后检查 → 未支付 → 取消订单 → 回滚库存
```

---

## 六、签名验证（规划中，当前版本未实现）

> ⚠️ 本章描述的是**尚未实现**的规划能力。当前代码中没有任何签名校验逻辑，
> 秒杀接口 `POST /product/seckill/execute` 不接收也不校验 `timestamp` / `sign` 字段。
> 保留此处仅作为后续扩展方向的记录，请勿据此对接。

防刷目前由限流（第四章）与「每人限购 1 件」两项机制承担。

### 签名算法（规划）

**签名生成**：
```java
String sign = MD5(params + secretKey);
```

**请求示例（规划）**：
```json
{
  "seckillId": 1,
  "quantity": 1,
  "timestamp": 1686384000000,
  "sign": "abc123def456..."
}
```

---

## 七、测试工具

### Postman 集合

导入以下 JSON 到 Postman：

```json
{
  "info": {
    "name": "高并发秒杀系统 API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "用户接口",
      "item": [
        {
          "name": "用户注册",
          "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"username\": \"testuser\",\n  \"password\": \"123456\"\n}"
            },
            "url": {
              "raw": "http://localhost:8080/user/register",
              "host": ["http://localhost:8080"],
              "path": ["user", "register"]
            }
          }
        }
      ]
    }
  ]
}
```

### cURL 示例

```bash
# 用户登录
curl -X POST "http://localhost:8080/user/login?username=testuser&password=123456"

# 获取商品列表
curl http://localhost:8080/product/list

# 执行秒杀
curl -X POST http://localhost:8080/product/seckill/execute \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: {token}" \
  -d '{"seckillId":1,"quantity":1}'

# 获取订单列表
curl "http://localhost:8080/order/list?pageNum=1&pageSize=10" \
  -H "X-Auth-Token: {token}"
```

---

## 八、常见问题

### Q1: Token 失效怎么办？

**A**: Token 有效期为 2 小时，失效后需要重新登录获取新 Token。

### Q2: 秒杀接口返回库存不足？

**A**: 可能原因：
1. 库存已售罄
2. Redis 库存未预热成功
3. 检查数据库和 Redis 数据

### Q3: 订单超时未取消？

**A**: 检查 RabbitMQ 是否正常运行，死信队列配置是否正确。

### Q4: 限流太严格？

**A**: 限流阈值目前**硬编码在常量类**中，不在配置文件里。需修改
`com.flashsale.constant.SystemConstant`：

```java
/** 接口限流：每秒最大请求数（用户维度） */
public static final Integer RATE_LIMIT_PER_SECOND = 100;

/** 秒杀限流：每秒最大请求数（活动维度） */
public static final Integer SECKILL_RATE_LIMIT_PER_SECOND = 1000;
```

修改后重新编译发布即可。若需要按环境动态调整，可将其改为
`@ConfigurationProperties` 绑定到 `application-{profile}.yml`。

### Q5: 订单超时未取消，还伴随「库存被永久占用」？

**A**: 排查两点：
1. RabbitMQ 是否正常运行，死信队列（TTL + DLX）是否正确创建
2. 秒杀订单是否成功投递了**延迟消息** —— 秒杀单与普通单都必须投递，
   漏投会导致订单永远不进 TTL 队列，从而既不取消也不回滚库存

---

<div align="center">

**文档版本**: v1.1  
**最后更新**: 2026-09-20  
**作者**: XXJ

</div>
