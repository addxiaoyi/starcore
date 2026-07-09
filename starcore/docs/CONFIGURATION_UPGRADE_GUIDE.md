# StarCore 配置升级指南

## 概述

本指南帮助从旧版本配置迁移到新版本，确保所有功能正常运行。

---

## 配置文件清单

| 文件 | 位置 | 用途 |
|------|------|------|
| `config.yml` | `plugins/StarCore/` | 主配置文件 |
| `nations.yml` | `plugins/StarCore/` | 国家设置 |
| `economy.yml` | `plugins/StarCore/` | 经济配置 |
| `permissions.yml` | `plugins/StarCore/` | 权限配置 |

---

## config.yml 升级

### 新增配置项

#### 1. 数据库迁移配置

```yaml
# ===== 数据库配置 =====
database:
  # 是否启用数据库存储（推荐 true）
  enabled: true
  
  # 数据库类型: sqlite | mysql
  type: sqlite
  
  # 启动失败策略
  # true: 数据库失败时停止启动
  # false: 数据库失败时降级到 Properties 文件（推荐）
  fail-fast: false
  
  # === SQLite 配置 ===
  sqlite:
    file: starcore.db  # 数据库文件名
  
  # === MySQL 配置 ===
  mysql:
    host: localhost
    port: 3306
    database: starcore_db
    username: starcore_user
    password: "your_password_here"  # ⚠️ 修改为实际密码
  
  # === 连接池配置 ===
  pool:
    maximum-pool-size: 10      # 最大连接数
    minimum-idle: 2            # 最小空闲连接
    connection-timeout: 30000  # 连接超时（毫秒）
    idle-timeout: 600000       # 空闲超时（10 分钟）
    max-lifetime: 1800000      # 连接最大生命周期（30 分钟）
    leak-detection-threshold: 60000  # 泄漏检测阈值（0 = 禁用）
```

#### 2. 性能配置优化

```yaml
# ===== 性能优化 =====
performance:
  # 异步线程池大小
  async-threads: 4
  
  # 缓存配置
  cache:
    # 国家数据缓存大小
    nation-cache-size: 100
    
    # 领地缓存大小
    territory-cache-size: 500
    
    # 玩家缓存大小
    player-cache-size: 1000
    
    # 缓存过期时间（秒）
    cache-expiry: 300
```

### 配置迁移对照表

| 旧配置 | 新配置 | 说明 |
|--------|--------|------|
| `storage.type` | `database.type` | 重命名 |
| `storage.mysql.*` | `database.mysql.*` | 路径变更 |
| - | `database.fail-fast` | 新增 |
| - | `database.pool.*` | 新增 |

---

## 完整配置示例

### SQLite 模式（推荐小型服务器）

```yaml
# config.yml - SQLite 配置示例

# ===== 数据库配置 =====
database:
  enabled: true
  type: sqlite
  fail-fast: false
  
  sqlite:
    file: starcore.db
  
  pool:
    maximum-pool-size: 5
    minimum-idle: 1

# ===== 国家系统 =====
nation:
  # 国家创建需要的玩家数
  min-players: 3
  
  # 领地圈选价格（每个区块）
  claim-price-per-chunk: 100.0
  
  # 最大领地数量
  max-territories: 50
  
  # 首都传送冷却（秒）
  capital-teleport-cooldown: 300

# ===== 经济系统 =====
economy:
  # 启用内部经济系统
  use-internal-economy: true
  
  # 初始余额
  starting-balance: 1000.0
  
  # 每日工资
  daily-wage: 50.0
  
  # 税率 (0.1 = 10%)
  tax-rate: 0.1

# ===== 外交系统 =====
diplomacy:
  # 宣战冷却（秒）
  war-cooldown: 86400  # 24 小时
  
  # 和平条约持续时间（秒）
  peace-duration: 604800  # 7 天

# ===== 地图系统 =====
map:
  # Web 地图端口
  web-port: 8080
  
  # 访问密钥（留空自动生成）
  access-secret: ""
  
  # 瓦片缓存大小
  tile-cache-size: 1000

# ===== 性能优化 =====
performance:
  async-threads: 4
  
  cache:
    nation-cache-size: 100
    territory-cache-size: 500
    player-cache-size: 1000
    cache-expiry: 300

# ===== 调试选项 =====
debug:
  # 启用调试日志
  enabled: false
  
  # 性能监控
  metrics-enabled: true
  
  # SQL 查询日志
  log-sql-queries: false
```

### MySQL 模式（推荐大型服务器）

```yaml
# config.yml - MySQL 配置示例

# ===== 数据库配置 =====
database:
  enabled: true
  type: mysql
  fail-fast: false
  
  mysql:
    host: localhost
    port: 3306
    database: starcore_db
    username: starcore_user
    password: "SecurePassword123!"
  
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
    leak-detection-threshold: 60000

# ===== 其他配置同 SQLite 模式 =====
# ... (复制上面的配置)
```

---

## 配置验证

### 启动时检查

插件启动时会自动验证配置：

```
[INFO] 加载配置文件...
[INFO] ✅ 数据库配置验证通过
[INFO] ✅ 国家系统配置验证通过
[INFO] ⚠️ 经济系统配置使用默认值
```

