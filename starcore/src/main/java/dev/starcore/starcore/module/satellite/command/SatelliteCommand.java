package dev.starcore.starcore.module.satellite.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.satellite.SatelliteRelation;
import dev.starcore.starcore.module.satellite.SatelliteService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 卫星国命令处理器
 * /satellite <子命令>
 */
public final class SatelliteCommand implements CommandExecutor, TabCompleter {

    private final SatelliteService satelliteService;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final MessageService messages;

    public SatelliteCommand(
        SatelliteService satelliteService,
        NationService nationService,
        TreasuryService treasuryService,
        MessageService messages
    ) {
        this.satelliteService = satelliteService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create", "establish" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleCreate(player, args);
                }
                case "dissolve", "break" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleDissolve(player, args);
                }
                case "independence", "declare" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleIndependence(player, args);
                }
                case "release" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleRelease(player, args);
                }
                case "tribute", "tax" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleTribute(player, args);
                }
                case "collect" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleCollect(player, args);
                }
                case "info", "i" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleInfo(player, args);
                }
                case "list", "ls" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
                        return true;
                    }
                    handleList(player, args);
                }
                case "status" -> handleStatus(sender);
                case "help" -> showHelp(sender);
                default -> sender.sendMessage(Component.text("未知命令，使用 /satellite help 查看帮助", NamedTextColor.RED));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("参数错误: " + e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            sender.sendMessage(Component.text("执行失败: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /satellite create <卫星国名> <关系类型>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("关系类型: dominion(自治领), vassal(附庸国), protectorate(保护国), colony(殖民地)", NamedTextColor.GRAY));
            return;
        }

        String satelliteName = args[1];
        String relationType = args[2].toLowerCase();

        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家才能建立卫星关系", NamedTextColor.RED));
            return;
        }

        Nation suzerainNation = playerNation.get();

        // 检查是否是国家领袖
        if (!suzerainNation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家领袖才能建立卫星关系", NamedTextColor.RED));
            return;
        }

        // 查找目标国家
        Optional<Nation> targetNation = nationService.nationByName(satelliteName);
        if (targetNation.isEmpty()) {
            player.sendMessage(Component.text("未找到国家: " + satelliteName, NamedTextColor.RED));
            return;
        }

        Nation satelliteNation = targetNation.get();

        // 解析关系类型
        SatelliteRelation relation = parseRelation(relationType);
        if (relation == SatelliteRelation.NONE) {
            player.sendMessage(Component.text("无效的关系类型: " + relationType, NamedTextColor.RED));
            player.sendMessage(Component.text("关系类型: dominion(自治领), vassal(附庸国), protectorate(保护国), colony(殖民地)", NamedTextColor.GRAY));
            return;
        }

        // 建立关系
        SatelliteService.SatelliteResult result = satelliteService.establishRelation(
            suzerainNation.id(),
            satelliteNation.id(),
            relation
        );

        if (result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleDissolve(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /satellite dissolve <卫星国名>", NamedTextColor.YELLOW));
            return;
        }

        String satelliteName = args[1];

        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation suzerainNation = playerNation.get();

        // 查找目标国家
        Optional<Nation> targetNation = nationService.nationByName(satelliteName);
        if (targetNation.isEmpty()) {
            player.sendMessage(Component.text("未找到国家: " + satelliteName, NamedTextColor.RED));
            return;
        }

        Nation satelliteNation = targetNation.get();

        // 检查是否是宗主国
        if (!satelliteService.hasRelation(suzerainNation.id(), satelliteNation.id())) {
            player.sendMessage(Component.text("该国不是你的卫星国", NamedTextColor.RED));
            return;
        }

        // 解除关系
        SatelliteService.SatelliteResult result = satelliteService.dissolveRelation(
            suzerainNation.id(),
            satelliteNation.id(),
            player.getUniqueId()
        );

        if (result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleIndependence(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation satelliteNation = playerNation.get();

        // 宣布独立
        SatelliteService.SatelliteResult result = satelliteService.declareIndependence(
            satelliteNation.id(),
            player.getUniqueId()
        );

        if (result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleRelease(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /satellite release <卫星国名>", NamedTextColor.YELLOW));
            return;
        }

        String satelliteName = args[1];

        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation suzerainNation = playerNation.get();

        // 查找目标国家
        Optional<Nation> targetNation = nationService.nationByName(satelliteName);
        if (targetNation.isEmpty()) {
            player.sendMessage(Component.text("未找到国家: " + satelliteName, NamedTextColor.RED));
            return;
        }

        Nation satelliteNation = targetNation.get();

        // 释放卫星国
        SatelliteService.SatelliteResult result = satelliteService.releaseSatellite(
            suzerainNation.id(),
            satelliteNation.id(),
            player.getUniqueId()
        );

        if (result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleTribute(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation nation = playerNation.get();

        // 检查是否是卫星国
        Optional<NationId> suzerainOpt = satelliteService.suzerainOf(nation.id());
        if (suzerainOpt.isEmpty()) {
            player.sendMessage(Component.text("你的国家不是任何国家的卫星", NamedTextColor.YELLOW));
            return;
        }

        // 设置税率
        if (args.length < 2) {
            // 显示当前贡金信息
            double currentRate = satelliteService.getTributeRate(nation.id());
            BigDecimal tributeAmount = satelliteService.getTributeAmount(nation.id());
            player.sendMessage(Component.text("当前贡金税率: " + String.format("%.1f%%", currentRate * 100), NamedTextColor.GRAY));
            player.sendMessage(Component.text("本次贡金金额: " + tributeAmount.toPlainString(), NamedTextColor.GRAY));
        } else {
            try {
                double newRate = Double.parseDouble(args[1]) / 100.0; // 转换为小数
                if (satelliteService.setTributeRate(nation.id(), newRate)) {
                    player.sendMessage(Component.text("贡金税率已设置为: " + String.format("%.1f%%", newRate * 100), NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("设置失败，税率超出允许范围", NamedTextColor.RED));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的税率值", NamedTextColor.RED));
            }
        }
    }

    private void handleCollect(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation suzerainNation = playerNation.get();

        // 检查是否有卫星国
        var satellites = satelliteService.satellitesOf(suzerainNation.id());
        if (satellites.isEmpty()) {
            player.sendMessage(Component.text("你的国家没有卫星国", NamedTextColor.YELLOW));
            return;
        }

        // 收取贡金
        BigDecimal collected = satelliteService.collectTributes(suzerainNation.id());

        if (collected.signum() > 0) {
            player.sendMessage(Component.text("已收取贡金: " + collected.toPlainString(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("没有可收取的贡金", NamedTextColor.YELLOW));
        }
    }

    private void handleInfo(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation nation = playerNation.get();

        player.sendMessage(Component.text("========== 卫星国信息 ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.WHITE));

        // 检查宗主国
        Optional<NationId> suzerainOpt = satelliteService.suzerainOf(nation.id());
        if (suzerainOpt.isPresent()) {
            Optional<Nation> suzerainNation = nationService.nationById(suzerainOpt.get());
            player.sendMessage(Component.text("宗主国: " + suzerainNation.map(Nation::name).orElse("未知"), NamedTextColor.RED));
            SatelliteService.SatelliteDefenseStatus defense = satelliteService.getDefenseStatus(nation.id());
            player.sendMessage(Component.text("防御状态: " + (defense.isProtected() ? "受保护" : "未受保护"), NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("宗主国: 无", NamedTextColor.GRAY));
        }

        // 检查卫星国
        var satellites = satelliteService.satellitesOf(nation.id());
        if (!satellites.isEmpty()) {
            player.sendMessage(Component.text("卫星国 (" + satellites.size() + "):", NamedTextColor.GREEN));
            for (NationId satId : satellites) {
                Optional<Nation> satNation = nationService.nationById(satId);
                String name = satNation.map(Nation::name).orElse("未知");
                SatelliteRelation rel = satelliteService.getRelation(nation.id(), satId);
                player.sendMessage(Component.text("  - " + name + " [" + rel.displayName() + "]", NamedTextColor.GRAY));
            }
        } else {
            player.sendMessage(Component.text("卫星国: 无", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void handleList(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        Nation nation = playerNation.get();

        // 显示作为宗主国的卫星国列表
        var satellites = satelliteService.satellitesOf(nation.id());
        player.sendMessage(Component.text("========== 宗主国列表 ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.WHITE));

        if (satellites.isEmpty()) {
            player.sendMessage(Component.text("没有卫星国", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("卫星国 (" + satellites.size() + "):", NamedTextColor.GREEN));
            for (NationId satId : satellites) {
                Optional<Nation> satNation = nationService.nationById(satId);
                String name = satNation.map(Nation::name).orElse("未知");
                SatelliteRelation rel = satelliteService.getRelation(nation.id(), satId);
                player.sendMessage(Component.text("  - " + name + " [" + rel.displayName() + "]", NamedTextColor.GRAY));
            }
        }
        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("========== 卫星国系统状态 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("宗主国总数: " + satelliteService.getTotalSuzerains(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("卫星国总数: " + satelliteService.getTotalSatellites(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(satelliteService.summary(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("========== 卫星国系统帮助 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/satellite create <卫星国> <类型> - 建立卫星关系", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite dissolve <卫星国> - 解除卫星关系", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite release <卫星国> - 释放卫星国", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite independence - 宣布独立（卫星国使用）", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite tribute [税率] - 设置/查看贡金税率", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite collect - 收取卫星国贡金", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite info - 查看卫星国信息", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite list - 列出卫星国", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/satellite status - 系统状态", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("关系类型: dominion, vassal, protectorate, colony", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("=========================================", NamedTextColor.GOLD));
    }

    private SatelliteRelation parseRelation(String type) {
        return switch (type.toLowerCase()) {
            case "dominion", "dom" -> SatelliteRelation.DOMINION;
            case "vassal", "vas" -> SatelliteRelation.VASSAL;
            case "protectorate", "prot" -> SatelliteRelation.PROTECTORATE;
            case "colony", "col" -> SatelliteRelation.COLONY;
            default -> SatelliteRelation.NONE;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "create", "dissolve", "release", "independence",
                "tribute", "collect", "info", "list", "status", "help"
            ));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("dissolve") || args[0].equalsIgnoreCase("release")) {
                // 列出所有国家名
                return nationService.nations().stream()
                    .map(Nation::name)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("tribute")) {
                // 税率建议值
                completions.addAll(Arrays.asList("5", "10", "15", "20", "25"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                // 关系类型
                completions.addAll(Arrays.asList("dominion", "vassal", "protectorate", "colony"));
            }
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}
