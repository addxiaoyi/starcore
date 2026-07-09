# StarCore 插件未完成功能清单

> 生成时间: 2026-06-23
> 插件路径: `D:\qwq\项目\mapadd\starcore`

---

## 📊 功能完成度总览

| 模块 | 完成度 | 状态 |
|------|--------|------|
| 国家模块 (Nation) | 95% | ⚠️ |
| 战争模块 (War) | 60% | 🔴 |
| 国策模块 (Policy) | 90% | 🟡 |
| 科技模块 (Technology) | 90% | 🟡 |
| 军队模块 (Army) | 60% | 🔴 |
| 外交模块 (Diplomacy) | 100% | ✅ |
| 财政模块 (Treasury) | 100% | ✅ |
| 资源模块 (Resource) | 95% | ✅ |
| 官员模块 (Officer) | 100% | ✅ |
| 决议模块 (Resolution) | 100% | ✅ |
| 事件模块 (Event) | 100% | ✅ |
| 地图模块 (Map) | 100% | ✅ |
| GUI系统 | 85% | 🟡 |
| 社交系统 | 100% | ✅ |
| PvP系统 | 95% | ✅ |
| 传送系统 | 90% | 🟡 |
| 经济系统 | 95% | ✅ |
| 任务系统 (Quest) | 60% | 🔴 |
| 领地系统 | 100% | ✅ |

---

## 🔴 P0 - 核心功能缺失 (高优先级)

### 1. 军队单位系统 ⚠️ 最重要
**文件位置**: `src/main/java/dev/starcore/starcore/module/army/`

| 子功能 | 状态 | 说明 |
|--------|------|------|
| ArmyUnit 模型 | ✅ 存在 | 基本结构已有 |
| ArmyService 服务 | ✅ 存在 | 基础服务 |
| ArmyType 兵种 | ✅ 存在 | 兵种定义 |
| BattleCalculator 战斗计算 | ✅ 存在 | 战斗公式 |
| **实际战斗执行** | ❌ 缺失 | 缺少触发机制 |
| **军队训练** | ❌ 缺失 | 无法训练军队 |
| **军队部署** | ❌ 缺失 | 无法派驻领土 |
| **攻城战** | ❌ 缺失 | 缺少攻城玩法 |
| **战争GUI** | ⚠️ 部分 | ArmyManagementMenu 存在但不完整 |

**TODO**:
- [ ] 完善军队训练系统 (消耗资源生成军队单位)
- [ ] 实现军队部署到领土
- [ ] 实现自动战斗触发 (当敌国军队进入领土时)
- [ ] 完善攻城战流程
- [ ] 集成战争系统

---

### 2. 战争系统持久化
**文件位置**: `src/main/java/dev/starcore/starcore/module/war/WarStateStorage.java`

```java
// 行 28-61: 所有方法标记为 TODO
public class WarStateStorage {
    public void save(War war) {
        // TODO: 实现数据库持久化  ← 缺失
    }
    public Optional<War> load(WarId id) {
        // TODO: 从数据库加载  ← 缺失
    }
    public void delete(WarId id) {
        // TODO: 从数据库删除  ← 缺失
    }
}
```

**TODO**:
- [ ] 实现 `SqlWarStateStorage` 与数据库集成
- [ ] 测试战争状态持久化
- [ ] 验证战争恢复功能

---

### 3. Nation 模型数据填充
**文件位置**: `src/main/java/dev/starcore/starcore/module/nation/model/Nation.java:136-223`

```java
// 多个 get 方法返回硬编码假数据
public int getTerritoryCount() {
    return 0; // TODO: 实现领土计数逻辑
}
public double getBalance() {
    return 0.0; // TODO: 实现国库余额查询
}
public String getFoundedDate() {
    return "2024-01-01"; // TODO: 添加建国日期字段并格式化
}
public int getPolicyCount() {
    return 0; // TODO: 实现政策计数
}
public int getTechnologyCount() {
    return 0; // TODO: 实现科技计数
}
public int getAllyCount() {
    return 0; // TODO: 实现盟友计数
}
public int getWarCount() {
    return 0; // TODO: 实现战争计数
}
public double getTaxRate() {
    return 0.0; // TODO: 实现税率查询
}
public Location getCapitalLocation() {
    return null; // TODO: 实现首都位置查询
}
public Location getTownLocation() {
    return null; // TODO: 实现城镇位置查询
}
public List<String> getTownNames() {
    return Collections.emptyList(); // TODO: 实现城镇名称列表查询
}
```

**TODO**:
- [ ] 实现领土计数 (从 TerritoryModule 查询)
- [ ] 实现国库余额查询 (从 TreasuryModule)
- [ ] 添加建国日期字段到 Nation 模型
- [ ] 实现政策/科技计数
- [ ] 实现外交计数 (盟友/战争)
- [ ] 实现位置查询

