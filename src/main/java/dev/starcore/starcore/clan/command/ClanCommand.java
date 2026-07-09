package dev.starcore.starcore.clan.command;

import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Clan命令系统
 * 命令：/clan <子命令>
 */
public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager clanManager;

    public ClanCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("用法: /clan create <标签> <名称>", NamedTextColor.YELLOW));
                    return true;
                }
                return handleCreate(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "disband" -> handleDisband(player);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan invite <玩家>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleInvite(player, target);
            }
            case "accept" -> {
                if (args.length >= 2) {
                    return handleAccept(player, args[1]);
                }
                handleRequests(player);
                return true;
            }
            case "deny", "reject" -> {
                if (args.length >= 2) {
                    return handleDeny(player, args[1]);
                }
                player.sendMessage(Component.text("用法: /clan deny <Clan标签>", NamedTextColor.YELLOW));
                return true;
            }
            case "requests", "invites" -> {
                handleRequests(player);
                return true;
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan kick <玩家>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleKick(player, target);
            }
            case "leave" -> handleLeave(player);
            case "home" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "settings" -> handleSettings(player, args);
            case "chat" -> handleChat(player);
            case "bank" -> handleBank(player);
            case "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan deposit <金额>", NamedTextColor.YELLOW));
                    return true;
                }
                return handleDeposit(player, args[1]);
            }
            case "withdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan withdraw <金额>", NamedTextColor.YELLOW));
                    return true;
                }
                return handleWithdraw(player, args[1]);
            }
            case "rank" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("用法: /clan rank <玩家> <职位>", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("职位: officer, member", NamedTextColor.GRAY));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleRank(player, target, args[2]);
            }
            case "perm" -> {
                if (args.length < 2) {
                    handlePerm(player);
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handlePerm(player, target);
            }
            case "ally" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan ally <add/remove> <Clan标签>", NamedTextColor.YELLOW));
                    return true;
                }
                if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length < 3) {
                        player.sendMessage(Component.text("用法: /clan ally remove <Clan标签>", NamedTextColor.YELLOW));
                        return true;
                    }
                    return handleAllyRemove(player, args[2]);
                }
                handleAlly(player, args[1]);
                return true;
            }
            case "rival" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan rival <Clan标签>", NamedTextColor.YELLOW));
                    return true;
                }
                handleRival(player, args[1]);
                return true;
            }
            case "tag" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan tag <新标签>", NamedTextColor.YELLOW));
                    return true;
                }
                return handleTag(player, args[1]);
            }
            case "transfer" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /clan transfer <玩家>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleTransfer(player, target);
            }
            case "info" -> {
                String tag = args.length >= 2 ? args[1] : null;
                return handleInfo(player, tag);
            }
            case "list" -> handleList(player);
            case "top" -> handleTop(player);
            case "stats" -> handleStats(player);
            default -> {
                player.sendMessage(Component.text("未知子命令: " + subCommand, NamedTextColor.RED));
                sendHelp(player);
                return true;
            }
        }
        return true;
    }

    // ==================== 成员管理命令 ====================

    private boolean handleCreate(Player player, String tag, String name) {
        Clan clan = clanManager.createClan(tag, name, player.getUniqueId());

        if (clan == null) {
            player.sendMessage(Component.text("创建失败！", NamedTextColor.RED));
            player.sendMessage(Component.text("可能原因：标签已存在、格式不正确（3-4个字母或数字）或你已在Clan中", NamedTextColor.GRAY));
            return true;
        }

        player.sendMessage(Component.text("成功创建Clan！", NamedTextColor.GREEN));
        player.sendMessage(Component.text("标签: " + clan.getColoredTag(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("名称: " + clan.getName(), NamedTextColor.WHITE));
        return true;
    }

    private void handleDisband(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以解散Clan", NamedTextColor.RED));
            return;
        }

        for (UUID memberId : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text("Clan " + clan.getColoredTag() + " 已被解散", NamedTextColor.RED));
            }
        }

        if (clanManager.disbandClan(player.getUniqueId(), clan.getId())) {
            player.sendMessage(Component.text("Clan已解散", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("解散Clan失败", NamedTextColor.RED));
        }
    }

    private boolean handleInvite(Player player, Player target) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.hasPermission(player.getUniqueId(), Clan.ClanPermission.INVITE)) {
            player.sendMessage(Component.text("你没有邀请权限", NamedTextColor.RED));
            return true;
        }

        if (clanManager.getPlayerClan(target) != null) {
            player.sendMessage(Component.text("该玩家已在其他Clan中", NamedTextColor.RED));
            return true;
        }

        if (clanManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(Component.text("已向 " + target.getName() + " 发送邀请", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你收到了Clan " + clan.getDisplayName() + " 的邀请", NamedTextColor.AQUA));
            target.sendMessage(Component.text("使用 /clan accept " + clan.getTag() + " 接受邀请", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("邀请失败（已邀请或权限不足）", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleAccept(Player player, String clanTag) {
        Clan clan = clanManager.acceptInviteByTag(player.getUniqueId(), clanTag);

        if (clan != null) {
            player.sendMessage(Component.text("已加入Clan " + clan.getDisplayName(), NamedTextColor.GREEN));
            clanManager.broadcastToClan(clan.getId(), Component.text(player.getName() + " 加入了Clan", NamedTextColor.YELLOW).toString());
        } else {
            player.sendMessage(Component.text("接受邀请失败（无邀请或Clan不存在）", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleDeny(Player player, String clanTag) {
        if (clanManager.rejectInviteByTag(player.getUniqueId(), clanTag)) {
            player.sendMessage(Component.text("已拒绝邀请", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("拒绝邀请失败（无该Clan的邀请）", NamedTextColor.RED));
        }
        return true;
    }

    private void handleRequests(Player player) {
        var invites = clanManager.getInvites(player.getUniqueId());

        if (invites.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的Clan邀请", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== Clan邀请 (" + invites.size() + ") ===", NamedTextColor.GOLD));
        for (var invite : invites) {
            Clan clan = clanManager.getClan(invite.clanId());
            if (clan == null) continue;

            String inviterName = Bukkit.getOfflinePlayer(invite.inviterId()).getName();
            if (inviterName == null) inviterName = "Unknown";

            Component msg = Component.text()
                .append(Component.text(" - " + clan.getDisplayName(), NamedTextColor.AQUA))
                .append(Component.text(" 邀请者: " + inviterName, NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受 /clan accept " + clan.getTag())))
                .clickEvent(ClickEvent.runCommand("/clan accept " + clan.getTag()))
                .build();
            player.sendMessage(msg);
        }
    }

    private boolean handleKick(Player player, Player target) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以踢出成员", NamedTextColor.RED));
            return true;
        }

        if (clanManager.kickMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(Component.text("已将 " + target.getName() + " 踢出Clan", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你已被踢出Clan " + clan.getColoredTag(), NamedTextColor.RED));
            clanManager.broadcastToClan(clan.getId(), Component.text(target.getName() + " 被踢出了Clan", NamedTextColor.YELLOW).toString());
        } else {
            player.sendMessage(Component.text("踢出失败", NamedTextColor.RED));
        }

        return true;
    }

    private void handleLeave(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (clan.isLeader(player.getUniqueId()) && clan.getMemberCount() > 1) {
            player.sendMessage(Component.text("作为族长，你必须先转让职位或解散Clan", NamedTextColor.RED));
            player.sendMessage(Component.text("使用 /clan disband 解散Clan 或 /clan transfer <玩家> 转让", NamedTextColor.GRAY));
            return;
        }

        clanManager.leaveClan(player.getUniqueId());
        player.sendMessage(Component.text("你已离开Clan " + clan.getColoredTag(), NamedTextColor.YELLOW));
        clanManager.broadcastToClan(clan.getId(), Component.text(player.getName() + " 离开了Clan", NamedTextColor.YELLOW).toString());
    }

    private boolean handleTransfer(Player player, Player target) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以转让职位", NamedTextColor.RED));
            return true;
        }

        if (!clan.isMember(target.getUniqueId())) {
            player.sendMessage(Component.text("目标玩家必须是Clan成员", NamedTextColor.RED));
            return true;
        }

        clanManager.transferLeader(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(Component.text("已将族长转让给 " + target.getName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("你已成为Clan " + clan.getColoredTag() + " 的新族长", NamedTextColor.GOLD));
        clanManager.broadcastToClan(clan.getId(), Component.text(player.getName() + " 将族长转让给了 " + target.getName(), NamedTextColor.YELLOW).toString());

        return true;
    }

    // ==================== 据点命令 ====================

    private void handleHome(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!clan.hasHome()) {
            player.sendMessage(Component.text("Clan尚未设置据点", NamedTextColor.RED));
            return;
        }

        if (!clan.hasPermission(player.getUniqueId(), Clan.ClanPermission.HOME)) {
            player.sendMessage(Component.text("你没有权限使用据点传送", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("正在传送至Clan据点...", NamedTextColor.YELLOW));
        player.teleportAsync(clan.getHome()).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("已传送至Clan据点", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("传送失败", NamedTextColor.RED));
            }
        });
    }

    private void handleSetHome(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!clan.hasPermission(player.getUniqueId(), Clan.ClanPermission.SET_HOME)) {
            player.sendMessage(Component.text("你没有权限设置据点", NamedTextColor.RED));
            return;
        }

        clanManager.setHome(player.getUniqueId(), player.getLocation());
        player.sendMessage(Component.text("Clan据点已设置在当前位置", NamedTextColor.GREEN));
    }

    // ==================== 设置命令 ====================

    private void handleSettings(Player player, String[] args) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!clan.hasPermission(player.getUniqueId(), Clan.ClanPermission.SETTINGS)) {
            player.sendMessage(Component.text("你没有权限修改Clan设置", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            // 显示当前设置
            player.sendMessage(Component.text("=== Clan设置 ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("友军伤害: " + (clan.isFriendlyFire() ? "启用" : "禁用"), NamedTextColor.GRAY));
            player.sendMessage(Component.text("PvP: " + (clan.isPvpEnabled() ? "启用" : "禁用"), NamedTextColor.GRAY));
            player.sendMessage(Component.text("使用 /clan settings ff <on/off> 修改友军伤害", NamedTextColor.GRAY));
            player.sendMessage(Component.text("使用 /clan settings pvp <on/off> 修改PvP", NamedTextColor.GRAY));
            return;
        }

        String setting = args[1].toLowerCase();
        boolean newValue = args.length >= 3 && args[2].equalsIgnoreCase("on");

        switch (setting) {
            case "ff", "friendlyfire" -> {
                clanManager.setFriendlyFire(player.getUniqueId(), newValue);
                player.sendMessage(Component.text("友军伤害已" + (newValue ? "启用" : "禁用"), NamedTextColor.GREEN));
            }
            case "pvp" -> {
                clanManager.setPvPEnabled(player.getUniqueId(), newValue);
                player.sendMessage(Component.text("PvP已" + (newValue ? "启用" : "禁用"), NamedTextColor.GREEN));
            }
            default -> {
                player.sendMessage(Component.text("未知设置项: " + setting, NamedTextColor.RED));
            }
        }
    }

    // ==================== 聊天命令 ====================

    private void handleChat(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        boolean enabled = clanManager.toggleClanChat(player.getUniqueId());

        if (enabled) {
            player.sendMessage(Component.text("Clan聊天已启用", NamedTextColor.GREEN));
            player.sendMessage(Component.text("你的消息将只发送给Clan成员", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Clan聊天已禁用", NamedTextColor.YELLOW));
        }
    }

    /**
     * 检查玩家是否启用Clan聊天模式
     */
    public boolean isClanChatEnabled(UUID playerId) {
        return clanManager.isClanChatEnabled(playerId);
    }

    /**
     * 发送Clan聊天消息
     */
    public void sendClanChatMessage(Player player, String message) {
        Clan clan = clanManager.getPlayerClan(player);
        if (clan == null || !isClanChatEnabled(player.getUniqueId())) {
            return;
        }

        String clanTag = clan.getColoredTag();
        String rankDisplay = clan.getRank(player.getUniqueId()).getDisplayName();

        Component chatMsg = Component.text()
            .append(Component.text("[Clan] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(clanTag + " ", NamedTextColor.YELLOW))
            .append(Component.text(player.getName(), NamedTextColor.WHITE))
            .append(Component.text(" [" + rankDisplay + "]: ", NamedTextColor.GRAY))
            .append(Component.text(message))
            .build();

        for (UUID memberId : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(chatMsg);
            }
        }
    }

    // ==================== 经济命令 ====================

    private void handleBank(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== Clan银行 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("余额: " + String.format("%.2f", clan.getBalance()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("存款: /clan deposit <金额>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("取款: /clan withdraw <金额>", NamedTextColor.GRAY));
    }

    private boolean handleDeposit(Player player, String amountStr) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效金额: " + amountStr, NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("金额必须大于0", NamedTextColor.RED));
            return true;
        }

        // 获取玩家经济服务
        var plugin = Bukkit.getPluginManager().getPlugin("StarCore");
        if (plugin != null && plugin instanceof dev.starcore.starcore.StarCorePlugin starCore) {
            var context = starCore.context();
            if (context != null && context.economyService() != null) {
                var ecoService = context.economyService();
                BigDecimal balance = ecoService.getBalance(player.getUniqueId());
                BigDecimal withdrawAmount = BigDecimal.valueOf(amount);

                if (balance.compareTo(withdrawAmount) < 0) {
                    player.sendMessage(Component.text("存款失败（余额不足）", NamedTextColor.RED));
                    player.sendMessage(Component.text("你的余额: " + String.format("%.2f", balance), NamedTextColor.GRAY));
                    return true;
                }

                if (ecoService.withdraw(player.getUniqueId(), withdrawAmount)) {
                    clanManager.deposit(player.getUniqueId(), amount);
                    player.sendMessage(Component.text("已存入 " + String.format("%.2f", amount) + " 到Clan银行", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Clan余额: " + String.format("%.2f", clan.getBalance()), NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("存款失败", NamedTextColor.RED));
                }
            } else {
                // 简化模式：直接存款到Clan
                clanManager.deposit(player.getUniqueId(), amount);
                player.sendMessage(Component.text("已存入 " + String.format("%.2f", amount) + " 到Clan银行", NamedTextColor.GREEN));
            }
        } else {
            // 简化模式
            clanManager.deposit(player.getUniqueId(), amount);
            player.sendMessage(Component.text("已存入 " + String.format("%.2f", amount) + " 到Clan银行", NamedTextColor.GREEN));
        }

        return true;
    }

    private boolean handleWithdraw(Player player, String amountStr) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.hasPermission(player.getUniqueId(), Clan.ClanPermission.WITHDRAW)) {
            player.sendMessage(Component.text("你没有取款权限", NamedTextColor.RED));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效金额: " + amountStr, NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("金额必须大于0", NamedTextColor.RED));
            return true;
        }

        if (clanManager.withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(Component.text("已取出 " + String.format("%.2f", amount), NamedTextColor.GREEN));
            player.sendMessage(Component.text("Clan余额: " + String.format("%.2f", clan.getBalance()), NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("取款失败（余额不足）", NamedTextColor.RED));
        }

        return true;
    }

    // ==================== 权限管理 ====================

    private boolean handleRank(Player player, Player target, String rankStr) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以设置成员职位", NamedTextColor.RED));
            return true;
        }

        Clan.ClanRank rank;
        switch (rankStr.toLowerCase()) {
            case "officer", "o", "官员" -> rank = Clan.ClanRank.OFFICER;
            case "member", "m", "成员" -> rank = Clan.ClanRank.MEMBER;
            default -> {
                player.sendMessage(Component.text("无效职位: " + rankStr, NamedTextColor.RED));
                player.sendMessage(Component.text("可用职位: officer, member", NamedTextColor.GRAY));
                return true;
            }
        }

        if (target.getUniqueId().equals(clan.getLeader())) {
            player.sendMessage(Component.text("不能修改族长的职位", NamedTextColor.RED));
            return true;
        }

        clanManager.setRank(player.getUniqueId(), target.getUniqueId(), rank);
        player.sendMessage(Component.text("已设置 " + target.getName() + " 为 " + rank.getDisplayName(), NamedTextColor.GREEN));
        target.sendMessage(Component.text("你的Clan职位已更新为: " + rank.getDisplayName(), NamedTextColor.YELLOW));

        return true;
    }

    private void handlePerm(Player player) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== 你的Clan权限 ===", NamedTextColor.GOLD));
        Clan.ClanRank rank = clan.getRank(player.getUniqueId());
        player.sendMessage(Component.text("职位: " + rank.getDisplayName(), NamedTextColor.YELLOW));

        StringBuilder perms = new StringBuilder();
        for (Clan.ClanPermission perm : rank.getPermissions()) {
            if (perms.length() > 0) perms.append(", ");
            perms.append(perm.name().toLowerCase());
        }
        player.sendMessage(Component.text("权限: " + perms, NamedTextColor.GRAY));
    }

    private boolean handlePerm(Player player, Player target) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以查看他人权限", NamedTextColor.RED));
            return true;
        }

        if (!clan.isMember(target.getUniqueId())) {
            player.sendMessage(Component.text("目标玩家不是Clan成员", NamedTextColor.RED));
            return true;
        }

        Clan.ClanRank rank = clan.getRank(target.getUniqueId());
        player.sendMessage(Component.text(target.getName() + " 的职位: " + rank.getDisplayName(), NamedTextColor.YELLOW));

        return true;
    }

    // ==================== 关系命令 ====================

    private void handleAlly(Player player, String targetTag) {
        Clan myClan = clanManager.getPlayerClan(player);

        if (myClan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!myClan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以建立联盟", NamedTextColor.RED));
            return;
        }

        Clan targetClan = clanManager.getClanByTag(targetTag);
        if (targetClan == null) {
            player.sendMessage(Component.text("找不到Clan: " + targetTag, NamedTextColor.RED));
            return;
        }

        if (targetClan.getId().equals(myClan.getId())) {
            player.sendMessage(Component.text("不能与自己的Clan建立联盟", NamedTextColor.RED));
            return;
        }

        if (clanManager.createAlliance(player.getUniqueId(), myClan.getId(), targetClan.getId())) {
            player.sendMessage(Component.text("已与 " + targetClan.getDisplayName() + " 建立联盟", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("建立联盟失败", NamedTextColor.RED));
        }
    }

    private boolean handleAllyRemove(Player player, String targetTag) {
        Clan myClan = clanManager.getPlayerClan(player);

        if (myClan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (!myClan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以解除联盟", NamedTextColor.RED));
            return true;
        }

        Clan targetClan = clanManager.getClanByTag(targetTag);
        if (targetClan == null) {
            player.sendMessage(Component.text("找不到Clan: " + targetTag, NamedTextColor.RED));
            return true;
        }

        if (clanManager.removeAlly(player.getUniqueId(), targetTag)) {
            player.sendMessage(Component.text("已解除与 " + targetClan.getDisplayName() + " 的联盟", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("解除联盟失败", NamedTextColor.RED));
        }

        return true;
    }

    private void handleRival(Player player, String targetTag) {
        Clan myClan = clanManager.getPlayerClan(player);

        if (myClan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return;
        }

        if (!myClan.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有族长可以宣布敌对", NamedTextColor.RED));
            return;
        }

        Clan targetClan = clanManager.getClanByTag(targetTag);
        if (targetClan == null) {
            player.sendMessage(Component.text("找不到Clan: " + targetTag, NamedTextColor.RED));
            return;
        }

        clanManager.declareRivalry(myClan.getId(), targetClan.getId());
        player.sendMessage(Component.text("已宣布与 " + targetClan.getDisplayName() + " 敌对", NamedTextColor.RED));
    }

    // ==================== 标签命令 ====================

    private boolean handleTag(Player player, String newTag) {
        Clan clan = clanManager.getPlayerClan(player);

        if (clan == null) {
            player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
            return true;
        }

        if (clanManager.setTag(player.getUniqueId(), newTag)) {
            player.sendMessage(Component.text("Clan标签已修改为: " + clan.getColoredTag(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("修改标签失败（格式错误或已被使用）", NamedTextColor.RED));
        }

        return true;
    }

    // ==================== 信息命令 ====================

    private boolean handleInfo(Player player) {
        return handleInfo(player, null);
    }

    private boolean handleInfo(Player player, String tag) {
        Clan clan;

        if (tag == null) {
            clan = clanManager.getPlayerClan(player);
            if (clan == null) {
                player.sendMessage(Component.text("你不在任何Clan中", NamedTextColor.RED));
                player.sendMessage(Component.text("使用 /clan info <标签> 查看其他Clan", NamedTextColor.GRAY));
                return true;
            }
        } else {
            clan = clanManager.getClanByTag(tag);
            if (clan == null) {
                player.sendMessage(Component.text("找不到Clan: " + tag, NamedTextColor.RED));
                return true;
            }
        }

        player.sendMessage(Component.text("==== Clan信息 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("标签: " + clan.getColoredTag(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("名称: " + clan.getName(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("族长: " + Bukkit.getOfflinePlayer(clan.getLeader()).getName(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("成员: " + clan.getMemberCount() + "/" + clan.getMaxMembers(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("银行: " + String.format("%.2f", clan.getBalance()), NamedTextColor.GOLD));
        player.sendMessage(Component.text("击杀: " + clan.getKills(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("死亡: " + clan.getDeaths(), NamedTextColor.RED));
        player.sendMessage(Component.text("KDR: " + String.format("%.2f", clan.getKDR()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("盟友: " + clan.getAllies().size(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("敌对: " + clan.getRivals().size(), NamedTextColor.RED));
        player.sendMessage(Component.text("据点: " + (clan.hasHome() ? "已设置" : "未设置"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("存在天数: " + clan.getAgeDays(), NamedTextColor.GRAY));

        return true;
    }

    private void handleList(Player player) {
        Collection<Clan> clans = clanManager.getAllClans();

        if (clans.isEmpty()) {
            player.sendMessage(Component.text("当前没有任何Clan", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("==== Clan列表 ====", NamedTextColor.GOLD));
        for (Clan clan : clans) {
            Component msg = Component.text()
                .append(Component.text(clan.getColoredTag() + " ", NamedTextColor.AQUA))
                .append(Component.text(clan.getName() + " ", NamedTextColor.WHITE))
                .append(Component.text("- " + clan.getMemberCount() + "人", NamedTextColor.GRAY))
                .append(Component.text(" KDR:" + String.format("%.2f", clan.getKDR()), NamedTextColor.YELLOW))
                .hoverEvent(HoverEvent.showText(Component.text("点击查看详情")))
                .clickEvent(ClickEvent.suggestCommand("/clan info " + clan.getTag()))
                .build();
            player.sendMessage(msg);
        }
        player.sendMessage(Component.text("总计: " + clans.size() + " 个Clan", NamedTextColor.GRAY));
    }

    private void handleTop(Player player) {
        player.sendMessage(Component.text("==== Clan排行榜 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("按KDR排名:", NamedTextColor.YELLOW));

        List<Clan> topKDR = clanManager.getTopClansByKDR(5);
        for (int i = 0; i < topKDR.size(); i++) {
            Clan clan = topKDR.get(i);
            NamedTextColor color = i == 0 ? NamedTextColor.GOLD : (i == 1 ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY);
            player.sendMessage(Component.text((i + 1) + ". " + clan.getColoredTag() + " " + clan.getName() + " - KDR:" + String.format("%.2f", clan.getKDR()), color));
        }

        player.sendMessage(Component.text("按成员数排名:", NamedTextColor.YELLOW));
        List<Clan> topMembers = clanManager.getTopClansByMembers(5);
        for (int i = 0; i < topMembers.size(); i++) {
            Clan clan = topMembers.get(i);
            NamedTextColor color = i == 0 ? NamedTextColor.GOLD : (i == 1 ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY);
            player.sendMessage(Component.text((i + 1) + ". " + clan.getColoredTag() + " " + clan.getName() + " - " + clan.getMemberCount() + "人", color));
        }
    }

    private void handleStats(Player player) {
        var stats = clanManager.getStats();

        player.sendMessage(Component.text("==== Clan系统统计 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总Clan数: " + stats.totalClans(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("总成员数: " + stats.totalMembers(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("活跃Clan: " + stats.activeClans(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("总击杀: " + stats.totalKills(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("总死亡: " + stats.totalDeaths(), NamedTextColor.RED));
    }

    // ==================== 帮助 ====================

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Clan命令帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/clan create <标签> <名称> - 创建Clan", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan invite <玩家> - 邀请加入", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan accept <标签> - 接受邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan deny <标签> - 拒绝邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan requests - 查看邀请列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan kick <玩家> - 踢出成员", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan leave - 离开Clan", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan transfer <玩家> - 转让族长", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan home - 传送至据点", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan sethome - 设置据点", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan settings - 查看/修改设置", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan chat - 切换Clan聊天", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan bank - 查看银行", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan deposit <金额> - 存款", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan withdraw <金额> - 取款", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan rank <玩家> <职位> - 设置职位", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan perm - 查看你的权限", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan ally <标签> - 建立联盟", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan ally remove <标签> - 解除联盟", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan rival <标签> - 宣布敌对", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan tag <新标签> - 修改标签", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan info [标签] - Clan信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan list - Clan列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan top - 排行榜", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/clan disband - 解散Clan", NamedTextColor.GRAY));
    }

    // ==================== Tab补全 ====================

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of(
                "create", "disband", "invite", "accept", "deny", "requests",
                "kick", "leave", "transfer", "home", "sethome", "settings",
                "chat", "bank", "deposit", "withdraw", "rank", "perm",
                "ally", "rival", "tag", "info", "list", "top", "stats"
            );
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "invite", "kick", "transfer" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "info", "rival", "accept", "deny" -> {
                    return clanManager.getAllClans().stream()
                        .map(Clan::getTag)
                        .filter(tag -> tag.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "rank" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "perm" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "settings" -> {
                    List<String> opts = List.of("ff", "friendlyfire", "pvp");
                    return opts.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "ally" -> {
                    List<String> opts = List.of("add", "remove");
                    return opts.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "rank" -> {
                    List<String> ranks = List.of("officer", "member");
                    return ranks.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "settings" -> {
                    List<String> opts = List.of("on", "off");
                    return opts.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "ally" -> {
                    return clanManager.getAllClans().stream()
                        .map(Clan::getTag)
                        .filter(tag -> tag.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}
