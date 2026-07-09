# StarCore 数据库迁移部署指南

## 概述

本指南帮助管理员从旧版本的 StarCore 升级到包含 Flyway 数据库迁移的新版本。

---

## 版本信息

- **目标版本**: 0.1.0-SNAPSHOT (含存储层重构)
- **数据库迁移**: V1 → V3
- **最低 Minecraft 版本**: Paper 1.21.9+
- **Java 版本**: 21

---

## 升级前准备

### 1. 备份现有数据 ⚠️

**必须操作**，升级前务必备份：

#### SQLite 数据库备份
```bash
# 停止服务器
screen -r minecraft
stop

# 备份数据库文件
cd plugins/StarCore/
cp starcore.db starcore.db.backup-$(date +%Y%m%d)

# 备份 Properties 文件
cp -r persistence/ persistence.backup-$(date +%Y%m%d)/
```

#### MySQL 数据库备份
```bash
# 导出数据库
mysqldump -u starcore_user -p starcore_db > starcore_backup_$(date +%Y%m%d).sql

# 备份 Properties 文件
cd plugins/StarCore/
cp -r persistence/ persistence.backup-$(date +%Y%m%d)/
```

### 2. 检查兼容性

| 组件 | 最低版本 | 检查命令 |
|------|---------|---------|
| Paper | 1.21.9 | `/version` |
| Java | 21 | `java -version` |
| MySQL | 8.0+ | `mysql --version` |

---

## 升级步骤

### 步骤 1: 停止服务器

```bash
screen -r minecraft
stop
```

等待服务器完全关闭（检查进程消失）：
```bash
ps aux | grep java
```

### 步骤 2: 替换插件 JAR

```bash
cd plugins/
# 备份旧版本
mv starcore-0.0.9.jar starcore-0.0.9.jar.old

# 上传新版本
# 使用 SFTP 或 scp 上传 starcore-0.1.0-SNAPSHOT.jar
```

### 步骤 3: 检查配置文件

新版本增加了数据库迁移配置，检查 `config.yml`：

```yaml
database:
  enabled: true
  type: sqlite  # 或 mysql
  fail-fast: false  # 推荐设置为 false，避免启动失败
  
  # SQLite 配置
  sqlite:
    file: starcore.db
  
  # MySQL 配置
  mysql:
    host: localhost
    port: 3306
    database: starcore_db
    username: starcore_user
    password: "your_password"  # 修改为实际密码
```

**重要参数**：
- `fail-fast: false` - 数据库迁移失败时不阻止启动，降级到 Properties 模式

### 步骤 4: 启动服务器

```bash
screen -S minecraft
cd /path/to/server
java -Xmx4G -Xms4G -jar paper-1.21.11.jar nogui
```

### 步骤 5: 监控启动日志

查看迁移过程：

```bash
tail -f logs/latest.log | grep -E "(STARCORE|migration|Flyway)"
```

**预期日志**：

```
[INFO] STARCORE database ready: SQLite @ starcore.db (pool: HikariCP-STARCORE-SQLite)
[INFO] 开始数据库迁移检查...
[INFO] ✅ 数据库迁移完成: 执行了 3 个迁移脚本 (目标版本: 3)
[INFO] STARCORE 0.1.0-SNAPSHOT enabled.
```

---

## 迁移过程详解

### 自动迁移流程

插件启动时，Flyway 会自动执行：

#### 1. 创建迁移历史表

```sql
CREATE TABLE starcore_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200),
    type VARCHAR(20),
    script VARCHAR(1000),
    checksum INT,
    installed_by VARCHAR(100),
    installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    execution_time INT,
    success BOOLEAN
);
```

#### 2. 执行迁移脚本

| 版本 | 脚本 | 说明 |
|------|------|------|
| V1 | `V1__initial_schema.sql` | 创建 13 张核心表 |
| V2 | `V2__map_module.sql` | 创建地图模块表 |
| V3 | `V3__performance_indexes.sql` | 创建性能索引 |

