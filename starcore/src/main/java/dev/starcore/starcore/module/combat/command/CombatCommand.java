package dev.starcore.starcore.module.combat.command;

import dev.starcore.starcore.module.combat.CombatService;
import dev.starcore.starcore.module.combat.config.CombatConfig;
import dev.starcore.starcore.module.combat.gui.BattleGui;
import dev.starcore.starcore.module.combat.gui.CombatGui;
import dev.starcore.starcore.module.combat.model.Battlefield;
import dev.starcore.starcore.module.combat.model.CombatSession;
import dev.starcore.starcore.module.combat.model.PlayerCombatState;
import dev.starcore.starcore.module.combat.storage.CombatStorage;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 战斗系统命令 - /combat 和 /battle 命令处理
 */
public final class CombatCommand implements CommandExecutor, TabCompleter {
    private final CombatService combatService;
    private final CombatGui combatGui;
    private final BattleGui battleGui;
    private final CombatConfig config;
    private final dev.starcore.starcore.module.combat.storage.CombatStorage storage;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public CombatCommand(CombatService combatService, CombatGui combatGui, BattleGui battleGui,
                        CombatConfig config, dev.starcore.starcore.module.combat.storage.CombatStorage storage) {
        this.combatService = combatService;
        this.combatGui = combatGui;
        this.battleGui = battleGui;
        this.config = config;
        this.storage = storage;
    }

    public CombatCommand(CombatService combatService, CombatGui combatGui, BattleGui battleGui, CombatConfig config) {
        this(combatService, combatGui, battleGui, config, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase();

        // /battle 命令路由
        if (commandName.equals("battle")) {
            return handleBattleCommand(sender, args);
        }

        // /combat 命令
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status" -> handleStatus(sender, args);
            case "list" -> handleList(sender, args);
            case "battlefield" -> handleBattlefield(sender, args);
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender, args);
            case "history" -> handleHistory(sender, args);
            case "togglepvp" -> handleTogglePvp(sender);
            case "safe" -> handleSafe(sender, args);
            case "gui" -> handleGui(sender);
            case "battle" -> handleBattleCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "pvp" -> handleTogglePvp(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * /battle 命令处理 - 战场管理
     */
    private boolean handleBattleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendBattleHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleBattleCreate(sender, args);
            case "join" -> handleBattleJoin(sender, args);
            case "leave" -> handleBattleLeave(sender, args);
            case "start" -> handleBattleStart(sender, args);
            case "end" -> handleBattleEnd(sender, args);
            case "list" -> handleBattleList(sender);
            case "info" -> handleBattleInfo(sender, args);
            case "gui" -> {
                if (sender instanceof Player player) {
                    battleGui.openMainMenu(player);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
                }
            }
            default -> sendBattleHelp(sender);
        }

        return true;
    }

