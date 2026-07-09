package dev.starcore.starcore.chat;

import dev.starcore.starcore.moderation.mute.MuteService;
import dev.starcore.starcore.moderation.mute.MuteRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.social.party.PartyService;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.Party;
import dev.starcore.starcore.social.guild.Guild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 增强聊天事件监听器
 * 支持气泡聊天、关键词高亮、聊天格式
 */
public final class EnhancedChatListener implements Listener {

    private final MuteService muteService;
    private final ChatFormatterService chatFormatter;
    private final NationService nationService;
    private final PartyService partyService;
    private final GuildService guildService;

    // 聊天前缀标识（区分频道）
    private static final Component GLOBAL_PREFIX = Component.text("[全局]")
        .color(NamedTextColor.GRAY);
    private static final Component NATION_PREFIX = Component.text("[国家]")
        .color(NamedTextColor.GOLD);
    private static final Component LOCAL_PREFIX = Component.text("[本地]")
        .color(NamedTextColor.GREEN);
    private static final Component PARTY_PREFIX = Component.text("[小队]")
        .color(NamedTextColor.AQUA);
    private static final Component GUILD_PREFIX = Component.text("[星座]")
        .color(NamedTextColor.LIGHT_PURPLE);

    public EnhancedChatListener(
            MuteService muteService,
            ChatFormatterService chatFormatter,
            @Nullable NationService nationService,
            @Nullable PartyService partyService,
            @Nullable GuildService guildService) {
        this.muteService = muteService;
        this.chatFormatter = chatFormatter;
        this.nationService = nationService;
        this.partyService = partyService;
        this.guildService = guildService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // 检查禁言
        if (muteService.isMuted(playerUuid)) {
            event.setCancelled(true);
            MuteRecord record = muteService.getMuteRecord(playerUuid);
            if (record != null) {
                String remaining = muteService.formatRemainingTime(record);
                Component muteMessage = Component.text()
                    .append(Component.text("[STARCORE] ").color(NamedTextColor.GOLD))
                    .append(Component.text("你已被禁言！").color(NamedTextColor.RED))
                    .build();
                player.sendMessage(muteMessage);
                player.sendMessage(Component.text("原因: ").color(NamedTextColor.GRAY)
                    .append(Component.text(record.getReason()).color(NamedTextColor.WHITE)));
                player.sendMessage(Component.text("剩余时间: ").color(NamedTextColor.GRAY)
                    .append(Component.text(remaining).color(NamedTextColor.YELLOW)));
            }
            return;
        }

        // 获取玩家聊天频道
        ChatFormatterService.ChatChannel channel = chatFormatter.getPlayerChannel(playerUuid);

        // 根据频道处理消息
        switch (channel) {
            case NATION -> handleNationChat(event, player);
            case LOCAL -> handleLocalChat(event, player);
            case PARTY -> handlePartyChat(event, player);
            case GUILD -> handleGuildChat(event, player);
            default -> handleGlobalChat(event, player);
        }
    }

    /**
     * 处理全局聊天
     */
    private void handleGlobalChat(AsyncPlayerChatEvent event, Player player) {
        event.setCancelled(true);

        String playerName = player.getName();
        String message = event.getMessage();

        // 获取国家信息
        String nationName = getNationName(player);
        String nationTag = nationName != null ? nationName : null;

        // 格式化消息
        Component formattedMessage = chatFormatter.formatChatMessage(
            playerName, message, nationName, nationTag, player.getUniqueId());

        // 添加频道前缀
        Component finalMessage = Component.text()
            .append(GLOBAL_PREFIX)
            .appendSpace()
            .append(formattedMessage)
            .build();

        // 广播消息
        broadcastToRecipients(event, finalMessage, player);

        // 记录气泡聊天
        chatFormatter.recordBubbleChat(player.getUniqueId(), message);
    }

    /**
     * 处理国家聊天
     */
    private void handleNationChat(AsyncPlayerChatEvent event, Player player) {
        event.setCancelled(true);

        if (nationService == null) {
            handleGlobalChat(event, player);
            return;
        }

        // 获取玩家国家
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            // 没有国家，切回全局
            chatFormatter.setPlayerChannel(player.getUniqueId(),
                ChatFormatterService.ChatChannel.GLOBAL);
            handleGlobalChat(event, player);
            return;
        }

        var nation = nationOpt.get();
        String playerName = player.getName();
        String message = event.getMessage();
        String nationName = nation.name();

