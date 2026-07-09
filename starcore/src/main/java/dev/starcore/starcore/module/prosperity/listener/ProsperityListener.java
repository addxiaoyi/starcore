package dev.starcore.starcore.module.prosperity.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.prosperity.ProsperityConfig;
import dev.starcore.starcore.module.prosperity.ProsperityService;
import dev.starcore.starcore.module.prosperity.event.ProsperityChangedEvent;
import dev.starcore.starcore.module.prosperity.event.ProsperityCrisisEvent;
import dev.starcore.starcore.module.prosperity.event.ProsperityLevelChangedEvent;
import dev.starcore.starcore.module.prosperity.event.ProsperityPeakEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 繁荣度事件监听器
 * 监听玩家活动并更新国家繁荣度
 */
public final class ProsperityListener implements Listener {

    private final ProsperityService prosperityService;
    private final NationService nationService;
    private final MessageService messages;
    private final ProsperityConfig config;

    // 玩家活动冷却记录（防止频繁触发）
    private final Map<UUID, Instant> lastActivityTime = new ConcurrentHashMap<>();
    // 建筑冷却
    private final Map<String, Instant> lastBlockTime = new ConcurrentHashMap<>();
    // 战斗击杀冷却
    private final Map<UUID, Instant> lastKillTime = new ConcurrentHashMap<>();

    // 活动冷却时间（秒）
    private static final long ACTIVITY_COOLDOWN_SECONDS = 30;
    // 建筑冷却时间（秒）
    private static final long BLOCK_COOLDOWN_SECONDS = 5;
    // 击杀冷却时间（秒）
    private static final long KILL_COOLDOWN_SECONDS = 60;

    // 繁荣度阈值
    private static final double CRISIS_THRESHOLD = 20.0;
    private static final double PEAK_THRESHOLD = 95.0;

    public ProsperityListener(
        ProsperityService prosperityService,
        NationService nationService,
        MessageService messages,
        ProsperityConfig config
    ) {
        this.prosperityService = prosperityService;
        this.nationService = nationService;
        this.messages = messages;
        this.config = config;
    }

    /**
     * 玩家加入时记录活跃度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        recordActivity(player, "interact");
    }

    /**
     * 玩家离开时更新活跃度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 不降低繁荣度，只是记录
    }

    /**
     * 玩家移动时检查是否需要更新活跃度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨区块移动
        if (isSameChunk(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        if (!isOnCooldown(player.getUniqueId())) {
            recordActivity(player, "explore");
        }
    }

    /**
     * 玩家传送时记录活跃度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        recordActivity(player, "interact");
    }

    /**
     * 玩家重生时记录活跃度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        recordActivity(player, "interact");
    }

    /**
     * 玩家破坏方块时增加繁荣度（建筑贡献）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        String chunkKey = chunkKey(loc);
        if (isOnBlockCooldown(chunkKey)) {
            return;
        }

        // 记录建筑贡献
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isPresent()) {
            NationId nationId = nationOpt.get().id();
            // 破坏方块给予少量繁荣度
            prosperityService.addChunkContribution(
                nationId.value(),
                loc.getWorld().getName(),
                loc.getChunk().getX(),
                loc.getChunk().getZ(),
                0.1  // 小量贡献
            );
            recordActivity(player, "interact");
        }
    }

    /**
     * 玩家放置方块时增加繁荣度（建筑贡献）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        String chunkKey = chunkKey(loc);
        if (isOnBlockCooldown(chunkKey)) {
            return;
        }

        // 记录建筑贡献
        Optional<Nation> nationOpt2 = nationService.nationOf(player.getUniqueId());
        if (nationOpt2.isPresent()) {
            NationId nationId = nationOpt2.get().id();
            // 放置方块给予更多繁荣度（因为建筑是繁荣的重要指标）
            prosperityService.addChunkContribution(
                nationId.value(),
                loc.getWorld().getName(),
                loc.getChunk().getX(),
                loc.getChunk().getZ(),
                0.5  // 中等贡献
            );
            recordActivity(player, "build");
        }
    }

    /**
     * 玩家击杀生物/玩家时增加繁荣度（战斗贡献）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        if (isOnKillCooldown(killerId)) {
            return;
        }

        Optional<Nation> nationOpt3 = nationService.nationOf(killerId);
        if (nationOpt3.isPresent()) {
            // 击杀增加繁荣度
            prosperityService.recordActivity(
                nationOpt3.get().id(),
                killerId,
                "combat"
            );
        }
    }

    /**
     * 玩家交互时增加繁荣度
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) {
            return;
        }

        // 只有使用特殊物品才记录
        ItemStack item = event.getItem();
        if (item != null && !isOnCooldown(player.getUniqueId())) {
            recordActivity(player, "interact");
        }
    }

    /**
     * 繁荣度变化事件处理
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProsperityChanged(ProsperityChangedEvent event) {
        NationId nationId = event.nationId();
        double newValue = event.newValue();

        // 检查是否达到峰值
        if (newValue >= PEAK_THRESHOLD && event.oldValue() < PEAK_THRESHOLD) {
            notifyNationMembers(nationId, ProsperityPeakEvent.create(nationId, newValue,
                prosperityService.getProsperityLevel(nationId)));
        }

        // 检查是否进入危机状态
        if (newValue < CRISIS_THRESHOLD && event.oldValue() >= CRISIS_THRESHOLD) {
            notifyNationMembers(nationId, ProsperityCrisisEvent.create(nationId, newValue,
                prosperityService.getProsperityLevel(nationId)));
        }
    }

    /**
     * 繁荣度等级变化事件处理
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProsperityLevelChanged(ProsperityLevelChangedEvent event) {
        NationId nationId = event.nationId();

        if (event.isUpgrade()) {
            // 升级通知
            notifyLevelChange(nationId, event.newLevel(), true);
        } else if (event.isDowngrade()) {
            // 降级警告
            notifyLevelChange(nationId, event.newLevel(), false);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录玩家活动
     */
    private void recordActivity(Player player, String activityType) {
        Optional<Nation> nationOpt4 = nationService.nationOf(player.getUniqueId());
        if (nationOpt4.isEmpty()) {
            return;
        }

        prosperityService.recordActivity(nationOpt4.get().id(), player.getUniqueId(), activityType);
        lastActivityTime.put(player.getUniqueId(), Instant.now());
    }

