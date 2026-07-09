# STARCORE 国策级核心插件 - 完整实施计划

**目标：** 打造替代 EssentialsX 的国策级核心插件  
**定位：** 服务器不可或缺的旗舰系统  
**时间：** 2026-06-15 开始  
**状态：** 🚀 启动

---

## 🎯 核心目标

### 从"国家战略插件"升级为"国策级核心系统"

**当前状态：** 专注国家战略（A+级国家插件）  
**目标状态：** 全服核心系统（SSS+级旗舰插件）

### 核心能力扩展

1. **替代 EssentialsX** - 完整基础命令系统
2. **PvP/Duel 核心** - 高性能战斗系统
3. **经济中心** - 完整经济生态
4. **社交系统** - 聊天/好友/邮件
5. **管理工具** - 服务器管理套件
6. **国策特色** - 国服特色功能

---

## 📊 功能清单（按优先级）

### P0 - 核心必做（替代 Essentials）

#### 1. 基础命令系统 ✅ 立即实施

**传送命令：**
- ✅ `/spawn` - 传送到出生点
- ✅ `/home [名称]` - 传送到家（支持多家园）
- ✅ `/sethome [名称]` - 设置家
- ✅ `/delhome [名称]` - 删除家
- ✅ `/warp <名称>` - 传送到传送点
- ✅ `/setwarp <名称>` - 设置传送点（管理员）
- ✅ `/delwarp <名称>` - 删除传送点（管理员）
- ✅ `/tpa <玩家>` - 请求传送
- ✅ `/tpaccept` - 接受传送
- ✅ `/tpdeny` - 拒绝传送
- ✅ `/back` - 返回上一个位置/死亡点

**社交命令：**
- ✅ `/msg <玩家> <消息>` - 私聊
- ✅ `/reply <消息>` - 回复私聊
- ✅ `/ignore <玩家>` - 屏蔽玩家
- ✅ `/unignore <玩家>` - 取消屏蔽

**昵称系统：**
- ✅ `/nick <昵称>` - 设置昵称
- ✅ `/realname <昵称>` - 查看真实名称
- ✅ 自定义前缀/后缀（结合 LuckPerms）

**经济命令：**
- ✅ `/bal` - 查看余额
- ✅ `/pay <玩家> <金额>` - 转账
- ✅ `/baltop` - 财富排行榜
- ✅ `/eco give/take/set` - 管理员经济命令

**死亡处理：**
- ✅ 自定义死亡消息
- ✅ 保留经验/物品选项
- ✅ 死亡位置记录

---

### P1 - PvP/Duel 核心（竞争力）

#### 2. Duel 决斗系统 ⭐

- ✅ 1v1 匹配系统
- ✅ 决斗邀请/接受
- ✅ 多个竞技场
- ✅ 赌注系统（经济）
- ✅ 胜率统计
- ✅ 排行榜

#### 3. 杀戮连击系统

- ✅ KillStreak 追踪
- ✅ 连杀广播
- ✅ 奖励系统（Kit/金钱/物品）
- ✅ 连杀音效和粒子

#### 4. PvP 统计

- ✅ K/D 比率
- ✅ 胜率统计
- ✅ 击杀排行榜
- ✅ GUI 查看

#### 5. Kit 系统增强

- ✅ GUI 选择 Kit
- ✅ 冷却系统
- ✅ 付费解锁
- ✅ 等级/连杀奖励解锁
- ✅ 自定义物品/附魔
- ✅ 特殊能力（粒子/音效/技能）

---

### P2 - 管理与运维

#### 6. 管理员工具

**权限管理：**
- ✅ 内置简单权限系统
- ✅ 深度 Hook LuckPerms

**Moderation：**
- ✅ `/mute <玩家> [时间] [原因]`
- ✅ `/kick <玩家> [原因]`
- ✅ `/ban <玩家> [原因]`
- ✅ `/tempban <玩家> <时间> [原因]`
- ✅ `/jail <玩家>` - 监狱系统
- ✅ `/vanish` - 隐身模式

**日志系统：**
- ✅ 轻量日志（或 Hook CoreProtect）
- ✅ 玩家操作记录
- ✅ 经济交易日志
- ✅ 命令使用日志

