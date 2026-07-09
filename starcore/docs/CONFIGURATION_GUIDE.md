# STARCORE 配置指南

**版本:** 0.1.0-SNAPSHOT  
**更新日期:** 2026-06-24

---

## 目录

- [配置文件位置](#配置文件位置)
- [语言配置](#语言配置)
- [数据库配置](#数据库配置)
- [国家配置](#国家配置)
- [领土配置](#领土配置)
- [经济配置](#经济配置)
- [外交配置](#外交配置)
- [战争配置](#战争配置)
- [官员配置](#官员配置)
- [地图配置](#地图配置)
- [模块配置](#模块配置)
- [外部集成](#外部集成)
- [Redis 配置](#redis-配置)
- [REST API 配置](#rest-api-配置)
- [视觉反馈配置](#视觉反馈配置)

---

## 配置文件位置

STARCORE 配置文件位于 `plugins/STARCORE/` 目录：

| 文件 | 说明 |
|------|------|
| `config.yml` | 主配置文件 |
| `messages_zh_cn.yml` | 中文语言包 |
| `messages_en_us.yml` | 英文语言包 |
| `starcore.db` | SQLite 数据库 |
| `economy/` | 个人经济数据 |
| `nations/` | 国家数据 |
| `claims/` | 领土数据 |

---

## 语言配置

```yaml
locale: zh_cn

# 支持的语言代码：
# - zh_cn: 简体中文（默认）
# - en_us: English
# - zh_tw: 繁體中文
# - ja_jp: 日本語
# - ko_kr: 한국어
```

---

## 数据库配置

### SQLite（默认）

```yaml
database:
  enabled: true
  type: sqlite
  sqlite:
    file: starcore.db
```

### MySQL

```yaml
database:
  enabled: true
  type: mysql
  fail-fast: false  # 数据库不可用时阻止插件启用
  mysql:
    host: 127.0.0.1
    port: 3306
    database: starcore
    username: starcore
    password: "your_password"
    parameters: "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC"
  pool:
    maximum-pool-size: 8
    minimum-idle: 1
    connection-timeout-ms: 30000
    idle-timeout-ms: 600000
    max-lifetime-ms: 1800000
    keepalive-time-ms: 0
    validation-timeout-ms: 5000
    leak-detection-threshold-ms: 0
```

### 数据库连接池说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maximum-pool-size` | 8 | 最大连接数 |
| `minimum-idle` | 1 | 最小空闲连接 |
| `connection-timeout-ms` | 30000 | 获取连接超时(ms) |
| `idle-timeout-ms` | 600000 | 空闲连接保留时间(ms) |
| `max-lifetime-ms` | 1800000 | 单连接最大生命周期(ms) |

---

## 国家配置

### 基础限制

```yaml
nation:
  limits:
    # 每名玩家最多可以创建/领导几个国家
    max-nations-per-player: 1
    # 每名玩家最多可以创建/领导几个城邦
    max-city-states-per-player: 3
    # 每个国家最多允许几个城邦
    max-city-states-per-nation: 3
    # 每个国家最多圈多少区块；-1 表示无限制
    max-claims-per-nation: 64
    # 每个国家最多多少成员；-1 表示无限制
    max-members-per-nation: 50
```

### 经济设置

```yaml
nation:
  economy:
    # 创建国家需要从玩家个人账户扣除的费用
    nation-create-cost: 500.00
    # 创建城邦需要从玩家个人账户扣除的费用
    city-state-create-cost: 250.00
    # 每次圈地区块需要从玩家个人账户扣除的费用
    claim-cost: 100.00
    # 是否只有国家创建者/领导者能圈地
    leader-only-claim: true
```

### 日常收入

```yaml
nation:
  economy:
    daily-income:
      # 是否允许自动结算国家日常收入
      auto-enabled: false
      # 基础金额
      base-amount: 100.00
      # 每名国家成员额外给予的金库收入
      per-member: 25.00
      # 每个已圈领地区块额外给予的金库收入
      per-claim: 5.00
```

### 税收设置

```yaml
nation:
  economy:
    tax:
      # 是否启用玩家税收
      enabled: false
      # 每名国家成员每次税收结算固定扣多少金币
      fixed-amount: 0.00
      # 每名成员按个人余额百分比扣税 (2.50 = 2.5%)
      balance-percent: 0.00
      # 税后至少保留多少个人余额
      minimum-balance: 0.00
      # 是否跳过余额不足的成员
      skip-insufficient-members: true
```

### 资源区块

```yaml
nation:
  resources:
    enabled: true
    # 国家领导者迁移一个资源区块需要支付的个人金币
    migration-cost: 100000.00
    # 迁移超时强制清空的小时数
    force-migration-hours: 4
    
    level:
      # 是否使用国家等级计算圈地上限
      use-level-claim-limit: true
      # 国家最高等级
      max-level: 100
      # 1 级升 2 级需要的国家经验
      base-experience: 1000
      # 每升一级额外增加的升级经验需求
      experience-step: 250
      # 1 级国家最多覆盖多少区块
      claims-at-level-1: 20
      # 每提升 1 级额外增加多少领地区块上限
      claims-per-level: 5
    
    resource-districts:
      # 初始资源区块上限
      initial-limit: 1
      # 每多少国家等级额外解锁 1 个资源区块上限
      levels-per-district: 10
      # 资源区块总硬上限
      max-limit: 11
    
    refresh:
      # 检查资源区块刷新间隔 (tick)
      check-interval-ticks: 1200
      # 资源刷新基础冷却 (分钟)
      base-cooldown-minutes: 60
      # 每次刷新尝试生成的基础矿物数量
      base-resource-amount: 32
      # 每次资源刷新给予国家的基础经验
      base-experience: 120
    
    treasury-income:
      # 是否在资源区块刷新时自动给国家金库发放资源产出收入
      enabled: true
      # 每个资源区块每轮成功刷新后的金库基础收入
      base-income: 250.00
      # 每生成 1 个矿物方块额外给予多少金库收入
      income-per-generated-block: 15.00
```

---

## 领土配置

### 圈地定价

```yaml
nation:
  claims:
    pricing:
      # 详细定价显示的区块数量限制
      detail-limit: 32
      
      distance:
        # 是否让距离世界中心越远的区块越贵
        enabled: true
        # 世界中心坐标
        world-center-x: 0.0
        world-center-z: 0.0
        # 每隔多少方块增加一次距离价格倍率
        step-blocks: 1000
        # 每个距离阶梯增加多少倍率
        step-multiplier: 0.05
        # 距离倍率上限
        max-multiplier: 3.0
      
      biome:
        # 是否按生物群系调整价格
        enabled: true
        # 生物群系价格倍率范围
        min-multiplier: 0.50
        max-multiplier: 1.80
        # 生物群系资源丰富度覆盖
        richness-overrides:
          snowy_plains: 0.55
          desert: 0.65
          forest: 1.15
          jungle: 1.25
          mushroom_fields: 1.40
```

### 圈地工具

```yaml
nation:
  claims:
    tool:
      enabled: true
      material: GOLDEN_SHOVEL
      name: 领地权杖
      lore:
        - 左键选择第一个区块
        - 右键选择第二个区块
        - 输入 /sc n ok 确认圈地
```

### 领地保护

```yaml
nation:
  claims:
    protection:
      # 是否启用原生领地保护
      enabled: true
      # 是否允许本国成员操作本国领地
      allow-nation-members: true
      # 保护方块破坏和放置
      protect-build: true
      # 保护按钮、门、容器等交互
      protect-interactions: true
      # 保护水桶/岩浆桶
      protect-buckets: true
      # 保护实体
      protect-entities: true
      # 保护爆炸
      protect-explosions: true
      # 保护活塞
      protect-pistons: true
      # 保护实体恶意破坏
      protect-entity-grief: true
      # 保护液体流动
      protect-liquid-flow: true
      # 保护火焰蔓延
      protect-fire-spread: true
```

---

## 经济配置

```yaml
economy:
  transfer:
    # 转账税率 (0.0-1.0)
    tax-rate: 0.05
    # 最小转账金额
    min-amount: 1.0
    # 每日转账限额
    daily-limit: 100000.0
```

---

## 外交配置

### 外交反馈

```yaml
nation:
  diplomacy:
    feedback:
      enabled: true
      # 关系更新反馈
      relation-updated:
        sound: ENTITY_EXPERIENCE_ORB_PICKUP
        particle: END_ROD
        particle-count: 10
        actionbar: "外交关系已更新，国家态势发生变化"
      # 提案提交反馈
      relation-proposed:
        sound: UI_BUTTON_CLICK
        actionbar: "外交提案已提交，等待签署"
```

---

## 战争配置

```yaml
war:
  # 战争准备时间（小时）- 宣战后、战争开始前的准备期
  preparation_hours: 24
  # 最小动员率 - 宣战所需最低兵力占国家总兵力的比例
  min_mobilization_rate: 0.1
  # 最大战争持续时间（天）
  max_duration_days: 30
  # 宣战冷却时间（小时）
  declaration_cooldown_hours: 12
  # 最大战争赔款比例
  max_reparations_ratio: 0.5
  # 最小宣战费用（金币）
  min_declaration_cost: 1000.0
  # 允许同时进行的战争数量上限
  max_simultaneous_wars: 3
  # 胜利条件
  victory_condition: OCCUPATION
  # 占领胜利所需天数
  occupation_victory_days: 7
  # 是否允许投降
  surrender_allowed: true
  # 投降所需赔款比例
  surrender_reparations_ratio: 0.2
  # 是否允许联盟参战
  alliance_war_enabled: true
  # 战后和平期（天）
  post_war_peace_days: 7
```

### 战争消息

```yaml
war:
  notifications:
    announcements: true
    title_notifications: true
  messages:
    declaration: "§c§l[战争] §6%attacker%§e 向 §6%defender%§e 宣战！"
    start: "§c§l[战争] §e战争正式开始！准备时间结束。"
    end: "§a§l[和平] §e战争已结束，%winner%§e 获胜！"
    surrender: "§e§l[投降] §6%surrenderer%§e 向 §6%receiver%§e 投降。"
    peace: "§a§l[和平] §e和平协议已签订。"
```

---

## 官员配置

```yaml
nation:
  officers:
    # 允许管理资源区块迁移的官职
    resource-district-migration-roles:
      - marshal
    # 允许从本国金库支出的官职
    treasury-withdraw-roles:
      - treasurer
    # 允许设置外交关系的官职
    diplomacy-set-roles:
      - diplomat
    # 允许宣告战争的官职
    war-declare-roles:
      - marshal
    # 允许结束战争的官职
    war-end-roles:
      - marshal
      - diplomat
    # 允许激活政策的官职
    policy-set-roles:
      - steward
    # 允许清除政策的官职
    policy-clear-roles:
      - steward
    # 允许解锁科技的官职
    technology-unlock-roles:
      - steward
    # 允许撤销科技的官职
    technology-revoke-roles:
      - steward
```

### 官员反馈

```yaml
nation:
  officers:
    feedback:
      enabled: true
      officer-appointed:
        sound: ENTITY_PLAYER_LEVELUP
        particle: HAPPY_VILLAGER
        particle-count: 12
      officer-removed:
        sound: UI_BUTTON_CLICK
        actionbar: "官员任命已移除"
```

---

## 地图配置

```yaml
map:
  # 静态地图导出目录
  export-directory: ..
  web:
    # 是否启动内置战略地图 Web 服务
    enabled: true
    # 监听地址
    host: 127.0.0.1
    port: 8716
    # 对外访问地址
    public-url: ""
    # 地图个人链接签名密钥
    access-secret: change-this-secret
    # 个人地图链接有效期（分钟）
    access-ttl-minutes: 120
    # 是否启用地图实时推送
    sse-enabled: true
    # 是否允许玩家在网页地图上拖框选择领地
    claim-selection-enabled: true
    # 财政流水 CSV 导出是否写入 UTF-8 BOM
    finance-export:
      csv-bom-enabled: true
    # 单次网页拖框最多允许的区块数
    claim-max-chunks: 64
    # 网页提交后确认超时（分钟）
    claim-pending-minutes: 5
    # 圈地提交冷却秒数
    claim-cooldown-seconds: 10
```

### 地形瓦片配置

```yaml
map:
  web:
    # 是否启用地形瓦片渲染
    terrain-enabled: true
    # 单张地形瓦片的像素尺寸 (128/256/512)
    terrain-tile-pixels: 256
    # 内存地形瓦片缓存秒数
    terrain-tile-cache-seconds: 300
    # 内存缓存最大条目数
    terrain-tile-cache-max-entries: 512
    # 是否启用磁盘瓦片缓存
    terrain-tile-disk-cache-enabled: true
    # 磁盘缓存保留小时数
    terrain-tile-disk-cache-hours: 24
    # 是否在启动后预热地形瓦片
    terrain-prewarm-enabled: true
    # 预热半径（方块）
    terrain-prewarm-radius-blocks: 256
```

### 头像缓存

```yaml
map:
  web:
    avatar-cache-enabled: true
    # 头像缓存有效期（分钟）
    avatar-cache-ttl-minutes: 360
    avatar-upstreams:
      - "https://crafatar.com/avatars/{uuid}?size=64&overlay"
      - "https://minotar.net/avatar/{uuid}/64.png"
```

---

## 模块配置

```yaml
modules:
  # 核心模块
  nation: true
  government: true
  resolution: true
  treasury: true
  diplomacy: true
  policy: true
  map: true
  war: true
  technology: true
  resource: true
  officer: true
  event: true
  
  # 功能模块
  essentials: true
  pvp: true
  army: true
  storage: true
  title: true
  city: true
```

---

## 外部集成

### ProtectorAPI

```yaml
integrations:
  protectorapi:
    # 是否启用 ProtectorAPI 兼容桥
    enabled: true
    # 是否禁止圈地覆盖外部保护区
    block-claims-in-protected-areas: true
    # 是否额外采样区块地表高度
    sample-surface-height: true
    # 额外采样的 Y 层
    sample-y-levels: [64, 96, 160]
    # 采样点距离区块边缘的内缩方块数
    edge-inset-blocks: 1
```

---

## Redis 配置

```yaml
redis:
  # 是否启用跨服通信
  enabled: false
  host: 127.0.0.1
  port: 6379
  password: ""
  database: 0
```

---

## REST API 配置

```yaml
rest-api:
  enabled: false
  host: 127.0.0.1
  port: 8717
  signing-secret: change-this-secret
  api-key-ttl-hours: 24
  cors:
    enabled: true
    allowed-origins:
      - "*"
  rate-limit:
    enabled: true
    requests-per-minute: 60
    burst-size: 10
    block-duration-seconds: 60
  endpoints:
    nations:
      enabled: true
      requires-auth: false
    territories:
      enabled: true
      requires-auth: false
    stats:
      enabled: true
      requires-auth: false
    finance:
      enabled: true
      requires-auth: true
    websocket:
      enabled: true
      max-connections: 1000
      ping-interval-seconds: 30
```

---

## 视觉反馈配置

### 反馈总开关

```yaml
# 在各类 feedback 下设置 enabled 控制

nation:
  claims:
    feedback:
      enabled: true
  operations:
    feedback:
      enabled: true
  strategy:
    feedback:
      enabled: true
  diplomacy:
    feedback:
      enabled: true
  officers:
    feedback:
      enabled: true
  treasury:
    feedback:
      enabled: true
```

### 通用反馈格式

```yaml
# 每个事件可配置以下反馈类型
feedback:
  default:
    sound: ""              # 声音名称
    sound-volume: 0.7      # 音量
    sound-pitch: 1.0       # 音调
    particle: ""           # 粒子名称
    particle-count: 0      # 粒子数量
    particle-spread: 0.35  # 粒子扩散
    particle-y-offset: 1.0 # 粒子 Y 轴偏移
    actionbar: ""          # 动作栏消息
    title: ""              # 主标题
    subtitle: ""           # 副标题
    title-fade-in-ticks: 5    # 标题淡入时间
    title-stay-ticks: 30      # 标题停留时间
    title-fade-out-ticks: 8   # 标题淡出时间
    bossbar: ""            # BossBar 消息
    bossbar-color: GREEN   # BossBar 颜色
    bossbar-overlay: PROGRESS
    bossbar-progress: 1.0
    bossbar-duration-ticks: 20
```

### BossBar 颜色选项

- `BLUE`, `GREEN`, `PINK`, `PURPLE`, `RED`, `WHITE`, `YELLOW`

### BossBar 样式选项

- `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`

---

## 菜单配置

```yaml
menu:
  # 菜单提供者类型
  # - auto: 自动选择最佳可用提供者（推荐）
  # - nightcore: NightCore Dialog
  # - protocollib: ProtocolLib AnvilGUI
  # - triumph: TriumphGUI 箱子界面
  # - fallback: 纯聊天式交互
  provider: auto
```

---

## 纪元配置

```yaml
epoch:
  enabled: true
  start-time: "1970-01-01T00:00:00Z"
  length:
    amount: 1
    unit: days  # seconds/minutes/hours/days
```

---

## 时间同步配置

```yaml
time-sync:
  enabled: false
  # 使用哪个现实世界时区
  timezone: Asia/Shanghai
  # 同步哪些世界
  worlds: []
  # 同步间隔 (tick)
  interval-ticks: 200
  # 是否关闭原版 daylightCycle
  freeze-daylight-cycle: true
  # 是否允许同步下界和末地
  allow-nether-end: false
```

---

## 核心配置

```yaml
core:
  # 异步线程数
  async-threads: 4
  # 调试模式
  debug: false
```

---

## 事件分类配置

```yaml
ledger:
  categories:
    finance:
      prefixes: [treasury., resource.]
    resource-income:
      event-types: [treasury.resource-income]
    income:
      event-types: [treasury.income]
    reward:
      event-types: [treasury.reward]
    tax:
      event-types: [treasury.tax]
    deposit:
      event-types: [treasury.deposit]
    withdraw:
      event-types: [treasury.withdraw]
```

---

## 城市配置

```yaml
city:
  enabled: true
  max-cities-per-nation: 5
  max-residents-per-city: 100
  create-cost: 100.00
  economy:
    initial-treasury: 0.00
    deposit-requires-permission: false
    withdraw-requires-mayor: true
```

---

## 军队配置

```yaml
army:
  max-armies-per-nation: 10
  max-soldiers-per-army: 1000
  auto-supply-in-territory: true
```

---

## 配置重载

修改配置后使用以下命令重载：

```bash
/starcore reload
```

**注意：**
- 模块开关 (`modules.*`) 修改后需要重启服务器
- 普通数值配置可用 `/starcore reload` 重载

---

## 配置验证

查看当前配置状态：

```bash
/starcore status
```

---

**文档版本:** v1.0  
**最后更新:** 2026-06-24
