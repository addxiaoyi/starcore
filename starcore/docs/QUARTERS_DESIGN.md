# StarCore 房屋分区 (Quarters) 系统设计方案

## 1. 系统概述

### 1.1 设计目标

基于 StarCore 现有的 `Territory`（领土）、`SubRegion`（子区域）、`TerritoryLease`（租赁）和 `PermissionTemplate`（权限模板）系统，设计一套完整的**房屋分区 (Quarters)** 功能：

- **房屋创建**: 玩家可在国家领土内创建私人房屋区域
- **权限隔离**: 房屋所有者拥有完整控制权，可邀请/移除访客
- **共享机制**: 支持好友共享、家庭模式（可继承权限）
- **租赁系统**: 可从国家租用房屋区域
- **分级权限**: OWNER > MEMBER > VISITOR > STRANGER 四个权限级别

### 1.2 核心概念

```
Nation Territory (国家领土)
├── SubRegion: Capital (首都区域)
├── SubRegion: Military Base (军事基地)
├── Quarters: House_001 (房屋分区)  ← 新增
│   ├── Owner: PlayerA
│   ├── Members: [PlayerB, PlayerC]
│   ├── Visitors: [PlayerD]
│   └── Permissions: 独立权限配置
├── Quarters: House_002 (房屋分区)
└── Quarters: Apartment_001 (公寓分区)
```

### 1.3 与现有系统集成

| 现有系统 | 集成方式 |
|---------|---------|
| Territory | Quarters 是 SubRegion 的特化类型 |
| SubRegion | 继承 SubRegion 的边界和优先级机制 |
| TerritoryPermission | 使用现有权限类型 (BUILD, BREAK, INTERACT 等) |
| TerritoryLease | 用于租赁型 Quarters 的租金管理 |
| PermissionTemplate | 用于预设权限模板 |
| HomeService | Quarters 可作为传送目标 |

---

## 2. 模块设计

### 2.1 核心类结构

```
module/quarters/
├── QuartersModule.java           # 模块入口
├── QuartersService.java          # 核心服务
├── Quarters.java                 # 房屋分区实体
├── QuartersType.java             # 房屋类型枚举
├── QuartersPermission.java       # 房屋权限级别
├── QuartersAccess.java           # 访问权限实体
├── QuartersAccessLevel.java      # 访问级别枚举
├── QuartersLease.java            # 房屋租赁（扩展 TerritoryLease）
├── QuartersTemplate.java         # 房屋模板
├── QuartersCommand.java          # 命令处理
├── QuartersListener.java         # 事件监听
├── QuartersStorage.java          # 数据存储
├── QuartersProtectionListener.java # 保护监听
└── QuartersHUD.java              # 头顶信息显示
```

### 2.2 核心实体: Quarters

```java
/**
 * 房屋分区
 * 继承 SubRegion 基础功能，扩展房屋专属功能
 */
public class Quarters extends SubRegion {
    
    // ============ 基础信息 ============
    private String name;                    // 房屋名称
    private QuartersType type;              // 房屋类型
    private UUID ownerId;                   // 所有者 UUID
    
    // ============ 访问控制 ============
    private final Map<UUID, QuartersAccessLevel> members = new HashMap<>();    // 成员
    private final Map<UUID, QuartersAccessLevel> visitors = new HashMap<>();   // 访客
    private final Set<UUID> blacklisted = new HashSet<>();                     // 黑名单
    
    // ============ 房屋设置 ============
    private boolean publiclyVisible;        // 是否公开可见
    private boolean allowVisitorInvites;    // 成员是否能邀请访客
    private boolean pvpEnabled;             // 是否允许 PvP
    private String welcomeMessage;          // 欢迎消息
    
    // ============ 租赁信息 ============
    private QuartersLease lease;            // 租赁信息（可为空）
    
    // ============ 统计 ============
    private long createdTime;
    private long lastVisitTime;
    private int visitorCount;
    
    // ============ 访问权限检查 ============
    
    /**
     * 检查玩家的访问级别
     */
    public QuartersAccessLevel getAccessLevel(UUID playerId) {
        if (ownerId.equals(playerId)) {
            return QuartersAccessLevel.OWNER;
        }
        if (members.containsKey(playerId)) {
            return members.get(playerId);
        }
        if (visitors.containsKey(playerId)) {
            return visitors.get(playerId);
        }
        return QuartersAccessLevel.NONE;
    }
    
    /**
     * 检查是否有访问权限
     */
    public boolean canAccess(UUID playerId) {
        return getAccessLevel(playerId) != QuartersAccessLevel.NONE 
            || publiclyVisible;
    }
    
    /**
     * 检查是否有管理权限
     */
    public boolean isManager(UUID playerId) {
        QuartersAccessLevel level = getAccessLevel(playerId);
        return level == QuartersAccessLevel.OWNER 
            || level == QuartersAccessLevel.MEMBER;
    }
}
```

