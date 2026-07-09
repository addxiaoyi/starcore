package dev.starcore.starcore.module.army.raid.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.raid.NightRaidService;
import dev.starcore.starcore.module.army.raid.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 突袭命令处理器
 * /raid <create|join|leave|list|info|cancel|status>
 */
public final class RaidCommand implements CommandExecutor, TabCompleter {
    private final NightRaidService raidService;
    private final NationService nationService;
    private final MessageService messages;
    private final Plugin plugin;

    public RaidCommand(NightRaidService raidService, NationService nationService, MessageService messages, Plugin plugin) {
        this.raidService = raidService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create", "c" -> handleCreate(player, args);
                case "join", "j" -> handleJoin(player, args);
                case "leave", "l" -> handleLeave(player);
                case "list", "ls" -> handleList(player);
                case "info", "i" -> handleInfo(player, args);
                case "cancel" -> handleCancel(player, args);
                case "status" -> handleStatus(player);
                case "alert" -> handleAlert(player);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.no-nation"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationOpt.get();

        // 检查是否在突袭时间窗口
        if (!raidService.isWithinRaidWindow()) {
            player.sendMessage(Component.text(messages.format("raid.error.not-raid-time"), NamedTextColor.RED));
            return;
        }

        // 检查目标国家
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /raid create <目标国家名>", NamedTextColor.YELLOW));
            return;
        }

        String targetName = args[1];
        Optional<Nation> targetOpt = nationService.nationByName(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.target-not-found", targetName), NamedTextColor.RED));
            return;
        }
        Nation targetNation = targetOpt.get();

        // 检查是否可以发起突袭
        String reason = raidService.canInitiateRaid(nation.id(), targetNation.id());
        if (reason != null) {
            player.sendMessage(Component.text(reason, NamedTextColor.RED));
            return;
        }