#### 3. Properties 数据自动迁移

首次启动时，如果检测到 Properties 文件存在但 SQL 表为空，会自动迁移：

```
[INFO] [nation] 检测到 Properties 数据，开始迁移到 SQL...
[INFO] [nation] 数据迁移完成
```

---

## 验证升级

### 1. 检查数据库表

#### SQLite
```bash
sqlite3 plugins/StarCore/starcore.db

.tables
# 应显示所有表：
# starcore_nation_state
# starcore_diplomacy_state
# starcore_schema_history
# ...
```

#### MySQL
```sql
USE starcore_db;
SHOW TABLES;

-- 检查迁移历史
SELECT * FROM starcore_schema_history;
```

**预期结果**：
```
+----------------+---------+----------------------+
| installed_rank | version | description          |
+----------------+---------+----------------------+
| 1              | 1       | initial schema       |
| 2              | 2       | map module           |
| 3              | 3       | performance indexes  |
+----------------+---------+----------------------+
```

### 2. 验证数据迁移

检查国家数据是否成功迁移：

```sql
-- SQLite
SELECT COUNT(*) FROM starcore_nation_state;

-- 应该与旧 Properties 文件中的键值对数量一致
```

### 3. 游戏内验证

登录服务器测试核心功能：

```
/nation list          # 国家列表
/diplomacy status     # 外交关系
/treasury balance     # 国库余额
/policy list          # 政策列表
```

---

## 故障排查

### 问题 1: 数据库迁移失败

**症状**：
```
[ERROR] 数据库迁移失败: Table 'xxx' already exists
```

**原因**：数据库中已存在旧表结构

**解决方案**：

#### 方案 A: 重置数据库（数据丢失）
```sql
-- SQLite
DROP DATABASE IF EXISTS starcore.db;

-- MySQL
DROP DATABASE starcore_db;
CREATE DATABASE starcore_db;
```

#### 方案 B: 手动 Baseline（保留数据）
```sql
-- 创建迁移历史表
CREATE TABLE starcore_schema_history (
    installed_rank INT PRIMARY KEY,
    version VARCHAR(50),
    description VARCHAR(200),
    script VARCHAR(1000),
    checksum INT,
    installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 标记为已迁移（跳过 V1-V3）
INSERT INTO starcore_schema_history (installed_rank, version, description, script, checksum)
VALUES (1, '1', 'baseline', 'V1__initial_schema.sql', 0);
```

### 问题 2: Properties 数据未迁移

**症状**：国家数据丢失，玩家余额为 0

**原因**：Properties 文件路径不正确

**解决方案**：
```bash
# 检查文件位置
ls -la plugins/StarCore/persistence/

# 应包含：
# nations.properties
# diplomacy.properties
# policies.properties
# ...

# 如果文件在其他位置，移动回来
mv backup/persistence/*.properties plugins/StarCore/persistence/
```

### 问题 3: 服务器启动失败

**症状**：
```
[ERROR] STARCORE database startup failed
[SEVERE] Plugin StarCore failed to enable
```

**解决方案**：

1. 设置 `fail-fast: false`（config.yml）
2. 检查数据库连接信息
3. 查看详细错误日志

```bash
tail -100 logs/latest.log | grep ERROR
```

### 问题 4: 性能下降

**症状**：服务器 TPS 下降，延迟增加

**可能原因**：
- MySQL 连接池配置不当
- 索引未正确创建

**解决方案**：

#### 检查索引
```sql
-- MySQL
SHOW INDEX FROM starcore_nation_state;

-- SQLite
.schema starcore_nation_state
```

#### 调整连接池
```yaml
# config.yml
database:
  pool:
    maximum-pool-size: 10  # 增加到 10
    minimum-idle: 2        # 最小空闲连接
```

---

## 回滚方案

如果升级失败，需要回滚到旧版本：

### 1. 停止服务器

