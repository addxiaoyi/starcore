# StarCore 数据库架构

## 概述

StarCore 使用统一的 key-value 状态存储 + 玩家数据表的设计模式。

**存储后端**: MySQL (生产) / SQLite (开发)

## 表结构

### 状态表 (State Tables)

所有业务模块使用统一的 key-value 结构存储状态：

```sql
CREATE TABLE starcore_{module}_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);
```

| 模块 | 表名 | 用途 |
|------|------|------|
| 国家 | `starcore_nation_state` | 国家元数据 |
| 外交 | `starcore_diplomacy_state` | 国家间关系 |
| 政策 | `starcore_policy_state` | 激活的政策 |
| 资源 | `starcore_resource_state` | 资源产地 |
| 科技 | `starcore_technology_state` | 科技进度 |
| 国库 | `starcore_treasury_state` | 国家资金 |
| 战争 | `starcore_war_state` | 战争状态 |
| 官员 | `starcore_officer_state` | 官职任命 |
| 事件 | `starcore_event_state` | 随机事件 |
| 决议 | `starcore_resolution_state` | 投票决议 |
| 领地 | `starcore_territory_state` | 领土 claiming |

### 玩家数据表

#### 玩家余额表

```sql
CREATE TABLE starcore_player_balance (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
    last_updated BIGINT NOT NULL
);

CREATE INDEX idx_balance ON starcore_player_balance(balance);
```

#### 经济事务日志

```sql
CREATE TABLE starcore_economy_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    balance_before DECIMAL(20, 2) NOT NULL,
    balance_after DECIMAL(20, 2) NOT NULL,
    reason VARCHAR(255),
    timestamp BIGINT NOT NULL
);

CREATE INDEX idx_player_transactions ON starcore_economy_transactions(player_uuid, timestamp);
CREATE INDEX idx_transaction_time ON starcore_economy_transactions(timestamp);
```

## Key 命名规范

### 国家数据 Key

```
nation:{nationId}:meta          # 国家元数据 (JSON)
nation:{nationId}:members       # 成员列表 (JSON)
nation:{nationId}:claims        # 领土区块列表 (JSON)
nation:{nationId}:officers      # 官员列表 (JSON)
nation:{nationId}:policy:{policyId}  # 激活的政策
nation:{nationId}:tech:{techId}      # 科技进度
nation:{nationId}:resource:{districtId}  # 资源产地
```

### 外交数据 Key

```
diplomacy:{nation1}:{nation2}   # 两国关系状态
diplomacy:pending:{nationId}    # 待处理外交请求
```

### 战争数据 Key

```
war:active:{warId}              # 活跃战争
war:history:{nation1}:{nation2}  # 战争历史
```

### 领土数据 Key

```
territory:chunk:{world}:{x}:{z}     # 区块归属
territory:selection:{playerId}      # 玩家选区
```

## 数据格式

### 国家元数据 (JSON)

```json
{
  "id": "uuid",
  "name": "国家名称",
  "leaderId": "leader-uuid",
  "governmentType": "MONARCHY",
  "createdAt": 1234567890,
  "color": "#FF0000",
  "capital": "world,x,z",
  "population": 10
}
```

### 成员列表 (JSON)

```json
{
  "members": [
    {"uuid": "xxx", "role": "MEMBER", "joinedAt": 123},
    {"uuid": "xxx", "role": "OFFICER", "joinedAt": 123, "title": "大将军"}
  ]
}
```

### 外交关系 (JSON)

```json
{
  "nation1": "uuid",
  "nation2": "uuid",
  "relation": "ALLY",
  "since": 1234567890,
  "treaty": "optional treaty text"
}
```

## 迁移管理

迁移文件位于 `src/main/resources/db/migration/`:

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__initial_schema.sql` | 初始表结构 |
| V2 | `V2__map_module.sql` | 地图模块扩展 |
| V3 | `V3__performance_indexes.sql` | 性能索引 |

## 备份策略

建议定期备份以下数据：
- `starcore_nation_state` - 国家核心数据
- `starcore_player_balance` - 玩家经济数据
- `starcore_territory_state` - 领土数据

## 性能优化

1. **索引**: 在高频查询字段上建立索引
2. **分区**: 大数据量时按国家 ID 分区
3. **缓存**: Redis 缓存热点状态数据
4. **异步写入**: 非关键数据异步写入

## 数据库连接配置

```yaml
database:
  type: mysql  # mysql / sqlite
  host: localhost
  port: 3306
  database: starcore
  username: root
  password: ""
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 30000
```