        // 创建突袭
        Raid raid = raidService.createRaid(nation.id(), targetNation.id(), player.getLocation(), player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.create.success", targetNation.name()), NamedTextColor.GREEN));
        player.sendMessage(Component.text(messages.format("raid.create.info", raidService.getConfig().preparationTimeSeconds()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 通知目标国家
        notifyTargetNation(targetNation, nation.name());
    }

    private void handleJoin(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.no-nation"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationOpt.get();

        // 检查是否有待加入的突袭
        UUID raidId = findPendingRaidForNation(nation.id());
        if (raidId == null) {
            player.sendMessage(Component.text(messages.format("raid.error.no-pending-raid"), NamedTextColor.RED));
            return;
        }

        // 确定是攻击方还是防御方
        Optional<Raid> raidOpt = raidService.getRaid(raidId);
        if (raidOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.raid-not-found"), NamedTextColor.RED));
            return;
        }
        Raid raid = raidOpt.get();
        boolean isAttacker = nation.id().equals(raid.attackerNationId());

        // 加入突袭
        raidService.joinRaid(raidId, player, nation.id(), isAttacker);

        String side = isAttacker ? "攻击方" : "防御方";
        player.sendMessage(Component.text(messages.format("raid.join.success", side), NamedTextColor.GREEN));
    }

    private void handleLeave(Player player) {
        Optional<RaidParticipant> participantOpt = raidService.getParticipant(player.getUniqueId());
        if (participantOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.not-in-raid"), NamedTextColor.RED));
            return;
        }

        UUID raidId = playerRaids.get(player.getUniqueId());
        if (raidId != null) {
            raidService.leaveRaid(raidId, player);
            player.sendMessage(Component.text(messages.format("raid.leave.success"), NamedTextColor.GREEN));
        }
    }

    private void handleList(Player player) {
        Collection<Raid> activeRaids = raidService.getActiveRaids();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.list.header"), NamedTextColor.GOLD));

        if (activeRaids.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.list.empty"), NamedTextColor.GRAY));
        } else {
            for (Raid raid : activeRaids) {
                String attackerName = nationService.nationById(raid.attackerNationId())
                    .map(Nation::name)
                    .orElse("Unknown");
                String defenderName = nationService.nationById(raid.targetNationId())
                    .map(Nation::name)
                    .orElse("Unknown");

                player.sendMessage(Component.text(
                    messages.format("raid.list.entry",
                        raid.id().toString().substring(0, 8),
                        attackerName,
                        defenderName,
                        raid.attackerCount(),
                        raid.defenderCount(),
                        raid.phase().displayName()
                    ),
                    NamedTextColor.GRAY
                ));
            }
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        UUID raidId;
        if (args.length > 1) {
            try {
                raidId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(messages.format("raid.error.invalid-raid-id"), NamedTextColor.RED));
                return;
            }
        } else {
            // 获取玩家当前参与的突袭
            Optional<RaidParticipant> participantOpt = raidService.getParticipant(player.getUniqueId());
            if (participantOpt.isEmpty()) {
                player.sendMessage(Component.text(messages.format("raid.error.not-in-raid"), NamedTextColor.RED));
                return;
            }
            raidId = playerRaids.get(player.getUniqueId());
            if (raidId == null) {
                player.sendMessage(Component.text(messages.format("raid.error.not-in-raid"), NamedTextColor.RED));
                return;
            }
        }

        Optional<Raid> raidOpt = raidService.getRaid(raidId);
        if (raidOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.raid-not-found"), NamedTextColor.RED));
            return;
        }
        Raid raid = raidOpt.get();

        String attackerName = nationService.nationById(raid.attackerNationId())
            .map(Nation::name)
            .orElse("Unknown");
        String defenderName = nationService.nationById(raid.targetNationId())
            .map(Nation::name)
            .orElse("Unknown");

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("raid.info.id", raid.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.info.attacker", attackerName, raid.attackerCount()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.info.defender", defenderName, raid.defenderCount()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.info.phase", raid.phase().displayName()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.info.status", raid.status().displayName()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.info.points", raid.attackerTotalPoints(), raid.defenderTotalPoints()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 显示事件日志
        if (!raid.events().isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.info.events"), NamedTextColor.GOLD));
            int count = 0;
            for (RaidEvent event : raid.events()) {
                if (count++ >= 10) break;
                player.sendMessage(Component.text(
                    messages.format("raid.info.event", event.type().displayName(), event.description()),
                    NamedTextColor.DARK_GRAY
                ));
            }
        }
    }

    private void handleCancel(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.no-nation"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationOpt.get();

        // 查找该国家的突袭
        List<Raid> nationRaids = raidService.getNationRaids(nation.id());
        Optional<Raid> pendingRaid = nationRaids.stream()
            .filter(Raid::isPending)
            .findFirst();

        if (pendingRaid.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.no-pending-raid"), NamedTextColor.RED));
            return;
        }

        // 只有发起者可以取消
        Raid raid = pendingRaid.get();
        if (!raid.initiatorId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(messages.format("raid.error.not-initiator"), NamedTextColor.RED));
            return;
        }

        raidService.endRaid(raid.id(), "Cancelled by initiator");
        player.sendMessage(Component.text(messages.format("raid.cancel.success"), NamedTextColor.GREEN));
    }

    private void handleStatus(Player player) {
        NightRaidService.NightRaidConfig config = raidService.getConfig();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.status.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("raid.status.window",
            raidService.isWithinRaidWindow() ? "开放中" : "关闭中"),
            raidService.isWithinRaidWindow() ? NamedTextColor.GREEN : NamedTextColor.RED));
        player.sendMessage(Component.text(messages.format("raid.status.duration", config.raidDurationMinutes()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.status.preparation", config.preparationTimeSeconds()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.status.cooldown", config.cooldownHours()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.status.participants", config.minRaidParticipants(), config.maxRaidParticipants()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 显示活跃突袭数量
        int activeCount = raidService.getActiveRaids().size();
        player.sendMessage(Component.text(messages.format("raid.status.active", activeCount), NamedTextColor.YELLOW));
    }

    private void handleAlert(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.error.no-nation"), NamedTextColor.RED));
            return;
        }

        Optional<RaidAlert> alertOpt = raidService.getLatestAlert(nationOpt.get().id());
        if (alertOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("raid.alert.none"), NamedTextColor.YELLOW));
            return;
        }

        RaidAlert alert = alertOpt.get();
        String attackerName = nationService.nationById(alert.attackerNationId())
            .map(Nation::name)
            .orElse("Unknown");

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.alert.header"), NamedTextColor.RED));
        player.sendMessage(Component.text(messages.format("raid.alert.message", attackerName, alert.targetLocation(), alert.warningSeconds()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("raid.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("raid.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.join"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.leave"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.cancel"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("raid.help.status"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID findPendingRaidForNation(NationId nationId) {
        List<Raid> raids = raidService.getNationRaids(nationId);
        return raids.stream()
            .filter(Raid::isPending)
            .map(Raid::id)
            .findFirst()
            .orElse(null);
    }

    private void notifyTargetNation(Nation targetNation, String attackerName) {
        // 向目标国家在线成员发送警报
        for (UUID memberId : targetNation.members().stream().map(dev.starcore.starcore.module.nation.model.NationMember::playerId).toList()) {
            org.bukkit.entity.Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(
                    messages.format("raid.alert.incoming", attackerName),
                    NamedTextColor.RED
                ));
            }
        }
    }

    // 临时映射，简化实现
    private final Map<UUID, UUID> playerRaids = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "join", "leave", "list", "info", "cancel", "status", "alert");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            // 返回所有国家名称（排除自己的国家）
            return nationService.nations().stream()
                .map(Nation::name)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}