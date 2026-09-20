# 高并发秒杀系统 - 部署说明

## 一、环境要求

### 1.1 基础环境
- JDK 17
- Maven 3.6+
- MySQL 8.0+
- Redis 6.2+
- RabbitMQ 3.9+

### 1.2 操作系统
- Windows 10/11
- Linux (CentOS 7+/Ubuntu 18.04+)
- macOS

## 二、中间件安装

### 2.1 MySQL 8.0 安装

**Windows:**
```bash
# 下载 MySQL Installer
# 访问：https://dev.mysql.com/downloads/installer/
# 选择 MySQL Installer 8.0.x
# 按向导安装，设置 root 密码
```

**Linux:**
```bash
# Ubuntu
sudo apt-get update
sudo apt-get install mysql-server

# CentOS
sudo yum install mysql-server
```

### 2.2 Redis 6.2 安装

**Windows:**
```bash
# 访问：https://github.com/tporadowski/redis/releases
# 下载 Redis-x64-x.x.x.zip
# 解压后运行 redis-server.exe
```

**Linux:**
```bash
# 下载
wget http://download.redis.io/releases/redis-6.2.13.tar.gz
tar xzvf redis-6.2.13.tar.gz
cd redis-6.2.13
make
make install

# 启动
redis-server
```

### 2.3 RabbitMQ 3.9 安装

**Windows:**
```bash
# 1. 先安装 Erlang
# 访问：https://www.erlang.org/downloads
# 下载并安装

# 2. 安装 RabbitMQ
# 访问：https://www.rabbitmq.com/install-windows.html
# 下载并安装
```

**Linux:**
```bash
# 添加 RabbitMQ 仓库
curl -s https://packagecloud.io/install/repositories/rabbitmq/erlang/script.rpm.sh | sudo bash

# 安装
sudo yum install -y rabbitmq-server

# 启动
sudo systemctl start rabbitmq-server

# 启用管理插件
sudo rabbitmq-plugins enable rabbitmq_management
```

## 三、项目配置

### 3.1 数据库初始化

```bash
# 1. 登录 MySQL
mysql -u root -p

# 2. 执行建表脚本
source src/main/resources/db/schema.sql

# 3. 验证数据
USE flash_sale;
SELECT * FROM product;
SELECT * FROM seckill_product;
```

### 3.2 修改配置文件

编辑 `src/main/resources/application-dev.yml`:

```yaml
# MySQL 配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flash_sale?useUnicode=true&characterEncoding=utf8
    username: root
    password: 你的 MySQL 密码

# Redis 配置
  redis:
    host: localhost
    port: 6379
    password:  # 如果有密码请填写

# RabbitMQ 配置
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

## 四、启动项目

### 4.1 使用 Maven 启动

```bash
# 1. 清理编译
mvn clean

# 2. 编译打包
mvn package

# 3. 运行
mvn spring-boot:run

# 或直接运行 JAR
java -jar target/high-concurrency-flash-sale-1.0.0.jar
```

### 4.2 使用 IDE 启动

**IntelliJ IDEA:**
```
1. 打开项目
2. 找到 FlashSaleApplication.java
3. 右键 -> Run 'FlashSaleApplication'
```

**Eclipse:**
```
1. 打开项目
2. 找到 FlashSaleApplication.java
3. 右键 -> Run As -> Java Application
```

## 五、验证部署

### 5.1 检查日志

项目启动成功后，日志应显示：
```
========================================
   高并发秒杀系统启动成功！
   支持单机 5000 并发，P99 响应<500ms
========================================
```

### 5.2 测试接口

```bash
# 1. 健康检查
curl http://localhost:8080/api/product/list

# 2. 获取秒杀商品列表
curl http://localhost:8080/api/product/seckill/list

# 3. 用户注册
curl -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@example.com"}'

# 4. 用户登录
curl -X POST "http://localhost:8080/api/user/login?username=test&password=123456"
```

## 六、生产环境部署

### 6.1 打包

```bash
mvn clean package -DskipTests
```

### 6.2 修改生产配置

编辑 `src/main/resources/application-prod.yml`:
- 修改数据库连接为生产环境地址
- 修改 Redis 连接为生产环境地址
- 修改 RabbitMQ 连接为生产环境地址
- 配置日志文件路径

### 6.3 启动脚本

创建 `start.sh`:
```bash
#!/bin/bash

APP_NAME="high-concurrency-flash-sale"
JAR_FILE="$APP_NAME.jar"

# 检查是否已运行
PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "应用已在运行，PID: $PID"
    exit 1
fi

# 启动应用
nohup java -jar \
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -Dspring.profiles.active=prod \
  $JAR_FILE > /var/log/$APP_NAME.log 2>&1 &

echo "应用启动中..."
sleep 3

# 验证启动
PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "应用启动成功，PID: $PID"
else
    echo "应用启动失败"
    exit 1
fi
```

### 6.4 停止脚本

创建 `stop.sh`:
```bash
#!/bin/bash

APP_NAME="high-concurrency-flash-sale"
JAR_FILE="$APP_NAME.jar"

PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "应用未运行"
    exit 0
fi

echo "停止应用，PID: $PID"
kill -15 $PID

# 等待进程结束
for i in {1..10}; do
    sleep 1
    PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')
    if [ -z "$PID" ]; then
        echo "应用已停止"
        exit 0
    fi
done

# 强制停止
echo "强制停止应用"
kill -9 $PID
```

## 七、监控与运维

### 7.1 查看日志

```bash
# 实时查看日志
tail -f /var/log/high-concurrency-flash-sale.log

# 查看错误日志
grep "ERROR" /var/log/high-concurrency-flash-sale.log | tail -100
```

### 7.2 性能监控

```bash
# 查看 JVM 内存
jstat -gc <PID> 1000

# 查看线程
jstack <PID>

# 查看堆内存
jmap -heap <PID>
```

### 7.3 数据库备份

```bash
# 备份数据库
mysqldump -u root -p flash_sale > flash_sale_backup.sql

# 恢复数据库
mysql -u root -p flash_sale < flash_sale_backup.sql
```

## 八、常见问题

### 8.1 启动失败

**问题 1: 端口被占用**
```bash
# 查看端口占用
netstat -ano | findstr :8080

# 杀死进程
taskkill /F /PID <PID>
```

**问题 2: 数据库连接失败**
- 检查 MySQL 是否启动
- 检查用户名密码是否正确
- 检查数据库是否存在

**问题 3: Redis 连接失败**
- 检查 Redis 是否启动
- 检查 Redis 密码配置

### 8.2 秒杀失败

**问题：库存扣减失败**
- 检查 Redis 中是否有库存数据
- 检查 Lua 脚本是否正确加载
- 查看日志中的错误信息

## 九、性能优化建议

### 9.1 JVM 参数优化

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -jar app.jar
```

### 9.2 数据库优化

- 添加合适的索引
- 使用连接池（HikariCP）
- 读写分离（主从复制）

### 9.3 Redis 优化

- 使用集群模式（Redis Cluster）
- 开启持久化（RDB+AOF）
- 设置合理的过期时间

## 十、联系方式

如有问题，请查看项目 README 或提交 Issue。
