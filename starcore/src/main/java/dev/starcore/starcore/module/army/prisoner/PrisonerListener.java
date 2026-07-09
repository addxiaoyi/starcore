package dev.starcore.starcore.module.army.prisoner;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 俘虏事件监听器
 */
public final class PrisonerListener implements Listener {

    private final PrisonerService prisonerService;
    private final MessageService messages;

    // 逃跑尝试冷却
    private final ConcurrentHashMap<UUID, Long> escapeCooldowns = new ConcurrentHashMap<>();
    private static final long ESCAPE_COOLDOWN_MS = 60_000; // 1分钟

    public PrisonerListener(PrisonerService prisonerService, MessageService messages) {
        this.prisonerService = prisonerService;
        this.messages = messages;
    }

    /**
     * 玩家加入时检查俘虏状态
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());

        if (prisonerOpt.isPresent()) {
            PrisonerOfWar prisoner = prisonerOpt.get();
            // 通知玩家仍然被俘虏
            player.sendMessage(Component.text(
                messages.format("prisoner.still-captive",
                    prisoner.captorId() != null ? prisoner.captorId().toString() : "未知",
                    prisoner.captorName()),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 玩家退出时保存数据
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 数据已在服务中自动保存
    }

    /**
     * 玩家死亡时可能触发逃跑
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());

        if (prisonerOpt.isEmpty()) {
            return;
        }

        var config = prisonerService.getConfig();

        // 死亡时有几率逃跑成功 (使用配置的逃跑几率)
        if (config.escapeChancePerHour() > 0 && ThreadLocalRandom.current().nextDouble() < config.escapeChancePerHour() / 100.0) {
            prisonerService.completeEscape(prisonerOpt.get().id());
            player.sendMessage(Component.text(
                messages.format("prisoner.escape.death-success"),
                NamedTextColor.GREEN
            ));
        }
    }

    /**
     * 玩家移动时检查逃跑尝试
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // 玩家没有真正移动
        }

        Player player = event.getPlayer();
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());

        if (prisonerOpt.isEmpty()) {
            return;
        }

        // 检查逃跑冷却
        long lastEscape = escapeCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastEscape < ESCAPE_COOLDOWN_MS) {
            return;
        }

        var config = prisonerService.getConfig();

        // 逃跑尝试 (基于配置的逃跑几率)
        if (config.escapeChancePerHour() > 0 && ThreadLocalRandom.current().nextDouble() < config.escapeChancePerHour() / 3600.0) {
            prisonerService.completeEscape(prisonerOpt.get().id());
            player.sendMessage(Component.text(
                messages.format("prisoner.escape.movement-success"),
                NamedTextColor.GREEN
            ));
            escapeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
}