### 2.3 访问级别枚举

```java
/**
 * 房屋访问级别
 * 继承基础权限级别，添加房屋专属级别
 */
public enum QuartersAccessLevel {
    // 无权限
    NONE(0, "无权限", false),
    
    // 陌生人 - 仅可见，无法交互
    STRANGER(1, "陌生人", true),
    
    // 访客 - 可进入、使用基础物品
    VISITOR(2, "访客", true),
    
    // 成员 - 可建造、使用容器
    MEMBER(3, "成员", true),
    
    // 所有者 - 完全控制
    OWNER(4, "所有者", true);
    
    private final int level;
    private final String displayName;
    private final boolean canEnter;
    
    QuartersAccessLevel(int level, String displayName, boolean canEnter) {
        this.level = level;
        this.displayName = displayName;
        this.canEnter = canEnter;
    }
    
    public boolean hasPermission(QuartersPermission permission) {
        return this.level >= permission.getMinLevel().level;
    }
    
    public boolean canInvite() {
        return this == OWNER || this == MEMBER;
    }
    
    public boolean canKick() {
        return this == OWNER;
    }
}
```

### 2.4 房屋权限

```java
/**
 * 房屋专属权限
 * 对应 Minecraft 交互操作
 */
public enum QuartersPermission {
    ENTER("进入", QuartersAccessLevel.VISITOR),
    OPEN_DOOR("开门", QuartersAccessLevel.VISITOR),
    BREAK("破坏方块", QuartersAccessLevel.MEMBER),
    BUILD("放置方块", QuartersAccessLevel.MEMBER),
    INTERACT("交互", QuartersAccessLevel.VISITOR),
    USE_CONTAINER("使用容器", QuartersAccessLevel.MEMBER),
    USE_REDSTONE("使用红石", QuartersAccessLevel.MEMBER),
    MANAGE_MEMBER("管理成员", QuartersAccessLevel.OWNER),
    MANAGE_PERMISSION("管理权限", QuartersAccessLevel.OWNER),
    INVITE("邀请访客", QuartersAccessLevel.MEMBER),
    KICK("踢出访客", QuartersAccessLevel.OWNER);
    
    private final String displayName;
    private final QuartersAccessLevel minLevel;
    
    QuartersPermission(String displayName, QuartersAccessLevel minLevel) {
        this.displayName = displayName;
        this.minLevel = minLevel;
    }
}
```

---

## 3. 权限管理

### 3.1 权限继承机制

```
Territory 权限
    ↓ 继承 (可覆盖)
SubRegion 权限
    ↓ 继承 (可覆盖)
Quarters 权限
```

**权限检查流程**:

