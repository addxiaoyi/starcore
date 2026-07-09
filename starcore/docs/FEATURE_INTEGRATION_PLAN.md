# StarCore 基于 TownyPlus 分析的功能集成计划

## 1. 分析总结

TownyPlus 的核心特色：
- 跨平台聊天集成 (TownyChat/VentureChat Hook)
- DiscordSRV 双向同步
- REST API 服务 (Javalin)
- Towny 事件监听系统
- Cloud Command Framework
- Adventure/MiniMessage 文本组件

**StarCore 已有基础**:
- TreasuryModule: 国库、税收、预算、贷款、破产管理
- City/CityManager: 城镇基础系统
- ChatCommand: 聊天频道切换
- api/v1: REST API 模块
- StarCoreEventBus: 事件系统

---

## 2. 可集成功能列表

### 2.1 税收系统增强 (P1 - 高优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 差异化税率 | 根据城镇等级设置不同税率 | P1 | TaxationService |
| 滞纳金系统 | 欠税玩家惩罚机制 | P2 | - |
| 税收豁免 | 特定玩家/官职可免税 | P2 | - |
| 商业税种 | 按商店数量征收 | P2 | - |
| 关税系统 | 对外国商人征收 | P3 | - |

### 2.2 城镇升级系统 (P1 - 高优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 升级条件细化 | 居民/领地/金币多维度要求 | P1 | City.levelUp() |
| 升级奖励 | 升级后解锁功能和加成 | P1 | - |
| 城镇类型专属buff | 不同城市类型不同加成 | P2 | CityType 枚举 |
| 降级机制 | 不满足条件时降级 | P3 | - |
| 城镇声望 | 升级影响声望 | P3 | - |

### 2.3 领土扩展系统 (P1 - 高优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 领土等级 | 高等级城镇可圈更多地 | P1 | NationService |
| 领土购买折扣 | 城镇等级影响圈地价格 | P1 | - |
| 特殊区域类型 | 资源区、商业区、军事区 | P2 | - |
| 领土税 | 按持有的领土面积征税 | P1 | TaxationService |
| 领土转让 | 城镇间交易领土 | P2 | - |

### 2.4 跨平台聊天系统 (P2 - 中优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 国家频道 | Nation 内部聊天 | P2 | ChatCommand |
| 联盟频道 | Alliance 跨国家聊天 | P2 | - |
| 城镇频道 | City 内部聊天 | P2 | - |
| Discord 同步 | MC <-> Discord 双向消息 | P2 | - |
| 频道权限 | 不同官职使用不同频道 | P2 | - |

### 2.5 事件监听增强 (P2 - 中优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 城镇踢人事件 | KickedFromCityEvent | P2 | - |
| 市长变更事件 | MayorChangeEvent | P2 | - |
| 宣战事件 | NationDeclareWarEvent | P2 | WarModule |
| 投降事件 | NationSurrenderEvent | P2 | - |
| 领土易主事件 | TerritoryCapturedEvent | P2 | - |

### 2.6 REST API 增强 (P2 - 中优先级)

