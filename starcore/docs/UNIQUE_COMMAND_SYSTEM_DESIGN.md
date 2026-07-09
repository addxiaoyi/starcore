# STARCORE 独特命名系统设计

**目标：** 打造独特的、有品牌特色的命令体系  
**理念：** 星核（STARCORE）主题 + 现代化 + 易记忆

---

## 🌟 核心设计理念

### 命名主题
- **星核（Star）** - 核心传送功能
- **超新星（Nova）** - 社交互动功能
- **星云（Nebula）** - 经济系统
- **轨道（Orbit）** - 决斗/PvP功能
- **星座（Constellation）** - 公会/团队功能
- **彗星（Comet）** - 快速功能/工具

---

## 📋 STARCORE 独特命令系统

### 1. 核心传送系统（Star系列）

**原EssentialsX风格：**
- /spawn, /home, /sethome, /warp, /back

**STARCORE独特风格：**
```
/star                    - 主命令/主菜单
/star spawn              - 传送到星核（出生点）
/star anchor [名称]      - 传送到锚点（家）
/star anchor set [名称]  - 设置锚点
/star anchor del [名称]  - 删除锚点
/star anchor list        - 锚点列表

/star port <名称>        - 传送到星港（warp点）
/star port list          - 星港列表

/star recall             - 召回上一个位置（back）
/star beam <玩家>        - 传送请求（tpa）
/star accept             - 接受传送
/star deny               - 拒绝传送
```

### 2. 社交系统（Nova系列）

**原风格：**
- /msg, /reply, /ignore

**STARCORE独特风格：**
```
/nova <玩家> <消息>      - 星际通讯（私聊）
/nova reply <消息>       - 快速回复
/nova mute <玩家>        - 静音玩家（屏蔽）
/nova unmute <玩家>      - 取消静音

/nova link <玩家>        - 添加星链（好友）
/nova unlink <玩家>      - 解除星链
/nova links              - 星链列表（好友列表）
```

### 3. 昵称系统（独特）

**原风格：**
- /nick

**STARCORE独特风格：**
```
/alias <昵称>            - 设置别名（昵称）
/alias clear             - 清除别名
/identity <昵称>         - 查询真实身份（realname）
```

### 4. 经济系统（Nebula系列）

**原风格：**
- /balance, /pay, /baltop

**STARCORE独特风格：**
```
/nebula                  - 查看星尘（余额）
/nebula <玩家>           - 查看他人星尘

/nebula send <玩家> <数量>  - 转移星尘（pay）
/nebula top              - 星尘富豪榜（baltop）

/nebula admin give <玩家> <数量>   - 给予星尘
/nebula admin take <玩家> <数量>   - 扣除星尘
/nebula admin set <玩家> <数量>    - 设置星尘
```

### 5. PvP/决斗系统（Orbit系列）

**原风格：**
- /duel

**STARCORE独特风格：**
```
/orbit <玩家>            - 发起轨道决斗
/orbit accept            - 接受决斗
/orbit deny              - 拒绝决斗
/orbit stats [玩家]      - 战斗数据
/orbit rank              - 战斗排行

/orbit ai <难度>         - AI训练模式
/orbit match             - 智能匹配
```

### 6. 公会系统（Constellation系列）

**原风格：**
- /guild

**STARCORE独特风格：**
```
/constellation           - 公会主命令
/constellation create <名称> <标签>  - 创建星座（公会）
/constellation disband   - 解散星座
/constellation info      - 星座信息

/constellation invite <玩家>  - 邀请加入
/constellation join      - 加入星座
/constellation leave     - 离开星座
/constellation kick <玩家>    - 踢出成员

/constellation list      - 所有星座列表
/constellation top       - 星座排行榜
```

### 7. 派对系统（独特）

**原风格：**
- /party

**STARCORE独特风格：**
```
/squad                   - 派对主命令
/squad create            - 创建小队
/squad invite <玩家>     - 邀请加入
/squad join              - 加入小队
/squad leave             - 离开小队
/squad disband           - 解散小队
/squad list              - 小队成员
```

### 8. 管理命令（Comet系列 - 快速工具）

**原风格：**
- /kick, /ban, /mute

**STARCORE独特风格：**
```
/comet                   - 管理工具主命令

# 禁言系统
/comet silence <玩家> [时间] [原因]  - 禁言（mute）
/comet unsilence <玩家>              - 解除禁言

# 封禁系统
/comet exile <玩家> [时间] [原因]    - 放逐（ban）
/comet pardon <玩家>                 - 赦免（unban）

# 踢出
/comet eject <玩家> [原因]           - 弹出（kick）

# 监狱
/comet imprison <玩家> [时间]        - 监禁（jail）
/comet release <玩家>                - 释放（unjail）

# 隐身
/comet cloak                         - 隐形斗篷（vanish）
```

### 9. 每日系统（独特）

**原风格：**
- /daily