```java
/**
 * 检查玩家在房屋内的权限
 */
public PermissionLevel getEffectivePermission(Player player, TerritoryPermission permission) {
    UUID playerId = player.getUniqueId();
    QuartersAccessLevel accessLevel = getAccessLevel(playerId);
    
    // 黑名单检查
    if (blacklisted.contains(playerId)) {
        return PermissionLevel.NONE;
    }
    
    // OP 管理员 bypass
    if (player.hasPermission("starcore.admin.bypass")) {
        return PermissionLevel.ALL;
    }
    
    // 如果有覆盖权限设置，使用覆盖
    PermissionLevel override = overridePermissions.get(permission);
    if (override != null) {
        return override;
    }
    
    // 否则检查访问级别对应的权限
    return switch (accessLevel) {
        case OWNER -> PermissionLevel.ALL;
        case MEMBER -> getMemberDefaultPermission(permission);
        case VISITOR -> getVisitorDefaultPermission(permission);
        case STRANGER -> getStrangerDefaultPermission(permission);
        case NONE -> PermissionLevel.NONE;
    };
}
```

### 3.2 权限默认值

| 权限类型 | 陌生人 | 访客 | 成员 | 所有者 |
|---------|--------|------|------|--------|
| ENTER | 继承 | 允许 | 允许 | 允许 |
| OPEN_DOOR | 继承 | 允许 | 允许 | 允许 |
| INTERACT | 继承 | 允许 | 允许 | 允许 |
| BREAK | 继承 | 禁止 | 允许 | 允许 |
| BUILD | 继承 | 禁止 | 允许 | 允许 |
| USE_CONTAINER | 继承 | 禁止 | 允许 | 允许 |
| USE_REDSTONE | 继承 | 禁止 | 允许 | 允许 |

### 3.3 权限模板

```java
/**
 * 房屋权限模板
 * 预设常用权限配置
 */
public class QuartersTemplate {
    
    // 预设模板
    public static final QuartersTemplate PRIVATE_HOUSE = QuartersTemplate.builder()
        .name("私人住宅")
        .memberPermission(QuartersAccessLevel.MEMBER)
        .allowVisitorInvites(false)
        .build();
    
    public static final QuartersTemplate FRIENDLY_HOUSE = QuartersTemplate.builder()
        .name("友好住宅")
        .memberPermission(QuartersAccessLevel.MEMBER)
        .allowVisitorInvites(true)
        .visitorCanEnter(true)
        .build();
    
    public static final QuartersTemplate SHOP = QuartersTemplate.builder()
        .name("商店")
        .memberPermission(QuartersAccessLevel.MEMBER)
        .allowVisitorInvites(true)
        .visitorCanEnter(true)
        .visitorCanUseContainer(true)
        .build();
    
    public static final QuartersTemplate FAMILY_HOUSE = QuartersTemplate.builder()
        .name("家庭住宅")
        .memberPermission(QuartersAccessLevel.MEMBER)
        .allowVisitorInvites(true)
        .familyMode(true)  // 允许成员邀请
        .build();
}
```

---

## 4. 共享机制

### 4.1 成员管理

```java
/**
 * 添加成员
 */
public boolean addMember(UUID playerId, QuartersAccessLevel level) {
    if (level == QuartersAccessLevel.OWNER) {
        throw new IllegalArgumentException("无法添加所有者级别的成员");
    }
    
    // 验证调用者权限
    QuartersAccessLevel callerLevel = getAccessLevel(callerId);
    if (!callerLevel.hasPermission(QuartersPermission.MANAGE_MEMBER)) {
        return false;
    }
    
    members.put(playerId, level);
    return true;
}

/**
 * 邀请访客
 */
public boolean inviteVisitor(UUID playerId, UUID inviterId) {
    QuartersAccessLevel inviterLevel = getAccessLevel(inviterId);
    
    // 检查邀请权限
    if (!inviterLevel.hasPermission(QuartersPermission.INVITE)) {
        return false;
    }
    
    // 检查是否允许成员邀请
    if (inviterLevel == QuartersAccessLevel.MEMBER && !allowVisitorInvites) {
        return false;
    }
    
    // 添加访客
    visitors.put(playerId, QuartersAccessLevel.VISITOR);
    
    // 发送通知
    notifyPlayer(playerId, "您被邀请进入房屋: " + name);
    
    return true;
}
```

### 4.2 家庭模式

