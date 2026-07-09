package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.chat.PrivateMessageService;
import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.PartyService;
import dev.starcore.starcore.social.simulation.SocialActivityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 社交菜单事件监听器
 */
public final class SocialMenuListener implements Listener {
    private final FriendService friendService;
    private final GuildService guildService;
    private final PartyService partyService;
    private final PrivateMessageService messageService;
    private final SocialActivityService activityService;

    // 活动推荐GUI
    private ActivityRecommendationListener activityListener;
    // 排行榜GUI
    private SocialLeaderboardListener leaderboardListener;

    // 跟踪打开的社交菜单
    private final WeakHashMap<Player, SocialMenuGui> openMenus = new WeakHashMap<>();

    public SocialMenuListener(FriendService friendService, GuildService guildService,
                             PartyService partyService, PrivateMessageService messageService) {
        this(friendService, guildService, partyService, messageService, null);
    }

    public SocialMenuListener(FriendService friendService, GuildService guildService,
                             PartyService partyService, PrivateMessageService messageService,
                             SocialActivityService activityService) {
        this.friendService = friendService;
        this.guildService = guildService;
        this.partyService = partyService;
        this.messageService = messageService;
        this.activityService = activityService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SocialMenuGui socialMenu)) {
            return;
        }

        // 阻止玩家移动物品
        event.setCancelled(true);

        int slot = event.getRawSlot();
        SocialMenuGui.SocialAction action = SocialMenuGui.getActionFromSlot(slot);

        handleAction(player, action);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player);
        }
    }

    private void handleAction(Player player, SocialMenuGui.SocialAction action) {
        switch (action) {
            case FRIENDS -> {
                player.sendMessage(Component.text("=== 好友命令 ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("/friend add <玩家> - 添加好友", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/friend list - 查看好友", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/friend requests - 查看请求", NamedTextColor.GRAY));
            }
            case FRIEND_REQUESTS -> showFriendRequests(player);
            case FRIEND_LIST -> {
                showFriendList(player);
            }
            case GUILD -> {
                var guild = guildService.getPlayerGuild(player.getUniqueId());
                if (guild != null) {
                    player.sendMessage(Component.text("=== 公会 " + guild.getName() + " ===", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("等级: " + guild.getLevel() + " 经验: " + guild.getExperience(), NamedTextColor.GRAY));
                    player.sendMessage(Component.text("成员: " + guild.getMemberCount(), NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("=== 公会列表 ===", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("/guild create <名称> <标签> - 创建公会", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("/guild list - 查看公会列表", NamedTextColor.GRAY));
                }
            }
            case GUILD_MEMBERS -> showGuildMembers(player);
            case GUILD_INVITES -> showGuildInvites(player);
            case PARTY -> {
                var party = partyService.getPlayerParty(player.getUniqueId());
                if (party != null) {
                    player.sendMessage(Component.text("=== 派对 ===", NamedTextColor.AQUA));
                    player.sendMessage(Component.text("成员: " + party.getMemberCount() + "/" + party.getMaxMembers(), NamedTextColor.GRAY));
                    player.sendMessage(Component.text("/party leave - 离开派对", NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("=== 派对命令 ===", NamedTextColor.AQUA));
                    player.sendMessage(Component.text("/party create - 创建派对", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("/party invite <玩家> - 邀请玩家", NamedTextColor.GRAY));
                }
            }
            case PARTY_MEMBERS -> showPartyMembers(player);
            case PARTY_INVITES -> showPartyInvites(player);
            case PRIVATE_CHAT -> {
                player.sendMessage(Component.text("=== 私聊命令 ===", NamedTextColor.GREEN));
                player.sendMessage(Component.text("/msg <玩家> <消息> - 发送私聊", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/r <消息> - 回复最近私聊", NamedTextColor.GRAY));
            }
            case CHAT_HISTORY -> showChatHistory(player);
            case BLOCKED_PLAYERS -> showBlockedPlayers(player);
            case ACTIVITY -> openActivityMenu(player);
            case LEADERBOARD -> openLeaderboardMenu(player);
            case STATS -> {
                player.sendMessage(Component.text("=== 社交统计 ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("好友: " + friendService.getFriendCount(player.getUniqueId()), NamedTextColor.GRAY));
                player.sendMessage(Component.text("公会: " + (guildService.getPlayerGuild(player.getUniqueId()) != null ? "已加入" : "未加入"), NamedTextColor.GRAY));
                player.sendMessage(Component.text("派对: " + (partyService.getPlayerParty(player.getUniqueId()) != null ? "已加入" : "未加入"), NamedTextColor.GRAY));
            }
            case CLOSE -> {
                player.closeInventory();
            }
            case NONE -> {
                // 不做任何操作
            }
        }
    }

    /**
     * 打开活动推荐菜单
     */
    private void openActivityMenu(Player player) {
        if (activityListener != null && activityService != null) {
            activityListener.openMenu(player);
        } else {
            // 如果活动服务未初始化，显示帮助信息
            player.sendMessage(Component.text("=== 活动系统 ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("活动功能正在初始化...", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/activity create <类型> <名称> - 创建活动", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/activity list - 查看活动列表", NamedTextColor.GRAY));
            player.sendMessage(Component.text("活动类型: party, celebration, competition, gathering", NamedTextColor.GRAY));
        }
    }

    /**
     * 打开排行榜菜单
     */
    private void openLeaderboardMenu(Player player) {
        if (leaderboardListener != null) {
            leaderboardListener.openMenu(player);
        } else {
            // 如果排行榜服务未初始化，显示帮助信息
            player.sendMessage(Component.text("=== 排行榜系统 ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("/influence - 查看影响力排行榜", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/influence daily - 查看今日排行", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/influence weekly - 查看本周排行", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/influence monthly - 查看本月排行", NamedTextColor.GRAY));
        }
    }

    private void showFriendRequests(Player player) {
        var requests = friendService.getFriendRequests(player.getUniqueId());
        if (requests.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的好友请求", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 好友请求 ===", NamedTextColor.GOLD));
        for (UUID requesterId : requests) {
            var offlinePlayer = Bukkit.getOfflinePlayer(requesterId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : requesterId.toString();
            boolean online = offlinePlayer.isOnline();

            Component msg = Component.text()
                .append(Component.text(" - " + name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.text(" [在线]" , NamedTextColor.GREEN))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受/拒绝")))
                .clickEvent(ClickEvent.runCommand("/friend " + name))
                .build();

            player.sendMessage(msg);
        }
    }

    private void showFriendList(Player player) {
        var friends = friendService.getFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            player.sendMessage(Component.text("你还没有好友", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 好友列表 (" + friends.size() + ") ===", NamedTextColor.GOLD));
        for (UUID friendId : friends) {
            var offlinePlayer = Bukkit.getOfflinePlayer(friendId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : friendId.toString();
            boolean online = offlinePlayer.isOnline();

            Component msg = Component.text()
                .append(Component.text(" - " + name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.text(online ? " [在线]" : " [离线]", online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("点击私聊")))
                .clickEvent(ClickEvent.suggestCommand("/msg " + name + " "))
                .build();

            player.sendMessage(msg);
        }
    }

    private void showGuildMembers(Player player) {
        var guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(Component.text("你不在任何公会", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== 公会成员 ===", NamedTextColor.GOLD));
        for (UUID memberId : guild.getMembers()) {
            var offlinePlayer = Bukkit.getOfflinePlayer(memberId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : memberId.toString();
            boolean online = offlinePlayer.isOnline();
            var role = guild.getMemberRole(memberId);

            Component msg = Component.text()
                .append(Component.text(" - " + name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.text(" [" + role.getDisplayName() + "]", NamedTextColor.GOLD))
                .append(Component.text(online ? " [在线]" : " [离线]", online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();

            player.sendMessage(msg);
        }
    }

    private void showGuildInvites(Player player) {
        var invites = guildService.getGuildInvites(player.getUniqueId());
        if (invites.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的公会邀请", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 公会邀请 ===", NamedTextColor.GOLD));
        for (var invite : invites) {
            var guild = guildService.getPlayerGuild(invite.inviterId());
            String guildName = guild != null ? guild.getName() : "Unknown";

            player.sendMessage(Component.text()
                .append(Component.text(" - 来自: " + guildName, NamedTextColor.AQUA))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受")))
                .clickEvent(ClickEvent.runCommand("/guild accept " + guildName))
                .build());
        }
    }

    private void showPartyMembers(Player player) {
        var party = partyService.getPlayerParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("你不在任何派对", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== 派对成员 ===", NamedTextColor.AQUA));
        for (UUID memberId : party.getMembers()) {
            var offlinePlayer = Bukkit.getOfflinePlayer(memberId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : memberId.toString();
            boolean online = offlinePlayer.isOnline();
            boolean leader = party.isLeader(memberId);

            Component msg = Component.text()
                .append(Component.text(" - " + name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.text(leader ? " [队长]" : "", NamedTextColor.GOLD))
                .build();

            player.sendMessage(msg);
        }
    }

    private void showPartyInvites(Player player) {
        var invites = partyService.getPartyInvites(player.getUniqueId());
        if (invites.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的派对邀请", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 派对邀请 ===", NamedTextColor.GOLD));
        for (UUID inviterId : invites) {
            var offlinePlayer = Bukkit.getOfflinePlayer(inviterId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : inviterId.toString();

            player.sendMessage(Component.text()
                .append(Component.text(" - 来自: " + name, NamedTextColor.AQUA))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受")))
                .clickEvent(ClickEvent.runCommand("/party accept " + name))
                .build());
        }
    }

    private void showChatHistory(Player player) {
        var lastPartner = messageService.getLastChatPartner(player.getUniqueId());
        if (lastPartner == null) {
            player.sendMessage(Component.text("没有最近的私聊记录", NamedTextColor.GRAY));
            return;
        }

        var offlinePlayer = Bukkit.getOfflinePlayer(lastPartner);
        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : lastPartner.toString();

        player.sendMessage(Component.text("=== 与 " + name + " 的聊天记录 ===", NamedTextColor.GOLD));
        var messages = messageService.getConversation(player.getUniqueId(), lastPartner, 10);

        for (var msg : messages) {
            boolean isSender = msg.senderId().equals(player.getUniqueId());
            String prefix = isSender ? "我" : name;
            player.sendMessage(Component.text(prefix + ": " + msg.message(), isSender ? NamedTextColor.GREEN : NamedTextColor.AQUA));
        }
    }

    private void showBlockedPlayers(Player player) {
        var blocked = messageService.getMutedPlayers(player.getUniqueId());
        if (blocked.isEmpty()) {
            player.sendMessage(Component.text("你没有屏蔽任何玩家", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 屏蔽列表 ===", NamedTextColor.RED));
        for (UUID blockedId : blocked) {
            var offlinePlayer = Bukkit.getOfflinePlayer(blockedId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : blockedId.toString();

            Component msg = Component.text()
                .append(Component.text(" - " + name, NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("点击取消屏蔽")))
                .clickEvent(ClickEvent.suggestCommand("/unblock " + name))
                .build();

            player.sendMessage(msg);
        }
    }

    /**
     * 打开社交菜单
     */
    public void openMenu(Player player) {
        SocialMenuGui menu = new SocialMenuGui(player, friendService, guildService, partyService, messageService);
        openMenus.put(player, menu);
        player.openInventory(menu.getInventory());
    }

    /**
     * 设置活动推荐监听器
     */
    public void setActivityListener(ActivityRecommendationListener activityListener) {
        this.activityListener = activityListener;
    }

    /**
     * 设置排行榜监听器
     */
    public void setLeaderboardListener(SocialLeaderboardListener leaderboardListener) {
        this.leaderboardListener = leaderboardListener;
    }
}
