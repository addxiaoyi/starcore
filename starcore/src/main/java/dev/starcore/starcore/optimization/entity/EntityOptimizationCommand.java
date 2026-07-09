package dev.starcore.starcore.optimization.entity;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 实体优化命令
 */
public final class EntityOptimizationCommand implements CommandExecutor, TabCompleter {
    private final EntityOptimizationService service;

    public EntityOptimizationCommand(EntityOptimizationService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats" -> {
                showStats(sender);
                return true;
            }
            case "info" -> {
                showInfo(sender);
                return true;
            }
            case "help" -> {
                sendHelp(sender);
                return true;
            }
            default -> {
                sender.sendMessage("§c未知的子命令: " + subCommand);
                sender.sendMessage("§7使用 /entityopt help 查看帮助");
                return true;
            }
        }
    }

    /**
     * 显示统计信息
     */
    private void showStats(CommandSender sender) {
        EntityOptimizationService.EntityOptimizationStats stats = service.getStats();

        sender.sendMessage("§6========== 实体优化统计 ==========");
        sender.sendMessage("§e总清理: §f" + stats.totalCleared());
        sender.sendMessage("§e  - 物品: §f" + stats.itemsCleared());
        sender.sendMessage("§e  - 生物: §f" + stats.mobsCleared());
        // 分隔
        sender.sendMessage("§a当前实体:");
        sender.sendMessage("§e  - 总计: §f" + stats.currentEntities());
        sender.sendMessage("§e  - 生物: §f" + stats.currentMobs());
        sender.sendMessage("§e  - 物品: §f" + stats.currentItems());
        // 分隔
        sender.sendMessage("§b追踪的持久化生物: §f" + stats.trackedPersistentMobs());
        sender.sendMessage("§6================================");
    }

    /**
     * 显示信息
     */
    private void showInfo(CommandSender sender) {
        sender.sendMessage("§6========== 实体优化信息 ==========");
        sender.sendMessage("§e基于 §flet-me-despawn §e的理念");
        sender.sendMessage("§e增强版实现，提供全面的实体优化");
        // 分隔
        sender.sendMessage("§a核心功能:");
        sender.sendMessage("§7  - 持久化生物可自然消失");
        sender.sendMessage("§7  - 拾取的物品会掉落");
        sender.sendMessage("§7  - 自动清理过期掉落物");
        sender.sendMessage("§7  - Chunk实体密度限制");
        sender.sendMessage("§7  - 箭矢自动清理");
        // 分隔
        sender.sendMessage("§e作者: §fFrikinjay (let-me-despawn)");
        sender.sendMessage("§e集成: §fStarCore");
        sender.sendMessage("§6================================");
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== 实体优化命令 ==========");
        sender.sendMessage("§e/entityopt stats §7- 查看统计信息");
        sender.sendMessage("§e/entityopt info §7- 查看系统信息");
        sender.sendMessage("§e/entityopt help §7- 显示此帮助");
        sender.sendMessage("§6================================");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(
                Arrays.asList("stats", "info", "help"),
                args[0]
            );
        }

        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }
}