当启用家庭模式时，成员可以：
- 邀请其他玩家作为访客
- 邀请其他玩家作为成员（需所有者批准）

```java
/**
 * 家庭模式设置
 */
private boolean familyMode;      // 家庭模式
private int maxMembers;          // 最大成员数
private int maxVisitors;         // 最大访客数
private boolean requireApprovalForMember;  // 成员邀请需要批准

/**
 * 成员邀请（家庭模式）
 */
public void inviteAsMember(UUID playerId, UUID inviterId) {
    if (!familyMode) {
        return; // 非家庭模式不允许成员邀请
    }
    
    if (members.size() >= maxMembers) {
        throw new QuartersException("成员数量已达上限");
    }
    
    if (requireApprovalForMember) {
        // 添加待批准申请
        pendingMemberApprovals.add(new MemberApproval(playerId, inviterId));
        notifyOwner("有新的成员申请等待批准");
    } else {
        // 直接添加
        addMember(playerId, QuartersAccessLevel.MEMBER);
    }
}
```

### 4.3 共享邀请流程

```
邀请者执行 /quarters invite <玩家> [级别]
         ↓
    检查邀请权限
         ↓
    [需要批准] → 发送申请给所有者 → 所有者批准/拒绝
         ↓
    [直接添加] → 添加到成员/访客列表
         ↓
    被邀请者收到通知
         ↓
    被邀请者执行 /quarters accept 接受邀请
         ↓
    添加到房屋权限白名单
```

---

## 5. 租赁系统

### 5.1 租赁型房屋

```java
/**
 * 房屋租赁信息
 */
public class QuartersLease {
    
    private final UUID id;
    private final UUID quartersId;
    private final UUID landlordId;           // 房东（国家/所有者）
    private UUID tenantId;                   // 租户
    
    // 租金设置
    private double rentAmount;               // 租金金额
    private RentPeriod rentPeriod;           // 租金周期
    private int leaseDuration;               // 租期（天）
    
    // 时间
    private long leaseStartTime;
    private long leaseEndTime;
    
    // 自动续租
    private boolean autoRenew;
    
    // 状态
    private LeaseStatus status;
    
    // 权限转让
    private boolean transferOwnerOnExpire;   // 过期后转让所有权
}

/**
 * 租赁状态
 */
public enum LeaseStatus {
    LISTED,      // 挂牌出租
    PENDING,     // 等待确认
    ACTIVE,      // 租赁中
    OVERDUE,     // 欠租
    EXPIRED,     // 已过期
    CANCELLED    // 已取消
}
```

### 5.2 租赁命令

```
/quarters rent list                    # 列出可租赁房屋
/quarters rent info <房屋>             # 查看租赁信息
/quarters rent request <房屋>          # 申请租赁
/quarters rent approve <申请者>        # 批准租赁申请
/quarters rent reject <申请者>         # 拒绝租赁申请
/quarters rent pay <房屋>              # 支付租金
/quarters rent cancel                  # 取消租赁
/quarters rent renew                   # 续租
```

### 5.3 租赁状态流程

```
LISTED → PENDING (申请) → ACTIVE (批准) → OVERDUE (未支付)
                              ↓
                          EXPIRED (过期/取消)
                              ↓
                          LISTED (重新挂牌)
```

---

## 6. 数据存储

### 6.1 数据库表结构

