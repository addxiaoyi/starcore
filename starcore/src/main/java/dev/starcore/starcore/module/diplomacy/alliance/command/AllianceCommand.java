package dev.starcore.starcore.module.diplomacy.alliance.command;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceService;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceService.AllianceInviteInfo;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 联盟外交系统命令
 * 命令：/alliance <子命令>
 *
 * 中文别名:
 *   invite/邀请 → 发送联盟邀请
 *   accept/接受 → 接受联盟邀请
 *   reject/拒绝 → 拒绝联盟邀请
 *   cancel/取消 → 取消发出的邀请
 *   list/列表 → 查看联盟列表
 *   pending/待处理 → 查看待处理邀请
 *   break/断交 → 解除联盟关系
 *   info/信息 → 查看联盟状态
 *   stats/统计 → 查看联盟统计
 *   cooldown/冷却 → 查看外交冷却
 */
public class AllianceCommand implements CommandExecutor, TabCompleter {

    private final AllianceService allianceService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    public AllianceCommand(
            AllianceService allianceService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory
    ) {
        this.allianceService = allianceService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleHelp(player);
        }

        String subCommand = normalizeSubCommand(args[0].toLowerCase());

        switch (subCommand) {
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance invite <国家名称>");
                    return true;
                }
                return handleInvite(player, args[1]);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance accept <国家名称>");
                    return true;
                }
                return handleAccept(player, args[1]);
            }
            case "reject" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance reject <国家名称>");
                    return true;
                }
                return handleReject(player, args[1]);
            }
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance cancel <国家名称>");
                    return true;
                }
                return handleCancel(player, args[1]);
            }
            case "list" -> {
                return handleList(player);
            }
            case "pending" -> {
                return handlePending(player);
            }
            case "break" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance break <国家名称>");
                    return true;
                }
                return handleBreak(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance info <国家名称>");
                    return true;
                }
                return handleInfo(player, args[1]);
            }
            case "stats" -> {
                return handleStats(player);
            }
            case "cooldown" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /alliance cooldown <国家名称>");
                    return true;
                }
                return handleCooldown(player, args[1]);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                sendHelp(player);
                return true;
            }
        }
    }

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            // 邀请
            case "invite", "邀请", "邀", "发送" -> "invite";
            // 接受
            case "accept", "接受", "接", "同意" -> "accept";
            // 拒绝
            case "reject", "拒绝", "否", "不同意" -> "reject";
            // 取消
            case "cancel", "取消", "取", "撤回" -> "cancel";
            // 列表
            case "list", "列表", "列", "查看", "ls" -> "list";
            // 待处理
            case "pending", "待处理", "待", "邀请列表", "invites" -> "pending";
            // 断交
            case "break", "断交", "断", "解除", "解散" -> "break";
            // 信息
            case "info", "信息", "详", "详情", "i" -> "info";
            // 统计
            case "stats", "统计", "统", "s" -> "stats";
            // 冷却
            case "cooldown", "冷却", "cd", "co" -> "cooldown";
            default -> input;
        };
    }

    // ==================== 命令处理方法 ====================

    private boolean handleInvite(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到国家: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (myNationId.equals(targetNationId)) {
            player.sendMessage("§c不能邀请自己的国家");
            return true;
        }

        var result = allianceService.sendInvite(myNationId, targetNationId);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            broadcastToNation(targetNationId, String.format(
                "§6%s §e向你们发送了联盟邀请！使用 §a/alliance accept %s §e接受",
                myNation.name(),
                myNation.name()
            ));
        }

        return true;
    }

    private boolean handleAccept(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        var result = allianceService.acceptInvite(myNationId, targetNationName);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
            targetNationOpt.ifPresent(targetNation ->
                broadcastToNation(targetNation.id(), String.format(
                    "§a%s 接受了你们的联盟邀请！",
                    myNation.name()
                ))
            );
        }

        return true;
    }

    private boolean handleReject(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        allianceService.rejectInvite(myNationId, targetNationName);
        player.sendMessage(String.format("§7已拒绝来自 %s 的联盟邀请", targetNationName));

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        targetNationOpt.ifPresent(targetNation ->
            broadcastToNation(targetNation.id(), String.format(
                "§7%s 拒绝了你们的联盟邀请",
                myNation.name()
            ))
        );

        return true;
    }

    private boolean handleCancel(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        allianceService.cancelInvite(myNationId, targetNationName);
        player.sendMessage(String.format("§7已取消对 %s 的联盟邀请", targetNationName));

        return true;
    }

    private boolean handleList(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        player.sendMessage("§6§l==== " + myNation.name() + " 联盟列表 ====");

        Collection<NationId> allies = allianceService.getAllies(myNationId);
        if (allies.isEmpty()) {
            player.sendMessage("§7暂无联盟国家");
        } else {
            player.sendMessage("§a盟友们 (" + allies.size() + "):");
            for (NationId allyId : allies) {
                Optional<Nation> allyOpt = nationService.nationById(allyId);
                String allyName = allyOpt.map(Nation::name).orElse("未知");

                Optional<AllianceService.AllianceInfo> infoOpt = allianceService.getAllianceInfo(myNationId, allyId);
                String duration = infoOpt.map(info -> info.durationDays() + " 天").orElse("");

                player.sendMessage("  §a- " + allyName + " §7(" + duration + ")");
            }
        }

        return true;
    }

    private boolean handlePending(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        player.sendMessage("§6§l==== " + myNation.name() + " 待处理邀请 ====");

        List<AllianceInviteInfo> pending = allianceService.getPendingInvites(myNationId);
        if (pending.isEmpty()) {
            player.sendMessage("§7暂无待处理的联盟邀请");
        } else {
            player.sendMessage("§e待处理邀请 (" + pending.size() + "):");
            for (AllianceInviteInfo invite : pending) {
                long hours = invite.remainingMs() / (60 * 60 * 1000);
                player.sendMessage("  §6- " + invite.inviterName() + " §7(剩余 " + hours + " 小时)");
                player.sendMessage("    §7使用 /alliance accept " + invite.inviterName() + " 接受");
            }
        }

        return true;
    }

    private boolean handleBreak(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到国家: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (!allianceService.areAllied(myNationId, targetNationId)) {
            player.sendMessage("§c你们不是联盟关系");
            return true;
        }

        boolean success = allianceService.breakAlliance(myNationId, targetNationId, myNationId);
        if (success) {
            player.sendMessage(String.format("§c已解除与 %s 的联盟关系", targetNationName));
            broadcastToNation(targetNationId, String.format(
                "§c%s 解除与你们的联盟关系",
                myNation.name()
            ));
        } else {
            player.sendMessage("§c解除联盟失败");
        }

        return true;
    }

    private boolean handleInfo(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到国家: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        player.sendMessage("§6§l==== 联盟信息 ====");
        player.sendMessage("§7你的国家: §f" + myNation.name());
        player.sendMessage("§7对方国家: §f" + targetNation.name());
        // 分隔

        boolean isAllied = allianceService.areAllied(myNationId, targetNationId);
        player.sendMessage("§7联盟状态: " + (isAllied ? "§a是盟友" : "§7否"));

        if (isAllied) {
            Optional<AllianceService.AllianceInfo> infoOpt = allianceService.getAllianceInfo(myNationId, targetNationId);
            infoOpt.ifPresent(info -> {
                player.sendMessage("§7联盟成立时间: §f" + info.formedAt());
                player.sendMessage("§7持续时间: §f" + info.durationDays() + " 天");
            });
        }

        if (allianceService.hasPendingInvite(targetNationId)) {
            player.sendMessage("§e对方有待处理的邀请（可能是你之前的邀请）");
        }

        if (allianceService.isInCooldown(myNationId, targetNationId)) {
            long remaining = allianceService.getRemainingCooldownMs(myNationId, targetNationId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            player.sendMessage("§c外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        return true;
    }

    private boolean handleStats(Player player) {
        AllianceService.AllianceStats stats = allianceService.getStats();

        player.sendMessage("§6§l==== 联盟统计 ====");
        player.sendMessage("§7总联盟数: §a" + stats.totalAlliances());
        player.sendMessage("§7待处理邀请: §e" + stats.totalInvitesPending());
        player.sendMessage("§7最大联盟规模: §a" + stats.largestAllianceSize());
        player.sendMessage("§7最活跃国家: §b" + stats.mostActiveNation());

        return true;
    }

    private boolean handleCooldown(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到国家: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (allianceService.isInCooldown(myNationId, targetNationId)) {
            long remaining = allianceService.getRemainingCooldownMs(myNationId, targetNationId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            long seconds = (remaining % (60 * 1000)) / 1000;
            player.sendMessage("§c与 " + targetNation.name() + " 的外交冷却中");
            player.sendMessage("§7剩余时间: §e" + hours + "小时 " + minutes + "分钟 " + seconds + "秒");
        } else {
            player.sendMessage("§a与 " + targetNation.name() + " 无冷却限制");
        }

        return true;
    }

    private boolean handleHelp(Player player) {
        sendHelp(player);
        return true;
    }

    // ==================== 辅助方法 ====================

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== 联盟系统帮助 ====");
        player.sendMessage("§e/alliance invite <国家> §7- 发送联盟邀请 (邀请)");
        player.sendMessage("§e/alliance accept <国家> §7- 接受联盟邀请 (接受)");
        player.sendMessage("§e/alliance reject <国家> §7- 拒绝联盟邀请 (拒绝)");
        player.sendMessage("§e/alliance cancel <国家> §7- 取消发出的邀请 (取消)");
        player.sendMessage("§e/alliance list §7- 查看联盟列表 (列表)");
        player.sendMessage("§e/alliance pending §7- 查看待处理邀请 (待处理)");
        player.sendMessage("§e/alliance break <国家> §7- 解除联盟关系 (断交)");
        player.sendMessage("§e/alliance info <国家> §7- 查看与某国的联盟状态 (信息)");
        player.sendMessage("§e/alliance stats §7- 查看联盟统计 (统计)");
        player.sendMessage("§e/alliance cooldown <国家> §7- 查看外交冷却时间 (冷却)");
    }

    private void broadcastToNation(NationId nationId, String message) {
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        for (var member : nation.members()) {
            Optional<? extends Player> playerOpt = onlinePlayerDirectory.findOnlinePlayer(member.playerId());
            playerOpt.ifPresent(player -> player.sendMessage(message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // 第一级补全：所有子命令（中英文）
            return List.of(
                "invite", "邀请", "accept", "接受", "reject", "拒绝",
                "cancel", "取消", "list", "列表", "pending", "待处理",
                "break", "断交", "info", "信息", "stats", "统计", "cooldown", "冷却"
            ).stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = normalizeSubCommand(args[0]);
            switch (subCommand) {
                case "invite", "accept", "reject", "cancel", "break", "info", "cooldown" -> {
                    // 返回所有国家名称
                    String input = args[1].toLowerCase();
                    return nationService.nations().stream()
                        .map(Nation::name)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
