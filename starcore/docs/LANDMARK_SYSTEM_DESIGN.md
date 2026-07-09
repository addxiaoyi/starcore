# StarCore 地标系统设计文档

基于 TownyWaypoints 分析，为 StarCore 设计国家地标与路径传送系统。

---

## 1. 地标数据模型

### 1.1 核心实体: Landmark

```java
package dev.starcore.starcore.module.landmark;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * 地标配置（不可变对象）
 * 定义地标类型的全局配置
 */
public record LandmarkType(
    String id,                      // 类型标识: capital, port, airport, castle, shrine
    String displayName,             // 显示名称
    Material icon,                  // 图标材质
    String mapKey,                  // 地图显示字符
    double creationCost,            // 创建费用
    double travelCost,              // 旅行费用
    int maxPerNation,               // 每国家最大数量 (-1=无限制)
    boolean allowsVehicle,           // 允许载具传送
    String requiredPermission,       // 创建所需权限
    List<String> allowedBiomes,     // 允许的生物群系
    AccessLevel defaultAccess       // 默认访问级别
) {
    public static final LandmarkType CAPITAL = new LandmarkType(
        "capital", "首都", Material.BEACON, "C",
        0, 0, 1, true, "starcore.landmark.capital",
        List.of(), AccessLevel.NATION
    );
    
    public static final LandmarkType PORT = new LandmarkType(
        "port", "港口", Material.DARK_OAK_PRESSURE_PLATE, "P",
        5000, 100, 3, false, "starcore.landmark.port",
        List.of("OCEAN", "BEACH", "SWAMP", "MANGROVE_SWAMP"), AccessLevel.ALL
    );
    
    public static final LandmarkType AIRPORT = new LandmarkType(
        "airport", "机场", Material.LIGHTNING_ROD, "A",
        10000, 200, 2, true, "starcore.landmark.airport",
        List.of("PLAINS", "SAVANNA", "DESERT"), AccessLevel.ALL
    );
    
    public static final LandmarkType CASTLE = new LandmarkType(
        "castle", "城堡", Material.NETHER_BRICKS, "K",
        8000, 150, 5, true, "starcore.landmark.castle",
        List.of(), AccessLevel.ALLY
    );
    
    public static final LandmarkType SHRINE = new LandmarkType(
        "shrine", "圣地", Material.END_PORTAL_FRAME, "S",
        3000, 50, -1, true, "starcore.landmark.shrine",
        List.of(), AccessLevel.ALL
    );
}
```

### 1.2 国家地标实例: NationLandmark

```java
package dev.starcore.starcore.module.landmark;

/**
 * 国家地标实例
 * 每个国家拥有的具体地标
 */
public class NationLandmark {
    private final UUID id;
    private final UUID nationId;
    private final LandmarkType type;
    private final String customName;           // 自定义名称（可选）
    private final Location location;
    private final long createdAt;
    private AccessLevel accessLevel;            // 可修改的访问级别
    private String description;                   // 地标描述
    
    public NationLandmark(
        UUID id,
        UUID nationId,
        LandmarkType type,
        String customName,
        Location location
    ) {
        this.id = id;
        this.nationId = nationId;
        this.type = type;
        this.customName = customName;
        this.location = location;
        this.createdAt = System.currentTimeMillis();
        this.accessLevel = type.defaultAccess();
        this.description = "";
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getNationId() { return nationId; }
    public LandmarkType getType() { return type; }
    public String getDisplayName() { 
        return customName != null ? customName : type.displayName(); 
    }
    public Location getLocation() { return location; }
    public AccessLevel getAccessLevel() { return accessLevel; }
    public String getDescription() { return description; }
    public long getCreatedAt() { return createdAt; }
    
    // Setters
    public void setAccessLevel(AccessLevel level) { this.accessLevel = level; }
    public void setDescription(String desc) { this.description = desc; }
    public void setCustomName(String name) { this.customName = name; }
}
```

### 1.3 访问级别枚举

```java
package dev.starcore.starcore.module.landmark;

/**
 * 地标访问级别
 */
public enum AccessLevel {
    ALL("所有人", "a"),           // 所有人可访问
    ALLY("盟友", "b"),             // 盟友可访问
    NATION("国民", "n"),          // 同国家成员可访问
    OFFICER("官员", "o"),         // 官员及以上可访问
    LEADER("领袖", "l"),          // 仅领袖可访问
    NONE("禁止", "x");            // 禁止访问
    
    private final String displayName;
    private final String shortcut;  // 菜单快捷键
    
    AccessLevel(String displayName, String shortcut) {
        this.displayName = displayName;
        this.shortcut = shortcut;
    }
}
```