    /**
     * 检查玩家是否在活动冷却中
     */
    private boolean isOnCooldown(UUID playerId) {
        Instant lastTime = lastActivityTime.get(playerId);
        if (lastTime == null) {
            return false;
        }
        return Duration.between(lastTime, Instant.now()).toSeconds() < ACTIVITY_COOLDOWN_SECONDS;
    }

    /**
     * 检查区块是否在冷却中
     */
    private boolean isOnBlockCooldown(String chunkKey) {
        Instant lastTime = lastBlockTime.get(chunkKey);
        if (lastTime == null) {
            lastBlockTime.put(chunkKey, Instant.now());
            return false;
        }
        if (Duration.between(lastTime, Instant.now()).toSeconds() >= BLOCK_COOLDOWN_SECONDS) {
            lastBlockTime.put(chunkKey, Instant.now());
            return false;
        }
        return true;
    }

    /**
     * 检查玩家是否在击杀冷却中
     */
    private boolean isOnKillCooldown(UUID playerId) {
        Instant lastTime = lastKillTime.get(playerId);
        if (lastTime == null) {
            lastKillTime.put(playerId, Instant.now());
            return false;
        }
        if (Duration.between(lastTime, Instant.now()).toSeconds() >= KILL_COOLDOWN_SECONDS) {
            lastKillTime.put(playerId, Instant.now());
            return false;
        }
        return true;
    }

    /**
     * 检查两个位置是否在同一个区块
     */
    private boolean isSameChunk(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        return from.getChunk().getX() == to.getChunk().getX()
            && from.getChunk().getZ() == to.getChunk().getZ()
            && from.getWorld().equals(to.getWorld());
    }

    /**
     * 生成区块键
     */
    private String chunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getChunk().getX() + ":" + loc.getChunk().getZ();
    }

    /**
     * 通知国家成员
     */
    private void notifyNationMembers(NationId nationId, Object event) {
        String nationName = nationService.nationById(nationId)
            .map(n -> n.name())
            .orElse("未知");

        Component message;
        TextColor color;

        if (event instanceof ProsperityPeakEvent peak) {
            color = NamedTextColor.GOLD;
            message = Component.text()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text("繁荣度", NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(nationName, color))
                .append(Component.text(" 的繁荣度达到 ", NamedTextColor.GRAY))
                .append(Component.text("巅峰", color))
                .append(Component.text("！等级: Lv." + peak.level(), NamedTextColor.GOLD))
                .build();
        } else if (event instanceof ProsperityCrisisEvent crisis) {
            color = NamedTextColor.RED;
            message = Component.text()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text("繁荣度", NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(nationName, color))
                .append(Component.text(" 面临 ", NamedTextColor.GRAY))
                .append(Component.text(crisis.isSevere() ? "严重危机" : "危机", color))
                .append(Component.text("！繁荣度: " + String.format("%.1f", crisis.prosperity()) + "%", NamedTextColor.RED))
                .build();
        } else {
            return;
        }

        // 向所有在线的国家成员发送通知
        for (var member : nationService.nationById(nationId).orElse(null).members()) {
            org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                onlinePlayer.sendMessage(message);
            }
        }
    }

    /**
     * 通知等级变化
     */
    private void notifyLevelChange(NationId nationId, int newLevel, boolean isUpgrade) {
        String nationName = nationService.nationById(nationId)
            .map(n -> n.name())
            .orElse("未知");

        TextColor color = isUpgrade ? NamedTextColor.GREEN : NamedTextColor.RED;
        String action = isUpgrade ? "升级" : "降级";

        Component message = Component.text()
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(Component.text("繁荣度", NamedTextColor.YELLOW))
            .append(Component.text("] ", NamedTextColor.GRAY))
            .append(Component.text(nationName, color))
            .append(Component.text(" 繁荣度等级" + action + "至 ", NamedTextColor.GRAY))
            .append(Component.text("Lv." + newLevel, color))
            .build();

        // 向所有在线的国家成员发送通知
        var nation = nationService.nationById(nationId).orElse(null);
        if (nation != null) {
            for (var member : nation.members()) {
                org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayer(member.playerId());
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    onlinePlayer.sendMessage(message);
                }
            }
        }
    }
}