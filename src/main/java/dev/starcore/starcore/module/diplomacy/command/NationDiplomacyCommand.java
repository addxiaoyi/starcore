package dev.starcore.starcore.module.diplomacy.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.diplomacy.DiplomacyModule;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.relation.NationRelationManager;
import dev.starcore.starcore.nation.relation.NationRelationManager.RelationType;
import dev.starcore.starcore.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nation外交关系命令
 * 命令：/diplomacy <子命令>
 *
 * 中文别名:
 *   ally/联盟 → 发送联盟邀请
 *   allyinvite/邀请 → 发送联盟邀请
 *   allyaccept/接受 → 接受联盟邀请
 *   allyreject/拒绝 → 拒绝联盟邀请
 *   allylist/待处理 → 查看待处理邀请
 *   enemy/敌对 → 宣布敌对
 *   neutral/中立 → 恢复中立
 *   list/列表 → 查看关系列表
 *   check/检查 → 检查关系
 *   stats/统计 → 查看统计
 *   cooldown/冷却 → 查看冷却
 */
public class NationDiplomacyCommand implements CommandExecutor, TabCompleter {

    private final NationRelationManager relationManager;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final DiplomacyModule diplomacyModule;

    public NationDiplomacyCommand(
            NationRelationManager relationManager,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory,
            DiplomacyModule diplomacyModule
    ) {
        this.relationManager = relationManager;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.diplomacyModule = diplomacyModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            return handleList(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "ally" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy ally <Nation名称>");
                    return true;
                }
                return handleAlly(player, args[1]);
            }
            case "allyinvite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy allyinvite <Nation名称>");
                    return true;
                }
                return handleAllyInvite(player, args[1]);
            }
            case "allyaccept" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy allyaccept <Nation名称>");
                    return true;
                }
                return handleAllyAccept(player, args[1]);
            }
            case "allyreject" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy allyreject <Nation名称>");
                    return true;
                }
                return handleAllyReject(player, args[1]);
            }
            case "allylist" -> {
                return handleAllyList(player);
            }
            case "enemy" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy enemy <Nation名称>");
                    return true;
                }
                return handleEnemy(player, args[1]);
            }
            case "neutral" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy neutral <Nation名称>");
                    return true;
                }
                return handleNeutral(player, args[1]);
            }
            case "list" -> {
                return handleList(player);
            }
            case "check" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /diplomacy check <Nation名称>");
                    return true;
                }
                return handleCheck(player, args[1]);
            }
            case "stats" -> {
                return handleStats(player);
            }
            case "cooldown" -> {
                if (args.length < 2) {
                    return handleCooldown(player, null);
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

    private boolean handleAllyInvite(Player player, String targetNationName) {
        // 获取玩家的Nation ID
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 审计 A-096: 使用权限系统检查 ALLY_ADD 权限
        if (!PermissionUtil.hasNationPermission(player, myNationId.value(), NationPermission.ALLY_ADD)) {
            player.sendMessage("§c你没有添加盟友的权限");
            return true;
        }

        // 获取目标Nation ID
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (myNationId.equals(targetNationId)) {
            player.sendMessage("§c不能邀请自己的Nation");
            return true;
        }

        // 发送联盟邀请
        var result = diplomacyModule.sendAllianceInvite(myNationId, targetNationId);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            // 通知目标Nation
            broadcastToNation(targetNationId, String.format(
                "§6%s §e向你们发送了联盟邀请！使用 §a/diplomacy allyaccept %s §e接受",
                myNation.name(),
                myNation.name()
            ));
        }

        return true;
    }

    private boolean handleAllyAccept(Player player, String targetNationName) {
        // 获取玩家的Nation ID
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 获取目标Nation ID
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        // 接受联盟邀请
        var result = diplomacyModule.acceptAllianceInvite(myNationId, targetNationId);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            // 通知目标Nation
            broadcastToNation(targetNationId, String.format(
                "§a%s 接受了你们的联盟邀请！",
                myNation.name()
            ));
        }

        return true;
    }

    private boolean handleAllyReject(Player player, String targetNationName) {
        // 获取玩家的Nation ID
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 获取目标Nation ID
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        // 拒绝联盟邀请
        diplomacyModule.rejectAllianceInvite(myNationId, targetNationId);
        player.sendMessage(String.format("§7已拒绝来自 %s 的联盟邀请", targetNation.name()));

        // 通知目标Nation
        broadcastToNation(targetNationId, String.format(
            "§7%s 拒绝了你们的联盟邀请",
            myNation.name()
        ));

        return true;
    }

    private boolean handleAllyList(Player player) {
        // 获取玩家的Nation ID
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        player.sendMessage("§6§l==== " + myNation.name() + " 联盟邀请 ====");

        // 获取待处理的邀请
        List<NationId> pendingInvites = diplomacyModule.getPendingInvites(myNationId);

        if (pendingInvites.isEmpty()) {
            player.sendMessage("§7暂无待处理的联盟邀请");
        } else {
            player.sendMessage("§e待处理邀请 (" + pendingInvites.size() + "):");
            for (NationId inviterId : pendingInvites) {
                Optional<Nation> inviterOpt = nationService.nationById(inviterId);
                String inviterName = inviterOpt.map(Nation::name).orElse("未知");
                player.sendMessage("  §6- " + inviterName + " §7(使用 /diplomacy allyaccept " + inviterName + " 接受)");            }
        }

        return true;
    }

    private boolean handleAlly(Player player, String targetNationName) {
        // 获取玩家的Nation ID
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 获取目标Nation ID
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (myNationId.equals(targetNationId)) {
            player.sendMessage("§c不能与自己的Nation建立联盟");
            return true;
        }

        // 尝试直接建立联盟（简化流程，仅当双方都是 NPC 联盟时可用）
        // 正常流程需要使用 allyinvite -> allyaccept
        var result = diplomacyModule.sendAllianceInvite(myNationId, targetNationId);
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        if (result.success()) {
            // 通知目标Nation
            broadcastToNation(targetNationId, String.format(
                "§6%s §e向你们发送了联盟邀请！使用 §a/diplomacy allyaccept %s §e接受",
                myNation.name(),
                myNation.name()
            ));
        }

        return true;
    }

    private boolean handleEnemy(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 审计 A-097: 使用权限系统检查 ENEMY_ADD 权限
        if (!PermissionUtil.hasNationPermission(player, myNationId.value(), NationPermission.ENEMY_ADD)) {
            player.sendMessage("§c你没有宣布敌对的权限");
            return true;
        }

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        if (myNationId.equals(targetNationId)) {
            player.sendMessage("§c不能与自己的Nation为敌");
            return true;
        }

        // 检查冷却时间
        if (diplomacyModule != null && diplomacyModule.isInCooldown(myNationId, targetNationId)) {
            long remaining = diplomacyModule.getRemainingCooldownMs(myNationId, targetNationId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            player.sendMessage("§c外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
            return true;
        }

        // 尝试宣战（会检查费用）
        try {
            diplomacyModule.declareWar(myNationId, targetNationId);
            player.sendMessage(String.format("§c已向 %s 宣战！宣战费用: %s",
                targetNationName, diplomacyModule.getWarDeclarationCost()));

            // 通知目标Nation
            broadcastToNation(targetNationId, String.format(
                "§c%s 宣布与你们开战！",
                myNation.name()
            ));
        } catch (IllegalStateException e) {
            player.sendMessage("§c宣战失败: " + e.getMessage());
        }

        return true;
    }

    private boolean handleNeutral(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 审计 A-098: 使用权限系统检查 ENEMY_REMOVE 权限
        if (!PermissionUtil.hasNationPermission(player, myNationId.value(), NationPermission.ENEMY_REMOVE)) {
            player.sendMessage("§c你没有解除敌对的权限");
            return true;
        }

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        // 移除联盟和敌对
        relationManager.removeAlliance(myNationId.value(), targetNationId.value());
        relationManager.removeEnemy(myNationId.value(), targetNationId.value());

        player.sendMessage(String.format("§7已与 %s 恢复中立关系", targetNationName));

        return true;
    }

    private boolean handleList(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        UUID myNationId = myNation.id().value();

        var relations = relationManager.getRelations(myNationId);

        player.sendMessage("§6§l==== " + myNation.name() + " 外交关系 ====");
        // 分隔

        // 盟友
        player.sendMessage("§a§l盟友 (" + relations.getAllyCount() + "):");
        if (relations.getAllies().isEmpty()) {
            player.sendMessage("  §7无");
        } else {
            for (UUID allyId : relations.getAllies()) {
                Optional<Nation> allyNationOpt = nationService.nationById(NationId.of(allyId));
                String allyName = allyNationOpt.map(Nation::name).orElse("未知");
                player.sendMessage("  §a- " + allyName);
            }
        }

        // 分隔

        // 敌对
        player.sendMessage("§c§l敌对 (" + relations.getEnemyCount() + "):");
        if (relations.getEnemies().isEmpty()) {
            player.sendMessage("  §7无");
        } else {
            for (UUID enemyId : relations.getEnemies()) {
                Optional<Nation> enemyNationOpt = nationService.nationById(NationId.of(enemyId));
                String enemyName = enemyNationOpt.map(Nation::name).orElse("未知");
                player.sendMessage("  §c- " + enemyName);
            }
        }

        return true;
    }

    private boolean handleCheck(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c找不到Nation: " + targetNationName);
            return true;
        }
        Nation targetNation = targetNationOpt.get();
        NationId targetNationId = targetNation.id();

        RelationType type = relationManager.getRelationType(myNationId.value(), targetNationId.value());

        player.sendMessage("§6§l==== 关系检查 ====");
        player.sendMessage("§7你的Nation: §f" + myNation.name());
        player.sendMessage("§7对方Nation: §f" + targetNation.name());
        // 分隔

        // 检查冷却时间
        String cooldownInfo = "";
        if (diplomacyModule != null && diplomacyModule.isInCooldown(myNationId, targetNationId)) {
            long remaining = diplomacyModule.getRemainingCooldownMs(myNationId, targetNationId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            cooldownInfo = " §c(冷却中: " + hours + "小时" + minutes + "分钟)";
        }

        player.sendMessage("§7关系类型: " + type.getDisplayName() + cooldownInfo);
        // 分隔

        switch (type) {
            case ALLY -> {
                player.sendMessage("§a效果:");
                player.sendMessage("  §7- 可以进入对方Territory");
                player.sendMessage("  §7- 共享传送点");
                player.sendMessage("  §7- 可以协同作战");
            }
            case ENEMY -> {
                player.sendMessage("§c效果:");
                player.sendMessage("  §7- 可以攻击对方");
                player.sendMessage("  §7- 可以声明战争");
                player.sendMessage("  §7- Territory显示为红色");
            }
            case NEUTRAL -> {
                player.sendMessage("§7效果:");
                player.sendMessage("  §7- 正常外交关系");
                player.sendMessage("  §7- 可以建立联盟");
            }
        }

        return true;
    }

    private boolean handleStats(Player player) {
        var stats = relationManager.getStats();

        player.sendMessage("§6§l==== 外交统计 ====");
        player.sendMessage("§7总Nation数: §e" + stats.totalNations());
        player.sendMessage("§7总联盟数: §a" + stats.totalAlliances());
        player.sendMessage("§7总敌对数: §c" + stats.totalEnemies());

        return true;
    }

    private boolean handleCooldown(Player player, String targetNationName) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        player.sendMessage("§6§l==== 外交冷却 ====");

        if (targetNationName == null) {
            // 显示与所有国家的冷却状态
            player.sendMessage("§7检查冷却状态:");
            player.sendMessage("§e/diplomacy cooldown <Nation> §7- 检查与特定国家的冷却");
        } else {
            Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
            if (targetNationOpt.isEmpty()) {
                player.sendMessage("§c找不到Nation: " + targetNationName);
                return true;
            }
            Nation targetNation = targetNationOpt.get();
            NationId targetNationId = targetNation.id();

            if (diplomacyModule != null && diplomacyModule.isInCooldown(myNationId, targetNationId)) {
                long remaining = diplomacyModule.getRemainingCooldownMs(myNationId, targetNationId);
                long hours = remaining / (60 * 60 * 1000);
                long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
                long seconds = (remaining % (60 * 1000)) / 1000;
                player.sendMessage("§c与 " + targetNation.name() + " 的外交冷却中");
                player.sendMessage("§7剩余时间: §e" + hours + "小时 " + minutes + "分钟 " + seconds + "秒");
            } else {
                player.sendMessage("§a与 " + targetNation.name() + " 无冷却限制");
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§7可用命令:");
        player.sendMessage("  §e/diplomacy ally <Nation> §7- 发送联盟邀请 (联盟)");
        player.sendMessage("  §e/diplomacy allyinvite <Nation> §7- 发送联盟邀请 (邀请)");
        player.sendMessage("  §e/diplomacy allyaccept <Nation> §7- 接受联盟邀请 (接受)");
        player.sendMessage("  §e/diplomacy allyreject <Nation> §7- 拒绝联盟邀请 (拒绝)");
        player.sendMessage("  §e/diplomacy allylist §7- 查看待处理邀请 (待处理)");
        player.sendMessage("  §e/diplomacy enemy <Nation> §7- 宣布敌对 (敌对)");
        player.sendMessage("  §e/diplomacy neutral <Nation> §7- 恢复中立 (中立)");
        player.sendMessage("  §e/diplomacy list §7- 查看关系列表 (列表)");
        player.sendMessage("  §e/diplomacy check <Nation> §7- 检查关系 (检查)");
        player.sendMessage("  §e/diplomacy stats §7- 查看统计信息 (统计)");
        player.sendMessage("  §e/diplomacy cooldown [Nation] §7- 查看外交冷却 (冷却)");
    }

    // ==================== 辅助方法 ====================

    private void broadcastToNation(NationId nationId, String message) {
        // 获取 Nation
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        // 遍历所有成员，向在线成员发送消息
        for (var member : nation.members()) {
            Optional<? extends org.bukkit.entity.Player> playerOpt = onlinePlayerDirectory.findOnlinePlayer(member.playerId());
            playerOpt.ifPresent(player -> player.sendMessage(message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // 第一级补全：所有子命令（中英文）
            return Arrays.asList(
                "ally", "联盟", "allyinvite", "邀请", "allyaccept", "接受",
                "allyreject", "拒绝", "allylist", "待处理", "pending",
                "enemy", "敌对", "neutral", "中立", "list", "列表",
                "check", "检查", "stats", "统计", "cooldown", "冷却"
            ).stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = normalizeSubCommand(args[0]);
            switch (subCommand) {
                case "ally", "allyinvite", "allyaccept", "allyreject", "enemy", "neutral", "check", "cooldown" -> {
                    // 返回所有 Nation 名称（带前缀过滤）
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

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            // 联盟相关
            case "ally", "联盟", "结盟" -> "ally";
            case "allyinvite", "邀请", "邀请联盟" -> "allyinvite";
            case "allyaccept", "接受", "接受邀请" -> "allyaccept";
            case "allyreject", "拒绝", "拒绝邀请" -> "allyreject";
            case "allylist", "待处理", "pending", "邀请列表" -> "allylist";
            // 敌对
            case "enemy", "敌对", "敌" -> "enemy";
            // 中立
            case "neutral", "中立", "中" -> "neutral";
            // 列表
            case "list", "列表", "列" -> "list";
            // 检查
            case "check", "检查", "查" -> "check";
            // 统计
            case "stats", "统计", "统" -> "stats";
            // 冷却
            case "cooldown", "冷却", "cd" -> "cooldown";
            default -> input;
        };
    }
}