---

## 2. 数据库设计

### 2.1 地标状态表

```sql
-- 地标配置表（全局类型）
CREATE TABLE starcore_landmark_types (
    type_id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    map_key CHAR(1) NOT NULL,
    creation_cost DECIMAL(20,2) NOT NULL DEFAULT 0,
    travel_cost DECIMAL(20,2) NOT NULL DEFAULT 0,
    max_per_nation INT NOT NULL DEFAULT -1,
    allows_vehicle BOOLEAN NOT NULL DEFAULT TRUE,
    required_permission VARCHAR(100) NOT NULL,
    allowed_biomes TEXT,  -- JSON数组
    default_access VARCHAR(20) NOT NULL
);

-- 国家地标实例表
CREATE TABLE starcore_landmarks (
    id CHAR(36) PRIMARY KEY,
    nation_id CHAR(36) NOT NULL,
    type_id VARCHAR(50) NOT NULL,
    custom_name VARCHAR(100),
    world VARCHAR(100) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    description TEXT,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (nation_id) REFERENCES starcore_nations(id),
    FOREIGN KEY (type_id) REFERENCES starcore_landmark_types(type_id)
);

CREATE INDEX idx_landmarks_nation ON starcore_landmarks(nation_id);
CREATE INDEX idx_landmarks_world ON starcore_landmarks(world, x, z);
```

### 2.2 传送历史表

```sql
-- 传送记录表（用于统计和冷却）
CREATE TABLE starcore_landmark_travels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    from_nation_id CHAR(36),
    to_landmark_id CHAR(36) NOT NULL,
    cost DECIMAL(20,2) NOT NULL,
    travel_type VARCHAR(20) NOT NULL,  -- 'NORMAL', 'ADMIN', 'FREE'
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_travels_player ON starcore_landmark_travels(player_uuid, created_at);
CREATE INDEX idx_travels_landmark ON starcore_landmarks(nation_id);
```

### 2.3 Key-Value 状态存储

```java
// 地标模块 key 格式
landmark:{nationId}:list                    // 国家所有地标 (JSON)
landmark:{landmarkId}:meta                 // 地标元数据 (JSON)
landmark:cooldown:{playerId}                // 玩家传送冷却到期时间
landmark:travel:{playerId}:from             // 玩家上次传送来源
```

---

## 3. 路径类型系统

### 3.1 内置路径类型

| 类型 | 显示 | 创建费 | 旅行费 | 载具 | 限制 | 访问 |
|------|------|--------|--------|------|------|------|
| **首都** | C | 免费 | 免费 | 支持 | 每国1个 | 国民 |
| **港口** | P | 5000 | 100 | 不支持 | 每国3个 | 所有人 |
| **机场** | A | 10000 | 200 | 支持 | 每国2个 | 所有人 |
| **城堡** | K | 8000 | 150 | 支持 | 每国5个 | 盟友 |
| **圣地** | S | 3000 | 50 | 支持 | 无限制 | 所有人 |

### 3.2 路径连接 (PathConnection)

```java
package dev.starcore.starcore.module.landmark;

/**
 * 路径连接（用于限制传送路线）
 */
public class PathConnection {
    private final UUID fromLandmarkId;
    private final UUID toLandmarkId;
    private final int distance;           // 路径距离（用于冷却计算）
    private final double tollRate;        // 通行费率
    
    public PathConnection(UUID from, UUID to, int distance) {
        this.fromLandmarkId = from;
        this.toLandmarkId = to;
        this.distance = distance;
        this.tollRate = 0.0;
    }
    
    /**
     * 计算通行费
     */
    public double calculateToll(double baseCost) {
        return baseCost * (1.0 + tollRate);
    }
    
    /**
     * 计算冷却时间（距离越远冷却越短，鼓励长途旅行）
     */
    public int calculateCooldownSeconds(int baseCooldown) {
        // 距离每增加1000格，冷却减少10%，最低保留30%
        double reduction = Math.min(0.7, distance / 10000.0);
        return (int)(baseCooldown * (1.0 - reduction));
    }
}
```

