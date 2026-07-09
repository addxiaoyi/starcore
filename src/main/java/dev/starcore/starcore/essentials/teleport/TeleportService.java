package dev.starcore.starcore.essentials.teleport;
import java.util.Optional;

import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.warp.WarpService;
import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 传送服务（完整版）
 * 集成延迟和冷却系统
 */
public final class TeleportService {
    private final Plugin plugin;
    private final FoliaCompatScheduler scheduler;
    private final TeleportConfig config;

    // 引用 HomeService（用于获取家园数据）
    private HomeService homeService;

    // 引用 WarpService（用于获取星港数据）
    private WarpService warpService;

    // 主城传送点（星核）
    private Location spawnLocation;

    // 传送请求（带去重）
    private final Map<UUID, TeleportRequest> teleportRequests = new ConcurrentHashMap<>();

    // 最后一次传送位置（用于 /back）
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // TPA 请求冷却（防止刷屏）
    private final Map<UUID, Long> tpaCooldowns = new ConcurrentHashMap<>();
    private static final long TPA_COOLDOWN_MS = 30000; // 30秒冷却

    // 设置主城的权限节点（可配置）
    private String setSpawnPermission = "starcore.setspawn";

    public TeleportService(Plugin plugin, FoliaCompatScheduler scheduler, TeleportConfig config) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.config = config;
    }

    /**
     * 设置设置主城的权限节点
     */
    public void setSetSpawnPermission(String permission) {
        this.setSpawnPermission = permission;
    }

    /**
     * 获取设置主城的权限节点
     */
    public String getSetSpawnPermission() {
        return setSpawnPermission;
    }

    /**
     * 检查玩家是否有设置主城的权限
     */
    public boolean canSetSpawn(Player player) {
        if (setSpawnPermission == null || setSpawnPermission.isEmpty()) {
            return true; // 无权限配置时允许所有人
        }
        return player.hasPermission(setSpawnPermission);
    }

    /**
     * 设置 HomeService 引用
     */
    public void setHomeService(HomeService homeService) {
        this.homeService = homeService;
    }

    /**
     * 设置 WarpService 引用
     */
    public void setWarpService(WarpService warpService) {
        this.warpService = warpService;
    }

    /**
     * 记录玩家最后位置（传送前调用）
     */
    public void recordLastLocation(Player player) {
        lastLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    /**
     * 传送到主城（星核）- 带延迟
     */
    public void teleportToSpawn(Player player) {
        if (spawnLocation == null) {
            player.sendMessage(Component.text("主城传送点未设置", NamedTextColor.RED));
            return;
        }

        // 记录当前位置用于 /back
        recordLastLocation(player);

        player.sendMessage(Component.text(
            "将在 " + config.spawnDelay() + " 秒后传送到主城...",
            NamedTextColor.YELLOW
        ));

        scheduler.runEntityDelayed(player, () -> {
            player.teleport(spawnLocation);
            player.sendMessage(Component.text("欢迎来到主城！", NamedTextColor.GREEN));
        }, config.spawnDelay() * 20L);
    }

    /**
     * 传送到家园（锚点）- 带延迟
     */
    public void teleportToHome(Player player, String homeName) {
        if (homeService == null) {
            player.sendMessage(Component.text("家园系统未初始化", NamedTextColor.RED));
            return;
        }

        // 获取家园位置
        Optional<Location> homeOpt = homeService.getHome(player.getUniqueId(), homeName);
        if (homeOpt.isEmpty()) {
            player.sendMessage(Component.text("家园 '" + homeName + "' 不存在", NamedTextColor.RED));
            return;
        }

        // 记录当前位置用于 /back
        recordLastLocation(player);

        player.sendMessage(Component.text(
            "将在 " + config.homeDelay() + " 秒后传送到家园: " + homeName + "...",
            NamedTextColor.YELLOW
        ));

        scheduler.runEntityDelayed(player, () -> {
            player.teleport(homeOpt.get());
            player.sendMessage(Component.text("已传送到家园: " + homeName, NamedTextColor.GREEN));
        }, config.homeDelay() * 20L);
    }

    /**
     * 传送到星港 - 带延迟和权限检查
     */
    public void teleportToWarp(Player player, String warpName) {
        if (!player.hasPermission("starcore.warp.use")) {
            player.sendMessage(Component.text("你没有权限使用星港", NamedTextColor.RED));
            return;
        }

        if (warpService == null) {
            player.sendMessage(Component.text("星港系统未初始化", NamedTextColor.RED));
            return;
        }

        // 获取星港位置
        Optional<Location> warpOpt = warpService.getWarp(warpName);
        if (warpOpt.isEmpty()) {
            player.sendMessage(Component.text("星港 '" + warpName + "' 不存在", NamedTextColor.RED));
            return;
        }

        // 记录当前位置用于 /back
        recordLastLocation(player);

        player.sendMessage(Component.text(
            "将在 " + config.warpDelay() + " 秒后传送到星港: " + warpName + "...",
            NamedTextColor.YELLOW
        ));

        scheduler.runEntityDelayed(player, () -> {
            player.teleport(warpOpt.get());
            player.sendMessage(Component.text("已传送到星港: " + warpName, NamedTextColor.GREEN));
        }, config.warpDelay() * 20L);
    }

    /**
     * 玩家传送请求 - 带去重和冷却
     */
    public void sendTeleportRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();

        // 检查冷却
        Long lastRequest = tpaCooldowns.get(requesterId);
        if (lastRequest != null && System.currentTimeMillis() - lastRequest < TPA_COOLDOWN_MS) {
            long remaining = (TPA_COOLDOWN_MS - (System.currentTimeMillis() - lastRequest)) / 1000;
            requester.sendMessage(Component.text(
                "请等待 " + remaining + " 秒后再发送请求",
                NamedTextColor.RED
            ));
            return;
        }

        // 去重检查：检查请求者是否已有待处理请求
        for (Map.Entry<UUID, TeleportRequest> entry : teleportRequests.entrySet()) {
            if (entry.getValue().requesterId().equals(requesterId)
                    && System.currentTimeMillis() - entry.getValue().timestamp() < 60000) {
                requester.sendMessage(Component.text(
                    "你已有待处理的传送请求，请等待当前请求被处理后再试",
                    NamedTextColor.RED
                ));
                return;
            }
        }

        // 检查目标是否已有待处理请求
        TeleportRequest existing = teleportRequests.get(targetId);
        if (existing != null && System.currentTimeMillis() - existing.timestamp() < 60000) {
            requester.sendMessage(Component.text(
                "目标玩家有待处理的传送请求",
                NamedTextColor.RED
            ));
            return;
        }

        // 创建请求
        TeleportRequest request = new TeleportRequest(requesterId, targetId, System.currentTimeMillis());
        teleportRequests.put(targetId, request);
        tpaCooldowns.put(requesterId, System.currentTimeMillis());

        requester.sendMessage(Component.text(
            "已向 " + target.getName() + " 发送传送请求",
            NamedTextColor.GREEN
        ));
        target.sendMessage(Component.text(
            requester.getName() + " 请求传送到你的位置",
            NamedTextColor.YELLOW
        ));
        target.sendMessage(Component.text(
            "使用 /tpaccept 接受 或 /tpdeny 拒绝",
            NamedTextColor.GRAY
        ));

        // 60秒后自动过期
        scheduler.runDelayed(() -> {
            if (teleportRequests.get(targetId) == request) {
                teleportRequests.remove(targetId);
                if (requester.isOnline()) {
                    requester.sendMessage(Component.text("传送请求已过期", NamedTextColor.RED));
                }
            }
        }, 60 * 20L);
    }

    /**
     * 接受传送请求
     */
    public void acceptTeleportRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TeleportRequest request = teleportRequests.remove(targetId);

        if (request == null) {
            target.sendMessage(Component.text("没有待处理的传送请求", NamedTextColor.RED));
            return;
        }

        Player requester = target.getServer().getPlayer(request.requesterId());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(Component.text("请求者已离线", NamedTextColor.RED));
            return;
        }

        target.sendMessage(Component.text("已接受传送请求", NamedTextColor.GREEN));
        requester.sendMessage(Component.text(target.getName() + " 接受了你的传送请求", NamedTextColor.GREEN));

        // 记录请求者的最后位置用于 /back
        recordLastLocation(requester);

        // 传送
        scheduler.runEntityDelayed(requester, () -> {
            requester.teleport(target.getLocation());
            requester.sendMessage(Component.text("传送成功！", NamedTextColor.GREEN));
        }, config.tpaDelay() * 20L);
    }

    /**
     * 拒绝传送请求
     */
    public void denyTeleportRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TeleportRequest request = teleportRequests.remove(targetId);

        if (request == null) {
            target.sendMessage(Component.text("没有待处理的传送请求", NamedTextColor.RED));
            return;
        }

        Player requester = target.getServer().getPlayer(request.requesterId());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Component.text(
                target.getName() + " 拒绝了你的传送请求",
                NamedTextColor.RED
            ));
        }
        target.sendMessage(Component.text("已拒绝传送请求", NamedTextColor.YELLOW));
    }

    /**
     * 返回上一个位置（/back 命令）
     */
    public void teleportBack(Player player) {
        Location lastLoc = lastLocations.remove(player.getUniqueId());

        if (lastLoc == null) {
            player.sendMessage(Component.text("没有可返回的位置", NamedTextColor.RED));
            player.sendMessage(Component.text(
                "提示: 传送后会自动记录你的位置",
                NamedTextColor.GRAY
            ));
            return;
        }

        // 检查世界是否存在
        if (lastLoc.getWorld() == null) {
            player.sendMessage(Component.text("之前的位置世界已不存在", NamedTextColor.RED));
            return;
        }

        // 记录当前位置
        recordLastLocation(player);

        player.sendMessage(Component.text(
            "将在 " + config.backDelay() + " 秒后返回上一个位置...",
            NamedTextColor.YELLOW
        ));

        scheduler.runEntityDelayed(player, () -> {
            player.teleport(lastLoc);
            player.sendMessage(Component.text("已返回上一个位置", NamedTextColor.GREEN));
        }, config.backDelay() * 20L);
    }

    /**
     * 设置主城传送点
     */
    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        teleportRequests.remove(playerId);
        lastLocations.remove(playerId);
        tpaCooldowns.remove(playerId);
    }

    /**
     * 传送请求
     */
    private record TeleportRequest(UUID requesterId, UUID targetId, long timestamp) {}
}
