package dev.starcore.starcore.module.army.siege.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.siege.SiegeService;
import dev.starcore.starcore.module.army.siege.event.*;
import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import dev.starcore.starcore.module.army.siege.model.WallData;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 攻城器械事件监听器
 * 处理攻城器械相关的玩家交互和战斗事件
 */
public final class SiegeListener implements Listener {
    private final SiegeService siegeService;
    private final NationService nationService;
    private final WarService warService;
    private final MessageService messages;

    // 攻城器械附近玩家跟踪
    private final Map<String, Long> playerSiegeProximity = new ConcurrentHashMap<>();
    // 攻击冷却记录
    private final Map<String, Long> attackCooldowns = new ConcurrentHashMap<>();

    // 冷却时间（毫秒）
    private static final long ATTACK_COOLDOWN_MS = 5000;
    // 检测半径（方块）
    private static final double PROXIMITY_RADIUS = 30.0;
    // 攻击触发半径（方块）
    private static final double ATTACK_TRIGGER_RADIUS = 50.0;

    public SiegeListener(
        SiegeService siegeService,
        NationService nationService,
        WarService warService,
        MessageService messages
    ) {
        this.siegeService = siegeService;
        this.nationService = nationService;
        this.warService = warService;
        this.messages = messages;
    }

    // ==================== 攻城器械事件处理 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSiegeCreated(SiegeCreatedEvent event) {
        SiegeUnit siege = event.getSiegeUnit();

