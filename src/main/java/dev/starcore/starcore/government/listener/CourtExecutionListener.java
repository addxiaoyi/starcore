package dev.starcore.starcore.government.listener;

import dev.starcore.starcore.government.CourtExecutionService;
import dev.starcore.starcore.government.CourtService;
import dev.starcore.starcore.moderation.jail.JailRecord;
import dev.starcore.starcore.moderation.jail.JailService;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法庭判决执行监听器
 * 处理监禁和驱逐惩罚的实时效果
 */
public final class CourtExecutionListener implements Listener {

    private final CourtService courtService;
    private final CourtExecutionService executionService;
    private final JailService jailService;
    private final NationService nationService;

    // 活跃的监禁限制
    private final Map<UUID, JailRestrictions> jailRestrictions = new ConcurrentHashMap<>();

    public CourtExecutionListener(
            CourtService courtService,
            CourtExecutionService executionService,
            JailService jailService,
            NationService nationService
    ) {
        this.courtService = courtService;
        this.executionService = executionService;
        this.jailService = jailService;
        this.nationService = nationService;
    }

    /**
     * 玩家加入服务器时检查状态
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否被监禁
        if (jailService.isJailed(playerId)) {
            JailRecord record = jailService.getJailRecord(playerId);
            if (record != null) {
                applyJailRestrictions(playerId, record);
                // 传送到监狱
                Location jailLoc = jailService.getJailLocation();
                if (jailLoc != null && player.getLocation().distance(jailLoc) > 5) {
                    player.teleport(jailLoc);
                }
            }
        }

        // 检查是否被驱逐（禁止进入特定国家领土）
        if (executionService.isBanished(playerId)) {
            player.sendMessage("§c§l[法庭] 你目前处于驱逐状态，无法进入相关国家领土");
        }
    }

    /**
     * 玩家移动时检查领土权限和监狱限制
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只关心实际位置变化
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查监禁限制
        JailRestrictions restrictions = jailRestrictions.get(playerId);
        if (restrictions != null && restrictions.active()) {
            Location jailLoc = jailService.getJailLocation();
            if (jailLoc != null) {
                Location to = event.getTo();
                // 检查是否试图离开监狱区域（50格范围）
                if (to.getWorld().equals(jailLoc.getWorld()) &&
                    to.distance(jailLoc) > 50) {
                    event.setCancelled(true);
                    player.sendMessage("§c你被监禁中，无法离开监狱区域");
                    return;
                }
            }
        }

        // 检查驱逐 - 禁止进入驱逐国家的领土
        executionService.getBanishmentInfo(playerId).ifPresent(info -> {
            // 获取目标位置的国家
            String world = event.getTo().getWorld().getName();
            int x = event.getTo().getBlockX() >> 4; // chunk x
            int z = event.getTo().getBlockZ() >> 4; // chunk z

            nationService.claimAt(world, x, z).ifPresent(claim -> {
                if (info.nationId().toString().equals(claim.ownerId())) {
                    event.setCancelled(true);
                    player.sendMessage("§c你被驱逐出国家 §e" + info.nationName() + "§c，无法进入其领土");
                }
            });
        });
    }

    /**
     * 玩家传送时检查
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查监禁限制 - 禁止使用传送命令
        JailRestrictions restrictions = jailRestrictions.get(playerId);
        if (restrictions != null && restrictions.active()) {
            // 允许某些特殊传送（如命令触发），但禁止玩家主动传送
            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            // 允许的传送原因：命令、插件（允许死亡重生等）
            boolean allowedCause = cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
                                  cause == PlayerTeleportEvent.TeleportCause.PLUGIN;
            if (!allowedCause && event.getFrom().distance(event.getTo()) > 10) {
                event.setCancelled(true);
                player.sendMessage("§c你被监禁中，无法使用传送命令");
                return;
            }
        }

        // 检查驱逐 - 禁止传送进入驱逐国家的领土
        executionService.getBanishmentInfo(playerId).ifPresent(info -> {
            String world = event.getTo().getWorld().getName();
            int x = event.getTo().getBlockX() >> 4;
            int z = event.getTo().getBlockZ() >> 4;

            nationService.claimAt(world, x, z).ifPresent(claim -> {
                if (info.nationId().toString().equals(claim.ownerId())) {
                    event.setCancelled(true);
                    player.sendMessage("§c你被驱逐出国家 §e" + info.nationName() + "§c，无法传送进入其领土");
                }
            });
        });
    }

    /**
     * 监禁期间禁止攻击
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        JailRestrictions restrictions = jailRestrictions.get(attacker.getUniqueId());
        if (restrictions != null && restrictions.active() && restrictions.blockCombat()) {
            event.setCancelled(true);
            attacker.sendMessage("§c你被监禁中，无法进行战斗");
        }
    }

    /**
     * 监禁期间禁止破坏方块
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        JailRestrictions restrictions = jailRestrictions.get(player.getUniqueId());
        if (restrictions != null && restrictions.active() && restrictions.blockBuilding()) {
            event.setCancelled(true);
            player.sendMessage("§c你被监禁中，无法破坏方块");
        }
    }

    /**
     * 监禁期间禁止放置方块
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        JailRestrictions restrictions = jailRestrictions.get(player.getUniqueId());
        if (restrictions != null && restrictions.active() && restrictions.blockBuilding()) {
            event.setCancelled(true);
            player.sendMessage("§c你被监禁中，无法放置方块");
        }
    }

    /**
     * 监禁期间禁止物品交易
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        JailRestrictions restrictions = jailRestrictions.get(player.getUniqueId());
        if (restrictions != null && restrictions.active() && restrictions.blockTrading()) {
            event.setCancelled(true);
            player.sendMessage("§c你被监禁中，无法进行物品交易");
        }
    }

    /**
     * 监禁期间禁止聊天
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查监禁限制
        JailRestrictions restrictions = jailRestrictions.get(playerId);
        if (restrictions != null && restrictions.active() && restrictions.blockChat()) {
            event.setCancelled(true);
            player.sendMessage("§c你被监禁中，无法使用聊天");
            return;
        }

        // 检查法庭禁言
        if (executionService.isSilenced(playerId)) {
            event.setCancelled(true);
            player.sendMessage("§c你被法庭禁言中，无法使用聊天");
            return;
        }
    }

    /**
     * 应用监禁限制
     */
    public void applyJailRestrictions(UUID playerId, JailRecord record) {
        JailRestrictions restrictions = new JailRestrictions(
            playerId,
            System.currentTimeMillis() + record.getRemainingTime(),
            true,
            true,   // blockCombat
            true,   // blockBuilding
            true,   // blockTrading
            true    // blockChat
        );
        jailRestrictions.put(playerId, restrictions);
    }

