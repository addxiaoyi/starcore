# StarCore 已实现功能清单

> StarCore - 下一代 Minecraft 国家策略插件  
> 版本: 1.0.0 | Java 21 | Paper 1.21.11

## 📊 项目统计

| 指标 | 数量 |
|------|------|
| Java 文件 | 769 |
| 模块数量 | 23 |
| 命令数量 | 60+ |
| 配置文件 | 15+ |

---

## 🏛️ 核心模块 (Core)

### 1. 国家系统 (NationModule)
- [x] 国家创建/解散
- [x] 国家成员管理 (加入/离开/邀请)
- [x] 国家领土认领 (Chunk 级别)
- [x] 国家等级系统
- [x] 首都设置
- [x] 政府类型变更
- [x] 经验值积累

### 2. 领土系统 (RegionModule)
- [x] 子区域创建/编辑
- [x] 区域权限管理
- [x] 区域进入/退出事件
- [x] 区域类型 (城市/资源区/特殊区域)
- [x] 跨世界领土支持

### 3. 外交系统 (DiplomacyModule)
- [x] 外交关系类型 (友好/敌对/中立/联盟)
- [x] 关系冷却时间
- [x] 关系历史记录
- [x] 联盟战争支持

### 4. 战争系统 (WarModule)
- [x] 宣战/停战
- [x] 战争状态机 (准备期/活跃期/结束)
- [x] 战争目标 (领土/征服/防御)
- [x] 战争积分系统
- [x] 战争事件发布

### 5. 科技系统 (TechnologyModule)
- [x] 多时代科技树
- [x] 科技研发进度
- [x] 前置科技解锁
- [x] 科技效果应用 (属性加成/药水效果)
- [x] 研发成本与时间
- [x] 互斥科技

---

## 🏙️ 城市与官员

### 6. 城市系统 (CityModule)
- [x] 城市创建/升级
- [x] 城市邀请机制
- [x] 城市公告发布
- [x] 城市等级系统
- [x] 城市设置菜单
- [x] 城市 GUI 界面

### 7. 官员系统 (OfficerModule)
- [x] 可配置官员角色
- [x] 官员任命/移除
- [x] 官员列表查看
- [x] 官员 GUI 管理菜单
- [x] 权限层级

### 8. 政府系统 (GovernmentModule)
- [x] 议会服务 (ParliamentService)
- [x] 政党服务 (PartyService)
- [x] 法庭服务 (CourtService)
- [x] 判决执行服务 (CourtExecutionService)
- [x] 政府类型 (君主制/共和制/议会制/独裁制)

---

## 💰 经济与财政

### 9. 经济系统 (Foundation)
- [x] 玩家货币系统
- [x] Vault API 集成
- [x] 转账功能
- [x] 余额查询
- [x] 经济封锁

### 10. 国库系统 (TreasuryModule)
- [x] 国家金库
- [x] 存款/取款
- [x] 税收服务 (TaxationService)
- [x] 贷款服务 (LoanService)
- [x] 预算服务 (BudgetService)
- [x] 破产管理 (BankruptcyService)
- [x] 债务追踪

### 11. 税收系统 (module/treasury)
- [x] 土地税
- [x] 商业税
- [x] 所得税
- [x] 关税
- [x] 税收配置
- [x] 税收历史记录
- [x] 自动/手动征收

### 12. 资源系统 (ResourceModule)
- [x] 国家资源储量
- [x] 资源市场价格
- [x] 市场稳定性
- [x] 垄断检测
- [x] 资源贸易
- [x] 配额管理
- [x] 加工系统

---

## ⚔️ 军事与战争

### 13. 军队系统 (ArmyModule)
- [x] 军队创建/解散
- [x] 兵种系统 (步兵/骑兵/弓箭手/攻城/守军)
- [x] 兵种克制
- [x] 军队训练/补给
- [x] 军队移动
- [x] 攻城系统
- [x] 战场服务
- [x] 军事限制

