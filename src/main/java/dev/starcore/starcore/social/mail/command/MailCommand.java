package dev.starcore.starcore.social.mail.command;

import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.MailService;
import dev.starcore.starcore.social.mail.gui.MailGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 邮件命令处理
 *
 * 命令：
 * - /mail - 打开邮箱
 * - /mailbox - 打开邮箱
 * - /mail <玩家> <主题> <内容> - 发送邮件
 * - /mailgui - 打开邮箱 GUI
 * - /email - 打开邮箱
 *
 * 中文别名支持:
 *   mail/邮件 → 打开邮箱/发送邮件
 *   mailbox/邮箱 → 打开邮箱
 *   mailgui/邮件gui → 打开图形界面
 */
public final class MailCommand implements CommandExecutor, TabCompleter {

    // ========== 颜色代码常量 (技术债务，待统一重构) ==========
    // @deprecated 硬编码颜色代码，应使用统一颜色常量类
    private static final String C_GOLD = "§6";
    private static final String C_YELLOW = "§e";
    private static final String C_GRAY = "§7";
    private static final String C_RED = "§c";
    private static final String C_GREEN = "§a";
    private static final String C_WHITE = "§f";
    private static final String C_ORANGE = "§6";

    private final MailService mailService;
    private MailGuiListener mailGuiListener;

    public MailCommand(MailService mailService) {
        this.mailService = mailService;
    }

    public void setMailGuiListener(MailGuiListener listener) {
        this.mailGuiListener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家才能使用此命令");
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        // 打开邮箱 GUI
        if (cmdName.equals("mailgui") || cmdName.equals("mailbox") || args.length == 0) {
            openMailGui(player);
            return true;
        }

        // 发送邮件
        if (args.length >= 2) {
            String recipientName = args[0];

            // 检查玩家是否存在（D-032：拒绝 fake OfflinePlayer UUID）
            Player online = Bukkit.getPlayerExact(recipientName);
            OfflinePlayer recipient;
            if (online != null) {
                recipient = online;
            } else {
                recipient = Bukkit.getOfflinePlayer(recipientName);
                if (!recipient.hasPlayedBefore()) {
                    player.sendMessage(C_RED + "[邮件] 找不到玩家: " + recipientName);
                    return true;
                }
            }

            // 不能给自己发邮件
            if (recipient.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(C_RED + "[邮件] 不能给自己发邮件");
                return true;
            }

            // 解析主题和内容
            // 格式: /mail <玩家> <主题> [内容...]
            String subject = args[1];
            String content = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

            // 发送邮件
            boolean success = mailService.sendMail(player, recipientName, subject, content, null);

            if (success) {
                player.sendMessage(C_GREEN + "[邮件] 邮件已发送给 " + recipient.getName());
            } else {
                player.sendMessage(C_RED + "[邮件] 发送失败，请稍后重试");
            }
            return true;
        }

        // 帮助信息
        showHelp(player);
        return true;
    }

    /**
     * 打开邮箱 GUI
     */
    private void openMailGui(Player player) {
        if (mailGuiListener != null) {
            mailGuiListener.openMailList(player);
        } else {
            // 如果监听器未注入，显示命令行界面
            showMailList(player);
        }
    }

    /**
     * 显示邮件列表（命令行模式）
     */
    private void showMailList(Player player) {
        List<Mail> mails = mailService.getPlayerMails(player.getUniqueId());
        int unreadCount = mailService.getUnreadCount(player.getUniqueId());

        player.sendMessage(C_GOLD + "=== 邮件列表 ===");
        player.sendMessage(C_GRAY + "总邮件: " + mails.size() + " | 未读: " + unreadCount);

        if (mails.isEmpty()) {
            player.sendMessage(C_GRAY + "你的邮箱是空的");
        } else {
            int count = 0;
            for (Mail mail : mails) {
                if (count >= 10) break;
                String status = mail.isRead() ? C_GRAY : C_GREEN;
                String attachments = mail.hasAttachments() ? (mail.isClaimed() ? " " + C_GRAY + "[附件]" : " " + C_ORANGE + "[附件]") : "";
                player.sendMessage(status + (count + 1) + ". " + mail.getSenderName() + " " + C_WHITE + "- " + mail.getSubject() + attachments);
                count++;
            }

            if (mails.size() > 10) {
                player.sendMessage(C_GRAY + "... 还有 " + (mails.size() - 10) + " 封邮件");
            }
        }

        player.sendMessage(C_YELLOW + "使用 /mailgui 打开图形界面");
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(C_GOLD + "=== 邮件系统帮助 ===");
        player.sendMessage(C_YELLOW + "/mail " + C_GRAY + "- 打开邮箱");
        player.sendMessage(C_YELLOW + "/mailgui " + C_GRAY + "- 打开邮箱图形界面");
        player.sendMessage(C_YELLOW + "/mail <玩家> <主题> [内容] " + C_GRAY + "- 发送邮件");
        // 分隔
        player.sendMessage(C_GRAY + "示例:");
        player.sendMessage(C_GRAY + "/mail Steve 你好 这是一封测试邮件");
        player.sendMessage(C_GRAY + "/mailgui");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 玩家名补全
            String partial = args[0].toLowerCase();
            completions = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .filter(name -> !name.equals(sender.getName())) // 排除自己
                .limit(10)
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            // 常用主题前缀
            String partial = args[1].toLowerCase();
            List<String> suggestions = Arrays.asList(
                "你好", "交易", "合作", "有事找你", "紧急",
                "问候", "通知", "邀请", "感谢", "道歉"
            );
            completions = suggestions.stream()
                .filter(s -> s.startsWith(partial))
                .limit(5)
                .collect(Collectors.toList());
        }

        return completions;
    }
}