**公告系统：**
- ✅ `/broadcast <消息>`
- ✅ 自动公告
- ✅ Title/Subtitle 广播
- ✅ ActionBar 提示

---

### P3 - QoL 提升留存

#### 7. GUI 菜单系统 ⭐

**主菜单：**
- ✅ `/menu` - 主菜单
- ✅ Kit 选择 GUI
- ✅ Warp 传送点 GUI
- ✅ 玩家信息面板
- ✅ 排行榜 GUI

**美观特性（1.21+）：**
- ✅ Adventure Component 标题
- ✅ MiniMessage 支持
- ✅ 自定义字体（Resource Pack）
- ✅ 非箱子风格 GUI
- ✅ 动态更新

#### 8. 聊天增强

- ✅ 聊天格式化
- ✅ 颜色和 Emoji
- ✅ 频道系统（全局/本地/PvP/管理）
- ✅ 反刷屏保护
- ✅ 敏感词过滤

#### 9. 每日任务系统

- ✅ 登录奖励
- ✅ 在线时长奖励
- ✅ PvP 任务（击杀/Duel）
- ✅ 经济 + 物品奖励

#### 10. 便利功能

- ✅ `/enderchest` - 末影箱
- ✅ `/invsee <玩家>` - 查看背包（管理员）
- ✅ 多世界支持

---

### P4 - 高级功能

#### 11. 排行榜系统 ⭐

**Holographic 显示：**
- ✅ 财富排行
- ✅ 击杀排行
- ✅ 在线时长排行
- ✅ 连杀记录排行

**PlaceholderAPI 集成：**
```
%starcore_balance%
%starcore_kills%
%starcore_deaths%
%starcore_kd%
%starcore_killstreak%
%starcore_duel_wins%
%starcore_duel_losses%
```

#### 12. 国服特色

**签到系统：**
- ✅ 每日签到
- ✅ 连续签到递增奖励
- ✅ 签到 GUI

**红包系统：**
- ✅ 聊天红包
- ✅ 服务器红包
- ✅ 节日红包

**活动系统：**
- ✅ 节日活动框架
- ✅ 双倍经验
- ✅ 限时 Kit

---

## 🏗️ 技术架构

### 模块化设计

```
starcore/
├── core/               # 核心系统
├── essentials/         # 基础命令（替代 Essentials）⭐新增
│   ├── teleport/       # 传送系统
│   ├── home/           # 家园系统
│   ├── warp/           # 传送点系统
│   ├── social/         # 社交系统
│   └── nick/           # 昵称系统
├── pvp/                # PvP 系统⭐新增
│   ├── duel/           # 决斗系统
│   ├── killstreak/     # 连杀系统
│   ├── stats/          # 统计系统
│   └── kit/            # Kit 增强
├── moderation/         # 管理工具⭐新增
│   ├── punishment/     # 处罚系统
│   ├── log/            # 日志系统
│   └── vanish/         # 隐身系统
├── gui/                # GUI 系统⭐新增
│   ├── modern/         # 现代化 GUI（1.21+）
│   └── menu/           # 菜单系统
├── chat/               # 聊天系统⭐新增
├── daily/              # 每日系统⭐新增
│   ├── quest/          # 任务系统
│   ├── checkin/        # 签到系统
│   └── reward/         # 奖励系统
├── leaderboard/        # 排行榜系统⭐新增
└── integration/        # 集成系统
    ├── vault/          # Vault（已完成）
    ├── papi/           # PlaceholderAPI⭐新增
    └── luckperms/      # LuckPerms⭐新增
```

---

## 📈 实施优先级

### 第一阶段（立即开始，2-3天）

1. ✅ 传送系统（spawn/home/warp/tpa/back）
2. ✅ 社交系统（msg/reply/ignore）
3. ✅ 昵称系统（nick/realname）
4. ✅ 经济命令完善（baltop）

### 第二阶段（3-5天）

5. ✅ Duel 决斗系统
6. ✅ 杀戮连击系统
7. ✅ PvP 统计系统
8. ✅ Kit 系统增强

### 第三阶段（2-3天）

9. ✅ 管理员工具
10. ✅ GUI 菜单系统（现代化）
11. ✅ 聊天增强

### 第四阶段（2-3天）