---

## 4. 传送机制

### 4.1 LandmarkTeleportService

```java
package dev.starcore.starcore.module.landmark;

import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.nation.relation.NationRelationManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地标传送服务
 */
public final class LandmarkTeleportService {
    
    private final Plugin plugin;
    private final FoliaCompatScheduler scheduler;
    private final LandmarkStorage storage;
    private final TreasuryService treasuryService;
    private final NationRelationManager relationManager;
    
    // 冷却回调队列
    private final Map<UUID, Runnable> pendingCooldownCallbacks = new ConcurrentHashMap<>();
    
    // 传送中状态
    private final Set<UUID> teleportingPlayers = ConcurrentHashMap.newKeySet();
    
    // 配置
    private int baseCooldownSeconds = 300;     // 基础冷却5分钟
    private boolean enableWarmup = true;        // 启用热身
    private int warmupSeconds = 3;              // 热身时间
    private boolean cancelOnMove = true;       // 移动取消传送
    
    public LandmarkTeleportService(Plugin plugin, FoliaCompatScheduler scheduler,
                                   LandmarkStorage storage, TreasuryService treasuryService,
                                   NationRelationManager relationManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.storage = storage;
        this.treasuryService = treasuryService;
        this.relationManager = relationManager;
    }
    
    /**
     * 传送验证链
     */
    public TeleportResult validateAndTeleport(Player player, NationLandmark target) {
        // 1. 检查目标地标存在
        if (target == null) {
            return TeleportResult.fail("§c地标不存在");
        }
        
        // 2. 检查地标可传送
        if (!isAccessible(player, target)) {
            return TeleportResult.fail("§c你没有权限传送到此地标");
        }
        
        // 3. 检查冷却
        if (isOnCooldown(player)) {
            long remaining = getRemainingCooldown(player);
            return TeleportResult.fail("§c传送冷却中，还需 " + formatTime(remaining));
        }
        
        // 4. 检查费用
        double cost = target.getType().travelCost();
        if (cost > 0 && !hasEnoughMoney(player, cost)) {
            return TeleportResult.fail("§c传送费用不足 (需要 " + cost + ")");
        }
        
        // 5. 距离检查
        if (isOutOfRange(player, target)) {
            return TeleportResult.fail("§c目标地标距离过远");
        }
        
        // 6. 执行传送
        return executeTeleport(player, target, cost);
    }
    
    /**
     * 执行传送
     */
    private TeleportResult executeTeleport(Player player, NationLandmark target, double cost) {
        Location destination = target.getLocation();
        Location from = player.getLocation().clone();
        
        // 扣费
        if (cost > 0) {
            deductCost(player, cost);
            // 部分费用归属目标国家
            splitCost(target.getNationId(), cost);
        }
        
        // 记录传送来源
        pendingCooldownCallbacks.put(player.getUniqueId(), () -> {
            // 传送完成后触发冷却
            startCooldown(player);
        });
        
        // 标记传送中
        teleportingPlayers.add(player.getUniqueId());
        
        // 发送热身提示
        if (enableWarmup) {
            player.sendMessage(Component.text("§e正在传送到 §f" + target.getDisplayName() + 
                " §e，§c请勿移动§e..."));
            
            scheduler.runEntityDelayed(player, () -> {
                performActualTeleport(player, destination, from);
            }, warmupSeconds * 20L);
        } else {
            performActualTeleport(player, destination, from);
        }
        
        return TeleportResult.success("§a正在传送到 §f" + target.getDisplayName());
    }
    
    /**
     * 实际传送执行
     */
    private void performActualTeleport(Player player, Location destination, Location from) {
        if (!teleportingPlayers.remove(player.getUniqueId())) {
            return; // 已被取消
        }
        
        // 处理载具传送
        Entity vehicle = player.getVehicle();
        if (vehicle != null && target.getType().allowsVehicle()) {
            // 载具传送
            teleportWithVehicle(player, vehicle, destination);
        } else {
            // 普通传送
            player.teleport(destination);
        }
        
        player.sendMessage(Component.text("§a已到达 §f" + destination));
    }
    
    /**
     * 载具传送
     */
    private void teleportWithVehicle(Player player, Entity vehicle, Location destination) {
        // 保存乘客列表
        List<Entity> passengers = new ArrayList<>(vehicle.getPassengers());
        
        // 传送载具和所有乘客
        vehicle.eject();
        vehicle.teleport(destination);
        
        // 乘客传送到载具位置
        for (Entity passenger : passengers) {
            if (passenger instanceof Player) {
                ((Player) passenger).teleport(destination);
            } else {
                passenger.teleport(destination);
            }
        }
        
        // 重新骑上载具
        for (Entity passenger : passengers) {
            vehicle.addPassenger(passenger);
        }
    }
    
    /**
     * 传送完成回调（PlayerTeleportEvent触发）
     */
    public void onTeleportComplete(Player player) {
        Runnable callback = pendingCooldownCallbacks.remove(player.getUniqueId());
        if (callback != null) {
            callback.run();
        }
    }
    
    /**
     * 检查访问权限
     */
    private boolean isAccessible(Player player, NationLandmark landmark) {
        // 管理员无视限制
        if (player.hasPermission("starcore.landmark.admin")) {
            return true;
        }
        
        UUID playerNation = getPlayerNationId(player);
        UUID targetNation = landmark.getNationId();
        
        switch (landmark.getAccessLevel()) {
            case ALL -> { return true; }
            case NATION -> { return playerNation != null && playerNation.equals(targetNation); }
            case ALLY -> { 
                return playerNation != null && 
                       relationManager.areAllies(playerNation, targetNation); 
            }
            case OFFICER -> {
                return isOfficer(player, targetNation);
            }
            case LEADER -> {
                return isLeader(player, targetNation);
            }
            case NONE -> { return false; }
        }
        return false;
    }
    
    /**
     * 冷却管理
     */
    private void startCooldown(Player player) {
        storage.setProperty(
            "landmark:cooldown:" + player.getUniqueId(),
            String.valueOf(System.currentTimeMillis() + baseCooldownSeconds * 1000L)
        );
    }
    
    private boolean isOnCooldown(Player player) {
        String cooldownStr = storage.getProperty("landmark:cooldown:" + player.getUniqueId());
        if (cooldownStr == null) return false;
        
        long cooldownEnd = Long.parseLong(cooldownStr);
        return System.currentTimeMillis() < cooldownEnd;
    }
    
    private long getRemainingCooldown(Player player) {
        String cooldownStr = storage.getProperty("landmark:cooldown:" + player.getUniqueId());
        if (cooldownStr == null) return 0;
        
        long cooldownEnd = Long.parseLong(cooldownStr);
        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }
    
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "秒";
        return (seconds / 60) + "分" + (seconds % 60) + "秒";
    }
    
    // 传送结果
    public record TeleportResult(boolean success, String message) {
        public static TeleportResult success(String msg) { return new TeleportResult(true, msg); }
        public static TeleportResult fail(String msg) { return new TeleportResult(false, msg); }
    }
}
```

