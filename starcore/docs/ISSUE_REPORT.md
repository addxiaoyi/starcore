# StarCore 问题排查报告

生成时间: 2026-07-01

## 统计摘要

| 问题类别 | 高优先级 | 中优先级 | 低优先级 | 总计 |
|---------|---------|---------|---------|------|
| 硬编码消息 | 114+ | - | - | 2066处 |
| 国际化缺失 | - | 50+ | - | 50+ |
| 空消息发送 | - | - | 30+ | 30+ |
| 异常处理 | - | 20+ | - | 20+ |
| TODO未完成 | - | 8 | - | 8 |
| printStackTrace | - | - | 20+ | 20+ |
| UUID硬编码 | - | 2 | - | 2 |

---

## 高优先级问题

### 1. 硬编码消息 (2066处)

**问题描述**: 项目中有大量直接使用 `player.sendMessage("§...")` 发送硬编码消息，而不是通过消息服务获取。

**影响文件**:
- `achievement/AchievementService.java` - 成就奖励消息
- `ai/season/BattlePassService.java` - 战斗通行证消息
- `city/command/CityCommand.java` - 城市命令消息
- `government/command/*` - 政府系统命令消息
- `module/*/command/*.java` - 所有模块命令
- 等共114个文件

**示例代码**:
```java
// ❌ 错误 - 硬编码消息
player.sendMessage("§6§l★ 战斗通行证升级！");
player.sendMessage("§e等级: §a" + level);

// ✅ 正确 - 应该使用消息服务
player.sendMessage(messageService.getMessage("battlepass.upgrade", level));
```

**建议修复**: 创建统一的 `MessageService`，所有消息都通过配置获取。

---

### 2. 空消息发送 (30+处)

**问题描述**: 发送空消息 `player.sendMessage("")` 而不是使用正确的分隔符。

**位置**:
- `city/command/CityCommand.java` - 351, 355, 364, 505, 520行
- `command/MainCommandHandler.java` - 106, 113, 118, 125行
- `government/command/*.java` - 多个命令文件
- 等共30+处

**建议修复**: 使用消息服务获取正确的分隔符或空白消息。

---

### 3. TODO未完成 (8处)

**问题描述**: 代码中有TODO标记但未实现的功能。

**位置**:
1. `module/officer/gui/OfficerGuiConfig.java:135`
   - TODO: 实现从 officer-gui.yml 加载配置

2. `module/lease/LeaseServiceImpl.java:261`
   - TODO: 如果有 TreasuryService，实现租金转移

3. `module/army/mercenary/command/MercenaryCommand.java:163`
   - TODO: 实现通过名称查找玩家

4. `module/army/mercenary/command/MercenaryCommand.java:553`
   - TODO: 实现续约请求发送给雇主

---

## 中优先级问题

### 4. 异常处理问题 (20+处)

**问题描述**: 使用 `e.printStackTrace()` 而不是正确记录日志。

**位置**:
- `event/random/effect/*.java` - 随机事件效果
- `ranking/database/MySQLRankingDatabase.java` - 排行榜数据库
- `integration/vault/VaultIntegration.java` - Vault集成
- 等共20+处

**示例**:
```java
// ❌ 错误
e.printStackTrace();

// ✅ 正确
getLogger().warning("操作失败: " + e.getMessage());
getLogger().severe("严重错误", e);
```

---

### 5. 国际化消息键问题

**问题描述**: 消息应该使用国际化键而不是直接硬编码。

**当前使用**:
```java
// ❌ 错误
msg("command.event.message.treasury-deposit", ...)

// ✅ 正确 (需要确保语言文件存在)
msg("command.event.message.treasury-deposit")
```

**需要检查语言文件**: `lang/messages_zh_cn.yml`

---

### 6. UUID硬编码 (2处)

**问题描述**: 使用硬编码的UUID。

**位置**:
- `social/simulation/GossipVerificationService.java:560`
  ```java
  new UUID(0L, 0L), // Console UUID
  ```
- `social/simulation/GossipVerificationService.java:585`
  ```java
  new UUID(0L, 0L), // Console UUID
  ```

**建议**: 应该使用 `Bukkit.getConsoleSender().getUniqueId()` 或常量定义。

---

## 低优先级问题

### 7. System.out/err 使用

**问题描述**: 使用 `System.out.println` 而不是日志框架。

**位置**:
- `core/StarCoreBanner.java` - 横幅显示 (可接受)
- `core/database/DataConsistencyValidatorCommand.java:196` - 调试输出

**建议**: 生产环境应使用日志框架。

---

### 8. 可能的空指针风险

**问题描述**: 对 Optional 调用 `.get()` 后直接使用。

**示例**:
```java
// ⚠️ 潜在风险
dummyOpt.get().getLocation()
npcOpt.get().getName()

// ✅ 安全写法
dummyOpt.map(Dummy::getLocation).orElse(null)
dummyOpt.map(Dummy::getName).orElse("Unknown")
```

---

## 建议修复优先级

### P0 - 必须修复
1. ✅ 硬编码消息统一化 (创建 MessageService)
2. ✅ 空消息发送修复
3. ✅ TODO未完成功能实现或标记

### P1 - 建议修复
1. 异常处理改用日志框架
2. UUID硬编码替换
3. Optional 安全性检查

### P2 - 可选优化
1. System.out 替换
2. 代码格式化
3. 注释完善

---

## 修复建议

### 1. 创建 MessageService

```java
public interface MessageService {
    String getMessage(String key, Object... args);
    Component getMessageComponent(String key, Object... args);
    String getPrefix();
}
```

### 2. 批量替换硬编码消息

使用 IDE 的正则搜索替换功能：
- 搜索: `player\.sendMessage\("§`
- 替换: 通过 MessageService

### 3. 创建迁移脚本

为每个模块创建消息迁移脚本，将硬编码消息添加到语言文件。

---

## 附录: 问题文件列表

<details>
<summary>点击展开完整文件列表</summary>

### 硬编码消息文件 (114个)
- achievement/AchievementService.java
- ai/bot/AIBotService.java
- ai/season/BattlePassService.java
- city/command/CityCommand.java
- command/MainCommandHandler.java
- dummy/TrainingDummyManager.java
- essentials/home/HomeService.java
- government/command/*.java
- module/alliance/*.java
- module/diplomacy/*.java
- module/military/*.java
- module/nation/*.java
- module/resource/*.java
- module/technology/*.java
- module/war/*.java
- npc/CustomNPCManager.java
- social/simulation/*.java
- storage/WarehouseCommand.java
- ... (更多)

</details>