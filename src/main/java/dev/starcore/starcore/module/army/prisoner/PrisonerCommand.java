package dev.starcore.starcore.module.army.prisoner;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerStatus;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * 俘虏命令处理器
 * /prisoner <子命令>
 */
public final class PrisonerCommand implements CommandExecutor, TabCompleter {
    private final PrisonerService prisonerService;
    private final NationService nationService;
    private final MessageService messages;

    public PrisonerCommand(PrisonerService prisonerService, NationService nationService, MessageService messages) {
        this.prisonerService = prisonerService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
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
                case "list", "ls" -> handleList(player);
                case "info", "i" -> handleInfo(player, args);
                case "release" -> handleRelease(player, args);
                case "execute" -> handleExecute(player, args);
                case "ransom" -> handleRansom(player, args);
                case "exchange" -> handleExchange(player, args);
                case "escape" -> handleEscape(player);
                case "status" -> handleStatus(player);
                case "captured" -> handleCaptured(player);
                case "help" -> showHelp(player);
                default -> {
                    player.sendMessage(Component.text("未知命令: " + subCommand, NamedTextColor.RED));
                    showHelp(player);
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (SecurityException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleList(Player player) {
        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏列表 ===", NamedTextColor.GOLD));

        if (prisoners.isEmpty()) {
            player.sendMessage(Component.text("当前没有俘虏", NamedTextColor.GRAY));
        } else {
            for (PrisonerOfWar prisoner : prisoners) {
                if (prisoner.status().isActive()) {
                    Component item = Component.text()
                        .append(Component.text("• ", NamedTextColor.YELLOW))
                        .append(Component.text(prisoner.prisonerName(), NamedTextColor.WHITE))
                        .append(Component.text(" [", NamedTextColor.GRAY))
                        .append(Component.text(prisoner.status().name(), NamedTextColor.AQUA))
                        .append(Component.text("] 赎金: ", NamedTextColor.GRAY))
                        .append(Component.text(prisoner.ransomAmount(), NamedTextColor.GOLD))
                        .build();
                    player.sendMessage(item);
                }
            }
        }
        player.sendMessage(Component.text(""));
    }

    private void handleCaptured(Player player) {
        // 获取玩家国家中被俘的成员
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> captured = prisonerService.getNationCapturedMembers(nationId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 被俘成员 ===", NamedTextColor.GOLD));

        if (captured.isEmpty()) {
            player.sendMessage(Component.text("当前没有成员被俘", NamedTextColor.GRAY));
        } else {
            for (PrisonerOfWar prisoner : captured) {
                if (prisoner.status().isActive()) {
                    Component item = Component.text()
                        .append(Component.text("• ", NamedTextColor.RED))
                        .append(Component.text(prisoner.prisonerName(), NamedTextColor.WHITE))
                        .append(Component.text(" 被 ", NamedTextColor.GRAY))
                        .append(Component.text(prisoner.captorName(), NamedTextColor.YELLOW))
                        .append(Component.text(" 俘虏", NamedTextColor.GRAY))
                        .build();
                    player.sendMessage(item);
                }
            }
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /prisoner info <玩家名>", NamedTextColor.YELLOW));
            return;
        }

        String targetName = args[1];
        // 查找俘虏
        Collection<PrisonerOfWar> allPrisoners = prisonerService.getNationPrisoners(
            nationService.nationOf(player.getUniqueId()).map(n -> n.id().value()).orElse(null)
        );

        PrisonerOfWar targetPrisoner = allPrisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(targetName))
            .findFirst()
            .orElse(null);

        if (targetPrisoner == null) {
            // 检查是否是被俘的
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                Collection<PrisonerOfWar> captured = prisonerService.getNationCapturedMembers(nationOpt.get().id().value());
                targetPrisoner = captured.stream()
                    .filter(p -> p.prisonerName().equalsIgnoreCase(targetName))
                    .findFirst()
                    .orElse(null);
            }
        }

        if (targetPrisoner == null) {
            player.sendMessage(Component.text("未找到俘虏: " + targetName, NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏详情 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("玩家: " + targetPrisoner.prisonerName(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("状态: " + targetPrisoner.status().name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("捕获者: " + targetPrisoner.captorName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("赎金: " + targetPrisoner.ransomAmount(), NamedTextColor.GOLD));
        if (targetPrisoner.notes() != null && !targetPrisoner.notes().isEmpty()) {
            player.sendMessage(Component.text("备注: " + targetPrisoner.notes(), NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleRelease(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /prisoner release <玩家名>", NamedTextColor.YELLOW));
            return;
        }

        String targetName = args[1];
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationId);

        PrisonerOfWar targetPrisoner = prisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(targetName))
            .findFirst()
            .orElse(null);

        if (targetPrisoner == null) {
            player.sendMessage(Component.text("未找到俘虏: " + targetName, NamedTextColor.RED));
            return;
        }

        if (!PermissionUtil.hasNationPermission(player, nationId, NationPermission.PRISONER_RELEASE)) {
            player.sendMessage(Component.text("你没有权限释放俘虏", NamedTextColor.RED));
            return;
        }

        prisonerService.releasePrisoner(targetPrisoner.id(), player.getUniqueId());
        player.sendMessage(Component.text("已释放俘虏: " + targetPrisoner.prisonerName(), NamedTextColor.GREEN));
    }

    private void handleExecute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /prisoner execute <玩家名>", NamedTextColor.YELLOW));
            return;
        }

        String targetName = args[1];
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationId);

        PrisonerOfWar targetPrisoner = prisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(targetName))
            .findFirst()
            .orElse(null);

        if (targetPrisoner == null) {
            player.sendMessage(Component.text("未找到俘虏: " + targetName, NamedTextColor.RED));
            return;
        }

        if (!PermissionUtil.hasNationPermission(player, nationId, NationPermission.PRISONER_EXECUTE)) {
            player.sendMessage(Component.text("你没有权限处决俘虏", NamedTextColor.RED));
            return;
        }

        prisonerService.executePrisoner(targetPrisoner.id(), player.getUniqueId());
        player.sendMessage(Component.text("已处决俘虏: " + targetPrisoner.prisonerName(), NamedTextColor.DARK_RED));
    }

    private void handleRansom(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /prisoner ransom <玩家名> <金额>", NamedTextColor.YELLOW));
            return;
        }

        String targetName = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的金额: " + args[2], NamedTextColor.RED));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationId);

        PrisonerOfWar targetPrisoner = prisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(targetName))
            .findFirst()
            .orElse(null);

        if (targetPrisoner == null) {
            player.sendMessage(Component.text("未找到俘虏: " + targetName, NamedTextColor.RED));
            return;
        }

        // 设置赎金
        prisonerService.setPrisonerNotes(targetPrisoner.id(), "赎金: " + amount);
        player.sendMessage(Component.text("已设置 " + targetPrisoner.prisonerName() + " 的赎金为: " + amount, NamedTextColor.GREEN));
        player.sendMessage(Component.text("提示: 俘虏方可以使用 /prisoner ransom accept 来支付赎金", NamedTextColor.GRAY));
    }

    private void handleExchange(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /prisoner exchange <玩家1> <玩家2>", NamedTextColor.YELLOW));
            return;
        }

        String name1 = args[1];
        String name2 = args[2];

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你需要加入一个国家才能使用俘虏系统", NamedTextColor.RED));
            return;
        }

        UUID nationId = nationOpt.get().id().value();
        Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationId);

        PrisonerOfWar prisoner1 = prisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(name1))
            .findFirst()
            .orElse(null);