### 4.2 热身取消监听器

```java
package dev.starcore.starcore.module.landmark;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 监听传送过程中的取消事件
 */
public class LandmarkTravelListener implements Listener {
    
    private final LandmarkTeleportService teleportService;
    private final Set<UUID> warmupPlayers = new HashSet<>();
    
    public LandmarkTravelListener(LandmarkTeleportService teleportService) {
        this.teleportService = teleportService;
    }
    
    /**
     * 移动取消热身
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        
        if (!warmupPlayers.contains(playerId)) return;
        
        // 检查是否真的移动了（方块级别）
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // 只是视角转动
        }
        
        // 取消传送
        warmupPlayers.remove(playerId);
        event.getPlayer().sendMessage("§c移动取消传送");
    }
    
    /**
     * 传送完成触发冷却
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        teleportService.onTeleportComplete(event.getPlayer());
        warmupPlayers.remove(event.getPlayer().getUniqueId());
    }
    
    public void addWarmupPlayer(UUID playerId) {
        warmupPlayers.add(playerId);
    }
}
```

---

## 5. 命令系统

### 5.1 命令结构

```
/landmark <子命令>
  ├── create <类型> [名称]    # 创建地标
  ├── delete <名称>           # 删除地标
  ├── list [页码]             # 列出本国地标
  ├── info <名称>            # 查看地标信息
  ├── setaccess <名称> <级别> # 设置访问级别
  ├── setdesc <名称> <描述>   # 设置地标描述
  ├── travel <国家> <地标>    # 传送到地标
  ├── nearby [页码]          # 附近地标列表
  └── admin                  # 管理员子命令
      ├── reload             # 重载配置
      ├── delete <ID>        # 强制删除
      └── setcost <类型> <费用> # 设置费用
```

