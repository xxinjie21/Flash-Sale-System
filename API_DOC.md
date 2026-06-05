# API 接口文档

<div align="center">

**高并发商品秒杀系统 - RESTful API 接口文档**

版本：v1.0 | 更新时间：2026-06-05

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

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录/Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 系统错误 |
| 1001 | 库存不足 |
| 1002 | 秒杀已结束 |
| 1003 | 重复下单 |
| 1004 | 限流 |

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

系统采用令牌桶限流算法，防止恶意刷单。

**限流维度**：
1. **用户维度**：每个用户每秒最多 1 次请求
2. **活动维度**：每个秒杀活动每秒最多 1000 次请求

**限流响应**：
```json
{
  "code": 1004,
  "message": "请求过于频繁，请稍后再试",
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

## 六、签名验证（可选）

### 签名算法

**签名生成**：
```java
String sign = MD5(params + secretKey);
```

**请求示例**：
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

**A**: 可在配置文件中调整限流参数：
```yaml
rate-limit:
  per-second: 1  # 用户维度每秒请求数
  seckill-per-second: 1000  # 活动维度每秒请求数
```

---

<div align="center">

**文档版本**: v1.0  
**最后更新**: 2026-06-05  
**作者**: XXJ

</div>