```sql
-- 房屋分区表
CREATE TABLE starcore_quarters (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    parent_territory_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    
    -- 边界坐标
    world_name VARCHAR(64) NOT NULL,
    min_x INT NOT NULL,
    min_y INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_y INT NOT NULL,
    max_z INT NOT NULL,
    
    -- 设置
    priority INT DEFAULT 0,
    publicly_visible BOOLEAN DEFAULT FALSE,
    allow_visitor_invites BOOLEAN DEFAULT FALSE,
    pvp_enabled BOOLEAN DEFAULT FALSE,
    family_mode BOOLEAN DEFAULT FALSE,
    welcome_message TEXT,
    
    -- 限制
    max_members INT DEFAULT 10,
    max_visitors INT DEFAULT 20,
    require_approval BOOLEAN DEFAULT TRUE,
    
    -- 统计
    created_time BIGINT NOT NULL,
    last_visit_time BIGINT,
    visitor_count INT DEFAULT 0,
    
    -- 索引
    INDEX idx_owner (owner_id),
    INDEX idx_parent (parent_territory_id),
    INDEX idx_type (type)
);

-- 房屋成员表
CREATE TABLE starcore_quarters_members (
    quarters_id CHAR(36) NOT NULL,
    player_id CHAR(36) NOT NULL,
    access_level VARCHAR(32) NOT NULL,
    added_time BIGINT NOT NULL,
    added_by CHAR(36) NOT NULL,
    PRIMARY KEY (quarters_id, player_id),
    INDEX idx_player (player_id)
);

-- 房屋黑名单表
CREATE TABLE starcore_quarters_blacklist (
    quarters_id CHAR(36) NOT NULL,
    player_id CHAR(36) NOT NULL,
    blocked_time BIGINT NOT NULL,
    blocked_by CHAR(36) NOT NULL,
    reason TEXT,
    PRIMARY KEY (quarters_id, player_id)
);

-- 房屋租赁表
CREATE TABLE starcore_quarters_lease (
    quarters_id CHAR(36) PRIMARY KEY,
    landlord_id CHAR(36) NOT NULL,
    tenant_id CHAR(36),
    rent_amount DECIMAL(10,2) NOT NULL,
    rent_period VARCHAR(16) NOT NULL,
    lease_duration INT NOT NULL,
    lease_start_time BIGINT,
    lease_end_time BIGINT,
    auto_renew BOOLEAN DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    last_payment_time BIGINT,
    next_payment_time BIGINT,
    deposit DECIMAL(10,2) DEFAULT 0
);

-- 租赁申请表
CREATE TABLE starcore_quarters_lease_requests (
    id CHAR(36) PRIMARY KEY,
    quarters_id CHAR(36) NOT NULL,
    applicant_id CHAR(36) NOT NULL,
    requested_at BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    INDEX idx_quarters (quarters_id),
    INDEX idx_applicant (applicant_id)
);

-- 权限覆盖表
CREATE TABLE starcore_quarters_permission_overrides (
    quarters_id CHAR(36) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    level VARCHAR(32) NOT NULL,
    PRIMARY KEY (quarters_id, permission)
);
```

### 6.2 Redis 缓存

```
quarters:{quartersId}:meta          # 房屋元数据
quarters:{quartersId}:members       # 成员列表
quarters:{quartersId}:visitors      # 访客列表
quarters:player:{playerId}:owned    # 玩家拥有的房屋
quarters:player:{playerId}:access   # 玩家可访问的房屋
quarters:lease:{quartersId}         # 租赁信息
```

---

## 7. 命令设计

### 7.1 主命令

```
/quarters help                          # 显示帮助
/quarters create <名称> [类型]          # 创建房屋
/quarters delete                        # 删除房屋
/quarters info                          # 查看房屋信息
/quarters rename <新名称>               # 重命名
/quarters list                          # 列出我的房屋

# 成员管理
/quarters member add <玩家> [级别]      # 添加成员
/quarters member remove <玩家>          # 移除成员
/quarters member list                   # 列出成员
/quarters invite <玩家>                 # 邀请访客
/quarters kick <玩家>                   # 踢出访客

# 权限管理
/quarters perm set <权限> <级别>        # 设置权限
/quarters perm reset                    # 重置权限
/quarters perm template <模板名>        # 应用模板

# 访客管理
/quarters visitor list                  # 列出访客
/quarters visitor invite <玩家>         # 邀请访客

# 设置
/quarters settings                      # 打开设置菜单
/quarters settings visibility           # 设置可见性
/quarters settings family               # 设置家庭模式
/quarters settings welcome <消息>       # 设置欢迎消息

# 传输
/quarters go                            # 传送到我的房屋
/quarters go <名称>                     # 传送到指定房屋
/quarters spawn                        # 传送出房屋

# 租赁
/quarters rent list                     # 列出可租房屋
/quarters rent info                     # 查看租赁信息
/quarters rent request                  # 申请租赁
/quarters rent cancel                   # 取消租赁
```

