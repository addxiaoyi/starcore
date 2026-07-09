# 数据库结构说明
# StarCore Warehouse System Database Schema

## 仓库表 (starcore_warehouses)
```sql
CREATE TABLE IF NOT EXISTS starcore_warehouses (
    warehouse_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    capacity INT NOT NULL,
    created_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_owner (owner_id),
    INDEX idx_type (type),
    INDEX idx_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 仓库物品表 (starcore_warehouse_items)
```sql
CREATE TABLE IF NOT EXISTS starcore_warehouse_items (
    item_id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL,
    slot INT NOT NULL,
    material VARCHAR(50) NOT NULL,
    amount INT NOT NULL DEFAULT 1,
    item_data TEXT,  -- JSON格式存储ItemStack完整数据
    deposit_time BIGINT NOT NULL,
    deposited_by VARCHAR(36),
    FOREIGN KEY (warehouse_id) REFERENCES starcore_warehouses(warehouse_id) ON DELETE CASCADE,
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_slot (warehouse_id, slot),
    UNIQUE KEY uk_warehouse_slot (warehouse_id, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 仓库日志表 (starcore_warehouse_logs)
```sql
CREATE TABLE IF NOT EXISTS starcore_warehouse_logs (
    log_id VARCHAR(36) PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36),
    player_name VARCHAR(50),
    action VARCHAR(30) NOT NULL,
    timestamp BIGINT NOT NULL,
    item_info VARCHAR(200),
    amount INT DEFAULT 0,
    ip_address VARCHAR(45),
    is_remote_access BOOLEAN NOT NULL DEFAULT FALSE,
    additional_info TEXT,
    FOREIGN KEY (warehouse_id) REFERENCES starcore_warehouses(warehouse_id) ON DELETE CASCADE,
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_player (player_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 仓库权限表 (starcore_warehouse_permissions)
```sql
CREATE TABLE IF NOT EXISTS starcore_warehouse_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    warehouse_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    permission VARCHAR(20) NOT NULL,
    granted_by VARCHAR(36),
    granted_time BIGINT NOT NULL,
    FOREIGN KEY (warehouse_id) REFERENCES starcore_warehouses(warehouse_id) ON DELETE CASCADE,
    UNIQUE KEY uk_warehouse_player (warehouse_id, player_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 升级进程表 (starcore_warehouse_upgrades)
```sql
CREATE TABLE IF NOT EXISTS starcore_warehouse_upgrades (
    warehouse_id VARCHAR(36) PRIMARY KEY,
    player_id VARCHAR(36) NOT NULL,
    from_level INT NOT NULL,
    to_level INT NOT NULL,
    start_time BIGINT NOT NULL,
    completion_time BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    FOREIGN KEY (warehouse_id) REFERENCES starcore_warehouses(warehouse_id) ON DELETE CASCADE,
    INDEX idx_completion (completion_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 使用说明

### 1. 创建数据库
在MySQL中执行上述SQL语句创建所有必需的表。

### 2. 数据类型说明
- VARCHAR(36): UUID格式（带连字符）
- BIGINT: 时间戳（毫秒级）
- TEXT: JSON或大文本数据
- BOOLEAN: 布尔值（0/1）

### 3. 索引说明
- PRIMARY KEY: 主键索引
- UNIQUE KEY: 唯一键索引
- INDEX: 普通索引，用于加速查询
- FOREIGN KEY: 外键约束，保证数据一致性

### 4. 性能优化建议
- 定期清理过期日志（warehouse_logs表）
- 为频繁查询的字段添加索引
- 使用连接池管理数据库连接
- 对大型仓库考虑分表策略

### 5. 数据迁移
如果需要从旧版本迁移，请参考以下步骤：
1. 备份现有数据
2. 创建新表结构
3. 编写迁移脚本转换数据格式
4. 验证数据完整性
5. 删除旧表

### 6. 权限配置
确保MySQL用户具有以下权限：
- SELECT
- INSERT
- UPDATE
- DELETE
- CREATE
- INDEX
- REFERENCES