### 2. 恢复旧插件
```bash
cd plugins/
mv starcore-0.1.0-SNAPSHOT.jar starcore-0.1.0-SNAPSHOT.jar.failed
mv starcore-0.0.9.jar.old starcore-0.0.9.jar
```

### 3. 恢复数据库备份

#### SQLite
```bash
cd plugins/StarCore/
mv starcore.db starcore.db.new
cp starcore.db.backup-20260617 starcore.db
```

#### MySQL
```bash
mysql -u starcore_user -p starcore_db < starcore_backup_20260617.sql
```

### 4. 恢复 Properties 文件
```bash
rm -rf plugins/StarCore/persistence/
cp -r plugins/StarCore/persistence.backup-20260617/ plugins/StarCore/persistence/
```

### 5. 启动服务器

---

## 性能优化建议

### 1. MySQL 配置

**推荐 my.cnf 配置**：
```ini
[mysqld]
# InnoDB 缓冲池（设置为物理内存的 70%）
innodb_buffer_pool_size = 2G

# 连接数
max_connections = 150

# 查询缓存
query_cache_size = 64M
query_cache_type = 1

# 日志
log_error = /var/log/mysql/error.log
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2
```

### 2. SQLite 优化

StarCore 已自动优化：
- WAL 模式（并发读写）
- 外键约束
- 5 秒繁忙超时

无需手动配置。

### 3. 监控工具

**推荐插件**：
- Spark (性能分析)
- Plan (查询分析)

**监控命令**：
```
/spark profiler start
/spark profiler stop
```

---

## 后续维护

### 定期备份

**推荐备份策略**：
- 每日自动备份
- 保留 7 天历史

**Cron 任务示例**：
```bash
# 每天凌晨 3 点备份
0 3 * * * /home/minecraft/backup.sh
```

**backup.sh**：
```bash
#!/bin/bash
DATE=$(date +%Y%m%d)
cd /home/minecraft/server/plugins/StarCore

# SQLite 备份
cp starcore.db /backups/starcore-$DATE.db

# MySQL 备份
mysqldump -u starcore_user -p'password' starcore_db > /backups/starcore-$DATE.sql

# 删除 7 天前的备份
find /backups/ -name "starcore-*.db" -mtime +7 -delete
find /backups/ -name "starcore-*.sql" -mtime +7 -delete
```

### 监控迁移状态

定期检查迁移历史：
```sql
SELECT version, description, installed_on, success
FROM starcore_schema_history
ORDER BY installed_rank DESC;
```

---

## 支持与反馈

- **问题反馈**: https://github.com/addxiaoyi/starcore/issues
- **文档**: `/docs` 目录
- **Discord**: （如有）

---

## 附录

### 附录 A: 完整表清单

| 表名 | 用途 | 预计行数 |
|------|------|---------|
| `starcore_nation_state` | 国家状态 | ~100 |
| `starcore_diplomacy_state` | 外交关系 | ~500 |
| `starcore_policy_state` | 政策状态 | ~200 |
| `starcore_resource_state` | 资源库存 | ~50 |
| `starcore_technology_state` | 科技状态 | ~30 |
| `starcore_treasury_state` | 国库状态 | ~100 |
| `starcore_war_state` | 战争状态 | ~20 |
| `starcore_officer_state` | 官员状态 | ~50 |
| `starcore_event_state` | 事件日志 | ~1000 |
| `starcore_resolution_state` | 决议状态 | ~100 |
| `starcore_territory_state` | 领地状态 | ~500 |
| `starcore_player_balance` | 玩家余额 | ~1000 |
| `starcore_economy_transactions` | 经济日志 | ~10000 |
| `starcore_map_chunks` | 地图区块 | ~50000 |
| `starcore_map_markers` | 地图标记 | ~100 |

### 附录 B: 迁移脚本内容

详见：
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/main/resources/db/migration/V2__map_module.sql`
- `src/main/resources/db/migration/V3__performance_indexes.sql`

---

**版本**: 1.0  
**更新日期**: 2026-06-17  
**作者**: StarCore Team
