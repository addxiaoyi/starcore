# StarCore 仓库系统 - README

## 功能概述

StarCore仓库系统是一个完整的仓库管理解决方案，提供以下核心功能：

### 1. 仓库类型
- **个人仓库 (PERSONAL)**: 玩家专属仓库，基础容量27格，最大10级
- **国家仓库 (NATION)**: 国家共享仓库，基础容量54格，最大15级
- **共享仓库 (SHARED)**: 多人共享仓库，基础容量36格，最大10级
- **高级仓库 (PREMIUM)**: 付费/VIP仓库，基础容量54格，最大20级

### 2. 远程访问
- 支持跨距离访问仓库
- 可配置最大访问距离
- 可设置访问费用
- 所有者和管理员免费访问

### 3. 容量升级
- 每级增加9格容量（1行）
- 升级需要金钱和材料
- 支持即时升级或延时升级
- 可付费加速升级进程

### 4. 访问日志
- 记录所有操作（存取、访问、升级等）
- 支持按时间、玩家、操作类型查询
- 自动清理过期日志
- 可导出日志文件

### 5. 权限管理
- 7级权限系统：NONE < VIEW < WITHDRAW < DEPOSIT < FULL < ADMIN < OWNER
- 细粒度权限控制
- 支持权限授予和撤销
- 权限变更记录日志

## 使用指南

### 命令列表

```
/warehouse open              - 打开您的默认仓库
/warehouse open <ID>         - 打开指定ID的仓库
/warehouse remote            - 打开远程访问GUI
/warehouse remote <ID>       - 直接远程访问指定仓库
/warehouse upgrade           - 打开升级界面
/warehouse logs              - 查看操作日志
/warehouse logs export       - 导出日志为文本
/warehouse logs summary [天数] - 显示日志摘要
/warehouse share <玩家> <权限> - 授予玩家访问权限
/warehouse revoke <玩家>     - 撤销玩家的访问权限
/warehouse list              - 列出您拥有的仓库
/warehouse list all          - 列出您可访问的所有仓库
/warehouse info              - 查看仓库详细信息
/warehouse create <类型> <名称> - 创建新仓库
/warehouse delete <ID>       - 删除仓库
/warehouse rename <名称>     - 重命名仓库
/warehouse lock              - 锁定仓库
/warehouse unlock            - 解锁仓库
```

### 权限节点

```
starcore.warehouse.use          - 使用基本仓库功能
starcore.warehouse.remote       - 使用远程访问
starcore.warehouse.upgrade      - 升级仓库
starcore.warehouse.share        - 共享仓库权限
starcore.warehouse.create       - 创建仓库
starcore.warehouse.delete       - 删除仓库
starcore.warehouse.admin        - 管理员功能
starcore.warehouse.premium      - 使用高级仓库
```

### GUI界面

1. **主界面**: 显示仓库内容，支持存取物品
2. **升级界面**: 显示升级需求和进度
3. **权限界面**: 管理仓库访问权限
4. **日志界面**: 查看操作历史记录
5. **远程访问界面**: 选择要访问的仓库

## 配置说明

### warehouse_config.yml

```yaml
warehouse:
  personal:
    base_capacity: 27          # 基础容量
    max_level: 10              # 最大等级
    upgrade_cost_multiplier: 1.5  # 升级费用倍率
    
  remote_access:
    enabled: true              # 启用远程访问
    max_distance: 1000         # 最大距离（-1无限制）
    cost_per_use: 100          # 每次使用费用
    
  logs:
    enabled: true              # 启用日志
    retention_days: 30         # 日志保留天数
    
  features:
    allow_sharing: true        # 允许共享
    auto_sort: true            # 自动整理
```

## 数据库结构

系统使用4个主要表：

1. **starcore_warehouses**: 仓库基本信息
2. **starcore_warehouse_items**: 存储的物品
3. **starcore_warehouse_logs**: 操作日志
4. **starcore_warehouse_permissions**: 访问权限

详细结构见 `database_schema.sql`

## 开发指南

### 核心类

- **StorageService**: 仓库服务核心，管理所有仓库
- **Warehouse**: 仓库实体类
- **RemoteAccessService**: 远程访问服务
- **WarehouseUpgradeService**: 升级服务
- **StorageLogService**: 日志服务

### 集成示例

```java
// 获取仓库服务
StorageService storageService = context.serviceRegistry()
    .require(StorageService.class);

// 创建仓库
Warehouse warehouse = storageService.createWarehouse(
    WarehouseType.PERSONAL,
    playerId,
    "我的仓库"
);

// 设置权限
storageService.setPermission(
    warehouse.getWarehouseId(),
    targetPlayerId,
    RemoteAccessPermission.FULL
);

// 升级仓库
storageService.getUpgradeService()
    .startUpgrade(player, warehouseId)
    .thenAccept(result -> {
        if (result.isSuccess()) {
            player.sendMessage("升级成功！");
        }
    });
```

### 扩展开发

1. **自定义仓库类型**: 扩展 `WarehouseType` 枚举
2. **自定义权限**: 扩展 `RemoteAccessPermission` 枚举
3. **自定义日志操作**: 扩展 `StorageLog.LogAction` 枚举
4. **自定义GUI**: 继承 `WarehouseGUI` 类

## API使用

### 查询仓库

```java
// 获取玩家的默认仓库
Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(playerId);

// 获取指定ID的仓库
Optional<Warehouse> opt = storageService.getWarehouse(warehouseId);

// 获取玩家可访问的所有仓库
List<Warehouse> warehouses = storageService.getAccessibleWarehouses(playerId);
```

### 物品操作

```java
// 添加物品
StorageItem item = new StorageItem(itemStack, playerId, slot);
warehouse.addItem(item);

// 移除物品
StorageItem removed = warehouse.removeItem(slot);

// 查找空槽位
int emptySlot = warehouse.findEmptySlot();
```

### 日志查询

```java
// 查询最近的日志
List<StorageLog> logs = storageService.getLogService()
    .getRecentLogs(warehouseId, 50);

// 使用过滤器查询
LogQueryFilter filter = LogQueryFilter.builder()
    .warehouseId(warehouseId)
    .playerId(playerId)
    .lastDays(7)
    .action(StorageLog.LogAction.DEPOSIT)
    .build();
    
List<StorageLog> filtered = storageService.getLogService()
    .queryLogs(filter);
```

## 性能优化

1. **异步操作**: 所有数据库操作都使用异步
2. **日志清理**: 自动清理过期日志，减少数据库负担
3. **缓存机制**: 仓库数据缓存在内存中
4. **批量操作**: 支持批量读写物品

## 故障排查

### 常见问题

1. **命令无法使用**: 检查plugin.yml中是否注册了命令
2. **GUI不显示**: 检查是否有权限和仓库是否锁定
3. **远程访问失败**: 检查距离限制和余额
4. **升级失败**: 检查材料和金钱是否足够

### 日志位置

- 插件日志: `plugins/StarCore/logs/`
- 错误日志: 服务器控制台

## 许可证

本系统是StarCore插件的一部分，遵循StarCore的许可证条款。

## 版本历史

- v1.0.0: 初始版本
  - 基础仓库功能
  - 远程访问
  - 容量升级
  - 访问日志

## 支持

如有问题或建议，请联系StarCore开发团队。