**STARCORE独特风格：**
```
/quest                   - 每日任务
/quest daily             - 每日任务列表
/quest progress          - 任务进度

/stellar sign            - 星际签到（每日签到）
/stellar calendar        - 签到日历
/stellar streak          - 连续签到记录
```

### 10. 赛季通行证（独特）

**STARCORE独特风格：**
```
/pass                    - 通行证主命令
/pass info               - 通行证信息
/pass rewards            - 奖励列表
/pass claim              - 领取奖励
/pass upgrade            - 升级至高级通行证
```

### 11. 观战系统（独特）

**STARCORE独特风格：**
```
/observe <玩家>          - 观察模式（观战）
/observe stop            - 停止观察
/observe switch <玩家>   - 切换目标
```

### 12. UGC系统（独特）

**STARCORE独特风格：**
```
/create                  - UGC主命令
/create submit <类型> <名称>  - 提交创作
/create list             - 我的创作
/create browse           - 浏览创作
/create review <ID>      - 审核创作（管理员）
```

---

## 🎨 命令别名系统

为了兼容性和易用性，提供简短别名：

```yaml
# 核心传送
/star -> /s
/star spawn -> /s spawn, /spawn
/star anchor -> /s anchor, /anchor
/star port -> /s port, /port

# 社交
/nova -> /n
/nova <玩家> <消息> -> /n <玩家> <消息>, /w <玩家> <消息>

# 经济
/nebula -> /neb, /money
/nebula send -> /neb send, /pay

# PvP
/orbit -> /o, /fight
/orbit stats -> /o stats, /stats

# 公会
/constellation -> /const, /c

# 派对
/squad -> /sq, /team

# 管理
/comet -> /cm

# 任务
/quest -> /q

# 通行证
/pass -> /bp

# 观战
/observe -> /obs, /spec
```

---

## 🌟 主命令层级

```
/star (传送核心)
  ├─ spawn          - 星核传送
  ├─ anchor         - 锚点系统
  ├─ port           - 星港系统
  ├─ recall         - 位置召回
  └─ beam           - 传送请求

/nova (社交通讯)
  ├─ <玩家>         - 星际通讯
  ├─ reply          - 快速回复
  ├─ mute/unmute    - 静音管理
  └─ link/unlink    - 星链管理

/nebula (经济系统)
  ├─ [余额查询]
  ├─ send           - 星尘转移
  ├─ top            - 富豪榜
  └─ admin          - 管理命令

/orbit (战斗系统)
  ├─ <玩家>         - 轨道决斗
  ├─ stats          - 战斗数据
  ├─ ai             - AI训练
  └─ match          - 智能匹配

/constellation (公会系统)
  ├─ create/disband
  ├─ invite/join/leave
  └─ info/list/top

/squad (派对系统)
  ├─ create/disband
  └─ invite/join/leave

/comet (管理工具)
  ├─ silence/unsilence
  ├─ exile/pardon
  ├─ eject
  ├─ imprison/release
  └─ cloak

/quest (任务系统)
/stellar (签到系统)
/pass (通行证)
/alias (昵称)
/observe (观战)
/create (UGC)
```

---

## 💡 独特术语表

| STARCORE术语 | 传统术语 | 说明 |
|-------------|---------|------|
| 星核 (Star) | Spawn | 出生点 |
| 锚点 (Anchor) | Home | 家园点 |
| 星港 (Port) | Warp | 传送点 |
| 召回 (Recall) | Back | 返回 |
| 传送束 (Beam) | TPA | 传送请求 |
| 星际通讯 (Nova) | MSG | 私聊 |
| 星链 (Link) | Friend | 好友 |
| 静音 (Mute) | Ignore | 屏蔽 |
| 别名 (Alias) | Nick | 昵称 |
| 星尘 (Nebula) | Money/Balance | 货币 |
| 轨道决斗 (Orbit) | Duel | 决斗 |
| 星座 (Constellation) | Guild | 公会 |
| 小队 (Squad) | Party | 派对 |
| 彗星工具 (Comet) | Admin Tools | 管理工具 |
| 放逐 (Exile) | Ban | 封禁 |
| 禁言 (Silence) | Mute | 禁言 |
| 监禁 (Imprison) | Jail | 监狱 |
| 隐形斗篷 (Cloak) | Vanish | 隐身 |
| 星际签到 (Stellar Sign) | Daily | 签到 |
| 观察 (Observe) | Spectate | 观战 |
| 创作 (Create) | UGC | 用户内容 |

---

## 🎯 设计优势

### 1. 独特性
- 完全原创的命名体系
- 星空主题贯穿始终
- 不与现有插件冲突

### 2. 易记性
- 主题相关，容易联想
- 层级清晰，逻辑明确
- 提供简短别名

### 3. 专业性
- 统一的命名规范
- 完整的命令层级
- 清晰的功能分类

### 4. 品牌化
- 独特的STARCORE风格
- 统一的术语体系
- 强烈的品牌识别

---

**这套命名系统完全原创，有自己的特色，不再是EssentialsX的翻版！** 🌟