| 功能 | 描述 | 优先级 | 现有基础 |
|------|------|--------|----------|
| 城镇 CRUD | /api/cities/* 端点 | P2 | api/v1 |
| 税收统计 | /api/nations/{id}/taxes | P2 | TaxationService |
| 经济趋势 | /api/nations/{id}/economy | P2 | TreasuryModule |
| Webhook 支持 | 事件推送通知 | P3 | - |

---

## 3. 实现优先级

### Phase 1: 核心完善 (1-2 周)

**P1 功能 - 必须实现**

1. **城镇升级条件细化**
   - 新增文件: `module/city/CityUpgradeService.java`
   - 修改: `City.java` 升级逻辑
   - 配置: `city-levels.yml`

2. **升级奖励系统**
   - 新增接口: `CityBonusProvider`
   - 实现类: 各类加成 (领土上限、金币加成、税率减免)
   - 配置: `city-bonuses.yml`

3. **领土等级联动**
   - 修改: `NationModule` 领土限制计算
   - 新增: `TerritoryLimitCalculator`
   - 配置: `territory-limits.yml`

4. **差异化税率**
   - 扩展: `TaxationService`
   - 新增: `CityTaxType`, `LandTierTax`
   - GUI: 税率设置界面

### Phase 2: 功能增强 (2-3 周)

**P2 功能 - 重要但非紧急**

5. **城镇/国家聊天频道**
   - 新增: `module/chat/ChannelManager.java`
   - 扩展: `ChatFormatterService`
   - 配置: `chat-channels.yml`

6. **事件监听系统**
   - 新增监听器: `CityEventListener.java`
   - 事件类: `MayorChangeEvent`, `CityLevelUpEvent`
   - 记录审计日志

7. **REST API 城镇端点**
   - 新增: `endpoint/CityEndpoint.java`
   - DTO: `CityDto.java`
   - 端点: CRUD + 升级 + 税率

8. **Discord 同步 (可选)**
   - 新增: `integration/DiscordSyncService.java`
   - 依赖: DiscordSRV (软依赖)

### Phase 3: 高级功能 (3-4 周)

**P3 功能 - 锦上添花**

9. **特殊区域类型**
   - 新增: `DistrictType` 枚举
   - 实现: `DistrictManager`
   - 配置: `districts.yml`

10. **领土转让系统**
    - 新增: `TerritoryTradeService`
    - 交易机制: 拍卖/直接交易

11. **城镇声望系统**
    - 新增: `CityReputationService`
    - 声望来源: 战争胜负、升级完成

12. **Webhook 通知**
    - 新增: `WebhookService`
    - 事件触发: 战争、升级、税收

---

## 4. 需要新增的模块

### 4.1 模块结构

```
module/
├── city-upgrade/           # 新增: 城镇升级模块
│   ├── CityUpgradeModule.java
│   ├── CityUpgradeService.java
│   ├── bonus/
│   │   ├── CityBonus.java
│   │   ├── BonusType.java
│   │   ├── TerritoryBonus.java
│   │   ├── TaxBonus.java
│   │   └── CombatBonus.java
│   └── gui/
│       ├── CityUpgradeGui.java
│       └── CityUpgradeGuiListener.java
│
├── territory-expansion/    # 新增: 领土扩展模块
│   ├── TerritoryExpansionModule.java
│   ├── TerritoryLimitService.java
│   ├── TerritoryTradeService.java
│   ├── district/
│   │   ├── DistrictType.java
│   │   └── DistrictManager.java
│   └── gui/
│       └── TerritoryGui.java
│
├── enhanced-tax/           # 新增: 增强税收模块
│   ├── EnhancedTaxModule.java
│   ├── DifferentialTaxService.java
│   ├── LateFeeService.java
│   ├── TaxExemptionService.java
│   └── gui/
│       └── TaxSettingsGui.java
│
└── cross-platform-chat/    # 新增: 跨平台聊天模块
    ├── CrossChatModule.java
    ├── ChannelManager.java
    ├── AllianceChannel.java
    ├── DiscordSyncService.java
    ├── hook/
    │   ├── ChatHook.java
    │   ├── TownyChatHook.java
    │   └── VentureChatHook.java
    └── listener/
        └── CrossChatListener.java
```

### 4.2 配置文件

```
resources/
├── city-levels.yml         # 城镇等级配置
├── city-bonuses.yml       # 城镇加成配置
├── territory-limits.yml    # 领土限制配置
├── districts.yml          # 区域类型配置
├── enhanced-tax.yml       # 增强税收配置
├── chat-channels.yml      # 聊天频道配置
└── discord-sync.yml       # Discord 同步配置
```

---

## 5. 与现有模块的整合

### 5.1 模块依赖图

```
StarCorePlugin
├── TreasuryModule (已有)
│   └── <- EnhancedTaxModule (新增)
│       └── 使用 TaxationService
│
├── NationModule (已有)
│   ├── <- CityUpgradeModule (新增)
│   │   └── 升级影响国家加成
│   ├── <- TerritoryExpansionModule (新增)
│   │   └── 领土上限联动
│   └── <- EnhancedTaxModule (新增)
│       └── 领土税计算
│
├── CityManager (已有)
│   └── <- CityUpgradeModule (新增)
│       └── 升级逻辑集成
│
├── ChatCommand (已有)
│   └── <- CrossChatModule (新增)
│       └── 频道管理器集成
│
└── EventBus (已有)
    └── <- 所有新模块的事件
```

### 5.2 关键接口设计

```java
// CityBonusProvider - 城镇加成提供者
public interface CityBonusProvider {
    BonusType getType();
    double apply(NationId nationId, City city);
    boolean isActive(City city);
}

// TerritoryLimitCalculator - 领土限制计算器
public interface TerritoryLimitCalculator {
    int calculateLimit(City city);
    double getClaimCost(City city);
    boolean canClaim(City city);
}

// TaxHook - 税收钩子
public interface TaxHook {
    BigDecimal calculate(NationId nationId, TaxContext context);
    void onCollected(NationId nationId, BigDecimal amount);
}
```

### 5.3 事件定义

```java
// CityLevelUpEvent - 城镇升级事件
public record CityLevelUpEvent(
    City city,
    int oldLevel,
    int newLevel,
    CityBonus bonus
) {}

// MayorChangeEvent - 市长变更事件
public record MayorChangeEvent(
    City city,
    UUID oldMayor,
    UUID newMayor
) {}

// TerritoryCapturedEvent - 领土占领事件
public record TerritoryCapturedEvent(
    UUID territoryId,
    NationId previousOwner,
    NationId newOwner
) {}
```

---

## 6. 实施建议

### 6.1 开发顺序

1. **CityUpgradeModule** (最先实现)
   - 影响所有城镇系统
   - 为其他系统提供加成基础

2. **TerritoryExpansionModule** (第二)
   - 依赖 CityUpgradeModule
   - 领土系统是核心玩法

3. **EnhancedTaxModule** (第三)
   - 依赖 TerritoryExpansionModule
   - 税收是经济核心

4. **CrossChatModule** (可选)
   - 独立功能
   - 提升玩家体验

### 6.2 数据库变更

```sql
-- 城镇加成表
CREATE TABLE city_bonuses (
    city_id VARCHAR(36) PRIMARY KEY,
    bonus_type VARCHAR(32),
    bonus_value DOUBLE,
    activated_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- 领土等级表
CREATE TABLE territory_limits (
    city_level INT PRIMARY KEY,
    max_claims INT,
    claim_cost_modifier DOUBLE
);

-- 区域类型表
CREATE TABLE city_districts (
    territory_id VARCHAR(36) PRIMARY KEY,
    district_type VARCHAR(32),
    bonus_type VARCHAR(32),
    bonus_value DOUBLE
);
```

### 6.3 配置示例

```yaml
# city-levels.yml
levels:
  1:
    name: "定居点"
    color: "&7"
    requiredResidents: 5
    requiredTerritories: 2
    requiredGold: 1000
  5:
    name: "城市"
    color: "&e"
    requiredResidents: 50
    requiredTerritories: 25
    requiredGold: 50000

# city-bonuses.yml
bonuses:
  territory_limit:
    per_level: 10
    base: 50
  claim_cost_discount:
    per_level: 0.05
    max: 0.5
  tax_reduction:
    per_level: 0.02
    max: 0.2
```

---

## 7. 总结

基于 TownyPlus 分析，StarCore 需要优先实现:

| 优先级 | 模块 | 功能 | 工作量 |
|--------|------|------|--------|
| P1 | CityUpgradeModule | 城镇升级系统 | 中 |
| P1 | TerritoryExpansionModule | 领土扩展 | 大 |
| P1 | EnhancedTaxModule | 差异化税率 | 中 |
| P2 | CrossChatModule | 跨平台聊天 | 中 |
| P2 | 事件监听增强 | 城镇事件 | 小 |
| P3 | 高级功能 | 声望/拍卖 | 大 |

建议先实现 P1 三个模块，建立核心玩法框架，再逐步扩展 P2/P3 功能。