---

## 🟡 P1 - 功能不完整 (中优先级)

### 4. GUI 菜单集成
**文件位置**: `src/main/java/dev/starcore/starcore/module/nation/gui/NationManagementMenuListener.java:168-198`

```java
// 多个菜单按钮尚未集成实际功能
case 48: // 财政管理
    // TODO: 集成财政模块的GUI  ← 缺失
    break;
case 50: // 领土管理
    // TODO: 显示领地列表  ← 缺失
    break;
case 52: // 国策
    // TODO: 集成国策模块的GUI  ← 缺失
    break;
case 53: // 科技
    // TODO: 集成科技模块的GUI  ← 缺失
    break;
case 54: // 外交
    // TODO: 集成外交模块的GUI  ← 缺失
    break;
```

**TODO**:
- [ ] 集成财政管理子菜单
- [ ] 集成领土列表显示
- [ ] 集成国策树 GUI
- [ ] 集成科技树 GUI
- [ ] 集成外交关系 GUI

---

### 5. TriumphNationMenu 成员详情
**文件位置**: `src/main/java/dev/starcore/starcore/module/nation/gui/TriumphNationMenu.java:222`

```java
// 点击成员图标后
// TODO: 打开成员详情菜单  ← 缺失
```

**TODO**:
- [ ] 实现成员详情菜单 (查看/修改权限/踢出)

---

### 6. 税收系统集成
**文件位置**: `src/main/java/dev/starcore/starcore/nation/tax/TaxCollectionService.java:151,159`

```java
// 需要集成 Vault 经济插件
// TODO: 实际实现需要集成Vault  ← 缺失
```

**TODO**:
- [ ] 集成 Vault Economy API
- [ ] 实现自动扣税
- [ ] 实现税款分配

---

### 7. 国策/科技 GUI
**完成度**: 90%

| 缺失项 | 状态 |
|--------|------|
| 国策树数据模型 | ✅ 完成 |
| 国策效果系统 | ✅ 完成 |
| **国策 GUI** | ⚠️ 需要集成到 TriumphNationMenu |
| 科技树数据模型 | ✅ 完成 |
| 科技效果系统 | ✅ 完成 |
| **科技 GUI** | ⚠️ 需要集成到 TriumphNationMenu |

**TODO**:
- [ ] 创建国策树 GUI 菜单
- [ ] 创建科技树 GUI 菜单
- [ ] 集成到主菜单导航

---

## 🟢 P2 - 待优化功能 (低优先级)

### 8. 任务系统 (Quest)
**文件位置**: `src/main/java/dev/starcore/starcore/quest/`

| 类 | TODO |
|----|------|
| `QuestService.java:188` | 集成经济系统 |
| `QuestService.java:207` | 集成声望系统 |
| `QuestService.java:210` | 集成称号系统 |
| `DailyQuestService.java:171` | 检查并扣除刷新费用 |
| `CommissionService.java:55` | 集成经济系统检查余额 |
| `CommissionService.java:147` | 集成经济系统 |
| `CommissionService.java:180` | 退还发布费用 |

**TODO**:
- [ ] 集成经济系统 (Vault)
- [ ] 实现声望系统
- [ ] 实现称号系统
- [ ] 实现任务奖励发放

---

### 9. PvP 统计持久化
**文件位置**:
- `src/main/java/dev/starcore/starcore/pvp/stats/PvPStatsService.java:129,140`
- `src/main/java/dev/starcore/starcore/pvp/duel/DuelService.java:316`

```java
// TODO: 实际保存到数据库或文件
// TODO: 实际从数据库或文件加载
```

**TODO**:
- [ ] 实现 PvP 统计持久化到数据库
- [ ] 实现决斗记录持久化

---

### 10. 跨服通信
**文件位置**: `src/main/java/dev/starcore/starcore/crossserver/`

| 类 | 缺失依赖 |
|----|----------|
| `RedisTransport.java` | Redis 客户端依赖 |
| `RabbitMQTransport.java` | RabbitMQ 客户端依赖 |

**TODO**:
- [ ] 添加 Redis 客户端依赖 (如 Jedis/Lettuce)
- [ ] 添加 RabbitMQ 客户端依赖
- [ ] 实现实际的消息传输

---

### 11. City 系统
**文件位置**: `src/main/java/dev/starcore/starcore/city/command/CityCommand.java`

| 行号 | TODO |
|------|------|
| 140 | 需要 Nation ID |
| 368 | 从玩家扣款 (需要 Vault) |
| 388 | 给玩家加钱 (需要 Vault) |
| 409 | 设置 City 出生点 |
| 421 | 传送到 City 出生点 |

**TODO**:
- [ ] 实现 City 与 Nation 关联
- [ ] 集成 Vault 经济
- [ ] 实现传送功能

---

### 12. 工具类 TODO

