# StarCore 架构改进计划

**创建日期**: 2026-07-10
**状态**: 待处理

---

## 📊 架构问题总结

### 1. 核心问题：God Class 反模式

| 文件 | 行数 | 问题 |
|------|------|------|
| `StarCoreCommand.java` | **3596** | 单个文件承担所有命令路由 |
| `TriumphNationMenu.java` | **2678** | GUI 和逻辑混杂 |
| `MainCommandHandler.java` | **48236** | 过多依赖注入 |
| `MapModule.java` | **2874** | 承担过多职责 |

### 2. 根因分析

```
问题: StarCoreCommand.java 包含所有命令路由逻辑
├── 3596 行代码
├── 30+ 个 handleXxx 方法
├── 大量重复的 service 查找逻辑
└── 每个 handle 方法都重复: context.serviceRegistry().find(XxxService.class)
```

---

## 🎯 改进目标

1. **拆分巨型类** - StarCoreCommand.java 从 3596 行 → 每子命令 < 200 行
2. **统一命令路由** - 建立标准化的命令处理器架构
3. **消除重复代码** - 提取公共的 service 查找和权限检查逻辑
4. **提高可维护性** - 模块化、独立测试

---

## 📋 改进方案

### 阶段一：命令路由重构 (高优先级)

#### 1.1 创建统一的命令路由框架

```java
// 新架构: 命令注册中心
public final class CommandRegistry {
    private final Map<String, SubCommand> commands = new HashMap<>();
    
    public void register(String name, SubCommand command) {
        commands.put(name, command);
    }
    
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        SubCommand cmd = commands.get(args[0].toLowerCase());
        return cmd != null && cmd.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
```

#### 1.2 拆分 StarCoreCommand.java

**当前结构** (3596 行):
```
StarCoreCommand
├── onCommand()           # 主入口
├── onTabComplete()       # 补全
├── handleNation()        # 100+ 行
├── handleTreasury()      # 100+ 行
├── handleDiplomacy()     # 100+ 行
├── handleWar()           # 100+ 行
├── handlePolicy()        # 100+ 行
├── handleTechnology()     # 100+ 行
├── handleOfficer()       # 100+ 行
├── handleEpoch()         # 100+ 行
├── handleTimeSync()      # 100+ 行
├── handleMap()           # 100+ 行
├── handleGovernment()     # 100+ 行
├── handleResolution()     # 100+ 行
└── handleEconomy()       # 100+ 行
```

**目标结构** (每模块 < 200 行):
```
command/
├── CoreCommandRouter.java        # 主路由 (100行)
├── nation/
│   ├── NationCommand.java        # /sc n
│   ├── NationCreateCommand.java  # create 子命令
│   ├── NationInfoCommand.java    # info 子命令
│   ├── NationInviteCommand.java  # invite 子命令
│   └── NationLeaveCommand.java   # leave 子命令
├── treasury/
│   ├── TreasuryCommand.java      # /sc treasury
│   ├── TreasuryDepositCommand.java
│   └── TreasuryBalanceCommand.java
├── diplomacy/
│   └── DiplomacyCommand.java
└── ...
```

#### 1.3 实施步骤

```bash
# 1. 创建 nation 命令包
src/main/java/dev/starcore/starcore/command/nation/

# 2. 提取 Nation 相关命令
- NationCreateCommand.java
- NationInfoCommand.java
- NationInviteCommand.java
- NationDisbandCommand.java

# 3. 创建 NationCommand 聚合类
# 4. 更新 StarCorePlugin 注册
# 5. 删除原 handleNation() 方法
```

### 阶段二：服务定位器优化

#### 2.1 当前问题

```java
// 每个 handle 方法都重复这段代码:
private void handleNation(CommandSender sender, String label, String[] args) {
    NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
    ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
    GovernmentService governmentService = context.serviceRegistry().find(GovernmentService.class).orElse(null);
    // ... 重复 30+ 次
}
```

#### 2.2 解决方案：上下文对象

```java
// 新增命令上下文类
public final class CommandContext {
    private final CommandSender sender;
    private final StarCoreContext context;
    private final Map<Class<?>, Object> cache = new HashMap<>();
    
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceClass) {
        return (T) cache.computeIfAbsent(serviceClass, 
            k -> context.serviceRegistry().find(serviceClass).orElse(null));
    }
    
    public boolean isPlayer() { return sender instanceof Player; }
    public Player asPlayer() { return (Player) sender; }
    public boolean hasPermission(String perm) { return sender.hasPermission(perm); }
}

// 使用方式:
private void handleNation(CommandContext ctx, String[] args) {
    NationService nationService = ctx.get(NationService.class);
    // 自动缓存，无需重复查找
    GovernmentService govService = ctx.get(GovernmentService.class);
}
```

