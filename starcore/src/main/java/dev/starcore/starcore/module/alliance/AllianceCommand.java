package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.alliance.AllianceService.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 联盟外交系统命令处理
 *
 * 命令格式:
 * - /alliance create <联盟名称> - 创建联盟
 * - /alliance disband - 解散联盟
 * - /alliance info [联盟名称] - 查看联盟信息
 * - /alliance invite <国家> - 邀请国家加入联盟
 * - /alliance accept [联盟名称] - 接受加入邀请
 * - /alliance reject [联盟名称] - 拒绝加入邀请
 * - /alliance leave - 离开联盟
 * - /alliance kick <国家> - 将国家踢出联盟
 * - /alliance members - 查看联盟成员
 * - /alliance transferleadership <国家> - 转移领导权
 * - /alliance rename <新名称> - 重命名联盟
 * - /alliance relation <联盟> <friendly|neutral|hostile> - 设置联盟关系
 * - /alliance announcement <内容> - 发布公告
 * - /alliance list - 列出所有联盟
 * - /alliance pending - 查看待处理邀请
 */
public class AllianceCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[§b联盟§6] §f";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            sender.sendMessage(PREFIX + "§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleHelp(player);
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "disband" -> handleDisband(player);
            case "info" -> handleInfo(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "reject" -> handleReject(player, args);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "members" -> handleMembers(player, args);
            case "transferleadership", "transfer" -> handleTransferLeadership(player, args);
            case "rename" -> handleRename(player, args);
            case "relation" -> handleRelation(player, args);
            case "announcement" -> handleAnnouncement(player, args);
            case "list" -> handleList(player);
            case "pending" -> handlePending(player);
            case "help" -> handleHelp(player);
            default -> {
                player.sendMessage(PREFIX + "§c未知子命令: §e" + subCommand);
                sendHelp(player);
                yield true;
            }
        };
    }

    // ==================== 命令处理 ====================

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance create <联盟名称>");
            return true;
        }

        String allianceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }
        NationId leaderId = myNationOpt.orElseThrow().id();

        AllianceResult result = allianceService.createAlliance(allianceName, leaderId);
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleDisband(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        if (!alliance.leaderId().equals(myNationOpt.orElseThrow().id())) {
            player.sendMessage(PREFIX + "§c只有盟主可以解散联盟");
            return true;
        }

        AllianceResult result = allianceService.disbandAlliance(alliance.id());
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());

        Alliance targetAlliance = null;

        if (args.length > 1) {
            // 查看指定联盟的信息
            String allianceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            targetAlliance = allianceService.getAllianceByName(allianceName).orElse(null);
            if (targetAlliance == null) {
                player.sendMessage(PREFIX + "§c找不到联盟: " + allianceName);
                return true;
            }
        } else if (myNationOpt.isPresent()) {
            // 查看自己联盟的信息
            targetAlliance = allianceService.getNationAlliance(myNationOpt.orElseThrow().id()).orElse(null);
            if (targetAlliance == null) {
                player.sendMessage(PREFIX + "§c你的国家不在任何联盟中，使用 §e/alliance list §c查看所有联盟");
                return true;
            }
        } else {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        sendAllianceInfo(player, targetAlliance);
        return true;
    }

    private void sendAllianceInfo(Player player, Alliance alliance) {
        player.sendMessage(Component.text("§6§l═══ " + alliance.name() + " ═══")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §7盟主: §f" + getNationName(alliance.leaderId())));
        player.sendMessage(Component.text("  §7创建时间: §f" + alliance.createdAt().toString()));
        player.sendMessage(Component.text("  §7成员数: §f" + allianceService.getAllianceMembers(alliance.id()).size()));

        if (alliance.emblem() != null && !alliance.emblem().isEmpty()) {
            player.sendMessage(Component.text("  §7徽章: §f" + alliance.emblem()));
        }

        // 显示公告
        allianceService.getAnnouncement(alliance.id()).ifPresent(ann -> {
            player.sendMessage(Component.text("  §7公告: §e" + ann.content()));
        });

        // 显示友好联盟
        var friendlyAlliances = allianceService.getFriendlyAlliances(alliance.id());
        if (!friendlyAlliances.isEmpty()) {
            List<String> friendlyNames = friendlyAlliances.stream()
                .map(id -> allianceService.getAlliance(id).map(Alliance::name).orElse("未知"))
                .collect(Collectors.toList());
            player.sendMessage(Component.text("  §a友好联盟: §f" + String.join(", ", friendlyNames)));
        }

        // 显示敌对联盟
        var hostileAlliances = allianceService.getHostileAlliances(alliance.id());
        if (!hostileAlliances.isEmpty()) {
            List<String> hostileNames = hostileAlliances.stream()
                .map(id -> allianceService.getAlliance(id).map(Alliance::name).orElse("未知"))
                .collect(Collectors.toList());
            player.sendMessage(Component.text("  §c敌对联盟: §f" + String.join(", ", hostileNames)));
        }
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance invite <国家名称>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        String targetNationName = args[1];
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c找不到国家: " + targetNationName);
            return true;
        }

        InviteResult result = allianceService.inviteNation(allianceOpt.orElseThrow().id(), targetNationOpt.orElseThrow().id());
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        // 通知被邀请的国家
        if (result.success()) {
            broadcastToNation(targetNationOpt.orElseThrow().id(),
                PREFIX + "§e" + myNationOpt.orElseThrow().name() + " §a邀请你加入 §b[" + allianceOpt.orElseThrow().name() + "]");
        }

        return true;
    }

    private boolean handleAccept(Player player, String[] args) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        if (allianceService.isInAlliance(myNationOpt.orElseThrow().id())) {
            player.sendMessage(PREFIX + "§c你的国家已在联盟中，需要先离开当前联盟");
            return true;
        }

        // 获取待处理邀请
        List<AllianceInviteInfo> invites = allianceService.getPendingInvites(myNationOpt.orElseThrow().id());
        if (invites.isEmpty()) {
            player.sendMessage(PREFIX + "§c你没有待处理的联盟邀请");
            return true;
        }

        AllianceInviteInfo invite;
        if (args.length > 1) {
            // 指定联盟名称
            String allianceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            invite = invites.stream()
                .filter(i -> i.allianceName().equalsIgnoreCase(allianceName))
                .findFirst()
                .orElse(null);
            if (invite == null) {
                player.sendMessage(PREFIX + "§c没有收到来自该联盟的邀请");
                return true;
            }
        } else {
            // 只接受一个邀请
            if (invites.size() > 1) {
                player.sendMessage(PREFIX + "§e你有多个邀请，请指定联盟名称:");
                for (AllianceInviteInfo i : invites) {
                    player.sendMessage("  §e- " + i.allianceName());
                }
                player.sendMessage("§7用法: /alliance accept <联盟名称>");
                return true;
            }
            invite = invites.get(0);
        }

        AllianceResult result = allianceService.acceptInvite(myNationOpt.orElseThrow().id(), invite.allianceId());
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        // 通知联盟成员
        if (result.success()) {
            Optional<Alliance> allianceOpt = allianceService.getAlliance(invite.allianceId());
            allianceOpt.ifPresent(alliance -> {
                for (AllianceMember member : allianceService.getAllianceMembers(alliance.id())) {
                    if (!member.nationId().equals(myNationOpt.orElseThrow().id())) {
                        broadcastToNation(member.nationId(),
                            PREFIX + "§a" + myNationOpt.orElseThrow().name() + " §e加入了联盟!");
                    }
                }
            });
        }

        return true;
    }

    private boolean handleReject(Player player, String[] args) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        List<AllianceInviteInfo> invites = allianceService.getPendingInvites(myNationOpt.orElseThrow().id());
        if (invites.isEmpty()) {
            player.sendMessage(PREFIX + "§c你没有待处理的联盟邀请");
            return true;
        }

        AllianceInviteInfo invite;
        if (args.length > 1) {
            String allianceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            invite = invites.stream()
                .filter(i -> i.allianceName().equalsIgnoreCase(allianceName))
                .findFirst()
                .orElse(null);
        } else {
            invite = invites.get(0);
        }

        if (invite == null) {
            player.sendMessage(PREFIX + "§c没有收到来自该联盟的邀请");
            return true;
        }

        allianceService.rejectInvite(myNationOpt.orElseThrow().id(), invite.allianceId());
        player.sendMessage(PREFIX + "§7已拒绝来自 §e" + invite.allianceName() + " §7的邀请");

        return true;
    }

    private boolean handleLeave(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        AllianceResult result = allianceService.leaveAlliance(myNationOpt.orElseThrow().id());
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance kick <国家名称>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        if (!alliance.leaderId().equals(myNationOpt.orElseThrow().id())) {
            player.sendMessage(PREFIX + "§c只有盟主可以将成员踢出联盟");
            return true;
        }

        String targetNationName = args[1];
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c找不到国家: " + targetNationName);
            return true;
        }

        AllianceResult result = allianceService.removeMember(
            alliance.id(),
            targetNationOpt.orElseThrow().id(),
            myNationOpt.orElseThrow().id()
        );
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleMembers(Player player, String[] args) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        UUID allianceId;
        if (args.length > 1) {
            String allianceName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Alliance alliance = allianceService.getAllianceByName(allianceName).orElse(null);
            if (alliance == null) {
                player.sendMessage(PREFIX + "§c找不到联盟: " + allianceName);
                return true;
            }
            allianceId = alliance.id();
        } else {
            Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
            if (allianceOpt.isEmpty()) {
                player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
                return true;
            }
            allianceId = allianceOpt.orElseThrow().id();
        }

        List<AllianceMember> members = allianceService.getAllianceMembers(allianceId);
        Alliance alliance = allianceService.getAlliance(allianceId).orElse(null);

        player.sendMessage(Component.text("§6§l═══ 联盟成员 (" + members.size() + ") ═══")
            .decoration(TextDecoration.ITALIC, false));
        if (alliance != null) {
            player.sendMessage(Component.text("  §7联盟: §b" + alliance.name())
                .decoration(TextDecoration.ITALIC, false));
        }

        for (AllianceMember member : members) {
            String roleStr = switch (member.role()) {
                case LEADER -> "¦" + member.role().name();
                case OFFICER -> "§d" + member.role().name();
                case MEMBER -> "§7" + member.role().name();
            };
            String nationName = getNationName(member.nationId());
            String joinedStr = member.joinedAt().toString();
            player.sendMessage(Component.text("  " + roleStr + ": " + nationName + " §7(" + joinedStr + ")")
                .decoration(TextDecoration.ITALIC, false));
        }

        return true;
    }

    private boolean handleTransferLeadership(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance transferleadership <国家名称>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        if (!alliance.leaderId().equals(myNationOpt.orElseThrow().id())) {
            player.sendMessage(PREFIX + "§c只有盟主可以转移领导权");
            return true;
        }

        String targetNationName = args[1];
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c找不到国家: " + targetNationName);
            return true;
        }

        AllianceResult result = allianceService.transferLeadership(
            alliance.id(),
            targetNationOpt.orElseThrow().id(),
            myNationOpt.orElseThrow().id()
        );
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance rename <新名称>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        if (!alliance.leaderId().equals(myNationOpt.orElseThrow().id())) {
            player.sendMessage(PREFIX + "§c只有盟主可以重命名联盟");
            return true;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        NationId myNationId = myNationOpt.orElseThrow().id();
        AllianceResult result = allianceService.renameAlliance(myNationId, alliance.id(), newName);
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleRelation(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance relation <联盟名称> <friendly|neutral|hostile>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (myAllianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        String targetAllianceName = args[1];
        Optional<Alliance> targetAllianceOpt = allianceService.getAllianceByName(targetAllianceName);
        if (targetAllianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c找不到联盟: " + targetAllianceName);
            return true;
        }

        AllianceRelationType relationType;
        try {
            relationType = AllianceRelationType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(PREFIX + "§c无效的关系类型: " + args[2]);
            player.sendMessage("§7可用类型: friendly, neutral, hostile");
            return true;
        }

        AllianceResult result = allianceService.setAllianceRelation(
            myAllianceOpt.orElseThrow().id(),
            targetAllianceOpt.orElseThrow().id(),
            relationType
        );
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleAnnouncement(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§c用法: §e/alliance announcement <公告内容>");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        Optional<Alliance> allianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (allianceOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你的国家不在任何联盟中");
            return true;
        }

        String announcement = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AllianceResult result = allianceService.setAnnouncement(
            allianceOpt.orElseThrow().id(),
            announcement,
            myNationOpt.orElseThrow().id()
        );
        player.sendMessage(PREFIX + (result.success() ? "§a" : "§c") + result.message());

        return true;
    }

    private boolean handleList(Player player) {
        Collection<Alliance> alliances = allianceService.getAllAlliances();

        player.sendMessage(Component.text("§6§l═══ 所有联盟 (" + alliances.size() + ") ═══")
            .decoration(TextDecoration.ITALIC, false));

        if (alliances.isEmpty()) {
            player.sendMessage("§7  暂无联盟");
            return true;
        }

        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        Optional<UUID> myAllianceId = myNationOpt.flatMap(n -> allianceService.getNationAlliance(n.id()).map(Alliance::id));

        for (Alliance alliance : alliances) {
            int memberCount = allianceService.getAllianceMembers(alliance.id()).size();
            String prefix = myAllianceId.map(id -> id.equals(alliance.id()) ? "§b*" : "§7 ").orElse("§7 ");
            String name = alliance.name();
            player.sendMessage(Component.text(prefix + "- " + name + " §7(" + memberCount + " 成员)")
                .decoration(TextDecoration.ITALIC, false));
        }

        return true;
    }

    private boolean handlePending(Player player) {
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        if (myNationOpt.isEmpty()) {
            player.sendMessage(PREFIX + "§c你不在任何国家中");
            return true;
        }

        List<AllianceInviteInfo> invites = allianceService.getPendingInvites(myNationOpt.orElseThrow().id());

        player.sendMessage(Component.text("§6§l═══ 待处理邀请 ═══")
            .decoration(TextDecoration.ITALIC, false));

        if (invites.isEmpty()) {
            player.sendMessage("§7  暂无待处理的邀请");
        } else {
            for (AllianceInviteInfo invite : invites) {
                long hoursRemaining = java.time.Duration.between(java.time.Instant.now(), invite.expiresAt()).toHours();
                player.sendMessage(Component.text("  §e- §b" + invite.allianceName())
                    .append(Component.text(" §7(剩余 " + hoursRemaining + " 小时)")
                        .color(NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            }
            player.sendMessage("§7使用 §e/alliance accept [联盟名称] §7接受邀请");
        }

        return true;
    }

    private boolean handleHelp(Player player) {
        sendHelp(player);
        return true;
    }

    // ==================== 辅助方法 ====================

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6§l═══ 联盟系统帮助 ═══")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance create <名称> §7- 创建联盟")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance disband §7- 解散联盟")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance info [联盟] §7- 查看联盟信息")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance list §7- 列出所有联盟")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance members [联盟] §7- 查看成员")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance invite <国家> §7- 邀请加入")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance accept [联盟] §7- 接受邀请")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance reject [联盟] §7- 拒绝邀请")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance leave §7- 离开联盟")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance kick <国家> §7- 踢出成员")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance transferleadership <国家> §7- 转移领导权")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance rename <新名称> §7- 重命名联盟")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance relation <联盟> <类型> §7- 设置关系")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance announcement <内容> §7- 发布公告")
            .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("  §e/alliance pending §7- 待处理邀请")
            .decoration(TextDecoration.ITALIC, false));
    }

    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知国家");
    }

    private void broadcastToNation(NationId nationId, String message) {
        nationService.nationById(nationId).ifPresent(nation -> {
            for (var member : nation.members()) {
                onlinePlayerDirectory.findOnlinePlayer(member.playerId())
                    .ifPresent(player -> player.sendMessage(message));
            }
        });
    }

    // ==================== Tab补全 ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList(
                "create", "disband", "info", "list", "members",
                "invite", "accept", "reject", "leave", "kick",
                "transferleadership", "transfer", "rename",
                "relation", "announcement", "pending", "help"
            ).stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "invite", "kick", "transferleadership" -> nationService.nations().stream()
                    .map(Nation::name)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                case "relation" -> allianceService.getAllAlliances().stream()
                    .map(Alliance::name)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("relation")) {
            return Arrays.asList("friendly", "neutral", "hostile").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
