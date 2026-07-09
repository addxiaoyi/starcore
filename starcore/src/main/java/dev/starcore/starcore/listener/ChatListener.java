package dev.starcore.starcore.listener;

import dev.starcore.starcore.moderation.mute.MuteService;
import dev.starcore.starcore.moderation.mute.MuteRecord;
import dev.starcore.starcore.social.chat.PrivateMessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 聊天事件监听器
 */
public final class ChatListener implements Listener {
    private final MuteService muteService;
    private final PrivateMessageService pmService;

    public ChatListener(MuteService muteService, PrivateMessageService pmService) {
        this.muteService = muteService;
        this.pmService = pmService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 检查禁言
        if (muteService.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            MuteRecord record = muteService.getMuteRecord(player.getUniqueId());
            if (record != null) {
                String remaining = muteService.formatRemainingTime(record);
                player.sendMessage("§c你已被禁言！");
                player.sendMessage("§7原因: " + record.getReason());
                player.sendMessage("§7剩余时间: " + remaining);
            }
        }
    }
}