### 5.2 LandmarkCommand

```java
package dev.starcore.starcore.module.landmark.command;

import dev.starcore.starcore.module.landmark.*;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class LandmarkCommand implements CommandExecutor, TabExecutor {
    
    private final LandmarkService landmarkService;
    private final LandmarkTeleportService teleportService;
    
    @Override
    public boolean onCommand(Player player, CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            case "setaccess" -> handleSetAccess(player, args);
            case "setdesc" -> handleSetDesc(player, args);
            case "travel" -> handleTravel(player, args);
            case "nearby" -> handleNearby(player, args);
            case "admin" -> handleAdmin(player, args);
            default -> sendHelp(player);
        }
        return true;
    }
    
    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("starcore.landmark.create")) {
            player.sendMessage("§c你没有权限创建地标");
            return;
        }
        
        UUID nationId = getPlayerNationId(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage("§e用法: /landmark create <类型> [名称]");
            player.sendMessage("§7可用类型: capital, port, airport, castle, shrine");
            return;
        }
        
        LandmarkType type = LandmarkTypeRegistry.get(args[1].toLowerCase());
        if (type == null) {
            player.sendMessage("§c未知的地标类型: " + args[1]);
            return;
        }
        
        String customName = args.length > 2 ? args[2] : null;
        
        // 获取脚下位置
        Location location = player.getLocation();
        
        // 创建地标
        NationLandmark landmark = landmarkService.createLandmark(
            nationId, type, customName, location
        );
        
        if (landmark != null) {
            player.sendMessage("§a地标 §f" + landmark.getDisplayName() + " §a已创建!");
            player.sendMessage("§7访问级别: " + type.defaultAccess().displayName());
        } else {
            player.sendMessage("§c创建地标失败，请检查是否达到上限");
        }
    }
    
    private void handleTravel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e用法: /landmark travel <国家> <地标>");
            return;
        }
        
        String nationName = args[1];
        String landmarkName = args[2];
        
        // 查找目标国家
        UUID nationId = landmarkService.getNationIdByName(nationName);
        if (nationId == null) {
            player.sendMessage("§c未找到国家: " + nationName);
            return;
        }
        
        // 查找地标
        NationLandmark landmark = landmarkService.getLandmarkByName(nationId, landmarkName);
        if (landmark == null) {
            player.sendMessage("§c未找到地标: " + landmarkName);
            return;
        }
        
        // 执行传送
        TeleportResult result = teleportService.validateAndTeleport(player, landmark);
        player.sendMessage(result.message());
    }
    
    private void handleNearby(Player player, String[] args) {
        int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        
        List<NearbyLandmark> nearby = landmarkService.getNearbyLandmarks(
            player.getLocation(), 10000, 20
        );
        
        player.sendMessage("§6=== 附近地标 (第" + page + "页) ===");
        for (NearbyLandmark nl : nearby) {
            player.sendMessage("§e" + nl.landmark().getDisplayName() + 
                " §7- §f" + nl.nationName() + " §7[§a" + 
                formatDistance(nl.distance()) + "§7]");
        }
    }
}
```

---

## 6. GUI 菜单

### 6.1 地标管理菜单

```java
package dev.starcore.starcore.module.landmark.gui;

import dev.starcore.starcore.gui.StarCoreMenu;
import dev.starcore.starcore.module.landmark.NationLandmark;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 国家地标管理菜单
 */
public class LandmarkManageMenu extends StarCoreMenu {
    
    private final UUID nationId;
    
    public LandmarkManageMenu(Player player, UUID nationId) {
        super(player, 54, "§8§l国家地标管理");
        this.nationId = nationId;
    }
    
    @Override
    protected void draw() {
        List<NationLandmark> landmarks = landmarkService.getLandmarksByNation(nationId);
        
        // 显示现有地标
        int slot = 0;
        for (NationLandmark landmark : landmarks) {
            setItem(slot++, createLandmarkItem(landmark));
        }
        
        // 添加创建按钮
        setItem(49, createAddButton());
        
        // 填充边框
        fillBorder();
    }
    
    private ItemStack createLandmarkItem(NationLandmark landmark) {
        LandmarkType type = landmark.getType();
        
        ItemStack item = new ItemStack(type.icon());
        setDisplayName(item, "§f" + landmark.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7类型: §e" + type.displayName());
        lore.add("§7访问: §" + getAccessColor(landmark.getAccessLevel()) + 
                 landmark.getAccessLevel().displayName());
        lore.add("§7创建: §f" + formatTime(landmark.getCreatedAt()));
        
        if (!landmark.getDescription().isEmpty()) {
            lore.add("");
            lore.add("§7" + landmark.getDescription());
        }
        
        lore.add("");
        lore.add("§a左键 §7- 传送至此");
        lore.add("§e右键 §7- 编辑设置");
        
        setLore(item, lore);
        return item;
    }
    
    private ItemStack createAddButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        setDisplayName(item, "§a§l+ 创建新地标");
        setLore(item, List.of("§7在当前位置创建一个新地标"));
        return item;
    }
}
```

