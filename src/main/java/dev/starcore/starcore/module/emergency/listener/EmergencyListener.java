package dev.starcore.starcore.module.emergency.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.emergency.EmergencyService;
import dev.starcore.starcore.module.emergency.event.EmergencyCancelledEvent;
import dev.starcore.starcore.module.emergency.event.EmergencyDeclaredEvent;
import dev.starcore.starcore.module.emergency.event.EmergencyExpiredEvent;
import dev.starcore.starcore.module.emergency.model.EmergencyState;
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

import java.util.Optional;

/**
 * 紧急状态事件监听器
 * 响应紧急状态事件并执行相应效果
 */
public final class EmergencyListener implements Listener {

    private final EmergencyService emergencyService;
    private final NationService nationService;
    private final MessageService messages;

    public EmergencyListener(
        EmergencyService emergencyService,
        NationService nationService,
        MessageService messages
    ) {
        this.emergencyService = emergencyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 处理紧急状态宣布事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEmergencyDeclared(EmergencyDeclaredEvent event) {
        EmergencyState emergency = event.getEmergency();

        // 获取国家信息
        String nationName = nationService.nationById(emergency.nationId())
            .map(Nation::name)
            .orElse("Unknown Nation");

        // 广播通知
        String title = messages.format("emergency.event.declared.title", nationName, emergency.type().displayName());
        String subtitle = messages.format("emergency.event.declared.subtitle");

        broadcastToAll(Component.text("[!] ", NamedTextColor.WHITE)
            .append(Component.text(title, NamedTextColor.RED))
            .append(Component.text(" - " + subtitle, NamedTextColor.GRAY)));

        // 通知国家成员
        notifyNationMembers(emergency.nationId(),
            messages.format("emergency.event.declared.member", emergency.type().displayName(), emergency.remainingMinutes()));
    }

    /**
     * 处理紧急状态取消事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEmergencyCancelled(EmergencyCancelledEvent event) {
        EmergencyState emergency = event.getEmergency();

        // 获取国家信息
        String nationName = nationService.nationById(emergency.nationId())
            .map(Nation::name)
            .orElse("Unknown Nation");

        // 广播通知
        String title = messages.format("emergency.event.cancelled.title", nationName);
        String subtitle = messages.format("emergency.event.cancelled.subtitle");

        broadcastToAll(Component.text("[*] ", NamedTextColor.WHITE)
            .append(Component.text(title, NamedTextColor.GREEN))
            .append(Component.text(" - " + subtitle, NamedTextColor.GRAY)));

        // 通知国家成员
        notifyNationMembers(emergency.nationId(),
            messages.format("emergency.event.cancelled.member"));
    }

    /**
     * 处理紧急状态过期事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEmergencyExpired(EmergencyExpiredEvent event) {
        EmergencyState emergency = event.getEmergency();

        // 获取国家信息
        String nationName = nationService.nationById(emergency.nationId())
            .map(Nation::name)
            .orElse("Unknown Nation");

        // 广播通知
        String title = messages.format("emergency.event.expired.title", nationName);
        String subtitle = messages.format("emergency.event.expired.subtitle", emergency.type().displayName());

        broadcastToAll(Component.text("[*] ", NamedTextColor.WHITE)
            .append(Component.text(title, NamedTextColor.YELLOW))
            .append(Component.text(" - " + subtitle, NamedTextColor.GRAY)));

        // 通知国家成员
        notifyNationMembers(emergency.nationId(),
            messages.format("emergency.event.expired.member", emergency.type().displayName()));
    }

    /**
     * 广播消息给所有在线玩家
     */
    private void broadcastToAll(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
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

        for (NationMember member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
            }
        }
    }
}