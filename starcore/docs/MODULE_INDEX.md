# StarCore 模块清单

本文档列出 StarCore 所有模块的详细信息，包括功能、API、配置等。

## 模块索引

### 核心框架 (core/)

| 模块 | 类 | 功能 | 状态 |
|------|-----|------|------|
| 模块管理器 | `ModuleManager` | 模块生命周期管理 | ✅ |
| 数据库服务 | `DatabaseService` | MySQL/SQLite 连接池 | ✅ |
| 配置服务 | `ConfigurationService` | YAML 配置读写 | ✅ |
| 事件总线 | `StarCoreEventBus` | 跨模块事件通信 | ✅ |
| 任务调度 | `StarCoreScheduler` | 异步任务 (Folia兼容) | ✅ |
| 服务注册表 | `ServiceRegistry` | 依赖注入 | ✅ |
| 持久化服务 | `PersistenceService` | 配置持久化 | ✅ |
| 跨服服务 | `RedisCrossServerService` | Redis 跨服通信 | ✅ |
| 性能监控 | `PerformanceMetricsService` | TPS/内存监控 | ✅ |
| 缓存管理 | `CacheManager` | 数据缓存 | ✅ |

### 国家与领土 (module/nation/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 国家系统 | `NationModule` | 创建/解散国家、成员管理 | - |
| 国家服务 | `NationService` | 国家 CRUD 操作 | - |
| 领土申领 | `ClaimToolService` | 圈地工具 | - |
| 国家持久化 | `DatabaseAwareNationStateStorage` | 国家数据存储 | - |
| 原生保护 | `NativeTerritoryProtectionListener` | 领土保护监听 | - |

**数据库表**: `nations`, `nation_members`, `nation_claims`

### 政府系统 (module/government/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 政府模块 | `GovernmentModule` | 政体管理 |
| 政府服务 | `GovernmentService` | 政体类型切换 |

**政体类型**:
- `MONARCHY` - 君主制
- `REPUBLIC` - 共和制
- `DICTATORSHIP` - 独裁制
- `THEOCRACY` - 神权制
- `DEMOCRACY` - 民主制
- `OLIGARCHY` - 寡头制
- `FEDERATION` - 联邦制

### 外交系统 (module/diplomacy/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 外交模块 | `DiplomacyModule` | 外交关系管理 |
| 外交服务 | `DiplomacyService` | 联盟/停战/宣战 |

**外交状态**: ALLY, NEUTRAL, TRUCE, HOSTILE, WAR

### 战争系统 (module/war/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 战争模块 | `WarModule` | 战争管理 |
| 战争服务 | `WarService` | 宣战/停战/战斗 |

### 政策系统 (module/policy/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 政策模块 | `PolicyModule` | 国家政策 |
| 政策服务 | `PolicyService` | 政策效果应用 |

### 科技系统 (module/technology/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 科技模块 | `TechnologyModule` | 科技树 | - |
| 科技服务 | `TechnologyService` | 研发升级 | `technologies.yml` |
| 科技成本 | `TechnologyCost` | 研发费用 | - |

### 资源系统 (module/resource/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 资源模块 | `ResourceModule` | 资源类型 | - |
| 资源服务 | `ResourceService` | 采集加工 | `resources.yml` |
| 资源区域 | `NationResourceDistrict` | 资源产地 | - |

### 决议系统 (module/resolution/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 决议模块 | `ResolutionModule` | 投票决议 |
| 决议服务 | `ResolutionService` | 发起/投票/执行 |

### 国库系统 (module/treasury/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 国库模块 | `TreasuryModule` | 国家资金 |
| 国库服务 | `TreasuryService` | 存取款/审计 |

### 官职系统 (module/officer/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 官职模块 | `OfficerModule` | 官员管理 |
| 官职服务 | `OfficerService` | 任命/罢免 |

### 军事系统 (module/army/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 军事模块 | `ArmyModule` | 军队管理 |
| 军事服务 | `ArmyService` | 编制/训练 |

### 地图系统 (module/map/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 地图模块 | `MapModule` | 网页地图 |
| 地图服务 | `MapAccessManager` | 权限管理 |
| 标记 | `MapMarker` | 地图标记 |
| 领土多边形 | `MapTerritoryPolygon` | 领土边界 |

**网页资源**: `web/map/` (Leaflet.js)

### 区域标题 (region/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 区域模块 | `RegionModule` | 区域管理 | `region-config.yml` |
| 区域集成 | `RegionIntegrationService` | 第三方集成 |
| 标题显示 | `TitleDisplayService` | 进入区域显示标题 |