        // 广播给国家成员
        nationService.nationById(new NationId(siege.nationId())).ifPresent(nation -> {
            Component msg = Component.text("[攻城器械] " + siege.type().displayName() + " 已被创建")
                .color(NamedTextColor.GREEN);
            broadcastToNation(nation, msg);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSiegeDeployed(SiegeDeployedEvent event) {
        SiegeUnit siege = event.getSiegeUnit();
        Location target = event.getTargetLocation();

        // 广播部署信息
        nationService.nationById(new NationId(siege.nationId())).ifPresent(nation -> {
            Component msg = Component.text("[攻城器械] " + siege.type().displayName() +
                " 已部署到 (" + target.getBlockX() + "," + target.getBlockZ() + ")")
                .color(NamedTextColor.YELLOW);
            broadcastToNation(nation, msg);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSiegeStarted(SiegeStartedEvent event) {
        SiegeUnit siege = event.getSiegeUnit();
        WallData wall = event.getTargetWall();

        // 检查是否处于战争状态
        if (warService != null) {
            nationService.nationById(new NationId(siege.nationId())).ifPresent(attacker -> {
                nationService.nationById(new NationId(wall.nationId())).ifPresent(defender -> {
                    if (isAtWar(attacker, defender)) {
                        Component msg = Component.text("[战争] " + attacker.name() +
                            " 正在攻击 " + defender.name() + " 的 " + wall.type().displayName())
                            .color(NamedTextColor.DARK_RED);
                        broadcastToNation(attacker, msg);
                    }
                });
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSiegeFired(SiegeFiredEvent event) {
        SiegeUnit siege = event.getSiegeUnit();
        Location target = event.getTargetLocation();
        double damage = event.damageDealt();

        if (damage > 0) {
            // 记录攻击冷却
            String key = siege.id().toString() + "_" + target.getBlockX() + "_" + target.getBlockZ();
            attackCooldowns.put(key, System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWallDestroyed(WallDestroyedEvent event) {
        WallData wall = event.getWall();
        SiegeUnit destroyer = event.getDestroyer();

        // 记录城墙摧毁事件
        nationService.nationById(new NationId(wall.nationId())).ifPresent(defender -> {
            Component msg = Component.text("[城墙被摧毁] " + wall.type().displayName() +
                " 已被攻破! 位置: (" + wall.blockX() + "," + wall.blockZ() + ")")
                .color(NamedTextColor.DARK_RED);
            broadcastToNation(defender, msg);
        });

        nationService.nationById(new NationId(destroyer.nationId())).ifPresent(attacker -> {
            Component msg = Component.text("[胜利] 成功摧毁 " + wall.type().displayName() +
                " 位置: (" + wall.blockX() + "," + wall.blockZ() + ")")
                .color(NamedTextColor.GOLD);
            broadcastToNation(attacker, msg);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSiegeDestroyed(SiegeDestroyedEvent event) {
        SiegeUnit siege = event.getSiegeUnit();

        nationService.nationById(new NationId(siege.nationId())).ifPresent(nation -> {
            Component msg = Component.text("[攻城器械损失] " + siege.type().displayName() +
                " 已被摧毁!")
                .color(NamedTextColor.RED);
            broadcastToNation(nation, msg);
        });
    }

    // ==================== 玩家交互事件 ====================

    /**
     * 处理玩家右键点击攻城器械交互
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理右键动作
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // 检查玩家是否靠近己方攻城器械
        List<SiegeUnit> nearbySieges = siegeService.getSiegesNear(player.getLocation(), PROXIMITY_RADIUS);

        for (SiegeUnit siege : nearbySieges) {
            // 只处理玩家国家的攻城器械
            Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());
            if (playerNation.isEmpty()) {
                continue;
            }

            if (!siege.nationId().equals(playerNation.get().id().value())) {
                continue;
            }

            // 处理交互
            handleSiegeInteraction(player, siege);
            break; // 每次只处理一个
        }
    }

    /**
     * 处理玩家移动事件
     * 触发自动攻城检测
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getTo();

        // 检查附近是否有敌方攻城器械
        List<SiegeUnit> nearbySieges = siegeService.getSiegesNear(location, ATTACK_TRIGGER_RADIUS);

        for (SiegeUnit siege : nearbySieges) {
            // 跳过己方攻城器械
            Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());
            if (playerNation.isEmpty()) {
                continue;
            }

            if (siege.nationId().equals(playerNation.get().id().value())) {
                continue;
            }

            // 检查外交关系
            if (!isEnemySiege(siege, player)) {
                continue;
            }

            // 触发警告
            triggerProximityWarning(player, siege);
            break;
        }
    }

    /**
     * 处理攻城器械被攻击事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSiegeDamaged(EntityDamageByEntityEvent event) {
        // 这里可以添加对攻城器械实体的保护逻辑
        // 如果攻城器械被实现为实体，可以在这里处理
    }

    // ==================== 辅助方法 ====================

    /**
     * 处理玩家与攻城器械的交互
     */
    private void handleSiegeInteraction(Player player, SiegeUnit siege) {
        // 可以打开GUI或显示信息
        Component info = Component.text("")
            .append(Component.text("=== 攻城器械状态 ===\n").color(NamedTextColor.GOLD))
            .append(Component.text("类型: " + siege.type().displayName() + "\n").color(NamedTextColor.GRAY))
            .append(Component.text("生命值: " + String.format("%.0f%%", siege.health()) + "\n").color(NamedTextColor.GRAY))
            .append(Component.text("弹药: " + siege.ammunition() + "/100\n").color(NamedTextColor.GRAY))
            .append(Component.text("状态: " + siege.state().name() + "\n").color(NamedTextColor.GRAY));

        player.sendMessage(info);
    }

    /**
     * 触发接近警告
     */
    private void triggerProximityWarning(Player player, SiegeUnit siege) {
        String key = player.getUniqueId().toString() + "_" + siege.id().toString();
        long lastWarning = playerSiegeProximity.getOrDefault(key, 0L);

        // 每30秒警告一次
        if (System.currentTimeMillis() - lastWarning > 30000) {
            nationService.nationById(new NationId(siege.nationId())).ifPresent(attackerNation -> {
                Component warning = Component.text("[警告] 敌方攻城器械(" + attackerNation.name() +
                    " 的 " + siege.type().displayName() + ")在附近!")
                    .color(NamedTextColor.RED);
                player.sendMessage(warning);
            });

            playerSiegeProximity.put(key, System.currentTimeMillis());
        }
    }

    /**
     * 检查是否为敌方攻城器械
     */
    private boolean isEnemySiege(SiegeUnit siege, Player player) {
        Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());

        if (playerNation.isEmpty()) {
            return false;
        }

        // 检查战争状态
        if (warService != null) {
            nationService.nationById(new NationId(siege.nationId())).ifPresent(siegeNation -> {
                isAtWar(playerNation.get(), siegeNation);
            });
        }

        // 默认不同国家即为敌对
        return !playerNation.get().id().value().equals(siege.nationId());
    }

    /**
     * 检查两国家是否处于战争状态
     */
    private boolean isAtWar(Nation nation1, Nation nation2) {
        if (warService == null) {
            // 没有战争服务时，不同国家即为战争状态
            return !nation1.id().value().equals(nation2.id().value());
        }

        // 使用战争服务检查
        return warService.atWar(nation1.id(), nation2.id());
    }

    /**
     * 向国家成员广播消息
     */
    private void broadcastToNation(Nation nation, Component message) {
        for (NationMember member : nation.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 检查两点是否为同一方块
     */
    private boolean isSameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }

    /**
     * 清理过期的记录
     */
    public void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();

        // 清理过期警告
        playerSiegeProximity.entrySet().removeIf(entry ->
            now - entry.getValue() > 60000 // 1分钟过期
        );

        // 清理过期冷却
        attackCooldowns.entrySet().removeIf(entry ->
            now - entry.getValue() > ATTACK_COOLDOWN_MS
        );
    }

    // E-055 修复: 玩家退出时清理攻城器械状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerKey = event.getPlayer().getUniqueId().toString();
        playerSiegeProximity.remove(playerKey);
        attackCooldowns.remove(playerKey);
    }
}
