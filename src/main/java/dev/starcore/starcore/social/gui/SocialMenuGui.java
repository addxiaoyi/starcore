package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.chat.PrivateMessageService;
import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.guild.Guild;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.Party;
import dev.starcore.starcore.social.party.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 社交菜单 GUI - 好友/公会/派对/活动/排行榜
 */
public final class SocialMenuGui implements InventoryHolder {
    private static final int SIZE = 54;

    private final Player player;
    private final FriendService friendService;
    private final GuildService guildService;
    private final PartyService partyService;
    private final PrivateMessageService messageService;

    private final Inventory inventory;

    public SocialMenuGui(Player player, FriendService friendService, GuildService guildService,
                        PartyService partyService, PrivateMessageService messageService) {
        this.player = player;
        this.friendService = friendService;
        this.guildService = guildService;
        this.partyService = partyService;
        this.messageService = messageService;

        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("社交中心", NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题区域
        inventory.setItem(4, createTitleItem());

        // 好友模块（第一行）
        inventory.setItem(10, createFriendsButton());
        inventory.setItem(11, createFriendRequestsButton());
        inventory.setItem(12, createFriendListButton());

        // 公会模块（第二行）
        inventory.setItem(19, createGuildButton());
        inventory.setItem(20, createGuildMembersButton());
        inventory.setItem(21, createGuildInvitesButton());

        // 派对模块（第三行）
        inventory.setItem(28, createPartyButton());
        inventory.setItem(29, createPartyMembersButton());
        inventory.setItem(30, createPartyInvitesButton());

        // 私聊模块（第四行）
        inventory.setItem(37, createPrivateChatButton());
        inventory.setItem(38, createChatHistoryButton());
        inventory.setItem(39, createBlockedPlayersButton());

        // 活动推荐和排行榜（第五行 - 新增）
        inventory.setItem(43, createActivityButton());
        inventory.setItem(44, createLeaderboardButton());

        // 统计信息
        inventory.setItem(45, createStatsButton());

        // 关闭按钮
        inventory.setItem(49, createCloseButton());
    }

    // ==================== 好友模块 ====================

    private ItemStack createFriendsButton() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("好友", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("管理你的好友关系", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("在线好友: " + getOnlineFriendsCount() + "/" + getTotalFriendsCount(), NamedTextColor.GREEN));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开好友菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFriendRequestsButton() {
        int requestCount = friendService.getFriendRequests(player.getUniqueId()).size();
        Material material = requestCount > 0 ? Material.PAPER : Material.BOOK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("好友请求", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("待处理请求: " + requestCount, requestCount > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(""));
        if (requestCount > 0) {
            lore.add(Component.text("点击查看并处理请求", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("暂无待处理请求", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFriendListButton() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);

            skullMeta.displayName(Component.text("我的好友列表", NamedTextColor.GREEN));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("共 " + getTotalFriendsCount() + " 位好友", NamedTextColor.GRAY));
            lore.add(Component.text("其中 " + getOnlineFriendsCount() + " 位在线", NamedTextColor.GREEN));
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看详情", NamedTextColor.YELLOW));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    // ==================== 公会模块 ====================

    private ItemStack createGuildButton() {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        Material material = guild != null ? Material.BEACON : Material.ENDER_EYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (guild != null) {
            meta.displayName(Component.text("我的公会", NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(guild.getName() + " [" + guild.getTag() + "]", NamedTextColor.GOLD));
            lore.add(Component.text("等级: " + guild.getLevel(), NamedTextColor.GRAY));
            lore.add(Component.text("成员: " + guild.getMemberCount(), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看公会信息", NamedTextColor.YELLOW));

            meta.lore(lore);
        } else {
            meta.displayName(Component.text("公会", NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("你还没有加入公会", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("总公会数: " + guildService.getAllGuilds().size(), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看公会列表", NamedTextColor.YELLOW));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuildMembersButton() {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        Material material = Material.PLAYER_HEAD;
        ItemStack item = new ItemStack(material);

        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            if (guild != null) {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(guild.getLeader());
                skullMeta.setOwningPlayer(leader);

                skullMeta.displayName(Component.text("公会成员", NamedTextColor.GOLD));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("成员数量: " + guild.getMemberCount(), NamedTextColor.GRAY));
                lore.add(Component.text("在线: " + getOnlineGuildMembers(guild), NamedTextColor.GREEN));
                lore.add(Component.text(""));
                lore.add(Component.text("点击查看成员列表", NamedTextColor.YELLOW));

                skullMeta.lore(lore);
            } else {
                skullMeta.displayName(Component.text("公会成员", NamedTextColor.GRAY));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("你不在任何公会", NamedTextColor.RED));

                skullMeta.lore(lore);
            }

            item.setItemMeta(skullMeta);
        }

        return item;
    }

    private ItemStack createGuildInvitesButton() {
        // 安全获取公会邀请数量
        int inviteCount = 0;
        String lastGuildName = "无";
        try {
            var invites = guildService.getGuildInvites(player.getUniqueId());
            inviteCount = invites.size();
            // 获取第一个邀请的公会名称（如果有）
            if (inviteCount > 0) {
                var firstInvite = invites.iterator().next();
                var guild = guildService.getAllGuilds().stream()
                    .filter(g -> g.getId().equals(firstInvite.guildId()))
                    .findFirst().orElse(null);
                if (guild != null) {
                    lastGuildName = guild.getName();
                }
            }
        } catch (Exception e) {
            // 服务可能未初始化，安全处理
            inviteCount = 0;
        }

        Material material = inviteCount > 0 ? Material.BEACON : Material.ENDER_PEARL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("公会邀请", NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        if (inviteCount > 0) {
            lore.add(Component.text("待处理邀请: " + inviteCount, NamedTextColor.GREEN));
            lore.add(Component.text("最新邀请来自: " + lastGuildName, NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看并处理邀请", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("待处理邀请: 0", NamedTextColor.GRAY));
            lore.add(Component.text("暂无公会邀请", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 派对模块 ====================

    private ItemStack createPartyButton() {
        Party party = partyService.getPlayerParty(player.getUniqueId());
        Material material = party != null ? Material.NETHER_STAR : Material.FIREWORK_STAR;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (party != null) {
            meta.displayName(Component.text("我的派对", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("成员: " + party.getMemberCount() + "/" + party.getMaxMembers(), NamedTextColor.GRAY));
            lore.add(Component.text("经验共享: " + (party.isExpShare() ? "开启" : "关闭"), NamedTextColor.GRAY));
            lore.add(Component.text("友军伤害: " + (party.isFriendlyFire() ? "开启" : "关闭"), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看派对信息", NamedTextColor.YELLOW));

            meta.lore(lore);
        } else {
            meta.displayName(Component.text("派对", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("你还没有创建或加入派对", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击创建或加入派对", NamedTextColor.YELLOW));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPartyMembersButton() {
        Party party = partyService.getPlayerParty(player.getUniqueId());
        Material material = Material.PLAYER_HEAD;
        ItemStack item = new ItemStack(material);

        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            if (party != null) {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(party.getLeader());
                skullMeta.setOwningPlayer(leader);

                skullMeta.displayName(Component.text("派对成员", NamedTextColor.AQUA));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("队长: " + (leader.getName() != null ? leader.getName() : "Unknown"), NamedTextColor.GOLD));
                lore.add(Component.text("成员: " + party.getMemberCount() + "/" + party.getMaxMembers(), NamedTextColor.GRAY));
                lore.add(Component.text(""));
                lore.add(Component.text("点击查看成员列表", NamedTextColor.YELLOW));

                skullMeta.lore(lore);
            } else {
                skullMeta.displayName(Component.text("派对成员", NamedTextColor.GRAY));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("你不在任何派对", NamedTextColor.RED));

                skullMeta.lore(lore);
            }

            item.setItemMeta(skullMeta);
        }

        return item;
    }

    private ItemStack createPartyInvitesButton() {
        int inviteCount = partyService.getPartyInvites(player.getUniqueId()).size();
        Material material = inviteCount > 0 ? Material.FIREWORK_ROCKET : Material.BLAZE_POWDER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("派对邀请", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("待处理邀请: " + inviteCount, inviteCount > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(""));
        if (inviteCount > 0) {
            lore.add(Component.text("点击查看并处理邀请", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("暂无派对邀请", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 活动推荐和排行榜 ====================

    private ItemStack createActivityButton() {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("活动推荐", NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发现精彩社交活动", NamedTextColor.GRAY));
        lore.add(Component.text("聚会/庆典/比赛/集会", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("参与活动获得奖励", NamedTextColor.GREEN));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开活动中心", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLeaderboardButton() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("社交排行榜", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("影响力/活跃度/好友数量", NamedTextColor.GRAY));
        lore.add(Component.text("各维度社交排行", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("日/周/月/总排行榜", NamedTextColor.GREEN));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开排行榜", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 私聊模块 ====================

    private ItemStack createPrivateChatButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("私聊", NamedTextColor.DARK_GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("与其他玩家私聊", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("/msg <玩家> <消息> - 发送私聊", NamedTextColor.GRAY));
        lore.add(Component.text("/r <消息> - 回复最近私聊", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看帮助", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createChatHistoryButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("聊天记录", NamedTextColor.BLUE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("查看最近的私聊记录", NamedTextColor.GRAY));

        UUID lastPartner = messageService.getLastChatPartner(player.getUniqueId());
        if (lastPartner != null) {
            OfflinePlayer partner = Bukkit.getOfflinePlayer(lastPartner);
            lore.add(Component.text("最近聊天: " + (partner.getName() != null ? partner.getName() : "Unknown"), NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("最近聊天: 无", NamedTextColor.GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看更多记录", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBlockedPlayersButton() {
        int blockedCount = messageService.getMutedPlayers(player.getUniqueId()).size();
        Material material = blockedCount > 0 ? Material.BARRIER : Material.GLASS;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("屏蔽列表", NamedTextColor.DARK_RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("已屏蔽玩家: " + blockedCount, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        if (blockedCount > 0) {
            lore.add(Component.text("点击查看屏蔽列表", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("暂无屏蔽玩家", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 统计和导航 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("社交中心", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 社交统计 ===", NamedTextColor.GOLD));
        lore.add(Component.text("好友: " + getTotalFriendsCount() + " (在线: " + getOnlineFriendsCount() + ")", NamedTextColor.GRAY));
        lore.add(Component.text("公会: " + (guildService.getPlayerGuild(player.getUniqueId()) != null ? "已加入" : "未加入"), NamedTextColor.GRAY));
        lore.add(Component.text("派对: " + (partyService.getPlayerParty(player.getUniqueId()) != null ? "已加入" : "未加入"), NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("社交统计", NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 社交统计 ===", NamedTextColor.GOLD));

        // 好友统计
        int totalFriends = 0;
        int onlineFriends = 0;
        try {
            totalFriends = getTotalFriendsCount();
            onlineFriends = getOnlineFriendsCount();
        } catch (Exception e) {
            // 服务可能未初始化
        }
        lore.add(Component.text("好友: " + totalFriends + " (在线: " + onlineFriends + ")", NamedTextColor.GRAY));

        // 公会状态
        String guildStatus = "未加入";
        try {
            if (guildService != null && guildService.getPlayerGuild(player.getUniqueId()) != null) {
                guildStatus = "已加入";
            }
        } catch (Exception e) {
            // 服务可能未初始化
        }
        lore.add(Component.text("公会: " + guildStatus, NamedTextColor.GRAY));

        // 派对状态
        String partyStatus = "未加入";
        try {
            if (partyService != null && partyService.getPlayerParty(player.getUniqueId()) != null) {
                partyStatus = "已加入";
            }
        } catch (Exception e) {
            // 服务可能未初始化
        }
        lore.add(Component.text("派对: " + partyStatus, NamedTextColor.GRAY));

        lore.add(Component.text(""));

        // 私聊请求数量
        int privateMessageRequests = 0;
        try {
            if (messageService != null && messageService.getLastChatPartner(player.getUniqueId()) != null) {
                privateMessageRequests = 1;
            }
        } catch (Exception e) {
            // 服务可能未初始化
        }
        lore.add(Component.text("私聊请求: " + privateMessageRequests, NamedTextColor.GRAY));

        // 公会邀请数量
        int guildInvites = 0;
        try {
            if (guildService != null) {
                guildInvites = guildService.getGuildInvites(player.getUniqueId()).size();
            }
        } catch (Exception e) {
            // 服务可能未初始化
        }
        lore.add(Component.text("公会邀请: " + guildInvites, NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("关闭", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击关闭菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    private int getTotalFriendsCount() {
        return friendService.getFriendCount(player.getUniqueId());
    }

    private int getOnlineFriendsCount() {
        Set<UUID> friends = friendService.getFriends(player.getUniqueId());
        int count = 0;
        for (UUID friendId : friends) {
            if (Bukkit.getPlayer(friendId) != null) {
                count++;
            }
        }
        return count;
    }

    private int getOnlineGuildMembers(Guild guild) {
        int count = 0;
        for (UUID memberId : guild.getMembers()) {
            if (Bukkit.getPlayer(memberId) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取按钮对应的操作
     */
    public static SocialAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 10 -> SocialAction.FRIENDS;
            case 11 -> SocialAction.FRIEND_REQUESTS;
            case 12 -> SocialAction.FRIEND_LIST;
            case 19 -> SocialAction.GUILD;
            case 20 -> SocialAction.GUILD_MEMBERS;
            case 21 -> SocialAction.GUILD_INVITES;
            case 28 -> SocialAction.PARTY;
            case 29 -> SocialAction.PARTY_MEMBERS;
            case 30 -> SocialAction.PARTY_INVITES;
            case 37 -> SocialAction.PRIVATE_CHAT;
            case 38 -> SocialAction.CHAT_HISTORY;
            case 39 -> SocialAction.BLOCKED_PLAYERS;
            case 43 -> SocialAction.ACTIVITY;
            case 44 -> SocialAction.LEADERBOARD;
            case 45 -> SocialAction.STATS;
            case 49 -> SocialAction.CLOSE;
            default -> SocialAction.NONE;
        };
    }

    /**
     * 社交动作枚举
     */
    public enum SocialAction {
        NONE,
        FRIENDS,
        FRIEND_REQUESTS,
        FRIEND_LIST,
        GUILD,
        GUILD_MEMBERS,
        GUILD_INVITES,
        PARTY,
        PARTY_MEMBERS,
        PARTY_INVITES,
        PRIVATE_CHAT,
        CHAT_HISTORY,
        BLOCKED_PLAYERS,
        ACTIVITY,
        LEADERBOARD,
        STATS,
        CLOSE
    }
}
