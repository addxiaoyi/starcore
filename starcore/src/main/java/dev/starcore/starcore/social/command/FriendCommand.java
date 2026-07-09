package dev.starcore.starcore.social.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.social.friend.FriendService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 好友命令（星链系统） /friend
 */
public final class FriendCommand implements CommandExecutor, TabCompleter {
    private final FriendService service;
    private final MessageService messages;

    public FriendCommand(FriendService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                messages.format("common.player-only"),
                NamedTextColor.RED
            ));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        UUID self = player.getUniqueId();
        try {
            switch (args[0].toLowerCase()) {
                case "add", "添加" -> {
                    Player t = requireOnline(player, args);
                    if (t == null) return true;
                    service.sendFriendRequest(self, t.getUniqueId());
                    player.sendMessage(Component.text(
                        messages.format("friend.request-sent", t.getName()),
                        NamedTextColor.GREEN
                    ));
                    String hint = messages.format("friend.request-received-hint", player.getName(), player.getName());
                    t.sendMessage(Component.text(
                        messages.format("friend.request-received", player.getName()),
                        NamedTextColor.AQUA
                    ));
                    t.sendMessage(Component.text(hint, NamedTextColor.AQUA));
                }
                case "accept", "接受" -> {
                    UUID target = resolveOffline(player, args);
                    if (target == null) return true;
                    service.acceptFriendRequest(self, target);
                    String nm = nameOf(target);
                    player.sendMessage(Component.text(
                        messages.format("friend.accept-success", nm),
                        NamedTextColor.GREEN
                    ));
                }
                case "deny", "reject", "拒绝" -> {
                    UUID target = resolveOffline(player, args);
                    if (target == null) return true;
                    service.rejectFriendRequest(self, target);
                    player.sendMessage(Component.text(
                        messages.format("friend.deny-success", nameOf(target)),
                        NamedTextColor.YELLOW
                    ));
                }
                case "remove", "del", "删除" -> {
                    UUID target = resolveOffline(player, args);
                    if (target == null) return true;
                    service.removeFriend(self, target);
                    player.sendMessage(Component.text(
                        messages.format("friend.remove-success", nameOf(target)),
                        NamedTextColor.YELLOW
                    ));
                }
                case "list", "列表" -> listFriends(player);
                case "requests", "请求" -> listRequests(player);
                default -> sendHelp(player);
            }
        } catch (RuntimeException ex) {
            player.sendMessage(Component.text(
                messages.format("friend.operation-failed", ex.getMessage()),
                NamedTextColor.RED
            ));
        }
        return true;
    }

    // PLACEHOLDER_HELPERS
    @Nullable
    private Player requireOnline(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                messages.format(friendUsageKey(args[0])),
                NamedTextColor.YELLOW
            ));
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            sender.sendMessage(Component.text(
                messages.format("friend.player-not-online", args[1]),
                NamedTextColor.RED
            ));
        }
        return t;
    }

    /**
     * 通过名称解析离线/在线玩家 UUID，accept/deny/remove 不强制在线。
     */
    @Nullable
    private UUID resolveOffline(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                messages.format(friendUsageKey(args[0])),
                NamedTextColor.YELLOW
            ));
            return null;
        }
        // 优先匹配在线玩家，避免 Bukkit.getOfflinePlayer(name) 创建 fake UUID
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
        // 校验玩家真实存在过，避免 fake UUID
        if (off == null || (!off.hasPlayedBefore() && off.getFirstPlayed() <= 0)) {
            sender.sendMessage(Component.text(
                messages.format("friend.player-not-found", args[1]),
                NamedTextColor.RED
            ));
            return null;
        }
        return off.getUniqueId();
    }

    /**
     * 获取 UUID 对应的玩家名称，优先在线玩家，避免 OfflinePlayer 缓存 null。
     */
    private String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        String n = off.getName();
        return n != null ? n : id.toString();
    }

    private String friendUsageKey(String subcommand) {
        return "friend.usage." + subcommand.toLowerCase();
    }

    private void listFriends(Player player) {
        var friends = service.getFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("friend.list-empty"),
                NamedTextColor.GRAY
            ));
            return;
        }
        player.sendMessage(Component.text(
            messages.format("friend.list-header"),
            NamedTextColor.GOLD
        ));
        for (UUID id : friends) {
            String name = nameOf(id);
            Player p = Bukkit.getPlayer(id);
            boolean online = p != null;
            player.sendMessage(Component.text(
                messages.format(online ? "friend.list-item-online" : "friend.list-item-offline", name),
                online ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
        }
    }

    private void listRequests(Player player) {
        var reqs = service.getFriendRequests(player.getUniqueId());
        if (reqs.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("friend.requests-empty"),
                NamedTextColor.GRAY
            ));
            return;
        }
        player.sendMessage(Component.text(
            messages.format("friend.requests-header"),
            NamedTextColor.GOLD
        ));
        for (UUID id : reqs) {
            String name = nameOf(id);
            player.sendMessage(Component.text(
                messages.format("friend.request-item", name),
                NamedTextColor.AQUA
            ));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text(
            messages.format("friend.help-header"),
            NamedTextColor.GOLD
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-add"),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-accept"),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-deny"),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-remove"),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-list"),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("friend.help-requests"),
            NamedTextColor.GRAY
        ));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("add", "accept", "deny", "remove", "list", "requests");
            List<String> out = new ArrayList<>();
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        return new ArrayList<>();
    }
}