### 称号系统 (title/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 称号模块 | `TitleModule` | 称号管理 | `titles.yml` |
| 称号服务 | `TitleService` | 称号 CRUD | - |
| 徽章服务 | `BadgeService` | 徽章管理 | `badges.yml` |
| PAPI 扩展 | `StarCorePlaceholderExpansion` | 占位符 | - |

**占位符**:
- `%starcore_nation%` - 所属国家
- `%starcore_title%` - 称号
- `%starcore_badge%` - 徽章
- `%starcore_rank%` - 排名

### 社交系统 (social/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 社交模块 | `SocialModule` | 好友/邮件 |
| 好友服务 | `FriendService` | 好友管理 |
| 邮件服务 | `MailService` | 站内邮件 |

### 成就系统 (achievement/)

| 模块 | 类 | 功能 | 配置 |
|------|-----|------|------|
| 成就模块 | `AchievementModule` | 成就管理 | `achievements.yml` |
| 成就服务 | `AchievementService` | 成就解锁 | - |

### 仓库系统 (storage/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 仓库模块 | `StorageModule` | 个人仓库 |
| 仓库服务 | `StorageService` | 存取物品 |

### 经济系统 (economy/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 经济模块 | `EconomyModule` | 货币系统 |
| 经济服务 | `EconomyService` | 余额管理 |

### 基础功能 (essentials/)

| 子模块 | 功能 |
|--------|------|
| `home/` | 家设置 |
| `warp/` | 传送点 |
| `teleport/` | 传送请求 |
| `nickname/` | 昵称 |
| `baltop/` | 财富排行 |

### PvP 系统 (pvp/)

| 模块 | 类 | 功能 |
|------|-----|------|
| PvP 模块 | `PvPModule` | PvP 规则 |
| 决斗服务 | `DuelService` | 1v1 决斗 |

### 任务系统 (quest/, daily/)

| 模块 | 配置 |
|------|------|
| 任务系统 | `quest/quests.yml` |
| 每日任务 | `quest/daily_quests.yml` |
| 委托配置 | `quest/commission_config.yml` |

### 随机事件 (event/)

| 模块 | 配置 |
|------|------|
| 事件系统 | `events.yml` |
| 天气效果 | `weather_effects.yml` |

### 赛季系统 (ai/season/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 赛季模块 | `SeasonModule` | 赛季管理 |
| 赛季服务 | `SeasonService` | 赛季切换 |

### 领地保护 (protection/, foundation/territory/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 领土服务 | `TerritoryService` | 领地管理 |
| 外部保护 | `ExternalProtectionService` | 第三方集成 |

### NPC 系统 (npc/)

| 模块 | 类 | 功能 |
|------|-----|------|
| NPC 模块 | `NpcModule` | NPC 管理 |
| Citizens 集成 | - | NPC 对话 |

### 跨服系统 (crossserver/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 跨服服务 | `RedisCrossServerService` | Redis 通信 |
| 消息传输 | `TransportService` | 跨服消息 |

### 审计系统 (audit/)

| 模块 | 类 | 功能 |
|------|-----|------|
| 审计模块 | `AuditModule` | 操作日志 |
| 审计服务 | `AuditService` | 日志记录 |

## 服务依赖图

```
StarCorePlugin
├── StarCoreContext
│   ├── DatabaseService
│   │   ├── MySQL/PostgreSQL
│   │   └── SQLite
│   ├── RedisCrossServerService
│   │   └── Redis
│   ├── ModuleManager
│   │   ├── NationModule
│   │   │   ├── TerritoryService
│   │   │   └── NationService
│   │   ├── GovernmentModule
│   │   ├── DiplomacyModule
│   │   ├── WarModule
│   │   ├── PolicyModule
│   │   ├── TechnologyModule
│   │   ├── ResourceModule
│   │   ├── TreasuryModule
│   │   ├── OfficerModule
│   │   └── ArmyModule
│   └── ServiceRegistry
│       ├── TitleModule
│       ├── RegionModule
│       ├── SocialModule
│       ├── AchievementModule
│       └── ...
└── StarCoreEventBus
```

## 配置依赖

| 配置项 | 影响模块 |
|--------|----------|
| `database.type` | DatabaseService |
| `redis.enabled` | RedisCrossServerService |
| `modules.*.enabled` | 各模块 |
| `nation.claim-cost` | NationModule |
| `war.declare-cost` | WarModule |
