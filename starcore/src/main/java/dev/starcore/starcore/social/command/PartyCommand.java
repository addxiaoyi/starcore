package dev.starcore.starcore.social.command;

import dev.starcore.starcore.social.party.Party;
import dev.starcore.starcore.social.party.PartyService;
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
 * 派对命令（小队系统） /party
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {
    private final PartyService service;

    // D-048: 删除无参构造（旧实现 "new PartyService()" 脱离主存与持久化）。
    public PartyCommand(PartyService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用派对命令", NamedTextColor.RED));
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
                    Party p = service.createParty(self);
                    player.sendMessage(Component.text("已创建派对", NamedTextColor.GREEN));
                }
                case "invite", "邀请" -> {
                    Player t = requireOnline(player, args);
                    if (t == null) return true;
                    Party p = service.getPlayerParty(self);
                    if (p == null) {
                        player.sendMessage(Component.text("你还没创建派对，请先用 /party create 创建", NamedTextColor.RED));
                        return true;
                    }
                    service.inviteMember(p.getId(), self, t.getUniqueId());
                    player.sendMessage(Component.text("已邀请 " + t.getName(), NamedTextColor.GREEN));
                    t.sendMessage(Component.text(player.getName() + " 邀请你加入派对，使用 /party accept " + player.getName(), NamedTextColor.AQUA));
                }
                case "accept", "接受" -> {
                    UUID inviter = resolveOffline(player, args);
                    if (inviter == null) return true;
                    service.acceptInvite(self, inviter);
                    player.sendMessage(Component.text("已加入 " + nameOf(inviter) + " 的派对", NamedTextColor.GREEN));
                }
                case "deny", "reject", "拒绝" -> {
                    UUID inviter = resolveOffline(player, args);
                    if (inviter == null) return true;
                    service.rejectInvite(self, inviter);
                    player.sendMessage(Component.text("已拒绝邀请", NamedTextColor.YELLOW));
                }
                case "kick", "踢出" -> {
                    UUID target = resolveOffline(player, args);
                    if (target == null) return true;
                    Party p = service.getPlayerParty(self);
                    if (p == null) { player.sendMessage(Component.text("你不在任何派对", NamedTextColor.RED)); return true; }
                    service.kickMember(p.getId(), self, target);
                    player.sendMessage(Component.text("已踢出 " + nameOf(target), NamedTextColor.YELLOW));
                }
                case "leave", "离开" -> {
                    if (service.leaveParty(self)) player.sendMessage(Component.text("已离开派对", NamedTextColor.YELLOW));
                    else player.sendMessage(Component.text("你不在任何派对", NamedTextColor.RED));
                }
                case "info", "list", "信息" -> showInfo(player, self);
                default -> sendHelp(player);
            }
        } catch (RuntimeException ex) {
            player.sendMessage(Component.text("操作失败: " + ex.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    // PLACEHOLDER_HELPERS
    @Nullable
    private Player requireOnline(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /party " + args[0] + " <玩家>", NamedTextColor.YELLOW));
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[1], NamedTextColor.RED));
        }
        return t;
    }

    /**
     * 通过名称解析玩家 UUID，accept/deny/kick 不强制在线。
     */
    @Nullable
    private UUID resolveOffline(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /party " + args[0] + " <玩家>", NamedTextColor.YELLOW));
            return null;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) return online.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
        if (off == null || (!off.hasPlayedBefore() && off.getFirstPlayed() <= 0)) {
            sender.sendMessage(Component.text("玩家不存在: " + args[1], NamedTextColor.RED));
            return null;
        }
        return off.getUniqueId();
    }

    private String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        String n = off.getName();
        return n != null ? n : id.toString();
    }

    private void showInfo(Player player, UUID self) {
        Party p = service.getPlayerParty(self);
        if (p == null) {
            player.sendMessage(Component.text("你不在任何派对", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("=== 派对 (" + p.getMemberCount() + "/" + p.getMaxMembers() + ") ===", NamedTextColor.GOLD));
        for (UUID id : p.getMembers()) {
            OfflinePlayer m = Bukkit.getOfflinePlayer(id);
            boolean leader = p.isLeader(id);
            player.sendMessage(Component.text(" - " + (m.getName() == null ? id : m.getName())
                + (leader ? " [队长]" : ""), leader ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== 派对(小队)命令 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/party create - 创建派对", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party invite <玩家> - 邀请玩家", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party accept <玩家> - 接受邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party deny <玩家> - 拒绝邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party kick <玩家> - 踢出成员", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party leave - 离开派对", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party info - 派对信息", NamedTextColor.GRAY));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("create", "invite", "accept", "deny", "kick", "leave", "info");
            List<String> out = new ArrayList<>();
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        return new ArrayList<>();
    }
}

