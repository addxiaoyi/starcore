package dev.starcore.starcore.module.diplomacy.military.command;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactInviteInfo;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
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
 * 军事联盟外交系统命令
 * 命令：/militaryalliance <子命令> 或 /ma <子命令>
 */
public class MilitaryAllianceCommand implements CommandExecutor, TabCompleter {

    private final MilitaryAllianceService allianceService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    public MilitaryAllianceCommand(
            MilitaryAllianceService allianceService,
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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "invite" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /ma invite <国家名称> <条约类型>");
                    player.sendMessage("§7条约类型: observer, defensive, full, integrated");
                    return true;
                }
                return handleInvite(player, args[1], args[2]);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma accept <国家名称>");
                    return true;
                }
                return handleAccept(player, args[1]);
            }
            case "reject" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma reject <国家名称>");
                    return true;
                }
                return handleReject(player, args[1]);
            }
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma cancel <国家名称>");
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
                    player.sendMessage("§c用法: /ma break <国家名称>");
                    return true;
                }
                return handleBreak(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma info <国家名称>");
                    return true;
                }
                return handleInfo(player, args[1]);
            }
            case "upgrade" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /ma upgrade <国家名称> <新条约类型>");
                    player.sendMessage("§7条约类型: observer, defensive, full, integrated");
                    return true;
                }
                return handleUpgrade(player, args[1], args[2]);
            }
            case "stats" -> {
                return handleStats(player);
            }
            case "cooldown" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma cooldown <国家名称>");
                    return true;
                }
                return handleCooldown(player, args[1]);
            }
            case "defense" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ma defense <攻击方> <防守方>");
                    return true;
                }
                return handleDefense(player, args[1]);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                sendHelp(player);
                return true;
            }
        }
    }

    // ==================== 命令处理方法 ====================

    private boolean handleInvite(Player player, String targetNationName, String pactTypeStr) {
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

        // 解析条约类型
        PactType pactType = parsePactType(pactTypeStr);
        if (pactType == null || pactType == PactType.NONE) {
            player.sendMessage("§c无效的条约类型: " + pactTypeStr);
            player.sendMessage("§7可用类型: observer, defensive, full, integrated");
            return true;
        }

        var result = allianceService.sendPactInvite(myNationId, targetNationId, pactType);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            broadcastToNation(targetNationId, String.format(
                "§6%s §e向你们发送了 %s §e军事联盟邀请！使用 §a/ma accept %s §e接受",
                myNation.name(),
                pactType.displayName(),
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

        var result = allianceService.acceptPactInvite(myNationId, targetNationName);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
            targetNationOpt.ifPresent(targetNation ->
                broadcastToNation(targetNation.id(), String.format(
                    "§a%s 接受了你们的军事联盟邀请！",
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

        allianceService.rejectPactInvite(myNationId, targetNationName);
        player.sendMessage(String.format("§7已拒绝来自 %s 的军事联盟邀请", targetNationName));

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        targetNationOpt.ifPresent(targetNation ->
            broadcastToNation(targetNation.id(), String.format(
                "§7%s 拒绝了你们的军事联盟邀请",
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

        allianceService.cancelPactInvite(myNationId, targetNationName);
        player.sendMessage(String.format("§7已取消对 %s 的军事联盟邀请", targetNationName));

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

        player.sendMessage("§6§l==== " + myNation.name() + " 军事联盟列表 ====");

        // 显示防御同盟及以上
        Collection<NationId> allies = allianceService.getMilitaryAllies(myNationId, PactType.DEFENSIVE);
        if (allies.isEmpty()) {
            player.sendMessage("§7暂无军事联盟国家");
        } else {
            player.sendMessage("§a军事盟友 (" + allies.size() + "):");
            for (NationId allyId : allies) {
                Optional<Nation> allyOpt = nationService.nationById(allyId);
                String allyName = allyOpt.map(Nation::name).orElse("未知");

                Optional<MilitaryAllianceService.MilitaryPactInfo> infoOpt = allianceService.getPactInfo(myNationId, allyId);
                String pactType = infoOpt.map(info -> info.pactType().displayName()).orElse("");
                String duration = infoOpt.map(info -> info.durationDays() + " 天").orElse("");

                player.sendMessage("  §a- " + allyName + " §7[" + pactType + "] §f(" + duration + ")");
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

        List<PactInviteInfo> pending = allianceService.getPendingInvites(myNationId);
        if (pending.isEmpty()) {
            player.sendMessage("§7暂无待处理的军事联盟邀请");
        } else {
            player.sendMessage("§e待处理邀请 (" + pending.size() + "):");
            for (PactInviteInfo invite : pending) {
                long hours = invite.remainingMs() / (60 * 60 * 1000);
                player.sendMessage("  §6- " + invite.inviterName() + " §7["
                    + invite.pactType().displayName() + "] §f(剩余 " + hours + " 小时)");
                player.sendMessage("    §7使用 /ma accept " + invite.inviterName() + " 接受");
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

        Optional<MilitaryAllianceService.MilitaryPactInfo> infoOpt = allianceService.getPactInfo(myNationId, targetNationId);
        if (infoOpt.isEmpty()) {
            player.sendMessage("§c你们没有军事联盟关系");
            return true;
        }

        boolean success = allianceService.breakPact(myNationId, targetNationId, myNationId);
        if (success) {
            player.sendMessage(String.format("§c已解除与 %s 的军事联盟关系", targetNationName));
            broadcastToNation(targetNationId, String.format(
                "§c%s 解除与你们的军事联盟关系",
                myNation.name()
            ));
        } else {
            player.sendMessage("§c解除军事联盟失败");
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

        player.sendMessage("§6§l==== 军事联盟信息 ====");
        player.sendMessage("§7你的国家: §f" + myNation.name());
        player.sendMessage("§7对方国家: §f" + targetNation.name());
        // 分隔

        Optional<MilitaryAllianceService.MilitaryPactInfo> infoOpt = allianceService.getPactInfo(myNationId, targetNationId);
        if (infoOpt.isPresent()) {
            MilitaryAllianceService.MilitaryPactInfo info = infoOpt.get();
            player.sendMessage("§a军事联盟状态: §f是");
            player.sendMessage("§7条约类型: §f" + info.pactType().displayName());
            player.sendMessage("§7防御加成: §f" + String.format("%.1f%%", info.pactType().defenseBonus() * 100));
            player.sendMessage("§7联盟成立时间: §f" + info.formedAt());
            player.sendMessage("§7持续时间: §f" + info.durationDays() + " 天");
        } else {
            player.sendMessage("§7军事联盟状态: §c否");
        }

        if (allianceService.hasPendingInvite(targetNationId)) {
            player.sendMessage("§e对方有待处理的邀请（可能是你之前的邀请）");
        }

        if (allianceService.hasMilitaryAlliance(myNationId, targetNationId, PactType.OBSERVER)) {
            double bonus = allianceService.getDefenseBonus(myNationId, targetNationId);
            if (bonus > 0) {
                player.sendMessage("§a当前防御加成: §f" + String.format("%.1f%%", bonus * 100));
            }
        }

        return true;
    }

    private boolean handleUpgrade(Player player, String targetNationName, String newTypeStr) {
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

        PactType newType = parsePactType(newTypeStr);
        if (newType == null || newType == PactType.NONE) {
            player.sendMessage("§c无效的条约类型: " + newTypeStr);
            player.sendMessage("§7可用类型: observer, defensive, full, integrated");
            return true;
        }

        var result = allianceService.upgradePact(myNationId, targetNationId, newType);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            broadcastToNation(targetNationId, String.format(
                "§6%s §e升级了你们的军事联盟条约为 %s！",
                myNation.name(),
                newType.displayName()
            ));
        }

        return true;
    }

    private boolean handleStats(Player player) {
        MilitaryAllianceService.MilitaryAllianceStats stats = allianceService.getStats();

        player.sendMessage("§6§l==== 军事联盟统计 ====");
        player.sendMessage("§7总军事联盟数: §a" + stats.totalPacts());
        player.sendMessage("§7待处理邀请: §e" + stats.totalInvitesPending());
        player.sendMessage("§7最大盟国数量: §a" + stats.strongestAlliance());
        player.sendMessage("§7最活跃国家: §b" + stats.mostAlliedNation());

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

        // 检查冷却时间
        long remaining = allianceService.getRemainingCooldown(myNationId, targetNationId);
        if (remaining > 0) {
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

    private boolean handleDefense(Player player, String attackerName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> attackerOpt = nationService.nationByName(attackerName);
        if (attackerOpt.isEmpty()) {
            player.sendMessage("§c找不到国家: " + attackerName);
            return true;
        }
        Nation attacker = attackerOpt.get();
        NationId attackerId = attacker.id();

        player.sendMessage("§6§l==== 防御状态检查 ====");
        player.sendMessage("§7你的国家: §f" + myNation.name());
        player.sendMessage("§7潜在攻击方: §f" + attacker.name());

        // 检查防御加成
        double defenseBonus = allianceService.getDefenseBonus(myNationId, attackerId);
        if (defenseBonus > 0) {
            player.sendMessage("§a防御加成: §f" + String.format("%.1f%%", defenseBonus * 100));
            player.sendMessage("§a受军事联盟保护状态: §f是");
        } else {
            player.sendMessage("§c防御加成: §f0%");
            player.sendMessage("§c受军事联盟保护状态: §f否");
        }

        return true;
    }

    private boolean handleHelp(Player player) {
        sendHelp(player);
        return true;
    }

    // ==================== 辅助方法 ====================

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== 军事联盟系统帮助 ====");
        player.sendMessage("§e/ma invite <国家> <类型> §7- 发送军事联盟邀请");
        player.sendMessage("§7  类型: observer, defensive, full, integrated");
        player.sendMessage("§e/ma accept <国家> §7- 接受军事联盟邀请");
        player.sendMessage("§e/ma reject <国家> §7- 拒绝军事联盟邀请");
        player.sendMessage("§e/ma cancel <国家> §7- 取消发出的邀请");
        player.sendMessage("§e/ma list §7- 查看军事联盟列表");
        player.sendMessage("§e/ma pending §7- 查看待处理邀请");
        player.sendMessage("§e/ma break <国家> §7- 解除军事联盟关系");
        player.sendMessage("§e/ma info <国家> §7- 查看与某国的军事联盟状态");
        player.sendMessage("§e/ma upgrade <国家> <类型> §7- 升级军事联盟条约");
        player.sendMessage("§e/ma stats §7- 查看军事联盟统计");
        player.sendMessage("§e/ma defense <攻击方> §7- 检查防御状态");
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

    private PactType parsePactType(String str) {
        return switch (str.toLowerCase()) {
            case "observer", "obs" -> PactType.OBSERVER;
            case "defensive", "def" -> PactType.DEFENSIVE;
            case "full", "full_alliance" -> PactType.FULL_ALLIANCE;
            case "integrated", "int" -> PactType.INTEGRATED;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("invite", "accept", "reject", "cancel", "list", "pending",
                    "break", "info", "upgrade", "stats", "defense")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return nationService.nations().stream()
                .map(Nation::name)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("upgrade")) {
                return Arrays.asList("observer", "defensive", "full", "integrated")
                    .stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}