package dev.starcore.starcore.module.nation.teleport;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国家传送服务
 * 提供玩家传送到国家首都、城镇等功能
 */
public final class NationTeleportService {
    private final Plugin plugin;
    private final NationService nationService;
    private final EconomyService economyService;
    private final MessageService messages;
    private final NationTeleportConfig config;

    // 传送冷却记录
    private final ConcurrentHashMap<UUID, Instant> teleportCooldowns = new ConcurrentHashMap<>();
    // 传送倒计时任务
    private final ConcurrentHashMap<UUID, Integer> teleportTasks = new ConcurrentHashMap<>();

    public NationTeleportService(
        Plugin plugin,
        NationService nationService,
        EconomyService economyService,
        MessageService messages,
        NationTeleportConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
    }

    /**
     * 传送玩家到国家首都
     */
    public void teleportToCapital(Player player) {
        UUID playerId = player.getUniqueId();

        // 检查玩家是否属于某个国家
        Optional<Nation> nationOpt = nationService.getNationByMember(playerId);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("teleport.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        teleportToLocation(player, nation, nation.capitalLocation(), TeleportType.CAPITAL);
    }

    /**
     * 传送玩家到指定城镇
     */
    public void teleportToTown(Player player, String townName) {
        UUID playerId = player.getUniqueId();

        // 检查玩家是否属于某个国家
        Optional<Nation> nationOpt = nationService.getNationByMember(playerId);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("teleport.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 获取城镇位置（这里需要城镇系统支持）
        Optional<Location> townLocation = nation.getTownLocation(townName);
        if (townLocation.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("teleport.town-not-found", townName),
                NamedTextColor.RED
            ));
            return;
        }

        teleportToLocation(player, nation, townLocation.get(), TeleportType.TOWN);
    }

    private void teleportToLocation(Player player, Nation nation, Location destination, TeleportType type) {
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (!canTeleport(playerId)) {
            long remainingSeconds = getRemainingCooldown(playerId);
            player.sendMessage(Component.text(
                messages.format("teleport.cooldown", remainingSeconds),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 检查费用
        BigDecimal cost = type.getCost(config);
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = economyService.getBalance(playerId);
            if (balance.compareTo(cost) < 0) {
                player.sendMessage(Component.text(
                    messages.format("teleport.insufficient-funds", cost.toPlainString()),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        // 检查是否已有传送任务
        if (teleportTasks.containsKey(playerId)) {
            player.sendMessage(Component.text(
                messages.format("teleport.already-teleporting"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 开始传送倒计时
        startTeleportCountdown(player, nation, destination, cost, type);
    }

    private void startTeleportCountdown(Player player, Nation nation, Location destination, BigDecimal cost, TeleportType type) {
        UUID playerId = player.getUniqueId();
        Location startLocation = player.getLocation().clone();

        int warmupSeconds = config.warmupSeconds();

        player.sendMessage(Component.text(
            messages.format("teleport.warmup", warmupSeconds),
            NamedTextColor.GREEN
        ));

        // 创建倒计时任务
        int[] countdown = {warmupSeconds};
        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // 检查玩家是否移动
            if (hasMoved(player.getLocation(), startLocation)) {
                cancelTeleport(player);
                player.sendMessage(Component.text(
                    messages.format("teleport.cancelled-moved"),
                    NamedTextColor.RED
                ));
                return;
            }

            countdown[0]--;

            if (countdown[0] > 0) {
                // 显示倒计时
                if (countdown[0] <= 3) {
                    player.sendMessage(Component.text(
                        messages.format("teleport.countdown", countdown[0]),
                        NamedTextColor.YELLOW
                    ));
                }
            } else {
                // 执行传送
                executeTeleport(player, nation, destination, cost, type);
            }
        }, 0L, 20L).getTaskId();

        teleportTasks.put(playerId, taskId);
    }

    private void executeTeleport(Player player, Nation nation, Location destination, BigDecimal cost, TeleportType type) {
        UUID playerId = player.getUniqueId();

        // 取消任务
        Integer taskId = teleportTasks.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        // 扣除费用
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            if (!economyService.withdraw(playerId, cost)) {
                player.sendMessage(Component.text(
                    messages.format("teleport.insufficient-funds", cost.toPlainString()),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        // 传送
        player.teleport(destination);

        // 设置冷却
        teleportCooldowns.put(playerId, Instant.now());

        // 发送成功消息
        player.sendMessage(Component.text(
            messages.format("teleport.success", nation.name()),
            NamedTextColor.GREEN
        ));
    }

    private void cancelTeleport(Player player) {
        Integer taskId = teleportTasks.remove(player.getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    private boolean hasMoved(Location current, Location start) {
        if (!current.getWorld().equals(start.getWorld())) {
            return true;
        }
        double distance = current.distanceSquared(start);
        return distance > 0.1; // 允许微小的位置偏移
    }

    private boolean canTeleport(UUID playerId) {
        Instant lastTeleport = teleportCooldowns.get(playerId);
        if (lastTeleport == null) {
            return true;
        }

        Duration elapsed = Duration.between(lastTeleport, Instant.now());
        return elapsed.compareTo(config.cooldown()) >= 0;
    }

    private long getRemainingCooldown(UUID playerId) {
        Instant lastTeleport = teleportCooldowns.get(playerId);
        if (lastTeleport == null) {
            return 0;
        }

        Duration elapsed = Duration.between(lastTeleport, Instant.now());
        Duration remaining = config.cooldown().minus(elapsed);

        return Math.max(0, remaining.getSeconds());
    }

    /**
     * 玩家退出时清理
     */
    public void onPlayerQuit(UUID playerId) {
        Integer taskId = teleportTasks.remove(playerId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        teleportCooldowns.remove(playerId);
    }

    private enum TeleportType {
        CAPITAL,
        TOWN;

        BigDecimal getCost(NationTeleportConfig config) {
            return this == CAPITAL ? config.capitalCost() : config.townCost();
        }
    }
}