### 阶段三：GUI 类重构

#### 3.1 TriumphNationMenu.java (2678 行)

**问题**:
- GUI 构建逻辑和事件处理混杂
- 超过 30 个嵌套菜单
- 难以单独测试

**重构方案**:
```java
// 新架构
gui/
├── NationMenu.java                    # 抽象基类
├── NationMainMenu.java               # 主菜单 (< 300 行)
├── NationInfoMenu.java               # 信息菜单
├── NationMembersMenu.java             # 成员列表
├── NationSettingsMenu.java            # 设置菜单
├── NationPermissionsMenu.java         # 权限菜单
└── listener/
    └── NationMenuListener.java        # 统一事件处理
```

### 阶段四：模块间解耦

#### 4.1 当前强耦合

```
StarCoreCommand (直接依赖)
├── NationService
├── TreasuryService
├── DiplomacyService
├── WarService
├── PolicyService
└── ... (30+ 服务)
```

#### 4.2 目标：事件驱动解耦

```java
// 通过事件总线通信
public class TreasuryCommand {
    private final EventBus eventBus;
    
    public boolean deposit(Player player, double amount) {
        // 发布事件，而非直接调用
        eventBus.publish(new TreasuryDepositedEvent(player, amount));
        return true;
    }
}

// 服务订阅事件
public class TreasuryService {
    @Subscribe
    public void onDeposited(TreasuryDepositedEvent event) {
        // 处理存款逻辑
    }
}
```

---

## 📁 目录结构改进

### 当前结构
```
src/main/java/dev/starcore/starcore/
├── command/
│   ├── StarCoreCommand.java      # 3596 行 ❌
│   └── ...
├── module/nation/
│   └── gui/
│       └── TriumphNationMenu.java # 2678 行 ❌
```

### 目标结构
```
src/main/java/dev/starcore/starcore/
├── command/
│   ├── CoreCommandRouter.java    # 主入口 (100行)
│   ├── CommandContext.java       # 上下文 (50行)
│   ├── nation/
│   │   ├── NationCommand.java
│   │   ├── NationCreateCommand.java
│   │   ├── NationInfoCommand.java
│   │   └── NationInviteCommand.java
│   ├── treasury/
│   │   ├── TreasuryCommand.java
│   │   └── TreasuryDepositCommand.java
│   └── ...
├── gui/
│   ├── AbstractMenu.java
│   ├── nation/
│   │   ├── NationMainMenu.java
│   │   ├── NationInfoMenu.java
│   │   └── NationMembersMenu.java
│   └── NationMenuListener.java
```

---

## ⏱️ 实施优先级

| 阶段 | 工作内容 | 优先级 | 工作量 |
|------|----------|--------|--------|
| 1.1 | 创建 CommandRegistry 框架 | 🔴 高 | 1天 |
| 1.2 | 拆分 NationCommand | 🔴 高 | 2天 |
| 1.3 | 拆分 TreasuryCommand | 🔴 高 | 1天 |
| 1.4 | 拆分 DiplomacyCommand | 🟡 中 | 1天 |
| 1.5 | 拆分 WarCommand | 🟡 中 | 1天 |
| 2.1 | 实现 CommandContext | 🔴 高 | 0.5天 |
| 3.1 | 重构 TriumphNationMenu | 🟡 中 | 3天 |
| 4.1 | 引入事件驱动 | 🟢 低 | 长期 |

---

## ✅ 验收标准

1. **StarCoreCommand.java** 从 3596 行减少到 < 200 行
2. **每个子命令类** 不超过 300 行
3. **命令处理器** 可以独立单元测试
4. **无重复代码** - service 查找逻辑统一
5. **构建成功** - `mvn clean package` 通过
6. **功能测试** - 所有原有命令正常工作

---

## 📝 注意事项

1. **向后兼容** - 保持原有命令别名不变
2. **渐进式重构** - 每次只重构一个模块
3. **测试覆盖** - 重构前先确保有测试用例
4. **Git 提交** - 每个重构步骤单独提交

---

## 🔗 相关文件

- `src/main/java/dev/starcore/starcore/command/StarCoreCommand.java`
- `src/main/java/dev/starcore/starcore/command/MainCommandHandler.java`
- `src/main/java/dev/starcore/starcore/module/nation/gui/TriumphNationMenu.java`
- `src/main/java/dev/starcore/starcore/StarCorePlugin.java`
