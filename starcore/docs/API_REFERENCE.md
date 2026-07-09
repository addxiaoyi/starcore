# STARCORE API 参考文档

**版本:** 0.1.0-SNAPSHOT  
**更新日期:** 2026-06-24  
**API 版本:** v1

---

## 目录

- [概述](#概述)
- [获取 API 实例](#获取-api-实例)
- [核心接口](#核心接口)
- [服务接口](#服务接口)
- [REST API](#rest-api)
- [DTO 对象](#dto-对象)
- [事件系统](#事件系统)
- [集成示例](#集成示例)

---

## 概述

STARCORE 提供两种 API 访问方式：

1. **Java API** - 通过 Bukkit ServicesManager 获取 `StarCoreApi` 接口
2. **REST API** - 通过 HTTP 访问 `/api/*` 端点

### 版本信息

```java
String version = api.version();  // "0.1.0-SNAPSHOT"
```

### 模块列表

```java
Collection<ModuleDescriptor> modules = api.modules();
for (ModuleDescriptor module : modules) {
    System.out.println(module.getId() + " v" + module.getVersion());
}
```

---

## 获取 API 实例

### Bukkit ServicesManager

```java
import dev.starcore.starcore.api.StarCoreApi;
import org.bukkit.Bukkit;

public class MyPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // 获取 StarCore API
        var provider = getServer().getServicesManager()
            .getRegistration(StarCoreApi.class);
        
        if (provider != null) {
            StarCoreApi api = provider.getProvider();
            
            // 使用 API
            api.nationService().ifPresent(service -> {
                getLogger().info("NationService available");
            });
        } else {
            getLogger().warning("StarCore not found!");
        }
    }
}
```

### 获取服务

使用泛型方法 `service(Class<T>)` 获取任何注册的服务：

```java
// 推荐方式：使用类型安全的便捷方法
api.nationService()           // Optional<NationService>
api.treasuryService()         // Optional<TreasuryService>
api.warService()              // Optional<WarService>

// 或者使用通用方法
api.service(NationService.class)    // Optional<NationService>
api.service(TreasuryService.class)  // Optional<TreasuryService>
```

---

## 核心接口

### StarCoreApi

主 API 接口，提供所有服务的访问入口。

```java
package dev.starcore.starcore.api;

public interface StarCoreApi {
    String version();
    Collection<ModuleDescriptor> modules();
    <T> Optional<T> service(Class<T> serviceType);
    
    // REST API
    default Optional<RestApiServer> restApiServer() { ... }
    default Optional<ApiAuthService> apiAuthService() { ... }
    
    // 服务便捷方法
    default Optional<NationService> nationService() { ... }
    default Optional<ClaimToolService> claimToolService() { ... }
    default Optional<EpochService> epochService() { ... }
    default Optional<TimeSyncService> timeSyncService() { ... }
    default Optional<GovernmentService> governmentService() { ... }
    default Optional<ResolutionService> resolutionService() { ... }
    default Optional<TreasuryService> treasuryService() { ... }
    default Optional<TreasuryRewardService> treasuryRewardService() { ... }
    default Optional<DiplomacyService> diplomacyService() { ... }
    default Optional<PolicyService> policyService() { ... }
    default Optional<ResourceService> resourceService() { ... }
    default Optional<TechnologyService> technologyService() { ... }
    default Optional<WarService> warService() { ... }
    default Optional<OfficerService> officerService() { ... }
    default Optional<MapService> mapService() { ... }
}
```

---

## 服务接口

### NationService - 国家服务

管理国家创建、成员、领土等核心功能。

```java
package dev.starcore.starcore.module.nation;

public interface NationService {
    // 国家管理
    Optional<Nation> getNation(String name);
    Optional<Nation> getNation(UUID nationId);
    Collection<Nation> getAllNations();
    
    // 玩家国家
    Optional<Nation> getPlayerNation(UUID playerId);
    Optional<Nation> getPlayerNation(String playerName);
    
    // 创建/解散
    Nation createNation(String name, UUID founderId);
    boolean deleteNation(UUID nationId);
    
    // 成员管理
    boolean addMember(UUID nationId, UUID playerId);
    boolean removeMember(UUID nationId, UUID playerId);
    Collection<UUID> getMembers(UUID nationId);
    
    // 领土管理
    boolean claimChunk(Location location, UUID nationId);
    boolean unclaimChunk(Location location);
    Collection<ChunkCoord> getClaims(UUID nationId);
    
    // 国家信息
    int getNationLevel(UUID nationId);
    long getNationExperience(UUID nationId);
    GovernmentType getGovernmentType(UUID nationId);
}
```

### Nation 模型

```java
package dev.starcore.starcore.module.nation;

public class Nation {
    UUID getId();
    String getName();
    UUID getFounderId();
    GovernmentType getGovernmentType();
    int getLevel();
    long getExperience();
    int getClaimCount();
    int getMemberCount();
    long getCreatedAt();
    
    // 统计
    int getTotalKills();
    int getTotalDeaths();
    double getKDA();
}
```

### TreasuryService - 国库服务

管理国家金库和个人余额。

```java
package dev.starcore.starcore.module.treasury;

public interface TreasuryService {
    // 国家金库
    double getNationBalance(UUID nationId);
    boolean depositToNation(UUID nationId, double amount, String reason);
    boolean withdrawFromNation(UUID nationId, double amount, String reason);
    
    // 个人经济
    double getPlayerBalance(UUID playerId);
    boolean setPlayerBalance(UUID playerId, double amount);
    boolean addPlayerBalance(UUID playerId, double amount);
    boolean subtractPlayerBalance(UUID playerId, double amount);
    
    // 转账
    boolean transfer(UUID from, UUID to, double amount);
    
    // 交易记录
    Collection<TreasuryTransaction> getNationTransactions(UUID nationId);
    Collection<TreasuryTransaction> getPlayerTransactions(UUID playerId);
}
```

### TreasuryRewardService - 国库奖励服务

用于 RPG 插件集成，向国家金库写入奖励记录。

```java
package dev.starcore.starcore.module.treasury;

public interface TreasuryRewardService {
    /**
     * 向国家金库添加任务/活动奖励
     * @param nationId 国家 ID
     * @param amount 奖励金额
     * @param rewardType 奖励类型 (quest, dungeon, event 等)
     * @param description 奖励描述
     * @return 是否成功
     */
    boolean addReward(UUID nationId, double amount, String rewardType, String description);
    
    /**
     * 批量添加奖励
     */
    boolean addRewards(Map<UUID, Double> rewards, String rewardType, String description);
}
```

### WarService - 战争服务

管理宣战、战争状态和停战协议。

```java
package dev.starcore.starcore.module.war;

public interface WarService {
    // 战争管理
    Optional<War> getWar(UUID warId);
    Collection<War> getAllActiveWars();
    Collection<War> getNationWars(UUID nationId);
    
    // 宣战/停战
    boolean declareWar(UUID attackerId, UUID defenderId, String reason);
    boolean endWar(UUID warId, WarResult result);
    
    // 战争状态
    WarStatus getWarStatus(UUID warId);
    boolean isAtWar(UUID nationId);
    
    // 战争统计
    int getWarScore(UUID nationId);
    int getWarsWon(UUID nationId);
    int getWarsLost(UUID nationId);
}
```

### DiplomacyService - 外交服务

管理国家间的外交关系。

```java
package dev.starcore.starcore.module.diplomacy;

public interface DiplomacyService {
    // 关系管理
    boolean setRelation(UUID nation1, UUID nation2, RelationType type);
    RelationType getRelation(UUID nation1, UUID nation2);
    Map<UUID, RelationType> getRelations(UUID nationId);
    
    // 关系类型
    enum RelationType {
        NEUTRAL, FRIENDLY, ALLIED, HOSTILE, WAR, VASSAL
    }
    
    // 请求管理
    Optional<DiplomacyRequest> createRequest(UUID from, UUID to, RelationType type);
    boolean acceptRequest(UUID requestId);
    boolean rejectRequest(UUID requestId);
    Collection<DiplomacyRequest> getPendingRequests(UUID nationId);
}
```

### PolicyService - 国策服务

管理国家政策和效果。

```java
package dev.starcore.starcore.module.policy;

public interface PolicyService {
    // 政策管理
    Collection<Policy> getAllPolicies();
    Collection<Policy> getActivePolicies(UUID nationId);
    boolean activatePolicy(UUID nationId, String policyId);
    boolean deactivatePolicy(UUID nationId, String policyId);
    
    // 政策检查
    boolean canActivate(UUID nationId, String policyId);
    boolean isPolicyActive(UUID nationId, String policyId);
    
    // 政策效果
    PolicyEffect getPolicyEffect(String policyId);
}
```

### TechnologyService - 科技服务

管理科技研发和升级。

```java
package dev.starcore.starcore.module.technology;

public interface TechnologyService {
    // 科技管理
    Collection<Technology> getAllTechnologies();
    Collection<Technology> getUnlockedTechnologies(UUID nationId);
    boolean unlockTechnology(UUID nationId, String techId);
    boolean revokeTechnology(UUID nationId, String techId);
    
    // 科技检查
    boolean isUnlocked(UUID nationId, String techId);
    boolean canUnlock(UUID nationId, String techId);
    
    // 科技成本
    TechnologyCost getUnlockCost(String techId);
}
```

### ResourceService - 资源服务

管理国家资源区和采集。

```java
package dev.starcore.starcore.module.resource;

public interface ResourceService {
    // 资源区管理
    Collection<ResourceDistrict> getDistricts(UUID nationId);
    Optional<ResourceDistrict> getDistrict(Location location);
    boolean createDistrict(Location location, UUID nationId, ResourceType type);
    boolean removeDistrict(UUID districtId);
    
    // 采集
    boolean collectResource(UUID playerId, UUID districtId);
    Collection<Resource> getCollectedResources(UUID nationId);
    
    // 刷新
    void refreshDistrict(UUID districtId);
    void refreshAllDistricts(UUID nationId);
}
```

### OfficerService - 官员服务

管理国家官员任命和权限。

```java
package dev.starcore.starcore.module.officer;

public interface OfficerService {
    // 官员管理
    boolean appointOfficer(UUID nationId, UUID playerId, OfficerRank rank);
    boolean removeOfficer(UUID nationId, UUID playerId);
    Optional<OfficerRank> getOfficerRank(UUID nationId, UUID playerId);
    Collection<UUID> getOfficers(UUID nationId, OfficerRank rank);
    Collection<OfficerRank> getAllOfficerRanks(UUID nationId);
    
    // 权限检查
    boolean hasPermission(UUID playerId, OfficerPermission permission);
    
    // 官职类型
    enum OfficerRank {
        MARSHAL,      // 元帅 - 战争指挥
        TREASURER,    // 财政大臣 - 金库管理
        DIPLOMAT,     // 外交官 - 外交关系
        STEWARD,      // 内务大臣 - 内政管理
        GENERAL,      // 将领 - 军事行动
        MINISTER,     // 大臣 - 综合管理
        ADVISOR       // 顾问 - 咨询建议
    }
}
```

### GovernmentService - 政体服务

管理国家政体类型。

```java
package dev.starcore.starcore.module.government;

public interface GovernmentService {
    // 政体管理
    boolean setGovernmentType(UUID nationId, GovernmentType type);
    GovernmentType getGovernmentType(UUID nationId);
    
    // 政体类型
    enum GovernmentType {
        MONARCHY,      // 君主制
        REPUBLIC,      // 共和制
        DICTATORSHIP,  // 独裁制
        THEOCRACY,     // 神权制
        DEMOCRACY,     // 民主制
        OLIGARCHY,     // 寡头制
        FEDERATION     // 联邦制
    }
}
```

### ResolutionService - 决议服务

管理国家投票决议。

```java
package dev.starcore.starcore.module.resolution;

public interface ResolutionService {
    // 决议管理
    Optional<Resolution> createResolution(UUID nationId, String title, 
        String description, ResolutionType type);
    boolean vote(UUID nationId, UUID resolutionId, boolean inFavor);
    boolean closeResolution(UUID resolutionId);
    
    // 查询
    Optional<Resolution> getResolution(UUID resolutionId);
    Collection<Resolution> getActiveResolutions(UUID nationId);
    Collection<Resolution> getResolutionHistory(UUID nationId);
}
```

### EpochService - 纪元服务

管理游戏纪元和历史。

```java
package dev.starcore.starcore.foundation.epoch;

public interface EpochService {
    // 纪元管理
    int getCurrentEpoch();
    long getEpochStartTime();
    long getEpochDuration();
    boolean advanceEpoch();
    
    // 纪元信息
    String getEpochName();
    Map<String, Object> getEpochMetadata();
}
```

### TimeSyncService - 时间同步服务

管理 Minecraft 时间与现实时间的同步。

```java
package dev.starcore.starcore.foundation.timesync;

public interface TimeSyncService {
    // 状态
    boolean isEnabled();
    boolean isSyncing();
    
    // 同步控制
    void enable();
    void disable();
    void forceSync();
    
    // 时间信息
    long getMinecraftTime();
    long getRealTime();
    TimeSyncStatus getStatus();
}
```

### MapService - 地图服务

管理 Web 地图和可视化。

```java
package dev.starcore.starcore.module.map;

public interface MapService {
    // Web 地图
    String generateMapUrl(UUID playerId);
    boolean isMapEnabled();
    
    // 领土数据
    Collection<TerritoryMarker> getTerritoryMarkers(UUID nationId);
    Collection<PlayerMarker> getOnlinePlayerMarkers();
    
    // 标记管理
    boolean addMarker(TerritoryMarker marker);
    boolean removeMarker(UUID markerId);
}
```

### ClaimToolService - 圈地工具服务

管理 RPG 风格的圈地工具。

```java
package dev.starcore.starcore.module.nation.claimtool;

public interface ClaimToolService {
    // 工具获取
    void giveClaimTool(Player player);
    boolean isClaimTool(ItemStack item);
    
    // 选区管理
    Optional<ClaimSelection> getSelection(UUID playerId);
    void setFirstPoint(UUID playerId, Location location);
    void setSecondPoint(UUID playerId, Location location);
    void clearSelection(UUID playerId);
    
    // 圈地预览
    ClaimPreview getPreview(UUID playerId);
    boolean confirmClaim(UUID playerId);
}
```

---

## REST API

STARCORE 提供 REST API 用于网页集成。

### 认证

REST API 使用 Token 认证：

```
Authorization: Bearer <access-token>
```

### 端点列表

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/nations` | 获取所有国家列表 |
| GET | `/api/nations/{id}` | 获取国家详情 |
| GET | `/api/nations/{id}/members` | 获取国家成员列表 |
| GET | `/api/nations/{id}/territory` | 获取国家领土 |
| GET | `/api/territory` | 获取所有领土数据 |
| GET | `/api/territory/{chunkCoord}` | 获取指定领土 |
| GET | `/api/stats` | 获取服务器统计 |
| GET | `/api/events` | 获取事件列表 |
| POST | `/api/events` | 创建事件 |
| GET | `/api/map/snapshot` | 获取地图快照 |

### 响应格式

成功响应：

```json
{
    "success": true,
    "data": { ... },
    "timestamp": 1719254400000
}
```

错误响应：

```json
{
    "success": false,
    "error": {
        "code": "NATION_NOT_FOUND",
        "message": "国家不存在"
    },
    "timestamp": 1719254400000
}
```

### 分页

支持分页的端点使用以下查询参数：

```
GET /api/events?page=0&size=20&sort=timestamp,desc
```

响应包含分页信息：

```json
{
    "success": true,
    "data": {
        "content": [...],
        "page": 0,
        "size": 20,
        "totalElements": 100,
        "totalPages": 5
    }
}
```

---

## DTO 对象

### NationDto

```java
package dev.starcore.starcore.api.v1.dto;

public class NationDto {
    UUID id;
    String name;
    UUID founderId;
    String founderName;
    String governmentType;
    int level;
    long experience;
    int claimCount;
    int memberCount;
    double treasuryBalance;
    long createdAt;
    
    // 统计
    int totalKills;
    int totalDeaths;
    double kda;
}
```

### TerritoryDto

```java
public class TerritoryDto {
    UUID id;
    UUID nationId;
    String nationName;
    String world;
    int chunkX;
    int chunkZ;
    String biome;
    long claimedAt;
    UUID claimedBy;
}
```

### PlayerDto

```java
public class PlayerDto {
    UUID id;
    String name;
    UUID nationId;
    String nationName;
    String role;  // founder, officer, member
    String officerRank;
    double balance;
    int kills;
    int deaths;
    double kda;
}
```

### WarDto

```java
public class WarDto {
    UUID id;
    UUID attackerId;
    String attackerName;
    UUID defenderId;
    String defenderName;
    String reason;
    WarStatus status;
    long startTime;
    long endTime;
    WarResult result;
}
```

### EventDto

```java
public class EventDto {
    UUID id;
    String category;    // finance, war, territory, etc.
    String type;        // deposit, withdraw, claim, etc.
    UUID nationId;
    String nationName;
    UUID actorId;
    String actorName;
    double amount;
    double balance;
    String reason;
    long timestamp;
}
```

### TreasureDto

```java
public class TreasureDto {
    UUID nationId;
    String nationName;
    double balance;
    long lastTransaction;
    double income24h;
    double expense24h;
}
```

### ServerStatsDto

```java
public class ServerStatsDto {
    int totalNations;
    int totalPlayers;
    int totalClaims;
    int totalOnlinePlayers;
    double totalEconomy;
    long serverUptime;
}
```

---

## 事件系统

STARCORE 提供事件系统用于插件集成。

### 事件类型

| 事件 | 说明 |
|------|------|
| `NationCreateEvent` | 国家创建 |
| `NationDeleteEvent` | 国家删除 |
| `NationMemberJoinEvent` | 成员加入 |
| `NationMemberLeaveEvent` | 成员离开 |
| `TerritoryClaimEvent` | 领土占领 |
| `TerritoryUnclaimEvent` | 领土放弃 |
| `WarDeclareEvent` | 宣战 |
| `WarEndEvent` | 战争结束 |
| `TreasuryDepositEvent` | 金库存款 |
| `TreasuryWithdrawEvent` | 金库取款 |
| `PolicyActivateEvent` | 政策激活 |
| `TechnologyUnlockEvent` | 科技解锁 |

### 事件监听

```java
import dev.starcore.starcore.events.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {
    
    @EventHandler
    public void onNationCreate(NationCreateEvent event) {
        getLogger().info("新国家创建: " + event.getNationName());
    }
    
    @EventHandler
    public void onWarDeclare(WarDeclareEvent event) {
        getLogger().info(event.getAttackerName() + " 向 " + 
            event.getDefenderName() + " 宣战!");
    }
}
```

### 注册监听器

```java
getServer().getPluginManager().registerEvents(
    new MyListener(), this);
```

---

## 集成示例

### RPG 插件集成

使用 `TreasuryRewardService` 向国家金库添加任务奖励：

```java
public class MyRPGPlugin extends JavaPlugin {
    
    private StarCoreApi api;
    
    @Override
    public void onEnable() {
        var provider = getServer().getServicesManager()
            .getRegistration(StarCoreApi.class);
        
        if (provider != null) {
            api = provider.getProvider();
        }
    }
    
    public void onQuestComplete(Player player, String questName) {
        if (api == null) return;
        
        api.treasuryRewardService().ifPresent(service -> {
            UUID nationId = getPlayerNationId(player);
            if (nationId != null) {
                // 添加任务奖励到国家金库
                service.addReward(
                    nationId,
                    1000.0,                    // 奖励金额
                    "quest",                   // 奖励类型
                    "完成任务: " + questName  // 描述
                );
                
                player.sendMessage("任务完成! 国家金库获得 1000 金币奖励!");
            }
        });
    }
}
```

### 获取玩家国家信息

```java
public void displayNationInfo(Player player) {
    api.nationService().ifPresent(service -> {
        service.getPlayerNation(player.getUniqueId()).ifPresent(nation -> {
            player.sendMessage("=== " + nation.getName() + " ===");
            player.sendMessage("等级: " + nation.getLevel());
            player.sendMessage("经验: " + nation.getExperience());
            player.sendMessage("成员: " + nation.getMemberCount());
            player.sendMessage("领土: " + nation.getClaimCount());
        });
    });
}
```

### 检查战争状态

```java
public void checkWarStatus(Player player) {
    api.warService().ifPresent(service -> {
        UUID nationId = getPlayerNationId(player);
        if (nationId == null) return;
        
        if (service.isAtWar(nationId)) {
            Collection<War> wars = service.getNationWars(nationId);
            player.sendMessage("你的国家正处于战争中!");
            for (War war : wars) {
                UUID enemy = war.getAttackerId().equals(nationId) 
                    ? war.getDefenderId() 
                    : war.getAttackerId();
                String enemyName = getNationName(enemy);
                player.sendMessage("- vs " + enemyName);
            }
        } else {
            player.sendMessage("你的国家目前没有战争。");
        }
    });
}
```

### 使用 PlaceholderAPI

STARCORE 提供 PlaceholderAPI 占位符：

| 占位符 | 说明 | 示例 |
|--------|------|------|
| `%starcore_nation%` | 所属国家 | "天朝" |
| `%starcore_nation_color%` | 国家颜色 | "#FF0000" |
| `%starcore_nation_rank%` | 国家排名 | "5" |
| `%starcore_nation_members%` | 成员数量 | "12" |
| `%starcore_nation_claims%` | 领土数量 | "45" |
| `%starcore_treasury%` | 国库余额 | "50000.0" |
| `%starcore_title%` | 玩家称号 | "国王" |
| `%starcore_balance%` | 玩家余额 | "1234.56" |
| `%starcore_kills%` | 击杀数 | "156" |
| `%starcore_deaths%` | 死亡数 | "23" |
| `%starcore_kd%` | K/D 比率 | "6.78" |
| `%starcore_kda%` | KDA 比率 | "7.91" |

---

## 附录

### 依赖声明

```xml
<dependency>
    <groupId>dev.starcore</groupId>
    <artifactId>starcore-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### 软依赖配置

```yaml
# plugin.yml
softdepend:
  - StarCore
```

### 权限节点

| 权限 | 说明 |
|------|------|
| `starcore.nation.create` | 创建国家 |
| `starcore.nation.manage` | 管理国家 |
| `starcore.claim.tool` | 使用圈地工具 |
| `starcore.claim.confirm` | 确认圈地 |
| `starcore.claim.remove` | 移除领土 |
| `starcore.treasury.withdraw` | 从金库取款 |
| `starcore.war.declare` | 宣战 |
| `starcore.admin` | 管理员权限 |

---

**文档版本:** v1.0  
**最后更新:** 2026-06-24  
**维护者:** StarCore Team