### 7.2 快捷命令

```
/qh <名称>                              # 传送到我的房屋
/qh <玩家> <名称>                       # 传送到他人的房屋
/qinv <玩家>                            # 邀请玩家
/qvisit                                 # 查看访客邀请
/qaccept                                # 接受邀请
/qdecline                               # 拒绝邀请
```

---

## 8. GUI 菜单

### 8.1 房屋设置菜单

```
┌─────────────────────────────────────┐
│      [房屋名称] 设置                 │
├─────────────────────────────────────┤
│  👤 成员管理        →                │
│  👥 访客管理        →                │
│  🔐 权限设置        →                │
│  📋 权限模板        →                │
├─────────────────────────────────────┤
│  👁 公开可见: [是/否]                │
│  👨‍👩‍👧‍👦 家庭模式: [是/否]                │
│  ⚔️ PvP: [允许/禁止]                 │
├─────────────────────────────────────┤
│  📝 欢迎消息: [编辑]                 │
│  🏷️ 欢迎牌: [设置]                   │
├─────────────────────────────────────┤
│            [关闭]                    │
└─────────────────────────────────────┘
```

### 8.2 成员管理菜单

```
┌─────────────────────────────────────┐
│         成员管理                     │
├─────────────────────────────────────┤
│  👤 Player1 [所有者]                 │
│  👤 Player2 [成员]      [移除]       │
│  👤 Player3 [成员]      [移除]       │
│  👤 Player4 [成员]      [移除]       │
├─────────────────────────────────────┤
│  [添加成员]                          │
├─────────────────────────────────────┤
│  最大成员数: 10                      │
│  [减少]              [增加]          │
└─────────────────────────────────────┘
```

---

## 9. 事件监听

### 9.1 保护监听器

```java
/**
 * 房屋保护监听器
 * 拦截非法操作
 */
public class QuartersProtectionListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Quarters quarters = quartersService.getQuartersAt(event.getBlock().getLocation());
        if (quarters == null) return;
        
        UUID playerId = event.getPlayer().getUniqueId();
        
        // 检查权限
        if (!quarters.canInteract(playerId, QuartersPermission.BREAK)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c你不能在这里破坏方块");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return; // 同一方块不处理
        }
        
        Quarters from = quartersService.getQuartersAt(event.getFrom());
        Quarters to = quartersService.getQuartersAt(event.getTo());
        
        // 进入新房屋
        if (to != null && !to.equals(from)) {
            UUID playerId = event.getPlayer().getUniqueId();
            
            // 检查进入权限
            if (!to.canAccess(playerId)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c你没有权限进入此房屋");
                return;
            }
            
            // 显示进入消息
            to.showEntryMessage(event.getPlayer());
        }
        
        // 离开房屋
        if (from != null && to == null) {
            from.onPlayerExit(event.getPlayer());
        }
    }
}
```

### 9.2 HUD 显示

当玩家进入房屋时，头顶显示：

```
┌─────────────────────┐
│  🏠 玩家名称的房屋   │
│  [成员] 可建造       │
└─────────────────────┘
```

---

## 10. 集成设计

### 10.1 与 Territory 系统集成

