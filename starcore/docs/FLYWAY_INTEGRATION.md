# Flyway 数据库迁移集成指南

## 概述

StarCore 已集成 Flyway 数据库迁移框架，用于自动化管理数据库架构的版本化演进。

## 为什么需要数据库迁移？

### 问题

**之前的方式**：
- 每个模块手动创建表 (`CREATE TABLE IF NOT EXISTS`)
- 无版本跟踪
- 无法回滚
- 升级时可能遗漏表结构变更
- 难以在团队间同步数据库状态

### 解决方案

**Flyway 提供**：
- ✅ 版本化迁移脚本
- ✅ 自动检测并执行新迁移
- ✅ 迁移历史跟踪
- ✅ 数据库状态验证
- ✅ 团队协作友好

---

## 工作原理

### 1. 迁移脚本命名规范

```
src/main/resources/db/migration/
├── V1__initial_schema.sql          # 初始架构
├── V2__map_module.sql               # 地图模块表
├── V3__add_nation_alliances.sql     # 联盟系统
└── V4__performance_indexes.sql      # 性能索引
```

**命名规则**：
- `V{版本号}__{描述}.sql`
- 版本号必须递增（V1, V2, V3...）
- 描述使用下划线连接，简短清晰
- SQL 文件只能执行一次（幂等性）

### 2. 迁移表

Flyway 自动创建 `starcore_schema_history` 表：

```sql
SELECT * FROM starcore_schema_history;

+----------------+----------------------+---------------------+
| installed_rank | version              | description         |
+----------------+----------------------+---------------------+
| 1              | 1                    | initial schema      |
| 2              | 2                    | map module          |
+----------------+----------------------+---------------------+
```

### 3. 自动执行

插件启动时，Flyway 会：
1. 检查 `starcore_schema_history` 表
2. 对比 `db/migration/` 目录中的脚本
3. 执行所有未运行的新迁移
4. 记录执行历史

---

## 已有迁移脚本

### V1__initial_schema.sql

**创建的表**：

| 表名 | 用途 |
|------|------|
| `starcore_nation_state` | 国家状态 |
| `starcore_diplomacy_state` | 外交关系 |
| `starcore_policy_state` | 政策状态 |
| `starcore_resource_state` | 资源状态 |
| `starcore_technology_state` | 科技状态 |
| `starcore_treasury_state` | 国库状态 |
| `starcore_war_state` | 战争状态 |
| `starcore_officer_state` | 官员状态 |
| `starcore_event_state` | 事件日志 |
| `starcore_resolution_state` | 决议状态 |
| `starcore_territory_state` | 领地状态 |
| `starcore_player_balance` | 玩家余额 |
| `starcore_economy_transactions` | 经济事务日志 |

### V2__map_module.sql

**创建的表**：

| 表名 | 用途 |
|------|------|
| `starcore_map_chunks` | 地图区块数据 |
| `starcore_map_markers` | 地图标记 |

---

## 添加新迁移

### 场景：添加联盟系统

#### 步骤 1: 创建迁移脚本

```sql
-- src/main/resources/db/migration/V3__add_nation_alliances.sql

-- 联盟表
CREATE TABLE IF NOT EXISTS starcore_alliances (
    alliance_id VARCHAR(36) NOT NULL PRIMARY KEY,
    alliance_name VARCHAR(100) NOT NULL,
    leader_nation_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    description TEXT
);

-- 联盟成员表
CREATE TABLE IF NOT EXISTS starcore_alliance_members (
    alliance_id VARCHAR(36) NOT NULL,
    nation_id VARCHAR(36) NOT NULL,
    joined_at BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (alliance_id, nation_id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_alliance_leader 
    ON starcore_alliances(leader_nation_id);

CREATE INDEX IF NOT EXISTS idx_member_nation 
    ON starcore_alliance_members(nation_id);
```

#### 步骤 2: 重启插件

插件启动时会自动检测并执行新迁移：

```
[INFO] 开始数据库迁移检查...
[INFO] ✅ 数据库迁移完成: 执行了 1 个迁移脚本 (目标版本: 3)
```

#### 步骤 3: 验证

```sql
SELECT * FROM starcore_schema_history WHERE version = '3';
```

---

## 数据迁移示例

### 场景：重构现有表结构

假设你想将 `starcore_nation_state` 从 key-value 结构改为关系型表。

#### V4__refactor_nation_state.sql

```sql
-- 创建新表
CREATE TABLE IF NOT EXISTS starcore_nations (
    nation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    nation_name VARCHAR(100) NOT NULL,
    capital_city VARCHAR(100),
    government_type VARCHAR(50),
    treasury_balance DECIMAL(20, 2) DEFAULT 0.00,
    created_at BIGINT NOT NULL,
    last_updated BIGINT NOT NULL
);

-- 迁移数据（从 Properties 格式解析）
-- 注意：这需要根据实际数据格式编写

-- 保留旧表作为备份（可选）
-- ALTER TABLE starcore_nation_state RENAME TO starcore_nation_state_backup;
```

**重要**：
- 数据迁移脚本要小心编写
- 先在测试环境验证
- 考虑保留备份表

---

## 命令行工具

### 查看当前版本

