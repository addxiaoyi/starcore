# StarCore 待完成功能清单

更新时间: 2026/06/24

---

## 一、TODO 标记（按模块分类）

### 🔴 高优先级（影响核心功能）

| 文件 | 行 | 说明 |
|------|-----|------|
| WarStateStorage.java | 28,36,44,53,61 | 战争状态持久化未实现 |
| WarConfig.java | 26 | 从配置文件加载未实现 |
| DuelService.java | 316 | 决斗数据未保存到数据库 |
| RedisTransport.java | 38,73,98,120 | Redis 跨服通信未实现 |
| RabbitMQTransport.java | 43,85,110,134 | RabbitMQ 跨服通信未实现 |

### 🟡 中优先级（影响系统集成）

| 文件 | 行 | 说明 |
|------|-----|------|
| PermissionUtil.java | 199-223 | 权限检查返回临时值 |
| MessageUtil.java | 104,114,213 | 国家/公会成员消息未实现 |
| TitleDisplayService.java | 246 | 排名数据返回占位符 |
| StarCorePlaceholderExpansion.java | 315 | 占位符数据未实现 |
| EconomyEffect.java | 32 | 经济效果未集成 |

### 🟢 低优先级（辅助功能）

| 文件 | 行 | 说明 |
|------|-----|------|
| CityCommand.java | 145 | 获取玩家 Nation ID |
| NationStateTrigger.java | 36 | 国家状态触发器未集成 |
| QuestService.java | 188,207,210 | 任务系统经济/声望/称号集成 |
| CommissionService.java | 55,147,180 | 委托系统经济集成 |
| DailyQuestService.java | 171 | 刷新费用检查 |

---

## 二、临时硬编码值

| 文件 | 行 | 临时值 | 应替换为 |
|------|-----|--------|----------|
| TaxCommand.java | 228,233,238 | UUID.randomUUID(), null, true | NationManager |
| NationDiplomacyCommand.java | 275,280,285 | 临时 UUID 和名称 | NationManager |
| PermissionUtil.java | 200,208,216,224 | true/false | NationRankManager |
| TerritoryProtectionListener.java | 180 | 临时权限管理器 | 实际权限系统 |
| Commission.java | 164 | "玩家" | 实际玩家名称 |

---

## 三、未实现的空方法

| 文件 | 方法 | 说明 |
|------|------|------|
| WarStateStorage | load(), save(), delete(), query() | 数据库操作全部空实现 |
| WarehouseGUI | openPermissionManager(), handleDeposit(), handleWithdraw() | 仓库 GUI 逻辑未实现 |
| WarehouseUpgradeService | 余额检查、费用扣除 | 经济系统未集成 |

---

## 四、需要集成的模块

```
待集成模块:
├── 经济系统 (EconomyService)
│   ├── QuestService.questReward()
│   ├── CommissionService
│   ├── WarehouseUpgradeService
│   ├── DailyQuestService
│   └── EconomyEffect
├── 国家系统 (NationModule)
│   ├── PermissionUtil
│   ├── MessageUtil
│   ├── TaxCommand
│   ├── NationDiplomacyCommand
│   └── NationStateTrigger
├── 排名系统 (RankingModule)
│   └── TitleDisplayService
└── 跨服通信
    ├── RedisTransport
    └── RabbitMQTransport
```

---

## 五、GUI 占位符

| 文件 | 行 | 说明 |
|------|-----|------|
| NationManagementMenuListener.java | 168-198 | 财政/领地/国策/科技/外交 GUI 待集成 |
| WarehouseGUI.java | 174,281,308 | 权限管理和存取逻辑未实现 |

---

## 六、Nation 系统 API 集成详情

### EconomyEventListener.java
| 行号 | 说明 |
|------|------|
| 30 | 获取玩家的 Nation |
| 71 | 检查 Nation 是否还有在线成员 |
| 81 | 保存经济数据到数据库 |
| 110 | 从 NationManager 获取 |
| 115 | 检查 Nation 是否有在线成员 |

### TaxCommand.java
| 行号 | 说明 |
|------|------|
| 74 | 获取玩家的 Nation |
| 81 | 检查权限 |
| 92 | 设置 Nation 经济的税率 |
| 227 | 从 NationManager 获取 |
| 232 | 从 NationManager 获取 |
| 237 | 检查 Nation 权限 |
| 242 | 广播到 Nation 所有在线成员 |

### NationDiplomacyCommand.java
| 行号 | 说明 |
|------|------|
| 83 | 获取玩家的 Nation ID |
| 90 | 获取目标 Nation ID |
| 274 | 从 NationManager 获取 |
| 279 | 从 NationManager 获取 |
| 284 | 从 NationManager 获取 |
| 289 | 广播到 Nation 所有在线成员 |
| 304 | 返回所有 Nation 名称 |

---

## 七、开发优先级建议

### 第一阶段（核心）
1. WarStateStorage 数据库持久化
2. NationManager API 集成
3. PermissionUtil 权限检查实现

### 第二阶段（系统集成）
4. MessageUtil 成员消息系统
5. EconomyService 经济系统集成
6. QuestService 任务系统完善

### 第三阶段（跨服功能）
7. Redis/RabbitMQ 跨服通讯
8. DuelService 数据持久化

### 第四阶段（体验优化）
9. GUI 菜单完善
10. PlaceholderAPI 占位符实现
11. 剩余占位符清理