    /**
     * 移除监禁限制
     */
    public void removeJailRestrictions(UUID playerId) {
        jailRestrictions.remove(playerId);
    }

    /**
     * 检查玩家是否有监禁限制
     */
    public boolean hasJailRestrictions(UUID playerId) {
        JailRestrictions restrictions = jailRestrictions.get(playerId);
        return restrictions != null && restrictions.active();
    }

    /**
     * 获取监禁限制
     */
    public JailRestrictions getJailRestrictions(UUID playerId) {
        return jailRestrictions.get(playerId);
    }

    /**
     * 清理过期限制（定期调用）
     */
    public int cleanupExpiredRestrictions() {
        int cleaned = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, JailRestrictions> entry : jailRestrictions.entrySet()) {
            JailRestrictions r = entry.getValue();
            if (r.expiresAt() < now) {
                jailRestrictions.remove(entry.getKey());
                cleaned++;
            }
        }

        return cleaned;
    }

    /**
     * 监禁限制记录
     */
    public record JailRestrictions(
        UUID playerId,
        long expiresAt,
        boolean active,
        boolean blockCombat,
        boolean blockBuilding,
        boolean blockTrading,
        boolean blockChat
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    // E-052 修复: 玩家退出时清理监狱限制状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        jailRestrictions.remove(event.getPlayer().getUniqueId());
    }
}