### 14. 战争核心 (war/)
- [x] WarService - 战争管理
- [x] BattlefieldService - 战场管理
- [x] MobilizationService - 动员服务
- [x] IntelligenceService - 情报服务
- [x] WarEconomyService - 战争经济
- [x] SupplyLine - 补给线
- [x] PeaceTreatyService - 和平条约
- [x] 战争持久化

---

## 🎉 社交系统

### 15. 好友系统 (SocialModule)
- [x] 好友添加/删除
- [x] 好友列表
- [x] 好友邀请
- [x] 好友私聊

### 16. 公会系统 (SocialModule)
- [x] 公会创建/解散
- [x] 公会邀请
- [x] 公会成员管理
- [x] 公会标签设置
- [x] 公会列表

### 17. 派对系统 (SocialModule)
- [x] 派对创建
- [x] 邀请玩家
- [x] 踢出玩家
- [x] 派对聊天

### 18. 私信系统
- [x] 私聊 `/msg`
- [x] 快速回复 `/r`
- [x] 消息屏蔽

---

## 🎮 PvP 系统

### 19. 决斗系统 (PvPModule)
- [x] 决斗请求
- [x] 竞技场管理
- [x] 决斗套装
- [x] BO 制比赛
- [x] 观战系统
- [x] 决斗奖励
- [x] 决斗统计

### 20. PvP 统计
- [x] 击杀/死亡统计
- [x] KDA 计算
- [x] 连杀追踪
- [x] 排行榜系统

---

## 📜 决议系统

### 21. 决议模块 (ResolutionModule)
- [x] 决议创建/签署
- [x] 决议类型 (加入国家/修改政体/宣战等)
- [x] 签名要求
- [x] 决议执行
- [x] 决议历史
- [x] 决议取消

---

## 🎲 事件系统

### 22. 随机事件 (EventModule)
- [x] 事件配置加载
- [x] 触发器系统
- [x] 条件触发
- [x] 效果系统 (经济/作物/生成/建筑)
- [x] 事件链
- [x] 冷却管理
- [x] 事件响应菜单

### 23. 疾病与宗教服务
- [x] 疾病效果
- [x] 宗教信仰
- [x] 祝福系统

---

## 🏠 基础功能

### 24. 仓库系统 (StorageModule)
- [x] 个人仓库
- [x] 仓库升级
- [x] 仓库 GUI
- [x] 超限处理

### 25. 传送系统
- [x] 家设置 `/sethome`
- [x] 传送点 `/setwarp`
- [x] TPA 请求
- [x] 回城 `/back`
- [x] 传送延迟

### 26. 排行榜
- [x] 击杀排行榜
- [x] 死亡排行榜
- [x] 在线时间榜
- [x] KDA 排行榜
- [x] 排行榜 GUI
- [x] 数据持久化

---

## 🏷️ 称号系统

### 27. 称号模块 (TitleModule)
- [x] 称号创建/编辑
- [x] 称号装备/卸下
- [x] Tab 列表显示
- [x] 聊天前缀
- [x] 全息投影支持
- [x] PlaceholderAPI 扩展

---

## 🗺️ 地图系统

### 28. WebMap (MapModule)
- [x] 实时地图数据
- [x] Web 服务器 (Undertow)
- [x] REST API
- [x] CORS 配置
- [x] SSL 支持
- [x] 玩家位置
- [x] 领土显示
- [x] 资源区显示

---

## 🔧 管理功能

### 29. 管理命令
- [x] 禁言 `/mute`
- [x] 封禁 `/ban`
- [x] 监禁 `/jail`
- [x] 踢出 `/kick`
- [x] 隐身 `/vanish`

### 30. 权限系统
- [x] 基于官员的权限
- [x] 基于国家的权限
- [x] PermissionUtil 工具

---

## 🗄️ 数据与持久化

