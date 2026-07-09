package dev.starcore.starcore.clan.chat;

import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan聊天频道
 * 支持Clan内部聊天
 */
public class ClanChatChannel implements Listener {

    private final ClanManager clanManager;

    // 玩家聊天模式（true = Clan频道，false = 全局频道）
    private final Map<UUID, Boolean> chatMode = new ConcurrentHashMap<>();

    public ClanChatChannel(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    /**
     * 切换聊天模式
     */
    public void toggleChatMode(Player player) {
        UUID playerId = player.getUniqueId();
        boolean current = chatMode.getOrDefault(playerId, false);
        chatMode.put(playerId, !current);

        if (!current) {
            player.sendMessage("§a已切换到Clan聊天频道");
        } else {
            player.sendMessage("§7已切换到全局聊天");
        }
    }

    /**
     * 获取聊天模式
     */
    public boolean isClanChatMode(UUID playerId) {
        return chatMode.getOrDefault(playerId, false);
    }

    /**
     * 发送Clan消息
     */
    public void sendClanMessage(Player sender, String message) {
        Clan clan = clanManager.getPlayerClan(sender);

        if (clan == null) {
            sender.sendMessage("§c你不在任何Clan中");
            return;
        }

        // 格式化消息
        String formattedMessage = String.format(
            "§8[§6Clan§8] %s §f%s§8: §7%s",
            clan.getColoredTag(),
            sender.getName(),
            message
        );

        // 发送给所有成员
        for (UUID memberId : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(formattedMessage);
            }
        }
    }

    /**
     * 监听聊天事件
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 检查是否在Clan聊天模式
        if (isClanChatMode(player.getUniqueId())) {
            event.setCancelled(true);

            // 异步发送Clan消息
            Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("StarCore"),
                () -> sendClanMessage(player, event.getMessage())
            );
        }
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        chatMode.remove(playerId);
    }
}
