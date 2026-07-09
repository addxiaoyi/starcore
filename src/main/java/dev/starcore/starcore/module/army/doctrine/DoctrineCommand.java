package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import dev.starcore.starcore.module.army.doctrine.model.NationDoctrine;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 军事学说命令处理器
 * /doctrine <子命令>
 */
public final class DoctrineCommand implements CommandExecutor, TabCompleter {
    private final DoctrineService doctrineService;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final MessageService messages;

    public DoctrineCommand(
        DoctrineService doctrineService,
        NationService nationService,
        TreasuryService treasuryService,
        MessageService messages
    ) {
        this.doctrineService = doctrineService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
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
                case "set", "s" -> handleSet(player, args);
                case "list", "ls", "l" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "clear", "c" -> handleClear(player, args);
                case "preview", "p" -> handlePreview(player, args);
                case "stats" -> handleStats(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                "Usage: /doctrine set <学说类型>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("doctrine.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        // 检查是否为领导人或有权限的官员
        if (!nation.founderId().equals(player.getUniqueId()) && !hasPermission(player, nation)) {
            player.sendMessage(Component.text(
                messages.format("doctrine.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析学说类型
        DoctrineType doctrine;
        try {
            doctrine = DoctrineType.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("doctrine.invalid-type", args[1]),
                NamedTextColor.RED
            ));
            showAvailableDoctrines(player);
            return;
        }

        // 检查冷却
        if (!doctrineService.canSwitchDoctrine(nationId) && doctrine != DoctrineType.NONE) {
            long remaining = doctrineService.getDoctrineSwitchCooldownRemaining(nationId);
            String timeStr = formatDuration(remaining);
            player.sendMessage(Component.text(
                messages.format("doctrine.cooldown", timeStr),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查费用
        double cost = doctrineService.getSwitchCost();
        if (cost > 0 && doctrine != DoctrineType.NONE) {
            BigDecimal balance = treasuryService.balance(nation.id());
            if (balance.doubleValue() < cost) {
                player.sendMessage(Component.text(
                    messages.format("doctrine.insufficient-funds", String.format("%.2f", cost)),
                    NamedTextColor.RED
                ));
                return;
            }
            // 扣除费用
            treasuryService.withdraw(nation.id(), BigDecimal.valueOf(cost));
        }

        // 设置学说
        boolean success = doctrineService.setDoctrine(nationId, doctrine, player.getName());

        if (success) {
            if (doctrine == DoctrineType.NONE) {
                player.sendMessage(Component.text(
                    messages.format("doctrine.cleared"),
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(
                    messages.format("doctrine.set", doctrine.displayName()),
                    NamedTextColor.GREEN
                ));
                showDoctrineBonuses(player, doctrine);
            }
        } else {
            player.sendMessage(Component.text(
                messages.format("doctrine.set-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleList(Player player, String[] args) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 军事学说列表 ===", NamedTextColor.GOLD));

        for (DoctrineType type : DoctrineType.values()) {
            if (type == DoctrineType.NONE) continue;

            boolean isSelected = false;
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                DoctrineType current = doctrineService.getDoctrineType(nationOpt.get().id().value());
                isSelected = current == type;
            }

            String prefix = isSelected ? ">" : " ";
            NamedTextColor color = isSelected ? NamedTextColor.GREEN : NamedTextColor.GRAY;

            player.sendMessage(Component.text(
                prefix + type.displayName() + " (" + type.key() + ")",
                color
            ));
            player.sendMessage(Component.text(
                "   " + type.description(),
                NamedTextColor.DARK_GRAY
            ));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("使用 /doctrine info <学说> 查看详细加成", NamedTextColor.YELLOW));
    }

    private void handleInfo(Player player, String[] args) {
        DoctrineType type;

        if (args.length < 2) {
            // 显示当前学说信息
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("doctrine.not-in-nation"),
                    NamedTextColor.RED
                ));
                return;
            }

            type = doctrineService.getDoctrineType(nationOpt.get().id().value());
            if (type == DoctrineType.NONE) {
                player.sendMessage(Component.text(
                    messages.format("doctrine.no-doctrine"),
                    NamedTextColor.YELLOW
                ));
                showAvailableDoctrines(player);
                return;
            }
        } else {
            try {
                type = DoctrineType.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                    messages.format("doctrine.invalid-type", args[1]),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        showDoctrineDetails(player, type);
    }

    private void handleClear(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("doctrine.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        if (!nation.founderId().equals(player.getUniqueId()) && !hasPermission(player, nation)) {
            player.sendMessage(Component.text(
                messages.format("doctrine.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = doctrineService.clearDoctrine(nationId, player.getName());

        if (success) {
            player.sendMessage(Component.text(
                messages.format("doctrine.cleared"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("doctrine.clear-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handlePreview(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                "Usage: /doctrine preview <学说类型>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            DoctrineType type = DoctrineType.fromString(args[1]);
            showDoctrineDetails(player, type);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("doctrine.invalid-type", args[1]),
                NamedTextColor.RED
            ));
        }
    }

    private void handleStats(Player player, String[] args) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 学说使用统计 ===", NamedTextColor.GOLD));

        for (DoctrineType type : DoctrineType.values()) {
            if (type == DoctrineType.NONE) continue;
            int count = doctrineService.getNationCountByDoctrine(type);
            player.sendMessage(Component.text(
                type.displayName() + ": " + count + " 国家",
                NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 军事学说系统 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/doctrine set <学说> - 设置国家学说", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/doctrine list - 查看所有学说", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/doctrine info [学说] - 查看学说详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/doctrine clear - 清除当前学说", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/doctrine preview <学说> - 预览学说效果", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/doctrine stats - 查看使用统计", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showDoctrineDetails(Player player, DoctrineType type) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + type.displayName() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(type.description(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        if (type.attackBonus() != 0) {
            player.sendMessage(Component.text(
                "攻击加成: " + formatBonus(type.attackBonus()),
                type.attackBonus() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        if (type.defenseBonus() != 0) {
            player.sendMessage(Component.text(
                "防御加成: " + formatBonus(type.defenseBonus()),
                type.defenseBonus() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        if (type.mobilityBonus() != 0) {
            player.sendMessage(Component.text(
                "机动性加成: " + formatBonus(type.mobilityBonus()),
                type.mobilityBonus() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        if (type.ambushBonus() != 0) {
            player.sendMessage(Component.text(
                "伏击加成: " + formatBonus(type.ambushBonus()),
                type.ambushBonus() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        if (type.costEfficiency() != 0) {
            String sign = type.costEfficiency() > 0 ? "+" : "";
            player.sendMessage(Component.text(
                "成本效率: " + sign + formatPercent(type.costEfficiency() * 100),
                type.costEfficiency() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        if (type.moraleConsumptionMultiplier() != 1.0) {
            double diff = (type.moraleConsumptionMultiplier() - 1.0) * 100;
            player.sendMessage(Component.text(
                "士气消耗: " + formatPercent(diff),
                NamedTextColor.YELLOW
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void showDoctrineBonuses(Player player, DoctrineType type) {
        player.sendMessage(Component.text("生效加成:", NamedTextColor.DARK_GREEN));
        showDoctrineDetails(player, type);
    }

    private void showAvailableDoctrines(Player player) {
        player.sendMessage(Component.text(
            "可用学说: " + String.join(", ", DoctrineType.getAvailableKeys()),
            NamedTextColor.GRAY
        ));
    }

    private boolean hasPermission(Player player, Nation nation) {
        // 检查玩家是否有学说设置权限（创始人或拥有 admin 权限的官员）
        return nation.hasPermission(player.getUniqueId(), "admin")
            || nation.hasPermission(player.getUniqueId(), "doctrine")
            || nation.hasPermission(player.getUniqueId(), "military");
    }

    private String formatBonus(double bonus) {
        String sign = bonus > 0 ? "+" : "";
        return sign + formatPercent(bonus * 100);
    }

    private String formatPercent(double value) {
        return String.format("%+.0f%%", value);
    }

    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("set", "list", "info", "clear", "preview", "stats");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") ||
                args[0].equalsIgnoreCase("info") ||
                args[0].equalsIgnoreCase("preview")) {
                return Arrays.stream(DoctrineType.values())
                    .map(DoctrineType::key)
                    .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}