### 6.2 地标传送菜单

```java
package dev.starcore.starcore.module.landmark.gui;

/**
 * 地标传送选择菜单
 */
public class LandmarkTravelMenu extends StarCoreMenu {
    
    public LandmarkTravelMenu(Player player) {
        super(player, 54, "§8§l选择传送目标");
    }
    
    @Override
    protected void draw() {
        List<NearbyLandmark> nearby = landmarkService.getNearbyLandmarks(
            player.getLocation(), 100000, 45
        );
        
        // 按国家分组显示
        UUID lastNationId = null;
        int slot = 0;
        
        for (NearbyLandmark nl : nearby) {
            if (!nl.landmark().getNationId().equals(lastNationId)) {
                // 国家分隔符
                setItem(slot++, createNationHeader(nl));
                lastNationId = nl.landmark().getNationId();
            }
            
            setItem(slot++, createTravelItem(nl));
        }
        
        fillBorder();
    }
    
    private ItemStack createTravelItem(NearbyLandmark nl) {
        NationLandmark landmark = nl.landmark();
        LandmarkType type = landmark.getType();
        
        ItemStack item = new ItemStack(type.icon());
        setDisplayName(item, "§f" + landmark.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7国家: §f" + nl.nationName());
        lore.add("§7距离: §a" + formatDistance(nl.distance()));
        lore.add("§7费用: §e" + type.travelCost());
        lore.add("");
        lore.add("§7访问权限: " + getAccessDescription(landmark.getAccessLevel()));
        lore.add("");
        lore.add("§a点击传送到此地标");
        
        setLore(item, lore);
        return item;
    }
}
```

---

## 7. WebMap 集成

### 7.1 地标标记提供者

```java
package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.landmark.NationLandmark;
import dev.starcore.starcore.module.landmark.LandmarkService;
import dev.starcore.starcore.module.map.model.MapMarker;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * WebMap 地标标记提供者
 */
public class LandmarkMarkerProvider implements MapMarkerProvider {
    
    private final LandmarkService landmarkService;
    
    @Override
    public List<MapMarker> getMarkers(Location viewCenter, int radius) {
        List<MapMarker> markers = new ArrayList<>();
        
        for (NationLandmark landmark : landmarkService.getAllLandmarks()) {
            Location loc = landmark.getLocation();
            
            // 距离过滤
            if (loc.getWorld() != viewCenter.getWorld()) continue;
            if (loc.distance(viewCenter) > radius) continue;
            
            MapMarker marker = new MapMarker(
                "landmark:" + landmark.getId(),
                landmark.getType().mapKey(),
                loc.getX(),
                loc.getZ(),
                landmark.getDisplayName(),
                landmark.getType().icon().name(),
                getNationColor(landmark.getNationId()),
                MapMarkerType.LANDMARK
            );
            
            markers.add(marker);
        }
        
        return markers;
    }
    
    @Override
    public String getMarkerPopup(String markerId) {
        UUID landmarkId = UUID.fromString(markerId.replace("landmark:", ""));
        NationLandmark landmark = landmarkService.getLandmark(landmarkId);
        
        if (landmark == null) return null;
        
        return String.format("""
            <div class="landmark-popup">
                <h3>%s</h3>
                <p class="type">%s</p>
                <p class="nation">归属: %s</p>
                <p class="access">访问: %s</p>
                <button onclick="teleport('%s')">传送至此</button>
            </div>
            """,
            landmark.getDisplayName(),
            landmark.getType().displayName(),
            getNationName(landmark.getNationId()),
            landmark.getAccessLevel().displayName(),
            landmark.getId()
        );
    }
}
```

