package dev.starcore.starcore.module.army.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.battle.BattleCalculator;
import dev.starcore.starcore.module.army.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.util.PermissionUtil;
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
 * 军队命令处理器
 * /sc army <子命令>
 *
 * 中文别名:
 *   create/c/创建 → 创建军队
 *   list/ls/列表 → 列出军队
 *   info/i/信息 → 查看军队信息
 *   move/m/移动 → 移动军队
 *   attack/a/攻击 → 攻击敌军
 *   supply/s/补给 → 补给军队
 *   disband/d/解散 → 解散军队
 *   predict/p/预测 → 预测战斗结果
 */
public final class ArmyCommand implements CommandExecutor, TabCompleter {
    private final ArmyService armyService;
    private final NationService nationService;
    private final MessageService messages;

    public ArmyCommand(ArmyService armyService, NationService nationService, MessageService messages) {
        this.armyService = armyService;
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

        String subCommand = normalizeSubCommand(args[0].toLowerCase());

        try {
            switch (subCommand) {
                case "create" -> handleCreate(player, args);
                case "list" -> handleList(player, args);
                case "info" -> handleInfo(player, args);
                case "move" -> handleMove(player, args);
                case "attack" -> handleAttack(player, args);
                case "supply" -> handleSupply(player, args);
                case "disband" -> handleDisband(player, args);
                case "predict" -> handlePredict(player, args);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            // 创建军队
            case "create", "c", "创建", "建", "新建" -> "create";
            // 列出军队
            case "list", "ls", "列表", "列", "查看", "查" -> "list";
            // 查看信息
            case "info", "i", "信息", "详", "详情" -> "info";
            // 移动军队
            case "move", "m", "移动", "移", "调动" -> "move";
            // 攻击敌军
            case "attack", "a", "攻击", "攻", "打", "进攻" -> "attack";
            // 补给军队
            case "supply", "s", "补给", "供", "供应", "补充" -> "supply";
            // 解散军队
            case "disband", "d", "解散", "销", "撤销", "取消" -> "disband";
            // 预测战斗
            case "predict", "p", "预测", "预", "预判", "模拟" -> "predict";
            default -> input;
        };
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("army.create.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("army.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 审计 A-106: 使用 NationPermissionChecker 检查 ARMY_CREATE 权限
        if (!PermissionUtil.hasNationPermission(player, nation.id().value(), NationPermission.ARMY_CREATE)) {
            player.sendMessage(Component.text(
                "你没有权限创建军队，需要更高的职位或创始人权限",
                NamedTextColor.RED
            ));
            return;
        }

        // 解析兵种
        ArmyType type;
        try {
            type = ArmyType.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("army.invalid-type", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析数量
        int soldiers;
        try {
            soldiers = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("army.invalid-number", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 创建军队
        ArmyUnit army = armyService.createArmy(nation.id().value(), type, soldiers, player.getLocation());

        player.sendMessage(Component.text(
            messages.format("army.created", type.key(), soldiers, army.id().toString().substring(0, 8)),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("army.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        List<ArmyUnit> armies = armyService.getNationArmies(nation.id().value());

        if (armies.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("army.no-armies"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("army.list.header"), NamedTextColor.GOLD));
        for (ArmyUnit army : armies) {
            String shortId = army.id().toString().substring(0, 8);
            player.sendMessage(Component.text(
                messages.format("army.list.entry", shortId, army.type().key(), army.soldiers(), army.state().key()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("army.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID armyId = parseArmyId(player, args[1]);
        Optional<ArmyUnit> armyOpt = armyService.getArmy(armyId);

        if (armyOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("army.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        ArmyUnit army = armyOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("army.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("army.info.id", army.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.type", army.type().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.soldiers", army.soldiers()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.health", String.format("%.1f%%", army.health())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.morale", String.format("%.1f%%", army.morale())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.supply", army.supply()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("army.info.state", army.state().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleMove(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                messages.format("army.move.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID armyId = parseArmyId(player, args[1]);
        int x = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        Location destination = new Location(player.getWorld(), x, player.getLocation().getY(), z);

        armyService.moveArmy(armyId, destination);

        player.sendMessage(Component.text(
            messages.format("army.moved", args[1], x, z),
            NamedTextColor.GREEN
        ));
    }

    private void handleAttack(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("army.attack.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID attackerId = parseArmyId(player, args[1]);
        UUID defenderId = parseArmyId(player, args[2]);

        BattleResult result = armyService.attack(attackerId, defenderId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("army.battle.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(result.formatReport(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleSupply(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("army.supply.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 审计 A-107: 校验权限，仅领导人可补给
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty() || !nationOpt.get().founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "只有国家领导人可以补给军队",
                NamedTextColor.RED
            ));
            return;
        }

        UUID armyId = parseArmyId(player, args[1]);
        armyService.resupplyArmy(armyId);

        player.sendMessage(Component.text(
            messages.format("army.supplied", args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handleDisband(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("army.disband.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 审计 A-108: 校验权限，仅领导人可解散
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty() || !nationOpt.get().founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                "只有国家领导人可以解散军队",
                NamedTextColor.RED
            ));
            return;
        }

        UUID armyId = parseArmyId(player, args[1]);
        armyService.disbandArmy(armyId);

        player.sendMessage(Component.text(
            messages.format("army.disbanded", args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handlePredict(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("army.predict.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID attackerId = parseArmyId(player, args[1]);
        UUID defenderId = parseArmyId(player, args[2]);

        BattleCalculator.BattlePrediction prediction = armyService.predictBattle(attackerId, defenderId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("army.predict.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(prediction.formatPrediction(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("army.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text("§e/sc army create <兵种> <数量> §7- 创建军队 (c/创建)"));
        player.sendMessage(Component.text("§e/sc army list §7- 列出军队 (ls/列表)"));
        player.sendMessage(Component.text("§e/sc army info <ID> §7- 查看军队 (i/信息)"));
        player.sendMessage(Component.text("§e/sc army move <ID> <X> <Z> §7- 移动军队 (m/移动)"));
        player.sendMessage(Component.text("§e/sc army attack <ID> <目标ID> §7- 攻击敌军 (a/攻击)"));
        player.sendMessage(Component.text("§e/sc army supply <ID> §7- 补给军队 (s/补给)"));
        player.sendMessage(Component.text("§e/sc army disband <ID> §7- 解散军队 (d/解散)"));
        player.sendMessage(Component.text("§e/sc army predict <ID> <目标ID> §7- 预测战斗 (p/预测)"));
        player.sendMessage(Component.text(""));
    }

    private UUID parseArmyId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位），从玩家国家的军队中查找
        if (idStr.length() == 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<ArmyUnit> nationArmies = armyService.getNationArmies(nationOpt.get().id().value());
                Optional<UUID> match = nationArmies.stream()
                    .map(ArmyUnit::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Army not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid army ID format: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // 第一级补全：所有子命令（中英文）
        if (args.length == 1) {
            return List.of(
                "create", "c", "创建",
                "list", "ls", "列表",
                "info", "i", "信息",
                "move", "m", "移动",
                "attack", "a", "攻击",
                "supply", "s", "补给",
                "disband", "d", "解散",
                "predict", "p", "预测"
            ).stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        // 第二级补全：根据子命令提供参数补全
        String normalized = normalizeSubCommand(args[0]);

        if (args.length == 2) {
            switch (normalized) {
                case "create" -> {
                    // 补全兵种类型
                    return Arrays.stream(ArmyType.values())
                        .map(ArmyType::key)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "info", "move", "attack", "supply", "disband", "predict" -> {
                    // 补全军队ID
                    if (sender instanceof Player player) {
                        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
                        if (nationOpt.isPresent()) {
                            List<ArmyUnit> armies = armyService.getNationArmies(nationOpt.get().id().value());
                            return armies.stream()
                                .map(a -> a.id().toString().substring(0, 8))
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                        }
                    }
                }
            }
        }

        if (args.length == 3) {
            switch (normalized) {
                case "create" -> {
                    // 补全士兵数量建议
                    return List.of("10", "50", "100", "200", "500", "1000");
                }
                case "move" -> {
                    // 补全 X 坐标（玩家当前位置参考）
                    if (sender instanceof Player player) {
                        return List.of(String.valueOf(player.getLocation().getBlockX()));
                    }
                }
            }
        }

        if (args.length == 4 && normalized.equals("move")) {
            // 补全 Z 坐标
            if (sender instanceof Player player) {
                return List.of(String.valueOf(player.getLocation().getBlockZ()));
            }
        }

        if (args.length == 3 && (normalized.equals("attack") || normalized.equals("predict"))) {
            // 补全第二个军队ID（用于攻击和预测）
            if (sender instanceof Player player) {
                Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
                if (nationOpt.isPresent()) {
                    List<ArmyUnit> armies = armyService.getNationArmies(nationOpt.get().id().value());
                    return armies.stream()
                        .map(a -> a.id().toString().substring(0, 8))
                        .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }
}
