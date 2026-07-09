package dev.starcore.starcore.social.command;

import dev.starcore.starcore.social.guild.Guild;
import dev.starcore.starcore.social.guild.GuildService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
 * 公会命令（星座系统） /guild
 */
public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final GuildService service;

    public GuildCommand() {
        this(new GuildService());
    }

    public GuildCommand(GuildService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用公会命令", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        UUID self = player.getUniqueId();
        try {
            switch (args[0].toLowerCase()) {
                case "create", "创建" -> {
                    if (args.length < 3) {
                        player.sendMessage(Component.text("用法: /guild create <名称> <标签>", NamedTextColor.YELLOW));
                        return true;
                    }
                    Guild g = service.createGuild(self, args[1], args[2]);
                    player.sendMessage(Component.text("已创建公会 " + g.getName() + " [" + g.getTag() + "]", NamedTextColor.GREEN));
                }
                case "invite", "邀请" -> {
                    Player t = requireOnline(player, args);
                    if (t == null) return true;
                    Guild g = service.getPlayerGuild(self);
                    if (g == null) { player.sendMessage(Component.text("你不在任何公会", NamedTextColor.RED)); return true; }
                    service.inviteMember(g.getId(), self, t.getUniqueId());
                    player.sendMessage(Component.text("已向 " + t.getName() + " 发送公会邀请", NamedTextColor.GREEN));
                    t.sendMessage(Component.text("你收到了公会 " + g.getName() + " 的邀请，使用 /guild accept 加入", NamedTextColor.AQUA));
                }
                case "accept", "接受" -> {
                    if (args.length < 2) {
                        // 显示所有待处理的公会邀请
                        showGuildInvites(player);
                        return true;
                    }
                    Guild g = service.getGuildByName(args[1]);
                    if (g == null) {
                        player.sendMessage(Component.text("公会不存在: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                    service.acceptInvite(self, g.getId());
                    player.sendMessage(Component.text("已加入公会 " + g.getName(), NamedTextColor.GREEN));
                }
                case "deny", "reject", "拒绝" -> {
                    if (args.length < 2) {
                        player.sendMessage(Component.text("用法: /guild deny <公会名>", NamedTextColor.YELLOW));
                        return true;
                    }
                    Guild g = service.getGuildByName(args[1]);
                    if (g == null) {
                        player.sendMessage(Component.text("公会不存在: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                    service.rejectInvite(self, g.getId());
                    player.sendMessage(Component.text("已拒绝公会 " + g.getName() + " 的邀请", NamedTextColor.YELLOW));
                }
                case "requests", "邀请列表" -> showGuildInvites(player);
                case "kick", "踢出" -> {
                    Player t = requireOnline(player, args);
                    if (t == null) return true;
                    Guild g = service.getPlayerGuild(self);
                    if (g == null) { player.sendMessage(Component.text("你不在任何公会", NamedTextColor.RED)); return true; }
                    service.kickMember(g.getId(), self, t.getUniqueId());
                    player.sendMessage(Component.text("已踢出 " + t.getName(), NamedTextColor.YELLOW));
                }
                case "leave", "离开" -> {
                    if (service.leaveGuild(self)) player.sendMessage(Component.text("已离开公会", NamedTextColor.YELLOW));
                    else player.sendMessage(Component.text("你不在任何公会", NamedTextColor.RED));
                }
                case "info", "信息" -> showInfo(player, self);
                case "list", "列表" -> listGuilds(player);
                case "top", "排行" -> showLeaderboard(player);
                default -> sendHelp(player);
            }
        } catch (RuntimeException ex) {
            player.sendMessage(Component.text("操作失败: " + ex.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    // ========== 帮助方法 ==========

    @Nullable
    private Player requireOnline(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /guild " + args[0] + " <玩家>", NamedTextColor.YELLOW));
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[1], NamedTextColor.RED));
        }
        return t;
    }

    private void showInfo(Player player, UUID self) {
        Guild g = service.getPlayerGuild(self);
        if (g == null) {
            player.sendMessage(Component.text("你不在任何公会", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("=== 公会 " + g.getName() + " [" + g.getTag() + "] ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("等级: " + g.getLevel() + "  经验: " + g.getExperience() + "/" + g.getRequiredExperience(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("成员数: " + g.getMemberCount(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("公会银行: " + g.getBalance(), NamedTextColor.GOLD));

        // 显示成员列表
        player.sendMessage(Component.text("成员列表:", NamedTextColor.DARK_GRAY));
        for (UUID memberId : g.getMembers()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : memberId.toString().substring(0, 8);
            boolean online = offlinePlayer.isOnline();
            var role = g.getMemberRole(memberId);

            player.sendMessage(Component.text()
                .append(Component.text(" - " + name, online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .append(Component.text(" [" + role.getDisplayName() + "]", NamedTextColor.GOLD))
                .append(Component.text(online ? " [在线]" : " [离线]", online ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build());
        }
    }

    private void listGuilds(Player player) {
        var all = service.getAllGuilds();
        if (all.isEmpty()) {
            player.sendMessage(Component.text("当前没有公会", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("公会列表 (" + all.size() + "):", NamedTextColor.GOLD));
        for (Guild g : all) {
            Component msg = Component.text()
                .append(Component.text(" - " + g.getName() + " [" + g.getTag() + "] ", NamedTextColor.AQUA))
                .append(Component.text("Lv." + g.getLevel(), NamedTextColor.GOLD))
                .append(Component.text(" (" + g.getMemberCount() + "人)", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("点击查看详情")))
                .clickEvent(ClickEvent.suggestCommand("/guild info " + g.getName()))
                .build();
            player.sendMessage(msg);
        }
    }

    private void showLeaderboard(Player player) {
        List<Guild> top = service.getGuildLeaderboard(10);
        if (top.isEmpty()) {
            player.sendMessage(Component.text("当前没有公会", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("=== 公会排行榜 ===", NamedTextColor.GOLD));
        int rank = 1;
        for (Guild g : top) {
            player.sendMessage(Component.text(rank + ". " + g.getName() + " [" + g.getTag() + "] - Lv." + g.getLevel(),
                rank <= 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            rank++;
        }
    }

    private void showGuildInvites(Player player) {
        var invites = service.getGuildInvites(player.getUniqueId());
        if (invites.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的公会邀请", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 公会邀请 (" + invites.size() + ") ===", NamedTextColor.GOLD));
        for (var invite : invites) {
            Guild guild = service.getAllGuildsRaw().get(invite.guildId());
            if (guild == null) continue;

            String inviterName = Bukkit.getOfflinePlayer(invite.inviterId()).getName();
            if (inviterName == null) inviterName = "Unknown";

            Component msg = Component.text()
                .append(Component.text(" - " + guild.getName() + " [" + guild.getTag() + "]", NamedTextColor.AQUA))
                .append(Component.text(" 邀请者: " + inviterName, NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受 /guild accept " + guild.getName())))
                .clickEvent(ClickEvent.runCommand("/guild accept " + guild.getName()))
                .build();
            player.sendMessage(msg);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== 公会(星座)命令 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/guild create <名称> <标签> - 创建公会", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild invite <玩家> - 邀请加入", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild accept <公会名> - 接受邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild deny <公会名> - 拒绝邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild requests - 查看邀请列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild kick <玩家> - 踢出成员", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild leave - 离开公会", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild info - 公会信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild list - 公会列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/guild top - 排行榜", NamedTextColor.GRAY));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("create", "invite", "accept", "deny", "requests", "kick", "leave", "info", "list", "top");
            List<String> out = new ArrayList<>();
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("accept") || sub.equals("deny")) {
                // 补全公会名称
                List<String> guildNames = service.getAllGuilds().stream()
                    .map(Guild::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
                return new ArrayList<>(guildNames);
            }
            if (sub.equals("invite") || sub.equals("kick")) {
                // 补全在线玩家
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }
        return new ArrayList<>();
    }
}