12. ✅ 排行榜系统
13. ✅ PlaceholderAPI 集成
14. ✅ 每日任务系统

### 第五阶段（2-3天）

15. ✅ 签到/红包系统
16. ✅ 活动框架
17. ✅ 性能优化

---

## 🎨 现代化 GUI（1.21+）

### 核心特性

1. **Adventure Component 标题**
```java
Component title = Component.text("STARCORE 主菜单", NamedTextColor.GOLD)
    .decoration(TextDecoration.BOLD, true);
```

2. **MiniMessage 支持**
```java
Component title = MiniMessage.miniMessage()
    .deserialize("<gold><bold>STARCORE <gray>| <white>%starcore_balance%");
```

3. **自定义字体**
- Resource Pack 集成
- 非箱子风格 UI
- 全屏自定义背景

4. **动态物品**
```java
item.editMeta(meta -> {
    meta.displayName(Component.text("高级 Kit", NamedTextColor.GOLD));
    meta.lore(List.of(
        Component.text("点击解锁", NamedTextColor.YELLOW),
        Component.text("价格: 500 金币", NamedTextColor.GREEN)
    ));
});
```

---

## 🔌 PlaceholderAPI 完整集成

### 注册占位符

```java
public class StarcorePlaceholder extends PlaceholderExpansion {
    @Override
    public String getIdentifier() {
        return "starcore";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        switch (params) {
            case "balance" -> economyService.getBalance(player.getUniqueId());
            case "kills" -> statsService.getKills(player.getUniqueId());
            case "kd" -> statsService.getKD(player.getUniqueId());
            case "killstreak" -> killstreakService.getCurrent(player.getUniqueId());
            // ... 更多
        }
    }
}
```

### 支持的占位符

- `%starcore_balance%` - 余额
- `%starcore_kills%` - 击杀数
- `%starcore_deaths%` - 死亡数
- `%starcore_kd%` - K/D 比率
- `%starcore_killstreak%` - 当前连杀
- `%starcore_duel_wins%` - 决斗胜利
- `%starcore_rank%` - 排名
- 等 50+ 个占位符

---

## ⚡ 性能优化

### 核心策略

1. **异步一切可异步**
   - 数据库操作
   - 文件 I/O
   - 排行榜计算

2. **缓存策略**
   - 玩家数据缓存
   - 排行榜缓存
   - GUI 缓存

3. **内存管理**
   - WeakReference
   - 定期清理
   - 事件监听器管理

---

## 📊 数据持久化

### MySQL 优化

```sql
-- 玩家基础数据
CREATE TABLE starcore_players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16),
    balance DECIMAL(15,2),
    kills INT,
    deaths INT,
    last_seen BIGINT
);

-- 家园数据
CREATE TABLE starcore_homes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36),
    name VARCHAR(32),
    world VARCHAR(32),
    x DOUBLE,
    y DOUBLE,
    z DOUBLE
);

-- 决斗统计
CREATE TABLE starcore_duel_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    wins INT,
    losses INT,
    draws INT
);
```

---

## 🎯 成功指标

### 功能完整度

- ✅ 替代 EssentialsX 的所有基础功能
- ✅ 超越 EssentialsX 的 PvP 特性
- ✅ 国服特色功能

### 性能指标

- TPS 影响 <0.2%
- 命令响应 <10ms
- GUI 打开 <5ms

### 用户体验

- 现代化 GUI
- 完整中文支持
- 详细文档

---

## 📅 时间表

| 阶段 | 功能 | 时间 | 状态 |
|------|------|------|------|
| 1 | 传送+社交+昵称 | 2-3天 | ⏳ 进行中 |
| 2 | Duel+连杀+统计 | 3-5天 | ⏸️ 待开始 |
| 3 | 管理+GUI+聊天 | 2-3天 | ⏸️ 待开始 |
| 4 | 排行榜+PAPI+任务 | 2-3天 | ⏸️ 待开始 |
| 5 | 签到+红包+优化 | 2-3天 | ⏸️ 待开始 |

**总计：** 12-17 天

---

## 🚀 立即开始

我现在开始实施**第一阶段**：基础命令系统！

---

**文档版本：** 1.0  
**创建时间：** 2026-06-15  
**状态：** 🚀 启动实施