```java
DatabaseMigrationService migration = databaseService.migrationService().orElseThrow();
String version = migration.getCurrentVersion();
System.out.println("数据库版本: " + version);
```

### 验证数据库状态

```java
boolean valid = migration.validate();
if (!valid) {
    System.err.println("数据库状态与迁移脚本不匹配！");
}
```

---

## 多环境支持

### SQLite vs MySQL

Flyway 自动检测数据库类型并适配 SQL 语法：

**通用写法**：
```sql
-- 使用标准 SQL
CREATE TABLE IF NOT EXISTS starcore_example (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
```

**特定数据库**：
```sql
-- MySQL 特定
CREATE TABLE IF NOT EXISTS starcore_example (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- MySQL 自增
    name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SQLite 特定
CREATE TABLE IF NOT EXISTS starcore_example (
    id INTEGER PRIMARY KEY AUTOINCREMENT,  -- SQLite 自增
    name VARCHAR(100) NOT NULL
);
```

**建议**：优先使用通用 SQL，必要时创建特定版本：
- `V3__add_alliances.sql` （通用）
- `V3__add_alliances_mysql.sql` （MySQL 特定）

---

## 配置选项

### config.yml

```yaml
database:
  enabled: true
  type: sqlite  # 或 mysql
  fail-fast: false  # 迁移失败时是否停止启动
  
  # Flyway 配置（高级）
  migration:
    baseline-on-migrate: true   # 对现有数据库自动 baseline
    validate-on-migrate: true   # 迁移前验证
    out-of-order: false         # 禁止乱序执行
```

---

## 最佳实践

### ✅ DO

1. **每个迁移脚本单一职责**
   ```
   ✅ V3__add_alliance_table.sql
   ✅ V4__add_alliance_indexes.sql
   ❌ V3__add_alliance_and_fix_nations.sql
   ```

2. **使用事务**
   ```sql
   BEGIN TRANSACTION;
   
   CREATE TABLE ...;
   INSERT INTO ...;
   
   COMMIT;
   ```

3. **向后兼容**
   - 添加列时设置默认值
   - 避免删除正在使用的列

4. **测试先行**
   - 在测试环境先执行
   - 验证数据完整性

### ❌ DON'T

1. **不要修改已执行的迁移**
   ```
   ❌ 修改 V1__initial_schema.sql（已执行）
   ✅ 创建 V5__fix_initial_schema.sql（新迁移）
   ```

2. **不要跳过版本号**
   ```
   ❌ V1 → V3 → V7
   ✅ V1 → V2 → V3
   ```

3. **不要在迁移中使用插件代码**
   ```sql
   ❌ -- 调用 Java 方法
   ✅ -- 纯 SQL 脚本
   ```

---

## 故障排查

### 问题 1: 迁移失败导致插件无法启动

**解决方案**：

1. 设置 `fail-fast: false`（config.yml）
2. 手动修复数据库问题
3. 重启插件

### 问题 2: 迁移脚本已修改但未重新执行

**原因**：Flyway 通过校验和检测脚本变更

**解决方案**：
```sql
-- 删除历史记录（谨慎操作！）
DELETE FROM starcore_schema_history WHERE version = '3';

-- 或者创建新版本
-- V4__fix_v3_migration.sql
```

### 问题 3: 数据库状态验证失败

**诊断**：
```sql
SELECT * FROM starcore_schema_history 
ORDER BY installed_rank DESC;
```

**解决方案**：
1. 检查是否手动修改了数据库
2. 运行 `migration.validate()` 查看详细错误
3. 必要时重置数据库（备份先！）

---

## 回滚策略

Flyway **不支持自动回滚**，需要手动处理：

### 方法 1: 创建撤销迁移

```sql
-- V5__undo_alliances.sql
DROP TABLE IF EXISTS starcore_alliance_members;
DROP TABLE IF EXISTS starcore_alliances;
```

### 方法 2: 备份恢复

```bash
# 备份数据库
cp starcore.db starcore.db.backup

# 出问题后恢复
cp starcore.db.backup starcore.db
```

---

## 性能考虑

### 大数据迁移

对于大表（>10万行），避免阻塞：

```sql
-- ❌ 一次性迁移（可能超时）
UPDATE starcore_player_balance SET new_column = 0;

-- ✅ 分批迁移（Java 代码中实现）
-- 或者使用触发器延迟迁移
```

### 索引创建

```sql
-- 大表创建索引可能很慢，考虑：
CREATE INDEX CONCURRENTLY idx_player_balance 
    ON starcore_player_balance(balance);
-- 注意：CONCURRENTLY 仅 PostgreSQL 支持
```

---

## 相关文档

- [存储层抽象指南](STORAGE_LAYER_ABSTRACTION.md)
- [数据库配置说明](DATABASE_CONFIGURATION.md)
- [Flyway 官方文档](https://flywaydb.org/documentation/)

---

## 总结

✅ **集成完成**：
- Flyway 已添加到 pom.xml
- 初始迁移脚本已创建（V1, V2）
- DatabaseService 已集成自动迁移

✅ **使用简单**：
- 创建 `V{N}__{description}.sql` 文件
- 重启插件自动执行
- 无需手动管理表结构

✅ **生产就绪**：
- 支持 SQLite 和 MySQL
- 自动 baseline 现有数据库
- 失败降级机制