### 手动验证命令

```
/starcore config check
```

**输出示例**：
```
=== StarCore 配置检查 ===
✅ 数据库: SQLite @ starcore.db
✅ 经济: 内部系统 (起始余额: 1000.0)
✅ 国家: 最少 3 人，最大 50 领地
⚠️ 地图: Web 端口 8080 (确保端口未被占用)
✅ 性能: 4 异步线程，缓存启用
```

---

## 常见配置问题

### 问题 1: MySQL 连接失败

**错误日志**：
```
[ERROR] STARCORE database startup failed: Access denied for user 'starcore_user'@'localhost'
```

**解决方案**：

1. 检查用户名和密码
```sql
-- 创建用户
CREATE USER 'starcore_user'@'localhost' IDENTIFIED BY 'your_password';

-- 授予权限
GRANT ALL PRIVILEGES ON starcore_db.* TO 'starcore_user'@'localhost';
FLUSH PRIVILEGES;
```

2. 检查数据库是否存在
```sql
CREATE DATABASE IF NOT EXISTS starcore_db 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 问题 2: 端口冲突

**错误日志**：
```
[ERROR] Failed to start web server: Address already in use (port 8080)
```

**解决方案**：

修改 Web 地图端口：
```yaml
map:
  web-port: 8081  # 改为未占用的端口
```

### 问题 3: 性能下降

**症状**：服务器 TPS 下降

**解决方案**：

#### 调整线程池
```yaml
performance:
  async-threads: 8  # 增加到 8（推荐 CPU 核心数）
```

#### 调整缓存大小
```yaml
performance:
  cache:
    nation-cache-size: 200      # 增加到 200
    territory-cache-size: 1000  # 增加到 1000
    player-cache-size: 2000     # 增加到 2000
```

#### 启用连接池调优
```yaml
database:
  pool:
    maximum-pool-size: 20  # 增加连接池
    minimum-idle: 5        # 增加最小空闲
```

### 问题 4: 空密码警告

**错误日志**：
```
[WARN] MySQL password is empty! This is a security risk.
```

**解决方案**：

1. 设置强密码
```yaml
database:
  mysql:
    password: "ComplexPassword123!@#"
```

2. 或者禁用 fail-fast 并使用 SQLite
```yaml
database:
  enabled: true
  type: sqlite  # 改用 SQLite
```

---

## 配置优化建议

### 小型服务器 (< 50 人)

```yaml
database:
  type: sqlite
  pool:
    maximum-pool-size: 5

performance:
  async-threads: 2
  cache:
    nation-cache-size: 50
    territory-cache-size: 200
    player-cache-size: 500
```

### 中型服务器 (50-200 人)

```yaml
database:
  type: mysql  # 推荐 MySQL
  pool:
    maximum-pool-size: 10

performance:
  async-threads: 4
  cache:
    nation-cache-size: 100
    territory-cache-size: 500
    player-cache-size: 1000
```

### 大型服务器 (> 200 人)

```yaml
database:
  type: mysql
  pool:
    maximum-pool-size: 20
    minimum-idle: 5

performance:
  async-threads: 8
  cache:
    nation-cache-size: 200
    territory-cache-size: 1000
    player-cache-size: 2000
    cache-expiry: 600  # 延长到 10 分钟
```

---

## 配置热重载

部分配置支持热重载（无需重启）：

```
/starcore reload
```

**支持热重载的配置**：
- ✅ 经济设置（税率、工资）
- ✅ 外交设置（冷却时间）
- ✅ 缓存配置
- ❌ 数据库配置（需重启）
- ❌ 线程池配置（需重启）

---

## 配置安全建议

### 1. 保护数据库密码

**不要**将密码明文存储在配置文件中（生产环境）。

**推荐方案**：

#### 使用环境变量
```yaml
database:
  mysql:
    password: "${STARCORE_DB_PASSWORD}"  # 从环境变量读取
```

设置环境变量：
```bash
export STARCORE_DB_PASSWORD="your_secure_password"
```

#### 使用配置加密

StarCore 提供配置加密工具：
```
/starcore config encrypt database.mysql.password
```

### 2. 限制 Web 地图访问

```yaml
map:
  access-secret: "random_32_char_string_here"  # 自动生成
  
  # 允许的 IP 白名单
  allowed-ips:
    - "127.0.0.1"
    - "192.168.1.0/24"
```

### 3. 定期备份配置

```bash
# 每周备份
cp plugins/StarCore/config.yml plugins/StarCore/config.yml.backup-$(date +%Y%m%d)
```

---

## 附录

### 附录 A: 配置文件模板

完整配置模板位于：
- `src/main/resources/config.yml`（默认配置）
- `docs/config-examples/` （示例配置）

### 附录 B: 配置优先级

配置加载顺序（后者覆盖前者）：

1. 内置默认值
2. `config.yml`
3. 环境变量
4. 命令行参数（启动参数）

### 附录 C: 配置 API

开发者可通过 API 访问配置：

```java
ConfigurationService config = context.configurationService();
boolean dbEnabled = config.databaseEnabled();
String dbType = config.databaseType();
```

---

**版本**: 1.0  
**更新日期**: 2026-06-17  
**作者**: StarCore Team