### 31. 数据层
- [x] MySQL 支持
- [x] SQLite 支持
- [x] Properties 文件
- [x] JSON 存储
- [x] Flyway 数据库迁移
- [x] 数据库连接池 (HikariCP)

### 32. 缓存系统
- [x] Caffeine Cache
- [x] 内存压力检测
- [x] 自动驱逐

### 33. 跨服通信
- [x] Redis 集成
- [x] 跨服消息
- [x] 玩家数据同步

---

## 📡 服务架构

### 34. 核心服务 (Foundation)
- [x] 消息服务 (MessageService)
- [x] 权限服务 (PermissionService)
- [x] 经济服务 (EconomyService)
- [x] 领地服务 (TerritoryService)
- [x] 时间同步 (TimeSyncService)
- [x] 性能指标 (PerformanceMetrics)

### 35. 模块架构
- [x] StarCoreModule 接口
- [x] 模块管理器
- [x] 服务注册表
- [x] 事件总线
- [x] 调度器 (Folia 兼容)

---

## 🔌 第三方集成

- [x] Vault API
- [x] PlaceholderAPI
- [x] Citizens NPC
- [x] PacketEvents
- [x] ProtocolLib
- [x] TriumphGUI
- [x] HolographicDisplays
- [x] DecentHolograms

---

## 📁 命令列表

| 命令 | 功能 | 权限 |
|------|------|------|
| `/starcore` | 核心指令入口 | 所有玩家 |
| `/menu` | 主菜单 | 所有玩家 |
| `/nation` | 国家管理 | 国家成员 |
| `/city` | 城市管理 | 城市成员 |
| `/war` | 战争系统 | OP/管理员 |
| `/diplomacy` | 外交关系 | 国家成员 |
| `/tech` | 科技研发 | 国家成员 |
| `/army` | 军队管理 | 国家成员 |
| `/friend` | 好友系统 | 所有玩家 |
| `/guild` | 公会系统 | 所有玩家 |
| `/party` | 派对系统 | 所有玩家 |
| `/duel` | 决斗系统 | 所有玩家 |
| `/tax` | 税收管理 | 国家官员 |
| `/treasury` | 国库管理 | 国家官员 |
| `/loan` | 贷款管理 | 国家官员 |
| `/court` | 法庭系统 | 国家成员 |
| `/parliament` | 议会系统 | 国家成员 |
| `/politicalparty` | 政党系统 | 国家成员 |
| `/title` | 称号系统 | 所有玩家 |
| `/rank` | 排行榜 | 所有玩家 |
| `/balance` | 查询余额 | 所有玩家 |
| `/pay` | 转账 | 所有玩家 |
| `/home` | 家传送 | 所有玩家 |
| `/warp` | 传送点 | 所有玩家 |
| `/tpa` | 传送请求 | 所有玩家 |
| `/webmap` | 地图管理 | 管理员 |
| `/resource` | 资源管理 | 国家成员 |

---

## ⚠️ 待完善功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 商业系统 (BusinessService) | 待开发 | 商业税数据来源 |
| 交易历史 (TransactionHistory) | 待开发 | 准确所得税计算 |
| 联盟系统增强 | 部分完成 | 需要完整实现 |
| 外交条约 | 部分完成 | 需要更多条约类型 |
| 间谍系统 | 部分完成 | IntelligenceService 需完善 |

---

## 📝 配置文件

- `config.yml` - 主配置
- `messages_zh_cn.yml` - 中文消息
- `messages_en_us.yml` - 英文消息
- `nations/` - 国家数据
- `ranks.yml` - 官职配置
- `roles.yml` - 官员角色配置
- `technologies/` - 科技树配置
- `events.yml` - 随机事件配置

---

## 🚀 技术栈

- **语言**: Java 21
- **平台**: Paper 1.21.11
- **构建**: Maven
- **数据库**: MySQL / SQLite
- **缓存**: Caffeine
- **Web**: Undertow
- **跨服**: Redis / RabbitMQ
- **数据库迁移**: Flyway
