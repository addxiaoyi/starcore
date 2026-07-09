package dev.starcore.starcore.module.policy.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.event.PolicyActivatedEvent;
import dev.starcore.starcore.module.policy.event.PolicyExpiredEvent;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.Optional;
import java.util.UUID;

/**
 * 国策事件监听器
 * 响应 PolicyActivatedEvent, PolicyExpiredEvent 并广播消息给国家成员
 */
public final class PolicyEventListener implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault());

    private final PolicyService policyService;
    private final NationService nationService;
    private final MessageService messages;

    public PolicyEventListener(PolicyService policyService, NationService nationService, MessageService messages) {
        this.policyService = policyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 处理国策激活事件
     * 当国家激活一个国策时触发
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPolicyActivated(@NotNull PolicyActivatedEvent event) {
        NationId nationId = event.nationId();
        PolicyDefinition definition = event.definition();

        String nationName = getNationName(nationId);
        String policyName = definition.displayName();
        String categoryName = definition.category().displayName();

        // 格式化消息
        String titleMsg = messages.format("policy.event.activated.title", policyName);
        String subtitleMsg = messages.format("policy.event.activated.subtitle", nationName, categoryName);

        // 计算剩余时间（如果是限时国策）
        String durationStr = formatDuration(definition.durationSeconds());

        // 广播国策激活消息
        broadcastPolicyMessage(
            titleMsg,
            subtitleMsg,
            NamedTextColor.GOLD,
            createPolicyHoverInfo(nationName, definition, durationStr, true)
        );

        // 通知国家成员
        notifyNationMembers(nationId,
            messages.format("policy.event.your-nation.activated", policyName, durationStr));
    }

    /**
     * 处理国策过期事件
     * 当国家激活的国策到期时触发
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPolicyExpired(@NotNull PolicyExpiredEvent event) {
        NationId nationId = event.nationId();
        PolicyDefinition definition = event.definition();

        if (definition == null) {
            return;
        }

        String nationName = getNationName(nationId);
        String policyName = definition.displayName();
        String categoryName = definition.category().displayName();

        // 格式化消息
        String titleMsg = messages.format("policy.event.expired.title", policyName);
        String subtitleMsg = messages.format("policy.event.expired.subtitle", nationName);

        // 计算国策持续时间
        Duration duration = Duration.between(event.state().activatedAt(), Instant.now());
        String durationStr = formatDurationFromDuration(duration);

        // 广播国策过期消息
        broadcastPolicyMessage(
            titleMsg,
            subtitleMsg,
            NamedTextColor.YELLOW,
            createPolicyExpiredHoverInfo(nationName, definition, durationStr)
        );

        // 通知国家成员
        notifyNationMembers(nationId,
            messages.format("policy.event.your-nation.expired", policyName));
    }

    /**
     * 广播国策消息给所有在线玩家
     */
    private void broadcastPolicyMessage(String title, String subtitle, NamedTextColor color, Component hoverInfo) {
        Component message = Component.text()
            .append(Component.text("[*] ", NamedTextColor.WHITE))
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
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(message)
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
                member.sendMessage(Component.text(message, NamedTextColor.AQUA));
            }
        }
    }

    /**
     * 创建国策悬停信息（激活时）
     */
    private Component createPolicyHoverInfo(String nationName, PolicyDefinition definition,
                                            String durationStr, boolean isActivated) {
        String prefix = isActivated ? "Activated" : "Expired";

        return Component.empty()
            .append(Component.text("★ Policy " + prefix + " ★", NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Nation: ", NamedTextColor.GRAY))
            .append(Component.text(nationName, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Policy: ", NamedTextColor.GRAY))
            .append(Component.text(definition.displayName(), NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Category: ", NamedTextColor.GRAY))
            .append(Component.text(definition.category().displayName(), NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Duration: ", NamedTextColor.GRAY))
            .append(Component.text(durationStr, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Effects:", NamedTextColor.GRAY))
            .append(Component.newline());
    }

    /**
     * 创建国策过期悬停信息
     */
    private Component createPolicyExpiredHoverInfo(String nationName, PolicyDefinition definition, String durationStr) {
        return Component.text()
            .append(Component.text("★ Policy Expired ★", NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Nation: ", NamedTextColor.GRAY))
            .append(Component.text(nationName, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Policy: ", NamedTextColor.GRAY))
            .append(Component.text(definition.displayName(), NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Category: ", NamedTextColor.GRAY))
            .append(Component.text(definition.category().displayName(), NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Active Duration: ", NamedTextColor.GRAY))
            .append(Component.text(durationStr, NamedTextColor.WHITE))
            .build();
    }

    /**
     * 获取国家名称
     */
    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("Unknown Nation");
    }

    /**
     * 格式化国策持续时间
     */
    private String formatDuration(long seconds) {
        if (seconds < 0) {
            return messages.format("policy.duration.permanent", "Permanent");
        }
        return formatDurationFromDuration(Duration.ofSeconds(seconds));
    }

    /**
     * 格式化时长
     */
    private String formatDurationFromDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return String.format("%ds", duration.toSecondsPart());
        }
    }
}
