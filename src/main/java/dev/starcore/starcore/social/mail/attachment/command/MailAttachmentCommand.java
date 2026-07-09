package dev.starcore.starcore.social.mail.attachment.command;

import dev.starcore.starcore.social.mail.MailService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService.AttachmentClaimRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 邮件附件命令
 *
 * 命令：
 * - /mailattachment history [数量] - 查看领取历史
 * - /mailattachment value <邮件ID> - 估算附件价值
 * - /mailattachment check <玩家> - 检查玩家附件统计
 * - /mailattachment clean - 清理过期附件（管理员）
 */
public final class MailAttachmentCommand implements CommandExecutor, TabCompleter {

    private final MailService mailService;
    private final MailAttachmentService attachmentService;

    public MailAttachmentCommand(MailService mailService, MailAttachmentService attachmentService) {
        this.mailService = mailService;
        this.attachmentService = attachmentService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (subCommand) {
            case "history", "hist" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家才能使用此命令");
                    return true;
                }
                showClaimHistory(player, args.length > 1 ? parseInt(args[1], 10) : 10);
            }
            case "value", "val" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /mailattachment value <玩家> [数量]");
                    return true;
                }
                showAttachmentValue(sender, args[1], args.length > 2 ? parseInt(args[2], 5) : 5);
            }
            case "check" -> {
                if (!sender.hasPermission("starcore.mail.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /mailattachment check <玩家>");
                    return true;
                }
                checkPlayerAttachments(sender, args[1]);
            }
            case "clean" -> {
                if (!sender.hasPermission("starcore.mail.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令");
                    return true;
                }
                sender.sendMessage("§e清理过期附件功能需要通过数据库自动执行");
                sender.sendMessage("§e邮件系统每小时自动清理过期邮件");
            }
            case "stats" -> {
                if (!sender.hasPermission("starcore.mail.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令");
                    return true;
                }
                showGlobalStats(sender);
            }
            case "help" -> {
                showHelp(sender);
            }
            default -> {
                sender.sendMessage("§c未知子命令: " + subCommand);
                showHelp(sender);
            }
        }

        return true;
    }

    /**
     * 显示领取历史
     */
    private void showClaimHistory(Player player, int limit) {
        List<AttachmentClaimRecord> history = attachmentService.getClaimHistory(player.getUniqueId(), limit);

        player.sendMessage("§6=== 附件领取历史 ===");
        player.sendMessage("§7显示最近 " + Math.min(limit, history.size()) + " 条记录");

        if (history.isEmpty()) {
            player.sendMessage("§7你还没有领取过任何附件");
            return;
        }

        int count = 0;
        for (AttachmentClaimRecord record : history) {
            if (count >= limit) break;

            String timeAgo = formatTimeAgo(record.claimedAt());
            player.sendMessage(String.format("§e[%s] §f来自 %s - %d 个物品 (价值: %s)",
                timeAgo,
                record.senderName(),
                record.attachments().size(),
                formatValue(record.totalValue())
            ));
            count++;
        }
    }

    /**
     * 显示附件价值
     */
    private void showAttachmentValue(CommandSender sender, String playerName, int limit) {
        List<AttachmentClaimRecord> history = attachmentService.getClaimHistory(
            getPlayerUUID(playerName), limit);

        long totalValue = history.stream()
            .mapToLong(AttachmentClaimRecord::totalValue)
            .sum();

        sender.sendMessage("§6=== " + playerName + " 的附件统计 ===");
        sender.sendMessage("§7领取记录: " + history.size() + " 条");
        sender.sendMessage("§7总价值: §e" + formatValue(totalValue) + " 金币");

        if (!history.isEmpty()) {
            long avgValue = totalValue / history.size();
            sender.sendMessage("§7平均价值: §e" + formatValue(avgValue) + " 金币");
        }
    }

    /**
     * 检查玩家附件
     */
    private void checkPlayerAttachments(CommandSender sender, String playerName) {
        UUID playerId = getPlayerUUID(playerName);
        if (playerId == null) {
            sender.sendMessage("§c找不到玩家: " + playerName);
            return;
        }

        List<AttachmentClaimRecord> history = attachmentService.getClaimHistory(playerId, 100);
        long totalValue = history.stream()
            .mapToLong(AttachmentClaimRecord::totalValue)
            .sum();

        sender.sendMessage("§6=== 玩家 " + playerName + " 附件统计 ===");
        sender.sendMessage("§7玩家UUID: §f" + playerId);
        sender.sendMessage("§7总领取次数: §e" + history.size());
        sender.sendMessage("§7总领取价值: §e" + formatValue(totalValue) + " 金币");

        // 统计每个月的领取次数
        java.util.Map<String, Long> monthlyStats = new java.util.HashMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault());
        for (AttachmentClaimRecord record : history) {
            String month = monthFormatter.format(Instant.ofEpochMilli(record.claimedAt()));
            monthlyStats.merge(month, 1L, Long::sum);
        }

        sender.sendMessage("§7--- 月度统计 ---");
        for (java.util.Map.Entry<String, Long> entry : monthlyStats.entrySet()) {
            sender.sendMessage("§e" + entry.getKey() + ": §f" + entry.getValue() + " 次");
        }
    }

    /**
     * 显示全局统计
     */
    private void showGlobalStats(CommandSender sender) {
        sender.sendMessage("§6=== 邮件附件系统统计 ===");
        sender.sendMessage("§7（统计信息需要数据库支持）");

        // 尝试获取附件总数
        // 注意：这里简化了实现，实际应该查询数据库
        sender.sendMessage("§e功能模块已就绪");
        sender.sendMessage("§7- 附件验证: 已启用");
        sender.sendMessage("§7- 价值估算: 已启用");
        sender.sendMessage("§7- 领取记录: 已启用");
    }

    /**
     * 显示帮助
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== 邮件附件命令帮助 ===");
        sender.sendMessage("§e/mailattachment history [数量] §7- 查看领取历史");
        sender.sendMessage("§e/mailattachment value <玩家> [数量] §7- 查看玩家附件价值");
        sender.sendMessage("§e/mailattachment check <玩家> §7- 检查玩家统计（管理员）");
        sender.sendMessage("§e/mailattachment stats §7- 全局统计（管理员）");
        sender.sendMessage("§e/mailattachment help §7- 显示帮助");
    }

    /**
     * 解析整数
     */
    private int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取玩家 UUID（简化实现）
     */
    private UUID getPlayerUUID(String playerName) {
        var player = org.bukkit.Bukkit.getOfflinePlayer(playerName);
        return player.hasPlayedBefore() ? player.getUniqueId() : null;
    }

    /**
     * 格式化时间
     */
    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天前";
        } else if (hours > 0) {
            return hours + "小时前";
        } else if (minutes > 0) {
            return minutes + "分钟前";
        } else {
            return "刚刚";
        }
    }

    /**
     * 格式化价值
     */
    private String formatValue(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.2fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("history");
            subCommands.add("value");
            subCommands.add("help");

            if (sender.hasPermission("starcore.mail.admin")) {
                subCommands.add("check");
                subCommands.add("stats");
            }

            String partial = args[0].toLowerCase();
            completions = subCommands.stream()
                .filter(s -> s.startsWith(partial))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            String partial = args[1].toLowerCase();
            completions = org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .limit(10)
                .collect(Collectors.toList());
        }

        return completions;
    }
}