        // 格式化消息
        Component formattedMessage = chatFormatter.formatChatMessage(
            playerName, message, nationName, nationName, player.getUniqueId());

        // 添加国家频道前缀
        Component finalMessage = Component.text()
            .append(NATION_PREFIX)
            .appendSpace()
            .append(formattedMessage)
            .build();

        // 只发送给同国家成员
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            var recipientNationOpt = nationService.nationOf(recipient.getUniqueId());
            if (recipientNationOpt.isPresent() && recipientNationOpt.get().id().equals(nation.id())) {
                recipient.sendMessage(finalMessage);
            }
        }

        // 记录气泡聊天
        chatFormatter.recordBubbleChat(player.getUniqueId(), message);
    }

    /**
     * 处理本地聊天（附近玩家）
     */
    private void handleLocalChat(AsyncPlayerChatEvent event, Player player) {
        event.setCancelled(true);

        String playerName = player.getName();
        String message = event.getMessage();
        int radius = chatFormatter.getBubbleChatRadius();

        // 格式化消息
        Component formattedMessage = chatFormatter.formatChatMessage(
            playerName, message, getNationName(player), getNationName(player), player.getUniqueId());

        // 添加本地频道前缀
        Component finalMessage = Component.text()
            .append(LOCAL_PREFIX)
            .appendSpace()
            .append(formattedMessage)
            .build();

        // 只发送给附近的玩家
        for (Player recipient : player.getLocation().getWorld().getPlayers()) {
            if (recipient.getLocation().distance(player.getLocation()) <= radius) {
                recipient.sendMessage(finalMessage);
            }
        }

        // 记录气泡聊天
        chatFormatter.recordBubbleChat(player.getUniqueId(), message);
    }

    /**
     * 处理小队聊天
     */
    private void handlePartyChat(AsyncPlayerChatEvent event, Player player) {
        event.setCancelled(true);

        if (partyService == null) {
            handleGlobalChat(event, player);
            return;
        }

        // 获取玩家小队
        Party party = partyService.getPlayerParty(player.getUniqueId());
        if (party == null) {
            // 没有小队，切回全局
            chatFormatter.setPlayerChannel(player.getUniqueId(),
                ChatFormatterService.ChatChannel.GLOBAL);
            handleGlobalChat(event, player);
            return;
        }

        String playerName = player.getName();
        String message = event.getMessage();

        // 格式化消息
        Component formattedMessage = chatFormatter.formatChatMessage(
            playerName, message, null, null, player.getUniqueId());

        // 添加小队频道前缀
        Component finalMessage = Component.text()
            .append(PARTY_PREFIX)
            .appendSpace()
            .append(formattedMessage)
            .build();

        // 只发送给小队成员
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(finalMessage);
            }
        }

        // 记录气泡聊天
        chatFormatter.recordBubbleChat(player.getUniqueId(), message);
    }

    /**
     * 处理星座聊天
     */
    private void handleGuildChat(AsyncPlayerChatEvent event, Player player) {
        event.setCancelled(true);

        if (guildService == null) {
            handleGlobalChat(event, player);
            return;
        }

        // 获取玩家星座
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            // 没有星座，切回全局
            chatFormatter.setPlayerChannel(player.getUniqueId(),
                ChatFormatterService.ChatChannel.GLOBAL);
            handleGlobalChat(event, player);
            return;
        }

        String playerName = player.getName();
        String message = event.getMessage();

        // 格式化消息
        Component formattedMessage = chatFormatter.formatChatMessage(
            playerName, message, null, null, player.getUniqueId());

        // 添加星座频道前缀
        Component finalMessage = Component.text()
            .append(GUILD_PREFIX)
            .appendSpace()
            .append(formattedMessage)
            .build();

        // 只发送给星座成员
        for (UUID memberId : guild.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(finalMessage);
            }
        }

        // 记录气泡聊天
        chatFormatter.recordBubbleChat(player.getUniqueId(), message);
    }

    /**
     * 根据事件接收者广播消息
     */
    private void broadcastToRecipients(AsyncPlayerChatEvent event, Component message, Player sender) {
        for (Player recipient : event.getRecipients()) {
            if (recipient.isOnline()) {
                recipient.sendMessage(message);
            }
        }
    }

    /**
     * 获取玩家国家名称
     */
    private String getNationName(Player player) {
        if (nationService == null) return null;
        try {
            return nationService.nationOf(player.getUniqueId())
                .map(n -> n.name()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // E-058 修复: 玩家退出时清理聊天相关状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        chatFormatter.clearPlayerChatData(playerId);
    }
}