package dev.starcore.starcore.zone.listener;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.zone.Zone;
import dev.starcore.starcore.zone.ZoneEffect;
import dev.starcore.starcore.zone.ZoneModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经济区特效监听器
 * 处理玩家进入/离开经济区时的特效应用
 */
public class ZoneEffectListener implements Listener {

    private final ZoneModule zoneModule;
    private final Map<UUID, Set<UUID>> playerActiveEffects = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerCurrentZone = new ConcurrentHashMap<>();

    public ZoneEffectListener(ZoneModule zoneModule) {
        this.zoneModule = zoneModule;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否在经济区
        Location to = event.getTo();
        Zone zone = findZoneAt(to);

        UUID currentZoneId = playerCurrentZone.get(playerId);

        if (zone == null) {
            // 离开了经济区
            if (currentZoneId != null) {
                leaveZone(player, currentZoneId);
                playerCurrentZone.remove(playerId);
            }
        } else if (!zone.id().equals(currentZoneId)) {
            // 进入了新经济区
            if (currentZoneId != null) {
                leaveZone(player, currentZoneId);
            }
            enterZone(player, zone);
            playerCurrentZone.put(playerId, zone.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID zoneId = playerCurrentZone.remove(playerId);
        if (zoneId != null) {
            removeAllEffects(playerId);
        }
    }

    private void enterZone(Player player, Zone zone) {
        UUID playerId = player.getUniqueId();

        // 获取经济区所属国家
        NationId nationId = zone.nationId();

        // 根据特效应用效果
        for (ZoneEffect effect : zone.getEffects()) {
            applyEffect(player, effect);
        }

        // 显示经济区标题
        showZoneTitle(player, zone);

        // 记录活跃特效
        playerActiveEffects.computeIfAbsent(playerId, k -> new HashSet<>()).add(zone.id());
    }

    private void leaveZone(Player player, UUID zoneId) {
        UUID playerId = player.getUniqueId();

        // 移除离开的特效
        Set<UUID> affectedZones = playerActiveEffects.get(playerId);
        if (affectedZones != null) {
            affectedZones.remove(zoneId);
        }

        // 检查是否还有其他活跃经济区
        if (affectedZones == null || affectedZones.isEmpty()) {
            removeAllEffects(playerId);
        } else {
            // 重新应用剩余经济区的特效
            for (UUID remainingZoneId : affectedZones) {
                zoneModule.zoneById(remainingZoneId).ifPresent(zoneSnapshot -> {
                    // ZoneSnapshot 不直接提供 effects，需要通过 ZoneModule 获取完整 Zone
                    zoneModule.getZone(remainingZoneId).ifPresent(zone -> {
                        for (ZoneEffect effect : zone.getEffects()) {
                            applyEffect(player, effect);
                        }
                    });
                });
            }
        }

        // 隐藏标题
        player.sendTitle("", "", 0, 0, 0);
    }

    private void applyEffect(Player player, ZoneEffect effect) {
        switch (effect) {
            case LUCK_AURA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 200, 0, false, false));
            }
            case SPEED_AURA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, false));
            }
            case XP_BOOST -> {
                // XP加成通过其他方式实现
            }
            case PEACE_ZONE -> {
                // 和平区域效果由其他系统处理
            }
            default -> {
                // 其他被动效果
            }
        }
    }

    private void removeAllEffects(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        // 移除经济区特效
        player.removePotionEffect(PotionEffectType.LUCK);
        player.removePotionEffect(PotionEffectType.SPEED);
        // 其他特效的移除逻辑
    }

    private Zone findZoneAt(Location location) {
        // 经济区是虚拟概念，不与物理坐标绑定
        // 位置检测应该通过 TerritoryService/Claim 系统实现
        // 目前返回null，依赖玩家进入经济区时手动触发应用特效
        // 未来可通过 NationTerritory 或 ProtectorAPI 获取玩家所在领地来判断

        // 临时实现：检查玩家当前国家是否有经济区
        var nationService = zoneModule.getNationService();
        if (nationService != null) {
            var nationOpt = nationService.nationOf(location.getWorld().getUID());
            if (nationOpt.isPresent()) {
                var nation = nationOpt.get();
                // 返回该国家的第一个经济区（如果有）
                return zoneModule.getActiveZones().stream()
                    .filter(z -> z.nationId().equals(nation.id()))
                    .findFirst()
                    .orElse(null);
            }
        }

        return null; // 无经济区时返回null
    }

    private void showZoneTitle(Player player, Zone zone) {
        String title = "§6§l" + zone.getName();
        String subtitle = String.format("§e%s §7[Lv.%d]",
            zone.getType().getDisplayName(),
            zone.getLevel());

        player.sendTitle(title, subtitle, 10, 60, 20);
    }

    /**
     * 检查玩家是否在和平区域
     */
    public boolean isInPeaceZone(UUID playerId) {
        UUID zoneId = playerCurrentZone.get(playerId);
        if (zoneId == null) {
            return false;
        }

        return zoneModule.getZone(zoneId)
            .map(zone -> zone.getEffects().contains(ZoneEffect.PEACE_ZONE))
            .orElse(false);
    }

    /**
     * 检查玩家是否在指定经济区
     */
    public boolean isInZone(UUID playerId, UUID zoneId) {
        return playerCurrentZone.get(playerId) != null &&
               playerCurrentZone.get(playerId).equals(zoneId);
    }
}
