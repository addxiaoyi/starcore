package dev.starcore.starcore.module.army.siege.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.siege.SiegeService;
import dev.starcore.starcore.module.army.siege.SiegeServiceImpl;
import dev.starcore.starcore.module.army.siege.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 攻城器械命令处理器
 * /sc siege <子命令>
 */
public final class SiegeCommand implements CommandExecutor, TabCompleter {
    private final SiegeService siegeService;
    private final NationService nationService;
    private final MessageService messages;

    public SiegeCommand(SiegeService siegeService, NationService nationService, MessageService messages) {
        this.siegeService = siegeService;
        this.nationService = nationService;
        this.messages = messages;
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
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "deploy", "d" -> handleDeploy(player, args);
                case "attack", "a" -> handleAttack(player, args);
                case "repair", "r" -> handleRepair(player, args);
                case "reload", "rl" -> handleReload(player, args);
                case "move", "m" -> handleMove(player, args);
                case "retreat" -> handleRetreat(player, args);
                case "disband" -> handleDisband(player, args);
                case "wall", "w" -> handleWall(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /siege create <类型> <船员数>", NamedTextColor.YELLOW));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("siege.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // 解析类型
        SiegeType type;
        try {
            type = SiegeType.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("未知器械类型: " + args[1], NamedTextColor.RED));
            player.sendMessage(Component.text("可用类型: battering-ram, catapult, trebuchet, ballista, tower", NamedTextColor.GRAY));
            return;
        }

        // 解析船员数量
        int crewSize;
        try {
            crewSize = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的船员数量: " + args[2], NamedTextColor.RED));
            return;
        }

        // 检查部署冷却
        if (!siegeService.canDeploy(nation.id().value())) {
            long remaining = siegeService.getDeploymentCooldownRemaining(nation.id().value());
            player.sendMessage(Component.text("部署冷却中，剩余: " + formatTime(remaining) + "秒", NamedTextColor.YELLOW));
            return;
        }

        // 创建攻城器械
        SiegeUnit siege = siegeService.createSiege(nation.id().value(), type, crewSize, player.getLocation());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 攻城器械已创建 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: " + type.displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("船员: " + crewSize, NamedTextColor.GRAY));
        player.sendMessage(Component.text("ID: " + siege.id().toString().substring(0, 8), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("siege.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        List<SiegeUnit> sieges = siegeService.getNationSieges(nation.id().value());

        if (sieges.isEmpty()) {
            player.sendMessage(Component.text("你的国家没有攻城器械", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 国家攻城器械 (" + sieges.size() + ") ===", NamedTextColor.GOLD));
        for (SiegeUnit siege : sieges) {
            String shortId = siege.id().toString().substring(0, 8);
            String statusIcon = siege.state().canFire() ? "[*]" : "[ ]";
            player.sendMessage(Component.text(
                statusIcon + " " + siege.type().displayName() + " #" + shortId +
                " HP:" + String.format("%.0f%%", siege.health()) +
                " 弹药:" + siege.ammunition() +
                " 状态:" + siege.state().name(),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege info <ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        Optional<SiegeUnit> siegeOpt = siegeService.getSiege(siegeId);

        if (siegeOpt.isEmpty()) {
            player.sendMessage(Component.text("攻城器械不存在: " + args[1], NamedTextColor.RED));
            return;
        }

        SiegeUnit siege = siegeOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 攻城器械详情 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: " + siege.type().displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("ID: " + siege.id().toString(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("船员: " + siege.crewSize(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("生命值: " + String.format("%.1f%%", siege.health()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("士气: " + String.format("%.1f%%", siege.crewMorale()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("弹药: " + siege.ammunition() + "/100", NamedTextColor.GRAY));
        player.sendMessage(Component.text("状态: " + siege.state().name(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("经验: " + siege.siegeExperience() + " (Lv." + siege.experienceLevel() + " " + siege.experienceLevelName() + ")", NamedTextColor.GRAY));

        if (siege.deployedLocation() != null) {
            Location loc = siege.deployedLocation();
            player.sendMessage(Component.text("部署位置: " + loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ(), NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("有效射程: " + siege.type().effectiveRange() + " 格", NamedTextColor.GRAY));
        player.sendMessage(Component.text("攻城伤害: " + String.format("%.1f", siege.siegeDamage()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleDeploy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege deploy <ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        Optional<SiegeUnit> siegeOpt = siegeService.getSiege(siegeId);

        if (siegeOpt.isEmpty()) {
            player.sendMessage(Component.text("攻城器械不存在: " + args[1], NamedTextColor.RED));
            return;
        }

        SiegeUnit siege = siegeOpt.get();

        // 使用准心指向的位置作为部署位置
        Location target = player.getTargetBlock(null, 100).getLocation();
        target = target.add(0, 1, 0); // 向上移动一格

        siegeService.deploySiege(siegeId, target);

        player.sendMessage(Component.text("攻城器械已部署到 " + target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ(), NamedTextColor.GREEN));
    }

    private void handleAttack(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege attack <攻城器械ID> [城墙ID]", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        Optional<SiegeUnit> siegeOpt = siegeService.getSiege(siegeId);

        if (siegeOpt.isEmpty()) {
            player.sendMessage(Component.text("攻城器械不存在: " + args[1], NamedTextColor.RED));
            return;
        }

        SiegeUnit siege = siegeOpt.get();

        // 解析目标城墙ID（如果提供）
        UUID wallId = null;
        if (args.length >= 3) {
            try {
                wallId = UUID.fromString(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("无效的城墙ID: " + args[2], NamedTextColor.RED));
                return;
            }
        }

        // 使用准心指向的方块作为目标
        Location targetLoc = player.getTargetBlock(null, 100).getLocation();

        // 如果提供了城墙ID
        if (wallId != null) {
            Optional<WallData> wallOpt = siegeService.getWall(wallId);
            if (wallOpt.isPresent()) {
                targetLoc = wallOpt.get().location();
            }
        }

        // 开火
        double damage = siegeService.fireSiege(siegeId, targetLoc);

        if (damage > 0) {
            player.sendMessage(Component.text("攻城器械开火! 造成 " + String.format("%.1f", damage) + " 点伤害", NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text("未命中目标", NamedTextColor.YELLOW));
        }
    }

    private void handleRepair(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege repair <攻城器械ID> [修复量]", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        double repairAmount = args.length >= 3 ? Double.parseDouble(args[2]) : 50;

        siegeService.repairSiege(siegeId, repairAmount);

        Optional<SiegeUnit> siegeOpt = siegeService.getSiege(siegeId);
        siegeOpt.ifPresent(s ->
            player.sendMessage(Component.text("攻城器械已修复! 当前生命值: " + String.format("%.1f%%", s.health()), NamedTextColor.GREEN))
        );
    }

    private void handleReload(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege reload <攻城器械ID> [弹药量]", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        Optional<SiegeUnit> siegeOpt = siegeService.getSiege(siegeId);

        if (siegeOpt.isEmpty()) {
            player.sendMessage(Component.text("攻城器械不存在: " + args[1], NamedTextColor.RED));
            return;
        }

        SiegeUnit siege = siegeOpt.get();
        int ammo = args.length >= 3 ? Integer.parseInt(args[2]) : 20;

        // 补充弹药
        int currentAmmo = siege.ammunition();
        siegeService.getTypeConfig(siege.type()); // 验证类型
        // 注意：这里需要通过ServiceImpl来补充弹药
        if (siegeService instanceof SiegeServiceImpl impl) {
            impl.reloadSiege(siegeId, ammo);
            player.sendMessage(Component.text("弹药已补充! 当前弹药: " + (currentAmmo + ammo), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("无法补充弹药", NamedTextColor.RED));
        }
    }

    private void handleMove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege move <攻城器械ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        Location destination = player.getLocation();

        if (siegeService instanceof SiegeServiceImpl impl) {
            impl.moveSiege(siegeId, destination);
            player.sendMessage(Component.text("攻城器械已移动到当前位置", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("无法移动攻城器械", NamedTextColor.RED));
        }
    }

    private void handleRetreat(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege retreat <攻城器械ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);

        if (siegeService instanceof SiegeServiceImpl impl) {
            impl.retreatSiege(siegeId);
            player.sendMessage(Component.text("攻城器械开始撤退", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("无法撤退", NamedTextColor.RED));
        }
    }

    private void handleDisband(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege disband <攻城器械ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID siegeId = parseSiegeId(player, args[1]);
        siegeService.disbandSiege(siegeId);
        player.sendMessage(Component.text("攻城器械已解散", NamedTextColor.GREEN));
    }

    private void handleWall(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /siege wall <子命令>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("子命令: create, repair, info", NamedTextColor.GRAY));
            return;
        }

        String wallSubCommand = args[1].toLowerCase();

        switch (wallSubCommand) {
            case "create", "c" -> handleWallCreate(player, args);
            case "repair", "r" -> handleWallRepair(player, args);
            case "info", "i" -> handleWallInfo(player, args);
            default -> player.sendMessage(Component.text("未知子命令: " + wallSubCommand, NamedTextColor.RED));
        }
    }

    private void handleWallCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /siege wall create <城墙类型> [等级]", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("类型: wooden-wall, stone-wall, reinforced-stone-wall, iron-wall, gate, reinforced-gate, tower", NamedTextColor.GRAY));
            return;
        }

        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("siege.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        WallType type;
        try {
            type = WallType.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("未知城墙类型: " + args[2], NamedTextColor.RED));
            return;
        }

        int level = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
        Location location = player.getLocation().getBlock().getLocation();

        WallData wall = siegeService.createWall(location, nation.id().value(), type);
        wall.setLevel(level); // 手动设置等级

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 城墙已创建 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: " + wall.type().displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("等级: " + wall.level(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("耐久: " + wall.maxHealth(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("ID: " + wall.id().toString().substring(0, 8), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleWallRepair(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /siege wall repair <城墙ID> [修复量]", NamedTextColor.YELLOW));
            return;
        }

        UUID wallId;
        try {
            wallId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的城墙ID: " + args[2], NamedTextColor.RED));
            return;
        }

        double repairAmount = args.length >= 4 ? Double.parseDouble(args[3]) : 500;

        siegeService.repairWall(wallId, repairAmount);

        Optional<WallData> wallOpt = siegeService.getWall(wallId);
        wallOpt.ifPresent(w ->
            player.sendMessage(Component.text("城墙已修复! 当前耐久: " + w.currentHealth() + "/" + w.maxHealth(), NamedTextColor.GREEN))
        );
    }

    private void handleWallInfo(Player player, String[] args) {
        if (args.length < 2) {
            // 查找最近的城墙
            Optional<WallData> nearestWall = siegeService.getNearestWall(player.getLocation(), 50);
            if (nearestWall.isPresent()) {
                showWallInfo(player, nearestWall.get());
            } else {
                player.sendMessage(Component.text("附近50格内没有城墙", NamedTextColor.YELLOW));
            }
            return;
        }

        UUID wallId;
        try {
            wallId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的城墙ID: " + args[2], NamedTextColor.RED));
            return;
        }

        Optional<WallData> wallOpt = siegeService.getWall(wallId);
        if (wallOpt.isPresent()) {
            showWallInfo(player, wallOpt.get());
        } else {
            player.sendMessage(Component.text("城墙不存在: " + args[2], NamedTextColor.RED));
        }
    }

    private void showWallInfo(Player player, WallData wall) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 城墙详情 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: " + wall.type().displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("ID: " + wall.id().toString(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("等级: " + wall.level(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("耐久: " + wall.currentHealth() + "/" + wall.maxHealth() + " (" + String.format("%.1f%%", wall.healthPercent()) + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text("防御力: " + String.format("%.2f", wall.defensePower()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("位置: " + wall.world() + " " + wall.blockX() + "," + wall.blockY() + "," + wall.blockZ(), NamedTextColor.GRAY));

        if (wall.isUnderSiege()) {
            player.sendMessage(Component.text("状态: 正在被攻城! (" + wall.siegeDurationSeconds() + "秒)", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("状态: 安全", NamedTextColor.GREEN));
        }

        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 攻城器械命令 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/siege create <类型> <船员> - 创建攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege list - 列出国家攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege info <ID> - 查看攻城器械详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege deploy <ID> - 部署攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege attack <ID> [城墙] - 开火攻击", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege repair <ID> [量] - 修复攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege reload <ID> [量] - 补充弹药", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege move <ID> - 移动攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege retreat <ID> - 撤退攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege disband <ID> - 解散攻城器械", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege wall create <类型> - 创建城墙", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege wall repair <ID> [量] - 修复城墙", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/siege wall info [ID] - 查看城墙信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseSiegeId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位）
        if (idStr.length() <= 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<SiegeUnit> nationSieges = siegeService.getNationSieges(nationOpt.get().id().value());
                Optional<UUID> match = nationSieges.stream()
                    .map(SiegeUnit::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Siege unit not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid siege ID format: " + idStr);
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            return (seconds / 3600) + "时" + ((seconds % 3600) / 60) + "分";
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "deploy", "attack", "repair", "reload", "move", "retreat", "disband", "wall");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(SiegeType.values())
                .map(SiegeType::key)
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("wall")) {
            return List.of("create", "repair", "info");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("wall") && args[1].equalsIgnoreCase("create")) {
            return Arrays.stream(WallType.values())
                .map(WallType::key)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
