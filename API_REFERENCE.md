# 📚 StarCore API 参考文档

本文档提供 StarCore 所有公共 API 的详细说明。

---

## 📑 目录

- [IntegrationManager](#integrationmanager) - 统一集成管理器
- [Vault 经济系统](#vault-经济系统)
- [Jobs 职业系统](#jobs-职业系统)
- [mcMMO 技能系统](#mcmmo-技能系统)
- [MythicMobs 生物系统](#mythicmobs-生物系统)
- [Citizens NPC 系统](#citizens-npc-系统)
- [Typewriter 任务系统](#typewriter-任务系统)
- [Denizen 脚本系统](#denizen-脚本系统)
- [自定义 NPC](#自定义-npc)
- [训练假人](#训练假人)

---

## IntegrationManager

统一管理所有插件集成的中心类。

### 初始化

```java
IntegrationManager manager = new IntegrationManager(plugin);
manager.initAll();
```

### 主要方法

#### 检查集成状态

```java
// 检查特定集成是否启用
boolean hasVault = manager.hasVault();
boolean hasJobs = manager.hasJobs();
boolean hasMcMMO = manager.hasMcMMO();

// 检查所有集成
Map<String, Boolean> status = manager.getIntegrationStatus();
```

#### 获取集成实例

```java
// 使用 Optional 安全获取
Optional<VaultIntegration> vault = manager.getVault();
if (vault.isPresent()) {
    vault.get().deposit(uuid, 1000.0);
}

// 或者先检查再使用
if (manager.hasJobs()) {
    JobsIntegration jobs = manager.getJobs().get();
    // 使用 jobs...
}
```

#### 统计信息

```java
int enabledCount = manager.getEnabledCount();     // 已启用数量
int totalCount = manager.getTotalCount();         // 总数量
double percentage = manager.getEnabledPercentage(); // 启用率
```

---

## Vault 经济系统

### 获取实例

```java
VaultIntegration vault = integrations.getVault().get();
```

### 经济操作

#### 存款

```java
boolean success = vault.deposit(playerUuid, 1000.0);
```

#### 取款

```java
boolean success = vault.withdraw(playerUuid, 500.0);
```

#### 查询余额

```java
double balance = vault.getBalance(playerUuid);
```

#### 检查余额

```java
boolean hasEnough = vault.has(playerUuid, 100.0);
```

#### 设置余额

```java
vault.setBalance(playerUuid, 5000.0);
```

### 银行系统

```java
// 创建银行账户
vault.createBank("MyBank", playerUuid);

// 银行存款
vault.bankDeposit("MyBank", 1000.0);

// 银行取款
vault.bankWithdraw("MyBank", 500.0);

// 查询银行余额
double bankBalance = vault.bankBalance("MyBank");
```

---

## Jobs 职业系统

### 获取实例

```java
JobsIntegration jobs = integrations.getJobs().get();
```

### 职业管理

#### 加入职业

```java
boolean success = jobs.joinJob(player, "Miner");
```

#### 离开职业

```java
boolean success = jobs.leaveJob(player, "Miner");
```

#### 检查职业

```java
boolean hasMiner = jobs.hasJob(player, "Miner");
List<String> playerJobs = jobs.getPlayerJobs(player);
```

### 等级与经验

#### 获取等级

```java
int level = jobs.getJobLevel(player, "Miner");
```

#### 获取经验

```java
double exp = jobs.getJobExperience(player, "Miner");
```

#### 给予经验

```java
jobs.giveJobExperience(player, "Miner", 100.0);
```

#### 设置等级

```java
jobs.setJobLevel(player, "Miner", 50);
```

### 职业信息

#### 获取所有职业

```java
List<String> allJobs = jobs.getAllJobs();
```

#### 检查职业存在

```java
boolean exists = jobs.jobExists("Miner");
```

#### 获取职业上限

```java
int maxJobs = jobs.getMaxJobs(player);
```

---

## mcMMO 技能系统

### 获取实例

```java
McMMOIntegration mcmmo = integrations.getMcMMO().get();
```

### 技能等级

#### 获取技能等级

```java
int miningLevel = mcmmo.getSkillLevel(player, "Mining");
int swordsLevel = mcmmo.getSkillLevel(player, "Swords");
```

#### 设置技能等级

```java
mcmmo.setSkillLevel(player, "Mining", 100);
```

#### 添加技能等级

```java
mcmmo.addSkillLevel(player, "Mining", 5);
```

### 技能经验

#### 获取经验

```java
int xp = mcmmo.getSkillXP(player, "Mining");
```

#### 添加经验

```java
mcmmo.addSkillXP(player, "Mining", 500);
```

#### 经验进度

```java
int required = mcmmo.getXPToNextLevel(player, "Mining");
double percentage = mcmmo.getSkillXPPercentage(player, "Mining");
```

### 综合信息

#### 战斗力等级

```java
int powerLevel = mcmmo.getPowerLevel(player);
```

#### 所有技能等级

```java
Map<String, Integer> allSkills = mcmmo.getAllSkillLevels(player);
```

#### 最高技能

```java
String highestSkill = mcmmo.getHighestSkill(player);
```

### 技能管理

#### 重置技能

```java
mcmmo.resetSkill(player, "Mining");
```

#### 重置所有技能

```java
mcmmo.resetAllSkills(player);
```

#### 检查解锁

```java
boolean unlocked = mcmmo.hasUnlockedSkill(player, "Mining", 50);
```

---

## MythicMobs 生物系统

### 获取实例

```java
MythicMobsIntegration mm = integrations.getMythicMobs().get();
```

### 生物管理

#### 生成生物

```java
// 基础生成
Optional<Entity> mob = mm.spawnMob("SkeletonKing", location);

// 指定等级生成
Optional<Entity> mob = mm.spawnMob("DragonBoss", location, 50);
```

#### 检查生物

```java
boolean isMythicMob = mm.isMythicMob(entity);
Optional<String> mobType = mm.getMobType(entity);
```

#### 移除生物

```java
mm.removeMob(entity);
```

### 生物属性

#### 等级

```java
int level = mm.getMobLevel(entity);
mm.setMobLevel(entity, 100);
```

#### 血量

```java
double multiplier = mm.getHealthMultiplier(entity);
```

### 生物信息

#### 检查存在

```java
boolean exists = mm.mobTypeExists("SkeletonKing");
```

#### 获取显示名称

```java
Optional<String> displayName = mm.getMobDisplayName("SkeletonKing");
```

---

## Citizens NPC 系统

### 获取实例

```java
CitizensIntegration citizens = integrations.getCitizens().get();
```

### NPC 管理

#### 创建 NPC

```java
Optional<Integer> npcId = citizens.createNPC("商人", EntityType.VILLAGER, location);
```

#### 删除 NPC

```java
citizens.removeNPC(npcId);
```

#### 生成/取消生成

```java
citizens.spawnNPC(npcId, location);
citizens.despawnNPC(npcId);
```

### NPC 检查

```java
boolean isNPC = citizens.isNPC(entity);
Optional<Integer> npcId = citizens.getNPCId(entity);
boolean exists = citizens.npcExists(npcId);
boolean spawned = citizens.isSpawned(npcId);
```

### NPC 属性

#### 名称

```java
Optional<String> name = citizens.getNPCName(npcId);
citizens.setNPCName(npcId, "新名称");
```

#### 皮肤

```java
citizens.setSkin(npcId, "Steve");
```

### NPC 行为

#### 传送

```java
citizens.teleportNPC(npcId, newLocation);
```

#### 看向玩家

```java
citizens.lookAt(npcId, player);
```

---

## Typewriter 任务系统

### 获取实例

```java
TypewriterIntegration typewriter = integrations.getTypewriter().get();
```

### 任务管理

#### 开始任务

```java
typewriter.startQuest(player, "main_quest_1");
```

#### 完成任务

```java
typewriter.completeQuest(player, "main_quest_1");
```

#### 检查任务状态

```java
boolean completed = typewriter.hasCompletedQuest(player, "main_quest_1");
boolean active = typewriter.isQuestActive(player, "main_quest_1");
```

#### 设置任务进度

```java
typewriter.setQuestProgress(player, "main_quest_1", "kill_zombies", 10);
```

### 对话系统

```java
typewriter.triggerDialogue(player, "welcome_dialogue");
```

### 剧情系统

```java
typewriter.playCinematic(player, "intro_cutscene");
```

### 事件系统

```java
typewriter.triggerEvent(player, "boss_defeated");
```

### 任务标记

```java
typewriter.addQuestMarker(player, "main_quest_1", location);
typewriter.removeQuestMarker(player, "main_quest_1");
```

---

## Denizen 脚本系统

### 获取实例

```java
DenizenIntegration denizen = integrations.getDenizen().get();
```

### 脚本执行

#### 运行脚本

```java
denizen.runScript("my_script", player);
```

#### 执行命令脚本

```java
denizen.executeScript("narrate \"Hello!\"", player);
```

#### 运行任务

```java
denizen.runTask(player, "greeting_task");
```

#### 延迟执行

```java
denizen.runScriptDelayed("delayed_script", player, 100L); // 5秒后
```

### 标签管理

#### 设置玩家标签

```java
denizen.setPlayerFlag(player, "completed_tutorial", "true");
```

#### 获取标签

```java
Optional<String> value = denizen.getPlayerFlag(player, "completed_tutorial");
```

#### 删除标签

```java
denizen.removePlayerFlag(player, "completed_tutorial");
```

#### 检查标签

```java
boolean has = denizen.hasPlayerFlag(player, "completed_tutorial");
```

### 服务器标签

```java
denizen.setServerFlag("maintenance_mode", "false");
```

### 事件触发

```java
Map<String, String> context = new HashMap<>();
context.put("player", player.getName());
context.put("score", "100");

denizen.triggerEvent("custom_event", context);
```

### 消息发送

```java
denizen.sendNarrate(player, "§a欢迎来到服务器！");
```

---

## 自定义 NPC

### 获取管理器

```java
CustomNPCManager npcManager = plugin.getNPCManager();
```

### NPC 创建

```java
CustomNPC npc = npcManager.createNPC("商人", EntityType.VILLAGER, location);
npcManager.spawnNPC(npc.getId());
```

### NPC 配置

#### 显示名称

```java
npc.setDisplayName("§6神秘商人");
```

#### 对话

```java
npc.addDialogue("§a欢迎来到商店！");
npc.addDialogue("§e点击查看商品");
npc.clearDialogues();
```

#### 命令

```java
npc.addCommand("[console]shop open {player}");
npc.addCommand("[player]warp shop");
npc.clearCommands();
```

#### 属性

```java
npc.setInvulnerable(true);
npc.setLookAtPlayer(true);
npc.setInteractionRange(5.0);
npc.setPermission("shop.use");
```

### NPC 检查

```java
boolean isNPC = npcManager.isNPC(entity);
Optional<CustomNPC> npc = npcManager.getNPCByEntity(entity);
```

---

## 训练假人

### 获取管理器

```java
TrainingDummyManager dummyManager = plugin.getDummyManager();
```

### 假人创建

```java
TrainingDummy dummy = dummyManager.createDummy("测试假人", location);
dummy.copyEquipment(player);
dummy.copySkin(player);
dummyManager.spawnDummy(dummy.getId());
```

### 假人配置

#### 血量

```java
dummy.setMaxHealth(100.0);
dummy.setHealth(100.0);
dummy.resetHealth();
```

#### 属性

```java
dummy.setInvulnerable(false);
dummy.setShowHealthBar(true);
dummy.setAutoRespawn(true);
dummy.setRespawnDelay(3000); // 3秒
```

### 统计信息

```java
long totalDamage = dummy.getTotalDamageReceived();
int hits = dummy.getHitCount();
double avgDamage = dummy.getAverageDamage();
double dps = dummy.getDPS();
double healthPercent = dummy.getHealthPercentage();
```

### 假人管理

```java
boolean isDummy = dummyManager.isDummy(entity);
Optional<TrainingDummy> dummy = dummyManager.getDummyByEntity(entity);
```

---

## 🎯 完整示例

### 创建完整的任务流程

```java
public void createQuestFlow(Player player) {
    IntegrationManager im = plugin.getIntegrationManager();
    
    // 1. 玩家与 NPC 对话触发任务
    if (im.hasTypewriter()) {
        im.getTypewriter().get().triggerDialogue(player, "quest_intro");
        im.getTypewriter().get().startQuest(player, "dragon_slayer");
    }
    
    // 2. 生成 Boss
    if (im.hasMythicMobs()) {
        im.getMythicMobs().get().spawnMob("DragonBoss", bossLocation, 50);
    }
    
    // 3. 击败后给予奖励
    if (im.hasVault()) {
        im.getVault().get().deposit(player.getUniqueId(), 1000.0);
    }
    
    if (im.hasJobs()) {
        im.getJobs().get().giveJobExperience(player, "Warrior", 500.0);
    }
    
    if (im.hasMcMMO()) {
        im.getMcMMO().get().addSkillXP(player, "Swords", 1000);
    }
    
    // 4. 完成任务
    if (im.hasTypewriter()) {
        im.getTypewriter().get().completeQuest(player, "dragon_slayer");
        im.getTypewriter().get().playCinematic(player, "victory");
    }
}
```

---

## 📝 注意事项

1. **始终检查集成是否启用**
   ```java
   if (integrations.hasJobs()) {
       // 使用 Jobs API
   }
   ```

2. **使用 Optional 安全处理**
   ```java
   integrations.getVault().ifPresent(vault -> {
       vault.deposit(uuid, 100.0);
   });
   ```

3. **异常处理**
   ```java
   try {
       jobs.joinJob(player, "Miner");
   } catch (Exception e) {
       plugin.getLogger().warning("Failed to join job: " + e.getMessage());
   }
   ```

4. **异步操作**
   ```java
   Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
       // 数据库操作
   });
   ```

---

## 🔗 相关文档

- [README.md](README.md) - 项目介绍
- [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - 集成指南
- [CONFIG_GUIDE.md](CONFIG_GUIDE.md) - 配置说明

---

<div align="center">

**📚 完整 API 文档 v1.0**

</div>