#### MessageUtil.java
| 行号 | TODO |
|------|------|
| 104 | 获取 Nation 所有在线成员并发送消息 |
| 114 | 获取 Clan 所有在线成员并发送消息 |
| 213 | 根据 Nation/Clan 添加前缀 |

#### PermissionUtil.java
| 行号 | TODO |
|------|------|
| 199 | 从 NationRankManager 检查权限 |
| 207 | 从 NationManager 检查权限 |
| 215 | 从 ClanManager 检查权限 |
| 223 | 从 CityManager 检查权限 |

#### TitleDisplayService.java
| 行号 | TODO |
|------|------|
| 224 | 从 NationModule 获取国家名称 |
| 225 | 从 RankingModule 获取排名 |

**TODO**:
- [ ] 完善 MessageUtil 广播功能
- [ ] 完善 PermissionUtil 权限检查
- [ ] 完善 TitleDisplayService 数据获取

---

### 13. 领地权限管理
**文件位置**: `src/main/java/dev/starcore/starcore/territory/`

| 行号 | TODO |
|------|------|
| `PermissionTemplate.java:71,81,152,163` | UnsupportedOperationException (设计如此) |
| `TerritoryPermissionManager.java:145` | 从 NationManager 获取权限配置 |
| `TerritoryProtectionListener.java:179` | 从某处获取权限管理器 |

**TODO**:
- [ ] 实现领地权限管理器与 NationModule 集成

---

### 14. 经济事件监听
**文件位置**: `src/main/java/dev/starcore/starcore/nation/listener/EconomyEventListener.java`

| 行号 | TODO |
|------|------|
| 30 | 获取玩家的 Nation |
| 71 | 检查 Nation 是否还有在线成员 |
| 81 | 保存经济数据到数据库 |
| 110 | 从 NationManager 获取 |
| 115 | 检查 Nation 是否有在线成员 |

**TODO**:
- [ ] 实现经济事件与 Nation 关联

---

### 15. 外交命令
**文件位置**: `src/main/java/dev/starcore/starcore/nation/command/NationDiplomacyCommand.java`

| 行号 | TODO |
|------|------|
| 83 | 获取玩家的 Nation ID |
| 90 | 获取目标 Nation ID |
| 274,279,284,289 | 从 NationManager 获取数据 |
| 242 | 广播到 Nation 所有在线成员 |

**TODO**:
- [ ] 实现外交命令与 NationModule 完整集成

---

### 16. 税收命令
**文件位置**: `src/main/java/dev/starcore/starcore/nation/command/TaxCommand.java`

| 行号 | TODO |
|------|------|
| 74 | 获取玩家的 Nation |
| 81 | 检查权限 |
| 92 | 设置 Nation 经济的税率 |
| 227,232,237,242 | 数据获取和权限检查 |

**TODO**:
- [ ] 实现税收命令完整功能

---

### 17. 其他模块

#### 事件系统
**文件位置**: `src/main/java/dev/starcore/starcore/event/random/`

| 文件 | TODO |
|------|------|
| `NationStateTrigger.java:36` | 集成国家系统API |
| `RandomEvent.java:173` | 实现需求检查逻辑 |
| `EconomyEffect.java:32` | 集成经济系统API |

#### 占位符扩展
**文件位置**: `src/main/java/dev/starcore/starcore/title/StarCorePlaceholderExpansion.java`

| 行号 | TODO |
|------|------|
| 95 | 集成成就系统 |
| 309 | 从统计系统获取数据 |

#### 地图数据
**文件位置**: `src/main/java/dev/starcore/starcore/webmap/MapDataProvider.java:138`

```java
// TODO: 实际实现需要更精确的位置
```

---

## 📋 汇总清单

### 高优先级 (P0) - 建议优先完成
- [ ] **军队单位系统完善** (战争核心玩法)
- [ ] **战争状态持久化** (数据完整性)
- [ ] **Nation 模型数据填充** (GUI 显示真实数据)

### 中优先级 (P1) - 提升功能完整性
- [ ] GUI 菜单集成 (财政/领土/国策/科技/外交)
- [ ] TriumphNationMenu 成员详情菜单
- [ ] 税收系统 Vault 集成
- [ ] 国策/科技 GUI 菜单

### 低优先级 (P2) - 优化体验
- [ ] 任务系统经济集成
- [ ] PvP 统计持久化
- [ ] 跨服通信 (Redis/RabbitMQ)
- [ ] City 系统完善
- [ ] 工具类完善 (MessageUtil/PermissionUtil)

---

## 📈 预估工作量

| 优先级 | 任务数 | 预估工时 |
|--------|--------|----------|
| P0 | 3 | 3-5 天 |
| P1 | 4 | 2-3 天 |
| P2 | 8 | 3-4 天 |
| **总计** | **15** | **8-12 天** |

---

*文档自动生成 | StarCore Plugin*
