package dev.starcore.starcore.module.anniversary.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.anniversary.AnniversaryService;
import dev.starcore.starcore.module.anniversary.event.AnniversaryApproachingEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryCelebratedEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryCreatedEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryDeletedEvent;
import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.Optional;

/**
 * 纪念日事件监听器
 * 处理纪念日相关事件的响应
 */
public final class AnniversaryListener implements Listener {
    private final AnniversaryService anniversaryService;
    private final NationService nationService;
    private final MessageService messages;

    public AnniversaryListener(
        AnniversaryService anniversaryService,
        NationService nationService,
        MessageService messages
    ) {
        this.anniversaryService = anniversaryService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 监听纪念日创建事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAnniversaryCreated(AnniversaryCreatedEvent event) {
        NationAnniversary anniversary = event.getAnniversary();
        Optional<Nation> nation = nationService.nationById(new NationId(anniversary.nationId()));

        if (nation.isPresent()) {
            Bukkit.getLogger().info(String.format(
                "[Anniversary] %s 创建了纪念日: %s (%s)",
                nation.get().name(),
                anniversary.name(),
                anniversary.type().getDisplayName()
            ));
        }
    }

    /**
     * 监听纪念日删除事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAnniversaryDeleted(AnniversaryDeletedEvent event) {
        NationAnniversary anniversary = event.getAnniversary();
        Optional<Nation> nation = nationService.nationById(new NationId(anniversary.nationId()));

        if (nation.isPresent()) {
            Bukkit.getLogger().info(String.format(
                "[Anniversary] %s 删除了纪念日: %s",
                nation.get().name(),
                anniversary.name()
            ));
        }
    }

    /**
     * 监听纪念日即将到来事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnniversaryApproaching(AnniversaryApproachingEvent event) {
        NationAnniversary anniversary = event.getAnniversary();
        int daysUntil = event.getDaysUntil();

        // 查找国家
        Optional<Nation> nation = nationService.nationById(new NationId(anniversary.nationId()));
        if (nation.isEmpty()) {
            return;
        }

        // 获取国家成员
        Collection<NationMember> members = nation.get().members();
        String nationName = nation.get().name();

        // 构建通知消息
        Component notification = Component.text()
            .append(Component.text("[" + anniversary.type().getEmoji() + "] ", NamedTextColor.GOLD))
            .append(Component.text(nationName + " ", NamedTextColor.YELLOW))
            .append(Component.text("的纪念日即将到来！\n", NamedTextColor.GRAY))
            .append(Component.text(anniversary.name(), NamedTextColor.WHITE))
            .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
            .append(Component.text(daysUntil + "天后", NamedTextColor.YELLOW))
            .build();

        // 发送给在线成员
        for (NationMember member : members) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(notification);
            }
        }

        // 如果是重要纪念日，广播通知
        if (anniversary.isMilestone() || daysUntil == 1) {
            Bukkit.broadcast(Component.text(
                String.format("%s %s的纪念日 %s 即将到来！",
                    anniversary.type().getEmoji(),
                    nationName,
                    anniversary.name()),
                NamedTextColor.GOLD
            ));
        }
    }

    /**
     * 监听纪念日庆祝事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnniversaryCelebrated(AnniversaryCelebratedEvent event) {
        NationAnniversary anniversary = event.getAnniversary();

        // 查找国家
        Optional<Nation> nation = nationService.nationById(new NationId(anniversary.nationId()));
        if (nation.isEmpty()) {
            return;
        }

        String nationName = nation.get().name();
        int currentYear = anniversary.getCurrentYear();
        String emoji = anniversary.type().getEmoji();

        // 获取自定义庆祝消息
        String customMessage = anniversary.celebrationMessage();
        if (customMessage != null && !customMessage.isEmpty()) {
            customMessage = customMessage.replace("{year}", String.valueOf(currentYear))
                .replace("{nation}", nationName)
                .replace("{name}", anniversary.name());
        } else {
            customMessage = messages.format("anniversary.celebrate.default-message",
                nationName, anniversary.name(), currentYear);
        }

        // 广播庆祝消息
        Bukkit.broadcast(Component.text(
            String.format("%s %s 庆祝 %s 纪念日！%s",
                emoji, nationName, anniversary.name(),
                currentYear > 1 ? " (第" + currentYear + "周年)" : ""),
            NamedTextColor.GOLD
        ));

        // 发送详细消息
        Component detailMessage = Component.text()
            .append(Component.text("━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY))
            .append(Component.text(customMessage + "\n", NamedTextColor.WHITE))
            .append(Component.text("━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            .build();

        Bukkit.broadcast(detailMessage);

        // 如果是里程碑周年，发送特殊通知
        if (anniversary.isMilestone()) {
            Component milestoneMessage = Component.text()
                .append(Component.text("\n★ ", NamedTextColor.GOLD))
                .append(Component.text("里程碑纪念日！", NamedTextColor.YELLOW))
                .append(Component.text(" ★\n", NamedTextColor.GOLD))
                .build();
            Bukkit.broadcast(milestoneMessage);
        }
    }
}
