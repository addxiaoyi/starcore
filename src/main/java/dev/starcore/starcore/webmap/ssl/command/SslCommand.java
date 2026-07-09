package dev.starcore.starcore.webmap.ssl.command;

import dev.starcore.starcore.webmap.ssl.CertificateInfo;
import dev.starcore.starcore.webmap.ssl.CertificateResult;
import dev.starcore.starcore.webmap.ssl.SslCertificateService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SSL 证书管理命令
 * /ssl issue <domain> <email> [webroot] - 申请证书
 * /ssl renew <domain> - 续期证书
 * /ssl list - 列出所有证书
 * /ssl info <domain> - 查看证书信息
 * /ssl revoke <domain> - 撤销证书
 */
public class SslCommand implements CommandExecutor, TabCompleter {
    private final SslCertificateService sslService;

    public SslCommand(SslCertificateService sslService) {
        this.sslService = sslService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("starcore.ssl.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "issue" -> handleIssue(sender, args);
            case "renew" -> handleRenew(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "revoke" -> handleRevoke(sender, args);
            case "autorenew" -> handleAutoRenew(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleIssue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /ssl issue <domain> <email> [webroot]", NamedTextColor.RED));
            return;
        }

        String domain = args[1];
        String email = args[2];
        Path webroot = args.length >= 4 ? Paths.get(args[3]) : Paths.get("plugins/StarCore/webroot");

        sender.sendMessage(Component.text("正在申请 SSL 证书: " + domain, NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("这可能需要几分钟，请耐心等待...", NamedTextColor.GRAY));

        sslService.obtainCertificate(domain, email, webroot, false).thenAccept(result -> {
            if (result.success()) {
                sender.sendMessage(Component.text("✅ SSL 证书申请成功！", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("域名: " + domain, NamedTextColor.WHITE));
                sender.sendMessage(Component.text("过期时间: " + result.certificateInfo().expiryDate(), NamedTextColor.WHITE));
                sender.sendMessage(Component.text("剩余天数: " + result.certificateInfo().daysRemaining() + " 天", NamedTextColor.WHITE));
            } else {
                sender.sendMessage(Component.text("❌ 证书申请失败: " + result.message(), NamedTextColor.RED));
            }
        });
    }

    private void handleRenew(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /ssl renew <domain>", NamedTextColor.RED));
            return;
        }

        String domain = args[1];
        sender.sendMessage(Component.text("正在续期证书: " + domain, NamedTextColor.YELLOW));

        sslService.renewCertificate(domain).thenAccept(result -> {
            if (result.success()) {
                sender.sendMessage(Component.text("✅ 证书续期成功！", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("新的过期时间: " + result.certificateInfo().expiryDate(), NamedTextColor.WHITE));
            } else {
                sender.sendMessage(Component.text("❌ 证书续期失败: " + result.message(), NamedTextColor.RED));
            }
        });
    }

    private void handleList(CommandSender sender) {
        Map<String, CertificateInfo> certs = sslService.listCertificates();

        if (certs.isEmpty()) {
            sender.sendMessage(Component.text("暂无已安装的证书", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("=== SSL 证书列表 ===", NamedTextColor.GOLD));
        for (Map.Entry<String, CertificateInfo> entry : certs.entrySet()) {
            CertificateInfo info = entry.getValue();
            NamedTextColor color = info.isExpired() ? NamedTextColor.RED :
                                   info.isExpiringSoon() ? NamedTextColor.YELLOW :
                                   NamedTextColor.GREEN;

            String status = info.isExpired() ? "[已过期]" :
                           info.isExpiringSoon() ? "[即将过期]" :
                           "[正常]";

            sender.sendMessage(Component.text(
                String.format("%s %s - 剩余 %d 天", status, info.domain(), info.daysRemaining()),
                color
            ));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /ssl info <domain>", NamedTextColor.RED));
            return;
        }

        String domain = args[1];
        CertificateInfo info = sslService.getCertificateInfo(domain);

        if (info == null) {
            sender.sendMessage(Component.text("未找到域名的证书: " + domain, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("=== 证书信息 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("域名: " + info.domain(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("主题: " + info.subject(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("签发时间: " + info.issueDate(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("过期时间: " + info.expiryDate(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("剩余天数: " + info.daysRemaining() + " 天", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("证书文件: " + info.certFile(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("私钥文件: " + info.keyFile(), NamedTextColor.GRAY));

        NamedTextColor statusColor = info.isExpired() ? NamedTextColor.RED :
                                     info.isExpiringSoon() ? NamedTextColor.YELLOW :
                                     NamedTextColor.GREEN;
        String status = info.isExpired() ? "已过期" :
                       info.isExpiringSoon() ? "即将过期" :
                       "正常";
        sender.sendMessage(Component.text("状态: " + status, statusColor));
    }

    private void handleRevoke(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /ssl revoke <domain>", NamedTextColor.RED));
            return;
        }

        String domain = args[1];
        sender.sendMessage(Component.text("正在撤销证书: " + domain, NamedTextColor.YELLOW));

        boolean success = sslService.revokeCertificate(domain);
        if (success) {
            sender.sendMessage(Component.text("✅ 证书已撤销并删除", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("❌ 证书撤销失败", NamedTextColor.RED));
        }
    }

    private void handleAutoRenew(CommandSender sender) {
        sender.sendMessage(Component.text("开始自动续期检查...", NamedTextColor.YELLOW));
        sslService.autoRenewCertificates();
        sender.sendMessage(Component.text("自动续期任务已启动，请查看控制台日志", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== SSL 证书管理 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/ssl issue <domain> <email> [webroot] - 申请证书", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/ssl renew <domain> - 续期证书", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/ssl list - 列出所有证书", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/ssl info <domain> - 查看证书信息", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/ssl revoke <domain> - 撤销证书", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/ssl autorenew - 检查并续期即将过期的证书", NamedTextColor.WHITE));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("starcore.ssl.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterMatching(args[0], Arrays.asList("issue", "renew", "list", "info", "revoke", "autorenew"));
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("renew") ||
                                 args[0].equalsIgnoreCase("info") ||
                                 args[0].equalsIgnoreCase("revoke"))) {
            return new ArrayList<>(sslService.listCertificates().keySet());
        }

        return List.of();
    }

    private List<String> filterMatching(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(lower))
            .toList();
    }
}