        PrisonerOfWar prisoner2 = prisoners.stream()
            .filter(p -> p.prisonerName().equalsIgnoreCase(name2))
            .findFirst()
            .orElse(null);

        if (prisoner1 == null) {
            player.sendMessage(Component.text("未找到俘虏: " + name1, NamedTextColor.RED));
            return;
        }

        if (prisoner2 == null) {
            player.sendMessage(Component.text("未找到俘虏: " + name2, NamedTextColor.RED));
            return;
        }

        if (!PermissionUtil.hasNationPermission(player, nationId, NationPermission.PRISONER_RELEASE)) {
            player.sendMessage(Component.text("你没有权限交换俘虏", NamedTextColor.RED));
            return;
        }

        prisonerService.exchangePrisoners(prisoner1.id(), prisoner2.id(), player.getUniqueId());
        player.sendMessage(Component.text("已交换俘虏: " + name1 + " <-> " + name2, NamedTextColor.GREEN));
    }

    private void handleEscape(Player player) {
        // 检查玩家是否为俘虏
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());
        if (prisonerOpt.isEmpty()) {
            player.sendMessage(Component.text(
                "你当前没有被俘虏",
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否允许逃跑
        if (prisonerService.getConfig().escapeChancePerHour() <= 0) {
            player.sendMessage(Component.text(
                "逃跑功能已被禁用",
                NamedTextColor.RED
            ));
            return;
        }

        PrisonerOfWar prisoner = prisonerOpt.get();
        if (prisoner.status() == PrisonerStatus.ESCAPED) {
            // 逃跑中 - 完成逃跑
            prisonerService.completeEscape(prisoner.id());
            player.sendMessage(Component.text(
                "逃跑成功！你已获得自由",
                NamedTextColor.GREEN
            ));
        } else {
            // 开始逃跑
            prisonerService.startEscape(prisoner.id());
            player.sendMessage(Component.text(
                "你开始尝试逃跑...",
                NamedTextColor.YELLOW
            ));
            player.sendMessage(Component.text(
                "逃跑成功率: " + prisonerService.getConfig().escapeChancePerHour() + "%/小时",
                NamedTextColor.GRAY
            ));
        }
    }

    private void handleStatus(Player player) {
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏状态 ===", NamedTextColor.GOLD));

        if (prisonerOpt.isEmpty()) {
            player.sendMessage(Component.text(
                "你当前没有被俘虏",
                NamedTextColor.GREEN
            ));
        } else {
            PrisonerOfWar prisoner = prisonerOpt.get();
            player.sendMessage(Component.text(
                "状态: " + prisoner.status().name(),
                NamedTextColor.YELLOW
            ));
            player.sendMessage(Component.text(
                "俘虏方: " + (prisoner.captorNationId() != null ? "国家" : "未知"),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                "捕获者: " + prisoner.captorName(),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                "赎金: " + prisoner.ransomAmount(),
                NamedTextColor.GOLD
            ));
        }

        // 显示统计
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isPresent()) {
            UUID nationId = nationOpt.get().id().value();
            int prisoners = prisonerService.getNationPrisonerCount(nationId);
            player.sendMessage(Component.text(
                "国家俘虏数: " + prisoners,
                NamedTextColor.AQUA
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏系统帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/prisoner list - 查看本国的俘虏列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner captured - 查看本国被俘的成员", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner info <玩家> - 查看俘虏详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner release <玩家> - 释放俘虏 (需要权限)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner execute <玩家> - 处决俘虏 (需要权限)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner ransom <玩家> <金额> - 设置赎金", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner exchange <玩家1> <玩家2> - 交换俘虏", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner status - 查看自己的俘虏状态", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner escape - 尝试逃跑 (如被俘)", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("list", "captured", "info", "release", "execute", "ransom", "exchange", "escape", "status", "help");
        }

        // 提供玩家名补全
        if (args.length >= 2) {
            String subCmd = args[0].toLowerCase();
            if (List.of("info", "release", "execute", "ransom").contains(subCmd)) {
                Optional<Nation> nationOpt = nationService.nationOf(((Player) sender).getUniqueId());
                if (nationOpt.isPresent()) {
                    Collection<PrisonerOfWar> prisoners = prisonerService.getNationPrisoners(nationOpt.get().id().value());
                    return prisoners.stream()
                        .map(PrisonerOfWar::prisonerName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }
}