    /**
     * /battle create - 创建战场
     */
    private void handleBattleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.combat.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限创建战场。");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /battle create <名称> [半径] [类型] [国家1] [国家2]");
            sender.sendMessage(ChatColor.GRAY + "类型: WAR_ZONE, PVP_ARENA, ARMY_BATTLE, FREE_PVP, EVENT");
            return;
        }

        String name = args[1];
        double radius = args.length > 2 ? Double.parseDouble(args[2]) : config.getDefaultBattlefieldRadius();
        Battlefield.BattlefieldType type = args.length > 3
            ? Battlefield.BattlefieldType.valueOf(args[3].toUpperCase())
            : Battlefield.BattlefieldType.WAR_ZONE;

        Battlefield battlefield = combatService.createBattlefield(name, player.getLocation(), radius, type);

        // 如果指定了国家，创建国家对抗战场
        if (args.length > 5) {
            try {
                NationId nation1 = new NationId(UUID.fromString(args[4]));
                NationId nation2 = new NationId(UUID.fromString(args[5]));
                combatService.createBattlefield(name, nation1, nation2, player.getLocation(), radius, type);
                sender.sendMessage(ChatColor.GREEN + "国家对抗战场已创建: " + name);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.YELLOW + "国家ID格式无效，已创建普通战场。");
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + "战场已创建: " + name + " (ID: " + battlefield.battlefieldId().toString().substring(0, 8) + ")");
        }
    }

    /**
     * /battle join - 加入战场
     */
    private void handleBattleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /battle join <战场ID>");
            return;
        }

        try {
            UUID battlefieldId = UUID.fromString(args[1]);
            combatService.getBattlefieldAt(player.getLocation()).ifPresentOrElse(
                bf -> {
                    combatService.addPlayerToBattlefield(player.getUniqueId(), bf);
                    sender.sendMessage(ChatColor.GREEN + "你已加入战场: " + bf.name());
                },
                () -> sender.sendMessage(ChatColor.RED + "当前位置不在任何战场中。")
            );
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "无效的战场ID格式。");
        }
    }

    /**
     * /battle leave - 离开战场
     */
    private void handleBattleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
            return;
        }

        combatService.getBattlefieldAt(player.getLocation()).ifPresentOrElse(
            bf -> {
                combatService.removePlayerFromBattlefield(player.getUniqueId(), bf);
                sender.sendMessage(ChatColor.GREEN + "你已离开战场: " + bf.name());
            },
            () -> sender.sendMessage(ChatColor.RED + "你不在任何战场中。")
        );
    }

    /**
     * /battle start - 开始战斗
     */
    private void handleBattleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.combat.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限开始战斗。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /battle start <战场ID>");
            return;
        }

        try {
            UUID battlefieldId = UUID.fromString(args[1]);
            if (combatService.startBattle(battlefieldId)) {
                sender.sendMessage(ChatColor.GREEN + "战斗已开始！");
            } else {
                sender.sendMessage(ChatColor.RED + "无法开始战斗，请检查战场状态。");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "无效的战场ID格式。");
        }
    }

    /**
     * /battle end - 结束战斗
     */
    private void handleBattleEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.combat.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限结束战斗。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /battle end <战场ID> [获胜国家ID]");
            return;
        }

        try {
            UUID battlefieldId = UUID.fromString(args[1]);
            NationId winner = args.length > 2 ? new NationId(UUID.fromString(args[2])) : null;

            Battlefield.BattlefieldSummary summary = combatService.endBattle(battlefieldId, winner);
            if (summary != null) {
                sender.sendMessage(ChatColor.GREEN + "战斗已结束: " + summary.name());
            } else {
                sender.sendMessage(ChatColor.RED + "战场不存在。");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "无效的ID格式。");
        }
    }

    /**
     * /battle list - 列出所有战场
     */
    private void handleBattleList(CommandSender sender) {
        Collection<Battlefield> battlefields = combatService.getAllBattlefields();

        sender.sendMessage(ChatColor.GOLD + "===== 战场列表 (" + battlefields.size() + ") =====");

        if (battlefields.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "当前没有战场。");
            return;
        }

        for (Battlefield bf : battlefields) {
            ChatColor statusColor = bf.isActive() ? ChatColor.GREEN : ChatColor.GRAY;
            String nationInfo = "";
            if (bf.getNation1() != null || bf.getNation2() != null) {
                nationInfo = " [" + (bf.getNation1() != null ? bf.getNation1().value().toString().substring(0, 8) : "?") +
                    " VS " + (bf.getNation2() != null ? bf.getNation2().value().toString().substring(0, 8) : "?") + "]";
            }

            sender.sendMessage(String.format("%s[%s]%s %s - 类型: %s - 参与者: %d - 状态: %s%s",
                statusColor,
                bf.battlefieldId().toString().substring(0, 8),
                ChatColor.WHITE,
                bf.name(),
                bf.type(),
                bf.getParticipantCount(),
                bf.isActive() ? "进行中" : "等待",
                nationInfo
            ));
        }
    }

    /**
     * /battle info - 查看战场信息
     */
    private void handleBattleInfo(CommandSender sender, String[] args) {
        Player targetPlayer = null;

        if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        }

        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
        }

        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "用法: /battle info [玩家]");
            return;
        }

        combatService.getBattlefieldAt(targetPlayer.getLocation()).ifPresentOrElse(
            bf -> {
                sender.sendMessage(ChatColor.GOLD + "===== 战场信息 =====");
                sender.sendMessage(ChatColor.YELLOW + "名称: " + bf.name());
                sender.sendMessage(ChatColor.YELLOW + "类型: " + bf.type());
                sender.sendMessage(ChatColor.YELLOW + "状态: " + (bf.isActive() ? "进行中" : "等待"));
                sender.sendMessage(ChatColor.YELLOW + "参与者: " + bf.getParticipantCount());
                sender.sendMessage(ChatColor.YELLOW + "击杀: " + bf.totalKills());
                sender.sendMessage(ChatColor.YELLOW + "死亡: " + bf.totalDeaths());

                if (bf.getNation1() != null || bf.getNation2() != null) {
                    sender.sendMessage(ChatColor.GOLD + "===== 国家对抗 =====");
                    sender.sendMessage(ChatColor.BLUE + "蓝方: " + (bf.getNation1() != null ? bf.getNation1().value() : "待加入"));
                    sender.sendMessage(ChatColor.RED + "红方: " + (bf.getNation2() != null ? bf.getNation2().value() : "待加入"));
                }
            },
            () -> sender.sendMessage(ChatColor.RED + "当前位置不在任何战场中。")
        );
    }

    /**
     * 发送战斗帮助
     */
    private void sendBattleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 战场命令帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/battle create <名称> [半径] [类型]" + ChatColor.WHITE + " - 创建战场");
        sender.sendMessage(ChatColor.YELLOW + "/battle join <战场ID>" + ChatColor.WHITE + " - 加入战场");
        sender.sendMessage(ChatColor.YELLOW + "/battle leave" + ChatColor.WHITE + " - 离开战场");
        sender.sendMessage(ChatColor.YELLOW + "/battle start <战场ID>" + ChatColor.WHITE + " - 开始战斗");
        sender.sendMessage(ChatColor.YELLOW + "/battle end <战场ID> [获胜者]" + ChatColor.WHITE + " - 结束战斗");
        sender.sendMessage(ChatColor.YELLOW + "/battle list" + ChatColor.WHITE + " - 列出所有战场");
        sender.sendMessage(ChatColor.YELLOW + "/battle info [玩家]" + ChatColor.WHITE + " - 查看战场信息");
        sender.sendMessage(ChatColor.YELLOW + "/battle gui" + ChatColor.WHITE + " - 打开战场GUI");
    }

    /**
     * 处理GUI命令
     */
    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
            return;
        }

        combatGui.openMainMenu(player);
    }

    /**
     * /combat status [player] - 查看玩家战斗状态
     */
    private void handleStatus(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家不在线: " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "请指定玩家: /combat status <玩家>");
            return;
        }

        UUID playerId = target.getUniqueId();

        sender.sendMessage(ChatColor.GOLD + "===== 战斗状态: " + target.getName() + " =====");

        PlayerCombatState state = combatService.getPlayerState(playerId).orElse(null);

        if (state == null) {
            sender.sendMessage(ChatColor.GREEN + "该玩家从未参与过战斗。");
            return;
        }

        // 战斗状态
        ChatColor combatColor = state.isInCombat() ? ChatColor.RED : ChatColor.GREEN;
        sender.sendMessage(combatColor + "战斗状态: " + (state.isInCombat() ? "战斗中" : "非战斗"));

        // 战斗标签
        if (state.combatTag().isPresent()) {
            var tag = state.combatTag().get();
            sender.sendMessage(ChatColor.YELLOW + "标签类型: " + tag.type());
            sender.sendMessage(ChatColor.YELLOW + "标记者: " + tag.getTaggerName());
            sender.sendMessage(ChatColor.YELLOW + "剩余时间: " + (tag.remainingTime() / 1000) + " 秒");
        }

        // 战斗持续时间
        if (state.isInCombat()) {
            sender.sendMessage(ChatColor.YELLOW + "战斗持续: " + state.getCombatDurationSeconds() + " 秒");
        }

        // 统计
        sender.sendMessage(ChatColor.GRAY + "--- 战斗统计 ---");
        sender.sendMessage(ChatColor.AQUA + "总伤害输出: " + state.getTotalDamageDealt());
        sender.sendMessage(ChatColor.RED + "总承受伤害: " + state.getTotalDamageTaken());
        sender.sendMessage(ChatColor.GREEN + "击杀: " + state.getTotalKills());
        sender.sendMessage(ChatColor.DARK_RED + "死亡: " + state.getTotalDeaths());

        // 最后击杀/死亡
        if (state.lastKillerId().isPresent()) {
            sender.sendMessage(ChatColor.GRAY + "最后击杀: " + state.lastKillerId().get());
        }
        if (state.lastVictimId().isPresent()) {
            sender.sendMessage(ChatColor.GRAY + "最后死亡: " + state.lastVictimId().get());
        }
    }

    /**
     * /combat list - 列出活跃战斗
     */
    private void handleList(CommandSender sender, String[] args) {
        Collection<CombatSession> sessions = combatService.getActiveSessions();

        sender.sendMessage(ChatColor.GOLD + "===== 活跃战斗会话 (" + sessions.size() + ") =====");

        if (sessions.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "当前没有活跃的战斗。");
            return;
        }

        for (CombatSession session : sessions) {
            String attacker = getPlayerName(session.attackerId());
            String defender = getPlayerName(session.defenderId());
            long duration = session.getDurationSeconds();
            int totalDamage = session.attackerDamage() + session.defenderDamage();

            sender.sendMessage(String.format("%s[%s]%s %s vs %s - %d秒 - %d伤害",
                ChatColor.YELLOW,
                session.sessionId().toString().substring(0, 8),
                ChatColor.WHITE,
                attacker,
                defender,
                duration,
                totalDamage
            ));
        }
    }

    /**
     * /combat battlefield [info|create|delete|list] - 战场管理
     */
    private void handleBattlefield(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.combat.battlefield")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return;
        }

        if (args.length < 2) {
            sendBattlefieldHelp(sender);
            return;
        }

        String subCmd = args[1].toLowerCase();

        switch (subCmd) {
            case "list" -> {
                Collection<Battlefield> battlefields = combatService.getAllBattlefields();
                sender.sendMessage(ChatColor.GOLD + "===== 战场列表 (" + battlefields.size() + ") =====");

                for (Battlefield bf : battlefields) {
                    ChatColor statusColor = bf.isActive() ? ChatColor.GREEN : ChatColor.GRAY;
                    sender.sendMessage(String.format("%s[%s]%s %s - 类型: %s - 参与者: %d - 击杀: %d",
                        statusColor,
                        bf.battlefieldId().toString().substring(0, 8),
                        ChatColor.WHITE,
                        bf.name(),
                        bf.type(),
                        bf.getParticipantCount(),
                        bf.totalKills()
                    ));
                }
            }

            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
                    return;
                }

                Optional<Battlefield> bf = combatService.getBattlefieldAt(player.getLocation());
                if (bf.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "当前位置不在任何战场中。");
                    return;
                }

                Battlefield battlefield = bf.get();
                sender.sendMessage(ChatColor.GOLD + "===== 战场信息 =====");
                sender.sendMessage(ChatColor.YELLOW + "名称: " + battlefield.name());
                sender.sendMessage(ChatColor.YELLOW + "类型: " + battlefield.type());
                sender.sendMessage(ChatColor.YELLOW + "位置: " + battlefield.center().getWorld().getName()
                    + " (" + battlefield.center().getBlockX() + ", " + battlefield.center().getBlockY() + ", "
                    + battlefield.center().getBlockZ() + ")");
                sender.sendMessage(ChatColor.YELLOW + "半径: " + battlefield.radius());
                sender.sendMessage(ChatColor.YELLOW + "参与者: " + battlefield.getParticipantCount());
                sender.sendMessage(ChatColor.YELLOW + "总击杀: " + battlefield.totalKills());
                sender.sendMessage(ChatColor.YELLOW + "总死亡: " + battlefield.totalDeaths());
                sender.sendMessage(ChatColor.YELLOW + "总伤害: " + battlefield.totalDamage());
                sender.sendMessage(ChatColor.YELLOW + "持续时间: " + battlefield.getDurationSeconds() + " 秒");
            }

            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
                    return;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /combat battlefield create <名称> [半径] [类型]");
                    return;
                }

                String name = args[2];
                double radius = args.length > 3 ? Double.parseDouble(args[3]) : config.getDefaultBattlefieldRadius();
                Battlefield.BattlefieldType type = args.length > 4
                    ? Battlefield.BattlefieldType.valueOf(args[4].toUpperCase())
                    : Battlefield.BattlefieldType.FREE_PVP;

                Battlefield battlefield = combatService.createBattlefield(name, player.getLocation(), radius, type);
                sender.sendMessage(ChatColor.GREEN + "战场已创建: " + name + " (ID: " + battlefield.battlefieldId().toString().substring(0, 8) + ")");
            }

            default -> sendBattlefieldHelp(sender);
        }
    }

    /**
     * /combat reload - 重载配置
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.combat.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return;
        }

        config.loadConfig();
        sender.sendMessage(ChatColor.GREEN + "战斗配置已重载。");
    }

    /**
     * /combat stats - 查看统计
     */
    private void handleStats(CommandSender sender, String[] args) {
        CombatService.CombatStats stats = combatService.getStats();

        sender.sendMessage(ChatColor.GOLD + "===== 战斗系统统计 =====");
        sender.sendMessage(ChatColor.YELLOW + "追踪玩家数: " + stats.totalPlayers());
        sender.sendMessage(ChatColor.YELLOW + "总战斗会话: " + stats.totalSessions());
        sender.sendMessage(ChatColor.GREEN + "活跃会话: " + stats.activeSessions());
        sender.sendMessage(ChatColor.AQUA + "活跃参与者: " + stats.activeParticipants());
        sender.sendMessage(ChatColor.GOLD + "战场数: " + stats.battlefields());
    }

    /**
     * /combat history [player] [limit] - 查看战斗历史
     */
    private void handleHistory(CommandSender sender, String[] args) {
        Player target;
        int limit = 10;

        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家不在线: " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "请指定玩家: /combat history <玩家>");
            return;
        }

        if (args.length > 2) {
            try {
                limit = Math.min(50, Math.max(1, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "无效的数字: " + args[2]);
                return;
            }
        }

        sender.sendMessage(ChatColor.GOLD + "===== " + target.getName() + " 的战斗历史 =====");
        sender.sendMessage(ChatColor.GRAY + "(最近 " + limit + " 场战斗)");

        // 从存储中获取历史记录
        if (storage != null) {
            List<CombatStorage.CombatHistoryRecord> history = storage.getPlayerCombatHistory(target.getUniqueId(), limit);

            if (history.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "暂无战斗历史记录。");
                return;
            }

            for (CombatStorage.CombatHistoryRecord record : history) {
                UUID selfId = target.getUniqueId();
                boolean wasKiller = record.killerId() != null && record.killerId().equals(selfId);
                boolean wasVictim = record.victimId() != null && record.victimId().equals(selfId);
                boolean wasAttacker = record.attackerId().equals(selfId);
                boolean wasDefender = record.defenderId().equals(selfId);

                String role;
                ChatColor roleColor;
                if (wasKiller) {
                    role = "击杀";
                    roleColor = ChatColor.GREEN;
                } else if (wasVictim) {
                    role = "死亡";
                    roleColor = ChatColor.RED;
                } else if (wasAttacker) {
                    role = "战斗";
                    roleColor = ChatColor.YELLOW;
                } else {
                    role = "参与";
                    roleColor = ChatColor.GRAY;
                }

                // 获取对方玩家名
                UUID otherId = wasAttacker ? record.defenderId() : (wasDefender ? record.attackerId() : null);
                String otherName = "未知";
                if (otherId != null) {
                    Player other = Bukkit.getPlayer(otherId);
                    otherName = other != null ? other.getName() : otherId.toString().substring(0, 8);
                }

                String time = DATE_FORMAT.format(
                    java.time.Instant.ofEpochMilli(record.createdAt())
                        .atZone(java.time.ZoneId.systemDefault())
                );
                int totalDamage = record.attackerDamage() + record.defenderDamage();

                sender.sendMessage(String.format("%s[%s] %s%s %s %s%s %s - 伤害: %d",
                    ChatColor.GRAY,
                    time,
                    roleColor,
                    role,
                    ChatColor.WHITE,
                    wasKiller || wasAttacker ? "vs" : "被",
                    ChatColor.WHITE,
                    otherName,
                    totalDamage
                ));
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "战斗历史存储暂不可用。");
        }
    }

    /**
     * /combat togglepvp - 切换自己的PVP状态
     */
    private void handleTogglePvp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
            return;
        }

        // 检查是否在世界允许PVP
        if (!config.isPvPEnabled()) {
            sender.sendMessage(ChatColor.RED + "服务器已禁用PVP功能。");
            return;
        }

        // 切换玩家的PVP状态（使用持久化存储）
        UUID playerId = player.getUniqueId();
        boolean newState = combatService.togglePlayerPvp(playerId);

        if (newState) {
            sender.sendMessage(ChatColor.GREEN + "你已开启PVP模式。其他玩家可以对你发起攻击。");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "你已关闭PVP模式。其他玩家无法对你发起攻击。");
        }
    }

    /**
     * /combat safe - 安全区管理
     */
    private void handleSafe(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.combat.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return;
        }

        if (args.length < 2) {
            sendSafeHelp(sender);
            return;
        }

        String subCmd = args[1].toLowerCase();

        switch (subCmd) {
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "===== 安全区列表 =====");
                Map<String, CombatConfig.CombatZone> zones = config.getCombatZones();
                if (zones.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "当前没有配置安全区。");
                    return;
                }
                for (Map.Entry<String, CombatConfig.CombatZone> entry : zones.entrySet()) {
                    CombatConfig.CombatZone zone = entry.getValue();
                    ChatColor pvpColor = zone.pvpEnabled() ? ChatColor.RED : ChatColor.GREEN;
                    sender.sendMessage(String.format("%s[%s] %s - 世界: %s (%.1f, %.1f, %.1f) 半径: %.0f - PVP: %s",
                        ChatColor.YELLOW,
                        entry.getKey(),
                        zone.type().name(),
                        zone.world(),
                        zone.x(), zone.y(), zone.z(),
                        zone.radius(),
                        pvpColor + (zone.pvpEnabled() ? "允许" : "禁止")
                    ));
                }
            }

            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
                    return;
                }

                sender.sendMessage(ChatColor.GOLD + "===== 安全区检查 =====");
                sender.sendMessage(ChatColor.YELLOW + "位置: " + player.getLocation().getWorld().getName()
                    + " (" + player.getLocation().getBlockX() + ", "
                    + player.getLocation().getBlockY() + ", "
                    + player.getLocation().getBlockZ() + ")");

                // 检查每个区域
                for (Map.Entry<String, CombatConfig.CombatZone> entry : config.getCombatZones().entrySet()) {
                    CombatConfig.CombatZone zone = entry.getValue();
                    if (zone.isInside(player.getLocation().getWorld().getName(),
                            player.getLocation().getX(), player.getLocation().getZ())) {
                        sender.sendMessage(ChatColor.GREEN + "你正处于: " + entry.getKey());
                        sender.sendMessage(ChatColor.GREEN + "区域类型: " + zone.type());
                        sender.sendMessage(ChatColor.GREEN + "PVP状态: " + (zone.pvpEnabled() ? ChatColor.RED + "允许PVP" : ChatColor.GREEN + "禁止PVP"));
                        return;
                    }
                }
                sender.sendMessage(ChatColor.YELLOW + "你不在任何安全区内。");
            }

            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "此命令需要玩家执行。");
                    return;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /combat safe create <名称> [半径] [类型]");
                    sender.sendMessage(ChatColor.GRAY + "类型: SAFE_ZONE, PVP_ZONE, WAR_ZONE, ARENA, EVENT");
                    return;
                }

                String zoneName = args[2];
                double radius = args.length > 3 ? Double.parseDouble(args[3]) : 50.0;
                String typeStr = args.length > 4 ? args[4].toUpperCase() : "SAFE_ZONE";

                try {
                    CombatConfig.CombatZoneType type = CombatConfig.CombatZoneType.valueOf(typeStr);
                    CombatConfig.CombatZone zone = new CombatConfig.CombatZone(
                        zoneName,
                        player.getLocation().getWorld().getName(),
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ(),
                        radius,
                        type == CombatConfig.CombatZoneType.PVP_ZONE || type == CombatConfig.CombatZoneType.WAR_ZONE,
                        type
                    );

                    config.getCombatZones().put(zoneName, zone);
                    config.saveConfig();

                    sender.sendMessage(ChatColor.GREEN + "安全区已创建: " + zoneName);
                    sender.sendMessage(ChatColor.GRAY + "位置: " + zone.world() + " (" + zone.x() + ", " + zone.y() + ", " + zone.z() + ")");
                    sender.sendMessage(ChatColor.GRAY + "半径: " + radius);
                    sender.sendMessage(ChatColor.GRAY + "类型: " + type);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "无效的区域类型: " + typeStr);
                }
            }

            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /combat safe delete <名称>");
                    return;
                }

                String zoneName = args[2];
                CombatConfig.CombatZone removed = config.getCombatZones().remove(zoneName);
                if (removed != null) {
                    config.saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "安全区已删除: " + zoneName);
                } else {
                    sender.sendMessage(ChatColor.RED + "安全区不存在: " + zoneName);
                }
            }

            default -> sendSafeHelp(sender);
        }
    }

    private void sendSafeHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 安全区管理帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/combat safe list" + ChatColor.WHITE + " - 列出所有安全区");
        sender.sendMessage(ChatColor.YELLOW + "/combat safe info" + ChatColor.WHITE + " - 检查当前位置是否在安全区");
        sender.sendMessage(ChatColor.YELLOW + "/combat safe create <名称> [半径] [类型]" + ChatColor.WHITE + " - 创建安全区");
        sender.sendMessage(ChatColor.YELLOW + "/combat safe delete <名称>" + ChatColor.WHITE + " - 删除安全区");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 战斗系统帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/combat status [玩家]" + ChatColor.WHITE + " - 查看战斗状态");
        sender.sendMessage(ChatColor.YELLOW + "/combat list" + ChatColor.WHITE + " - 列出活跃战斗");
        sender.sendMessage(ChatColor.YELLOW + "/combat battlefield [子命令]" + ChatColor.WHITE + " - 战场管理");
        sender.sendMessage(ChatColor.YELLOW + "/combat togglepvp" + ChatColor.WHITE + " - 切换PVP状态");
        sender.sendMessage(ChatColor.YELLOW + "/combat safe [子命令]" + ChatColor.WHITE + " - 安全区管理");
        sender.sendMessage(ChatColor.YELLOW + "/combat stats" + ChatColor.WHITE + " - 查看系统统计");
        sender.sendMessage(ChatColor.YELLOW + "/combat history [玩家]" + ChatColor.WHITE + " - 查看战斗历史");
        sender.sendMessage(ChatColor.YELLOW + "/combat gui" + ChatColor.WHITE + " - 打开战斗GUI");
        sender.sendMessage(ChatColor.YELLOW + "/combat reload" + ChatColor.WHITE + " - 重载配置");
    }

    private void sendBattlefieldHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 战场管理帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/combat battlefield list" + ChatColor.WHITE + " - 列出所有战场");
        sender.sendMessage(ChatColor.YELLOW + "/combat battlefield info" + ChatColor.WHITE + " - 查看当前位置战场信息");
        sender.sendMessage(ChatColor.YELLOW + "/combat battlefield create <名称> [半径] [类型]" + ChatColor.WHITE + " - 创建战场");
    }

    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : playerId.toString().substring(0, 8);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("status", "list", "battlefield", "togglepvp", "pvp", "safe", "stats", "history", "reload", "gui"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("history")) {
                return getOnlinePlayerNames();
            }
            if (args[0].equalsIgnoreCase("battlefield")) {
                return filterStartsWith(Arrays.asList("list", "info", "create"), args[1]);
            }
            if (args[0].equalsIgnoreCase("safe")) {
                return filterStartsWith(Arrays.asList("list", "info", "create", "delete"), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("battlefield") && args[1].equalsIgnoreCase("create")) {
            return Arrays.asList("50", "100", "200");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("battlefield") && args[1].equalsIgnoreCase("create")) {
            return Arrays.asList("FREE_PVP", "PVP_ARENA", "WAR_ZONE", "ARMY_BATTLE", "EVENT");
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
            .collect(Collectors.toList());
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList());
    }
}
