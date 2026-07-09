package dev.starcore.starcore.module.war.listener;
import java.util.Optional;

import dev.starcore.starcore.foundation.animation.ParticleEffectManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.event.WarDeclaredEvent;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import dev.starcore.starcore.war.War;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

/**
 * 战争事件监听器
 * 响应 WarStartedEvent, WarEndedEvent, WarDeclaredEvent 并广播消息
 */
public final class WarEventListener implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault());

    private final NationService nationService;
    private final MessageService messages;
    private final ParticleEffectManager particleManager;

    public WarEventListener(NationService nationService, MessageService messages) {
        this(nationService, messages, null);
    }

    public WarEventListener(NationService nationService, MessageService messages, ParticleEffectManager particleManager) {
        this.nationService = nationService;
        this.messages = messages;
        this.particleManager = particleManager;
    }

    /**
     * 处理战争宣战事件
     * 在宣战声明时触发（准备期开始时）
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWarDeclared(@NotNull WarDeclaredEvent event) {
        War war = event.getWar();

        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());

        // 格式化消息
        String titleMsg = messages.format("war.event.declared.title", aggressorName, defenderName);
        String subtitleMsg = messages.format("war.event.declared.subtitle", aggressorName, defenderName);

        // 广播战争宣战消息
        broadcastWarMessage(
            titleMsg,
            subtitleMsg,
            NamedTextColor.DARK_RED,
            createWarHoverInfo(war)
        );

        // 通知参战国成员
        notifyNationMembers(war.aggressor(), messages.format("war.event.your-nation.declared", defenderName));
        notifyNationMembers(war.defender(), messages.format("war.event.enemy.declared", aggressorName));

        // 播放宣战动画效果
        playWarDeclaredEffects();
    }

    /**
     * 处理战争开始事件
     * 在准备期结束后触发（战争正式开始）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWarStarted(@NotNull WarStartedEvent event) {
        War war = event.getWar();

        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());

        // 格式化消息
        String titleMsg = messages.format("war.event.started.title", aggressorName, defenderName);
        String subtitleMsg = messages.format("war.event.started.subtitle");

        // 广播战争开始消息
        broadcastWarMessage(
            titleMsg,
            subtitleMsg,
            NamedTextColor.RED,
            createWarHoverInfo(war)
        );

        // 通知参战国成员
        notifyNationMembers(war.aggressor(), messages.format("war.event.your-nation.started", defenderName));
        notifyNationMembers(war.defender(), messages.format("war.event.enemy.started", aggressorName));

        // 播放战争开始动画效果
        playWarStartedEffects();
    }

    /**
     * 处理战争结束事件
     * 在战争正式结束时触发
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWarEnded(@NotNull WarEndedEvent event) {
        War war = event.getWar();
        WarEndedEvent.WarEndReason reason = event.getReason();

        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());
        String reasonText = formatWarEndReason(reason);

        // 格式化消息
        String titleMsg = messages.format("war.event.ended.title", aggressorName, defenderName);
        String subtitleMsg = messages.format("war.event.ended.subtitle", reasonText);

        // 广播战争结束消息
        broadcastWarMessage(
            titleMsg,
            subtitleMsg,
            NamedTextColor.GREEN,
            createWarEndedHoverInfo(war, reason)
        );

        // 根据结束原因播放不同动画
        playWarEndedEffects(reason);

        // 计算战争持续时间
        Duration duration = Duration.between(war.declaredAt(), Instant.now());
        String durationStr = formatDuration(duration);

        // 通知参战国成员
        notifyNationMembers(war.aggressor(),
            messages.format("war.event.your-nation.ended", defenderName, durationStr));
        notifyNationMembers(war.defender(),
            messages.format("war.event.enemy.ended", aggressorName, durationStr));
    }

    /**
     * 广播战争消息给所有在线玩家
     */
    private void broadcastWarMessage(String title, String subtitle, NamedTextColor color, Component hoverInfo) {
        Component message = Component.text()
            .append(Component.text("[!] ", NamedTextColor.WHITE))
            .append(Component.text(title, color).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text(subtitle, NamedTextColor.GRAY))
            .hoverEvent(HoverEvent.showText(hoverInfo))
            .build();

        // 发送给所有在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }

        // 同时发送给控制台
        Bukkit.getConsoleSender().sendMessage(
            LegacyComponentSerializer.legacySection().serialize(message)
        );
    }

    /**
     * 通知国家所有在线成员
     */
    private void notifyNationMembers(NationId nationId, String message) {
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        for (UUID memberId : nation.members().stream().map(NationMember::playerId).toList()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(message, NamedTextColor.YELLOW));
            }
        }
    }

    /**
     * 创建战争悬停信息
     */
    private Component createWarHoverInfo(War war) {
        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());
        String declaredAt = DATE_FORMATTER.format(war.declaredAt());
        String goalText = war.goal() != null ? war.goal().displayName() : "Unknown";

        return Component.text()
            .append(Component.text("★ War Declaration ★", NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Aggressor: ", NamedTextColor.GRAY))
            .append(Component.text(aggressorName, NamedTextColor.DARK_RED))
            .append(Component.newline())
            .append(Component.text("Defender: ", NamedTextColor.GRAY))
            .append(Component.text(defenderName, NamedTextColor.BLUE))
            .append(Component.newline())
            .append(Component.text("Declared: ", NamedTextColor.GRAY))
            .append(Component.text(declaredAt, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Goal: ", NamedTextColor.GRAY))
            .append(Component.text(goalText, NamedTextColor.YELLOW))
            .build();
    }

    /**
     * 创建战争结束悬停信息
     */
    private Component createWarEndedHoverInfo(War war, WarEndedEvent.WarEndReason reason) {
        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());
        String reasonText = formatWarEndReason(reason);
        Duration duration = Duration.between(war.declaredAt(), Instant.now());
        String durationStr = formatDuration(duration);

        return Component.text()
            .append(Component.text("★ War Ended ★", NamedTextColor.GREEN))
            .append(Component.newline())
            .append(Component.text("Aggressor: ", NamedTextColor.GRAY))
            .append(Component.text(aggressorName, NamedTextColor.DARK_RED))
            .append(Component.newline())
            .append(Component.text("Defender: ", NamedTextColor.GRAY))
            .append(Component.text(defenderName, NamedTextColor.BLUE))
            .append(Component.newline())
            .append(Component.text("Duration: ", NamedTextColor.GRAY))
            .append(Component.text(durationStr, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(reasonText, NamedTextColor.YELLOW))
            .build();
    }

    /**
     * 格式化战争结束原因
     */
    private String formatWarEndReason(WarEndedEvent.WarEndReason reason) {
        if (reason == null) {
            return messages.format("war.reason.unknown");
        }

        return switch (reason) {
            case SURRENDER -> messages.format("war.reason.surrender");
            case PEACE_TREATY -> messages.format("war.reason.peace-treaty");
            case TIMEOUT -> messages.format("war.reason.timeout");
            case MAX_DURATION -> messages.format("war.reason.max-duration");
            case ADMIN_FORCE -> messages.format("war.reason.admin-force");
            case UNKNOWN -> messages.format("war.reason.unknown");
        };
    }

    /**
     * 格式化战争持续时间
     */
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    /**
     * 获取国家名称
     */
    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("Unknown Nation");
    }

    // ==================== 战争动画效果 ====================

    /**
     * 播放宣战动画效果
     */
    private void playWarDeclaredEffects() {
        if (particleManager == null) return;

        // 向所有在线玩家播放战旗飘扬效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            particleManager.playBattleFlag(player);
        }

        // 播放战鼓节奏
        for (Player player : Bukkit.getOnlinePlayers()) {
            particleManager.playBattleDrum(player);
        }
    }

    /**
     * 播放战争开始动画效果
     */
    private void playWarStartedEffects() {
        if (particleManager == null) return;

        // 向所有在线玩家播放战场迷雾效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            particleManager.playBattleCloud(player);
            particleManager.playWarCry(player);
        }
    }

    /**
     * 播放战争结束动画效果
     */
    private void playWarEndedEffects(WarEndedEvent.WarEndReason reason) {
        if (particleManager == null) return;

        // 根据结束原因播放不同动画
        switch (reason) {
            case SURRENDER -> {
                // 投降 - 播放投降旗帜效果
                for (Player player : Bukkit.getOnlinePlayers()) {
                    particleManager.playSurrenderFlag(player);
                }
            }
            case PEACE_TREATY -> {
                // 和平条约 - 播放胜利效果
                for (Player player : Bukkit.getOnlinePlayers()) {
                    particleManager.playVictoryBurst(player);
                }
            }
            default -> {
                // 其他情况 - 播放失败消散效果
                for (Player player : Bukkit.getOnlinePlayers()) {
                    particleManager.playDefeatFade(player);
                }
            }
        }
    }
}
