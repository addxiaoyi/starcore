# ===============================================
# STARCORE 配置助手教程
# ===============================================
# 这个文件将帮助你快速配置 STARCORE 插件
# 跟随步骤操作，即可完成基础配置
# ===============================================

## 📖 目录

1. [快速开始](#快速开始)
2. [基础配置](#基础配置)
3. [传送系统配置](#传送系统配置)
4. [经济系统配置](#经济系统配置)
5. [PvP系统配置](#pvp系统配置)
6. [高级功能](#高级功能)
7. [常见问题](#常见问题)

---

## 🚀 快速开始

### 步骤 1：安装插件

1. 下载 `STARCORE.jar` 文件
2. 放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 插件会自动生成配置文件

### 步骤 2：首次配置

配置文件位置：`plugins/STARCORE/config.yml`

**最基础的配置（推荐新手）：**
```yaml
settings:
  language: zh_cn          # 使用简体中文
  debug: false             # 关闭调试模式

database:
  type: sqlite             # 使用 SQLite 数据库（简单）
```

保存后执行：`/starcore reload`

---

## ⚙️ 基础配置

### 语言设置

```yaml
settings:
  language: zh_cn          # 中文
  # language: en_us        # 英文
```

**支持的语言：**
- `zh_cn` - 简体中文
- `en_us` - English

### 调试模式

```yaml
settings:
  debug: false             # 正常模式（推荐）
  # debug: true            # 调试模式（开发用）
```

**何时开启调试：**
- 遇到问题需要排查
- 提交 Bug 报告时
- 正常使用时请关闭

### 自动保存

```yaml
settings:
  auto-save-interval: 5    # 每5分钟自动保存
```

**推荐值：**
- 小型服务器（<20人）：5分钟
- 中型服务器（20-100人）：3分钟
- 大型服务器（>100人）：1-2分钟

---

## 🌟 传送系统配置

### 基础传送设置

```yaml
teleport:
  # 冷却时间（秒）
  cooldowns:
    spawn: 3               # 星核传送冷却
    anchor: 3              # 锚点传送冷却
    port: 3                # 星港传送冷却
    beam: 5                # 传送请求冷却
```

**调整建议：**
- PvP服务器：建议 5-10 秒
- 生存服务器：建议 3-5 秒
- 创造服务器：建议 0-1 秒

### 传送延迟

```yaml
teleport:
  # 传送延迟（秒）
  delays:
    spawn: 3               # 3秒后传送
    anchor: 3
    port: 3
    beam: 5
    recall: 0              # 立即传送
```

**零延迟配置（立即传送）：**
```yaml
delays:
  spawn: 0
  anchor: 0
  port: 0
  beam: 0
  recall: 0
```

### 取消传送条件

```yaml
teleport:
  cancel-on:
    move: true             # 移动时取消
    damage: true           # 受伤时取消
    combat: true           # 战斗中取消
```

**宽松模式（不易取消）：**
```yaml
cancel-on:
  move: false
  damage: false
  combat: true             # 只在战斗中取消
```

**严格模式（容易取消）：**
```yaml
cancel-on:
  move: true
  damage: true
  combat: true
```

### 锚点(家园)设置

```yaml
teleport:
  anchors:
    max-count: 5           # 每人最多5个锚点
    default-name: "home"   # 默认名称
    allow-bed: true        # 允许用床设置
```

**VIP配置示例：**
- 普通玩家：5个锚点
- VIP玩家：10个锚点（需要配合权限插件）
- SVIP玩家：20个锚点

---

## 💰 经济系统配置

### 货币设置

```yaml
economy:
  currency:
    name: "星尘"           # 货币名称
    symbol: "✦"            # 货币符号
```

**自定义货币：**
```yaml
currency:
  name: "金币"
  symbol: "¥"
```

### 初始余额

```yaml
economy:
  starting-balance: 1000.0  # 新玩家初始1000星尘
```

**推荐值：**
- 经济宽松：5000-10000
- 经济平衡：1000-3000
- 经济紧张：100-500

### 转账设置

```yaml
economy:
  transfer:
    min-amount: 1.0        # 最少转账1星尘
    max-amount: 1000000.0  # 最多转账100万
    tax-rate: 0.0          # 不收税
```

**收税配置：**
```yaml
tax-rate: 0.05             # 5%转账税
# 转账100星尘，接收者收到95星尘
```

---

## ⚔️ PvP系统配置

### 决斗系统

```yaml
pvp:
  orbit:
    request-timeout: 30    # 请求30秒后过期
    allow-spectators: true # 允许观战

    wager:
      enable: true         # 启用赌注
      min-amount: 100.0    # 最小赌注
      max-amount: 10000.0  # 最大赌注
```

**无赌注配置：**
```yaml
wager:
  enable: false            # 禁用赌注
```

### 连杀系统

```yaml
pvp:
  killstreak:
    milestones:
      3: 50                # 3连杀奖励50星尘
      5: 100               # 5连杀奖励100星尘
      10: 300              # 10连杀奖励300星尘
      15: 500
      20: 1000
```

**自定义连杀奖励：**
```yaml
milestones:
  3: 100                   # 提高奖励
  5: 200
  7: 400                   # 增加7连杀里程碑
  10: 800
  20: 2000
```

---

## 🤖 高级功能

### AI训练机器人

```yaml
ai:
  training-bot:
    enable: true           # 启用AI机器人
    max-bots-per-player: 1 # 每人最多1个机器人
```

**多机器人配置：**
```yaml
max-bots-per-player: 3     # VIP玩家可以3个
```

### 智能匹配

```yaml
ai:
  matchmaking:
    enable: true           # 启用智能匹配
```

**完全禁用AI功能：**
```yaml
ai:
  training-bot:
    enable: false
  matchmaking:
    enable: false
  battle-pass:
    enable: false
```

### 赛季通行证

```yaml
ai:
  battle-pass:
    enable: true
    season-duration: 60    # 赛季60天
    exp-per-level: 1000    # 每级1000经验
    premium-price: 5000    # 高级版5000星尘
```

### 公会系统

```yaml
guild:
  enable: true
  create-cost: 10000       # 创建费用
  max-members: 50          # 最多50成员
```

**调整建议：**
- 小服：create-cost: 5000, max-members: 20
- 中服：create-cost: 10000, max-members: 50
- 大服：create-cost: 20000, max-members: 100

---

## 💾 数据库配置

### SQLite（推荐新手）

```yaml
database:
  type: sqlite
  sqlite:
    file: "starcore.db"
```

**优点：**
- 无需额外配置
- 自动创建
- 适合小型服务器

### MySQL（推荐中大型服务器）

```yaml
database:
  type: mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "starcore"
    username: "root"
    password: "your_password"
    pool:
      minimum: 5
      maximum: 10
```

**配置步骤：**
1. 安装 MySQL 数据库
2. 创建数据库：`CREATE DATABASE starcore;`
3. 修改配置文件中的连接信息
4. 重启服务器

---

## 🎮 每日系统配置

### 签到系统

```yaml
daily:
  checkin:
    enable: true
    base-reward: 100       # 基础奖励100星尘
```

**连续签到奖励：**
插件会自动计算连续签到奖励：
- 1-2天：100星尘
- 3天：150星尘（+50奖励）
- 7天：300星尘（+200奖励）
- 14天：600星尘（+500奖励）
- 30天：1100星尘（+1000奖励）

### 每日任务

```yaml
daily:
  quest:
    enable: true
    refresh-time: "04:00"  # 每天凌晨4点刷新
```

---

## ❓ 常见问题

### Q1: 如何重载配置？
**A:** 使用命令 `/starcore reload` 或 `/sc reload`

### Q2: 修改配置后不生效？
**A:** 
1. 检查 YAML 格式是否正确（缩进必须用空格，不能用Tab）
2. 确保执行了 `/starcore reload`
3. 查看控制台是否有错误信息

### Q3: 如何禁用某个功能？
**A:** 找到对应功能的 `enable: true`，改为 `enable: false`

例如禁用公会：
```yaml
guild:
  enable: false
```

### Q4: 如何给VIP玩家更多锚点？
**A:** 使用权限插件（如 LuckPerms），给予权限：
```
starcore.anchors.vip.10    # VIP有10个锚点
starcore.anchors.svip.20   # SVIP有20个锚点
```

### Q5: 经济系统与Vault冲突？
**A:** 在配置中选择：
```yaml
integrations:
  vault:
    provide-economy: false  # 不提供经济，使用其他插件
```

### Q6: 如何备份数据？
**A:** 
- SQLite：备份 `plugins/STARCORE/starcore.db` 文件
- MySQL：使用 `mysqldump` 备份数据库

### Q7: 服务器卡顿怎么办？
**A:** 优化配置：
```yaml
performance:
  async:
    enable: true           # 启用异步处理
  cache:
    enable: true
    cache-size: 500        # 减小缓存
```

---

## 🎯 推荐配置方案

### 方案1：小型生存服务器（<20人）

```yaml
settings:
  language: zh_cn
  auto-save-interval: 5

teleport:
  cooldowns:
    spawn: 3
    anchor: 3
    port: 3
  delays:
    spawn: 3
    anchor: 3
    port: 3

economy:
  starting-balance: 5000

guild:
  enable: true
  create-cost: 5000
  max-members: 20

database:
  type: sqlite
```

### 方案2：中型PvP服务器（20-100人）

```yaml
settings:
  language: zh_cn
  auto-save-interval: 3

teleport:
  cooldowns:
    spawn: 5
    anchor: 5
    port: 5
    beam: 10
  cancel-on:
    move: true
    damage: true
    combat: true

pvp:
  orbit:
    wager:
      enable: true
      max-amount: 50000

economy:
  starting-balance: 2000

database:
  type: mysql
```

### 方案3：大型多元化服务器（>100人）

```yaml
settings:
  language: zh_cn
  auto-save-interval: 1

ai:
  training-bot:
    enable: true
  matchmaking:
    enable: true
  battle-pass:
    enable: true

guild:
  enable: true
  create-cost: 20000
  max-members: 100

party:
  enable: true
  max-members: 10

database:
  type: mysql
  mysql:
    pool:
      minimum: 10
      maximum: 20
```

---

## 📞 获取帮助

**遇到问题？**
1. 查看 `plugins/STARCORE/logs/` 日志文件
2. 在服务器控制台查找错误信息
3. 访问 GitHub Issues 提交问题
4. 加入 Discord 社区寻求帮助

**有建议？**
欢迎在 GitHub 提交 Feature Request！

---

## 🎉 配置完成

恭喜！你已经完成了 STARCORE 的基础配置！

**下一步：**
1. 进入游戏测试功能
2. 根据需要微调参数
3. 享受 STARCORE 带来的体验！

---

**感谢使用 STARCORE！** 🌟