```java
/**
 * Quarters 继承 SubRegion
 */
public class Quarters extends SubRegion {
    
    // Quarters 是 SubRegion 的特化类型
    // 自动在父领土内创建
    
    public static Quarters create(UUID territoryId, String name, 
                                  Location pos1, Location pos2) {
        Territory territory = territoryService.getTerritory(territoryId);
        
        // 验证位置在领土内
        if (!territory.contains(pos1) || !territory.contains(pos2)) {
            throw new QuartersException("房屋位置必须在领土范围内");
        }
        
        // 检查与其他 Quarters 不重叠
        for (Quarters existing : quartersService.getQuartersIn(territoryId)) {
            if (existing.overlaps(pos1, pos2)) {
                throw new QuartersException("与现有房屋重叠");
            }
        }
        
        Quarters quarters = new Quarters(
            UUID.randomUUID(),
            name,
            territoryId,
            pos1.getWorld().getName(),
            pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
            pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ()
        );
        
        quarters.setOwnerId(playerId);
        return quarters;
    }
}
```

### 10.2 与 HomeService 集成

```java
/**
 * 房屋作为传送点
 */
public boolean teleportToQuarters(Player player, String quartersName) {
    Quarters quarters = findQuarters(player.getUniqueId(), quartersName);
    if (quarters == null) {
        player.sendMessage("§c房屋不存在: " + quartersName);
        return false;
    }
    
    UUID playerId = player.getUniqueId();
    if (!quarters.canAccess(playerId)) {
        player.sendMessage("§c你没有权限进入此房屋");
        return false;
    }
    
    Location spawnPoint = quarters.getSpawnPoint();
    player.teleport(spawnPoint);
    
    // 更新最后访问时间
    quarters.setLastVisitTime(System.currentTimeMillis());
    
    return true;
}

/**
 * 同步到 HomeService
 */
public void syncToHomeService(Quarters quarters) {
    // 将房屋入口点同步到 HomeService
    homeService.setHome(owner, "quarters:" + quarters.getName(), 
                        quarters.getSpawnPoint());
}
```

---

## 11. 实现优先级

### Phase 1: 基础功能 (P0)
1. Quarters 实体和服务
2. 基础命令 (/quarters create, delete, info)
3. 权限检查 (ENTER, BREAK, BUILD)
4. 成员管理 (add, remove)
5. 保护监听

### Phase 2: 共享功能 (P1)
1. 访客邀请系统
2. 家庭模式
3. 权限模板
4. GUI 菜单

### Phase 3: 租赁功能 (P2)
1. 租赁系统
2. 租金支付
3. 租赁申请流程
4. 自动续租

### Phase 4: 增强功能 (P3)
1. HUD 显示
2. 欢迎消息
3. 访客记录
4. 隐私设置

---

## 12. 配置文件

### quarters.yml

```yaml
quarters:
  # 限制
  max-per-player: 5                    # 每玩家最大房屋数
  max-size: 10000                      # 最大面积（方块）
  min-size: 16                         # 最小面积
  
  # 权限默认值
  default-visitor-permissions:
    enter: true
    open-door: true
    interact: true
    break: false
    build: false
    container: false
  
  default-member-permissions:
    enter: true
    open-door: true
    interact: true
    break: true
    build: true
    container: true
  
  # 共享限制
  max-members-per-quarters: 20
  max-visitors-per-quarters: 50
  
  # 租赁设置
  rent:
    enabled: true
    default-deposit-days: 7
    late-fee-rate: 0.1                 # 每天10%滞纳金
    grace-period-days: 3               # 宽限期
  
  # HUD 设置
  hud:
    enabled: true
    show-owner: true
    show-access-level: true
    update-interval: 5                 # 秒
```

---

## 13. 总结

### 核心特性

| 特性 | 说明 |
|------|------|
| 房屋分区 | 在国家领土内划分的私人区域 |
| 权限继承 | 继承国家→领土→房屋的权限体系 |
| 四级权限 | OWNER > MEMBER > VISITOR > STRANGER |
| 共享机制 | 成员邀请、家庭模式 |
| 租赁系统 | 可租用的房屋 |
| 保护监听 | 自动保护房屋内物品 |

### 架构优势

1. **继承现有系统**: 基于 SubRegion 开发，减少重复代码
2. **灵活权限**: 支持覆盖和继承，可精细控制
3. **可扩展**: 支持模板、家庭模式、租赁等扩展
4. **高性能**: 使用 Redis 缓存热点数据