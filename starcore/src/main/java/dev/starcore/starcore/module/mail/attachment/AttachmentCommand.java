package dev.starcore.starcore.module.mail.attachment;

import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 邮件附件命令处理器
 *
 * 命令：
 * - /mail send <玩家> [主题] - 发送带附件的邮件
 * - /mail read [页码] - 阅读邮件
 * - /mail claim [邮件ID] - 领取附件
 * - /mail claimall - 一键领取所有附件
 * - /mail delete <邮件ID> - 删除邮件
 * - /mail list [页码] - 邮件列表
 */
public final class AttachmentCommand implements CommandExecutor, TabCompleter {

    private static final int MAILS_PER_PAGE = 10;

    private final MailAttachmentService service;

    public AttachmentCommand(MailAttachmentService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家才能使用此命令");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "send", "s" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                        .append(Component.text("用法: /mail send <玩家> [主题]", NamedTextColor.WHITE)));
                    return true;
                }
                String recipient = args[1];
                String subject = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "无主题";

                // 打开发送GUI（这里简化为直接发送，无附件版本）
                openSendGui(player, recipient, subject);
            }
            case "read", "r" -> {
                int page = args.length > 1 ? parseInt(args[1], 1) : 1;
                openReadGui(player, page);
            }
            case "claim", "c" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                        .append(Component.text("用法: /mail claim <邮件ID>", NamedTextColor.WHITE)));
                    return true;
                }
                try {
                    UUID mailId = UUID.fromString(args[1]);
                    claimMail(player, mailId);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                        .append(Component.text("无效的邮件ID", NamedTextColor.WHITE)));
                }
            }
            case "claimall", "ca" -> {
                claimAllMails(player);
            }
            case "delete", "d" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                        .append(Component.text("用法: /mail delete <邮件ID>", NamedTextColor.WHITE)));
                    return true;
                }
                try {
                    UUID mailId = UUID.fromString(args[1]);
                    deleteMail(player, mailId);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                        .append(Component.text("无效的邮件ID", NamedTextColor.WHITE)));
                }
            }
            case "list", "l" -> {
                int page = args.length > 1 ? parseInt(args[1], 1) : 1;
                showMailList(player, page);
            }
            case "help", "?" -> {
                showHelp(player);
            }
            case "unread", "u" -> {
                showUnreadCount(player);
            }
            default -> {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                    .append(Component.text("未知子命令: " + subCommand, NamedTextColor.WHITE)));
                showHelp(player);
            }
        }

        return true;
    }

    /**
     * 打开发送GUI
     */
    private void openSendGui(Player player, String recipient, String subject) {
        // 验证接收者存在
        boolean recipientExists = Bukkit.getPlayer(recipient) != null ||
            Arrays.stream(Bukkit.getOfflinePlayers())
                .anyMatch(p -> p.getName() != null && p.getName().equalsIgnoreCase(recipient));

        if (!recipientExists) {
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                .append(Component.text("玩家不存在: " + recipient, NamedTextColor.WHITE)));
            return;
        }

        // 打开 AttachmentGui 的发送界面
        AttachmentGui.openSendGui(player, service, recipient, subject);
    }

    /**
     * 打开阅读GUI
     */
    private void openReadGui(Player player, int page) {
        AttachmentGui.openMailListGui(player, service, page);
    }

    /**
     * 领取单个邮件附件
     */
    private void claimMail(Player player, UUID mailId) {
        AttachmentClaimResult result = service.claimAttachment(player, mailId);

        Component prefix = Component.text("[附件] ").color(
            result.success() ? NamedTextColor.GREEN : NamedTextColor.RED);
        player.sendMessage(prefix.append(Component.text(result.message(), NamedTextColor.WHITE)));

        if (result.success()) {
            player.sendMessage(Component.text("已领取 " + result.claimedCount() + " 个物品", NamedTextColor.GREEN));

            if (!result.overflowItems().isEmpty()) {
                player.sendMessage(Component.text("背包空间不足，" + result.overflowItems().size() + " 个物品被返还", NamedTextColor.YELLOW));
            }
        }
    }

    /**
     * 一键领取所有附件
     */
    private void claimAllMails(Player player) {
        List<AttachmentClaimResult> results = service.claimAllAttachments(player);

        if (results.isEmpty()) {
            player.sendMessage(Component.text("[附件] ").color(NamedTextColor.YELLOW)
                .append(Component.text("没有可领取的附件", NamedTextColor.WHITE)));
            return;
        }

        int totalClaimed = results.stream()
            .filter(AttachmentClaimResult::success)
            .mapToInt(AttachmentClaimResult::claimedCount)
            .sum();

        int totalMails = results.size();

        player.sendMessage(Component.text("[附件] ").color(NamedTextColor.GREEN)
            .append(Component.text("已处理 " + totalMails + " 封邮件，共领取 " + totalClaimed + " 个物品", NamedTextColor.WHITE)));
    }

    /**
     * 删除邮件
     */
    private void deleteMail(Player player, UUID mailId) {
        boolean deleted = service.deleteMail(player, mailId);

        if (deleted) {
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.GREEN)
                .append(Component.text("邮件已删除", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                .append(Component.text("删除失败，邮件不存在或无权删除", NamedTextColor.WHITE)));
        }
    }

    /**
     * 显示邮件列表
     */
    private void showMailList(Player player, int page) {
        List<MailAttachment> mails = service.getPlayerMails(player.getUniqueId());

        if (mails.isEmpty()) {
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.YELLOW)
                .append(Component.text("你没有邮件", NamedTextColor.WHITE)));
            return;
        }

        int totalPages = (int) Math.ceil((double) mails.size() / MAILS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        int start = (page - 1) * MAILS_PER_PAGE;
        int end = Math.min(start + MAILS_PER_PAGE, mails.size());

        player.sendMessage(Component.text("=== 邮件列表 (第 " + page + "/" + totalPages + " 页) ===").color(NamedTextColor.GOLD));

        for (int i = start; i < end; i++) {
            MailAttachment mail = mails.get(i);
            Component statusIcon = mail.read()
                ? Component.text("[已读]").color(NamedTextColor.GRAY)
                : Component.text("[未读]").color(NamedTextColor.RED);

            Component attachIcon = mail.hasAttachments()
                ? (mail.isClaimed()
                    ? Component.text("[附件-已领]").color(NamedTextColor.GRAY)
                    : Component.text("[附件]").color(NamedTextColor.GOLD))
                : Component.text("");

            String timeStr = formatTimeAgo(mail.sentAt());

            player.sendMessage(Component.text()
                .append(statusIcon)
                .append(Component.text(" "))
                .append(Component.text(mail.subject()).color(NamedTextColor.WHITE))
                .append(Component.text(" "))
                .append(attachIcon)
                .append(Component.text(" - 来自: " + mail.senderName() + " (" + timeStr + ")", NamedTextColor.GRAY)));
        }

        if (totalPages > 1) {
            player.sendMessage(Component.text("使用 /mail list <页码> 查看更多", NamedTextColor.GRAY));
        }
    }

    /**
     * 显示未读数量
     */
    private void showUnreadCount(Player player) {
        int unread = service.getUnreadCount(player.getUniqueId());
        int unclaimed = service.getUnclaimedAttachmentCount(player.getUniqueId());

        if (unread == 0 && unclaimed == 0) {
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.GREEN)
                .append(Component.text("你没有未读邮件和待领取附件", NamedTextColor.WHITE)));
        } else {
            if (unread > 0) {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                    .append(Component.text("你有 " + unread + " 封未读邮件", NamedTextColor.WHITE)));
            }
            if (unclaimed > 0) {
                player.sendMessage(Component.text("[附件] ").color(NamedTextColor.GOLD)
                    .append(Component.text("你有 " + unclaimed + " 个待领取附件", NamedTextColor.WHITE)));
            }
        }
    }

    /**
     * 显示帮助
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== 邮件命令帮助 ===").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("/mail send <玩家> [主题]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 发送邮件", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail read [页码]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 阅读邮件列表", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail claim <邮件ID>").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 领取附件", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail claimall").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 一键领取所有附件", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail delete <邮件ID>").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 删除邮件", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail list [页码]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 查看邮件列表", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/mail unread").color(NamedTextColor.YELLOW)
            .append(Component.text(" - 查看未读数量", NamedTextColor.GRAY)));
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
     * 解析整数
     */
    private int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                "send", "read", "claim", "claimall", "delete", "list", "unread", "help"
            );
            String partial = args[0].toLowerCase();
            completions = subCommands.stream()
                .filter(s -> s.startsWith(partial))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            String partial = args[1].toLowerCase();
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .limit(10)
                .collect(Collectors.toList());
            completions.addAll(playerNames);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            // 显示玩家未领取附件的邮件
            if (sender instanceof Player player) {
                String partial = args[1].toLowerCase();
                List<String> mailIds = service.getPlayerMails(player.getUniqueId()).stream()
                    .filter(m -> m.hasAttachments() && !m.isClaimed() && !m.isExpired())
                    .map(m -> m.id().toString())
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .limit(10)
                    .collect(Collectors.toList());
                completions.addAll(mailIds);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            // 显示玩家的邮件
            if (sender instanceof Player player) {
                String partial = args[1].toLowerCase();
                List<String> mailIds = service.getPlayerMails(player.getUniqueId()).stream()
                    .filter(m -> !m.isExpired())
                    .map(m -> m.id().toString())
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .limit(10)
                    .collect(Collectors.toList());
                completions.addAll(mailIds);
            }
        }

        return completions;
    }
}