---

## 8. 配置扩展

### 8.1 config.yml 扩展

```yaml
# 地标系统配置
landmark:
  # 是否启用地标系统
  enabled: true
  
  # 基础传送冷却时间（秒）
  base-cooldown-seconds: 300
  
  # 是否启用传送热身
  enable-warmup: true
  # 热身时间（秒）
  warmup-seconds: 3
  
  # 移动取消传送
  cancel-on-move: true
  
  # 允许载具传送的类型
  vehicle-types:
    - capital
    - airport
    - castle
    - shrine
  
  # 传送费用分配比例
  # 分配给目标国家的比例
  nation-split-ratio: 0.7
  # 分配给目标城市的比例
  city-split-ratio: 0.3
  
  # 最大传送距离（方块），-1表示无限制
  max-distance: -1
  
  # 每页显示的地标数量
  landmarks-per-page: 20
  
  # 附近地标搜索半径（方块）
  nearby-radius: 10000
```

---

## 9. 模块注册

### 9.1 LandmarkModule

```java
package dev.starcore.starcore.module.landmark;

/**
 * 地标模块
 */
public class LandmarkModule implements StarCoreModule {
    
    private final Plugin plugin;
    private LandmarkService landmarkService;
    private LandmarkTeleportService teleportService;
    private LandmarkCommand command;
    
    @Override
    public void enable(StarCoreContext context) {
        // 获取依赖服务
        TreasuryService treasuryService = context.getService(TreasuryService.class);
        NationRelationManager relationManager = context.getService(NationRelationManager.class);
        FoliaCompatScheduler scheduler = context.getService(FoliaCompatScheduler.class);
        
        // 初始化存储
        LandmarkStorage storage = new LandmarkStorage(plugin);
        
        // 初始化服务
        landmarkService = new LandmarkService(plugin, storage);
        teleportService = new LandmarkTeleportService(
            plugin, scheduler, storage, treasuryService, relationManager
        );
        
        // 注册监听器
        LandmarkTravelListener listener = new LandmarkTravelListener(teleportService);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        
        // 注册命令
        command = new LandmarkCommand(landmarkService, teleportService);
        registerCommand("landmark", command);
        
        // 注册 WebMap 标记提供者
        context.getService(MapModule.class).registerMarkerProvider(
            new LandmarkMarkerProvider(landmarkService)
        );
        
        plugin.getLogger().info("地标模块已启用");
    }
    
    @Override
    public void disable() {
        // 保存数据
        if (landmarkService != null) {
            landmarkService.save();
        }
        plugin.getLogger().info("地标模块已关闭");
    }
    
    @Override
    public String getId() {
        return "landmark";
    }
    
    @Override
    public String getName() {
        return "地标系统";
    }
}
```

---

## 10. 权限节点

| 权限节点 | 描述 | 默认组 |
|----------|------|--------|
| `starcore.landmark.use` | 使用传送功能 | 所有玩家 |
| `starcore.landmark.create` | 创建地标 | 官员 |
| `starcore.landmark.create.capital` | 创建首都 | 领袖 |
| `starcore.landmark.delete` | 删除本国地标 | 官员 |
| `starcore.landmark.admin` | 管理员无视限制 | OP |
| `starcore.landmark.travel.free` | 免费传送 | OP |
| `starcore.landmark.warmup.bypass` | 跳过热身 | OP |

---

## 11. TownyWaypoints 借鉴要点总结

| 设计模式 | StarCore 实现 |
|----------|--------------|
| 不可变 Waypoint 配置 | `record LandmarkType` 定义全局类型 |
| 冷却回调机制 | `pendingCooldownCallbacks` 传送完成时触发 |
| 双层生物群系过滤 | `allowedBiomes` 配置支持 |
| 经济分配模型 | `nationSplitRatio` 分配给目标国家 |
| 路径连接检查 | `PathConnection` 限制传送路线 |
| 载具传送 | `teleportWithVehicle()` 保留乘客 |
| 访问控制 | 6级 `AccessLevel` 枚举 |
| 地图集成 | `LandmarkMarkerProvider` WebMap 标记 |
