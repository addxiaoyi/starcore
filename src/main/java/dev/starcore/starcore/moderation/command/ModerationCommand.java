package dev.starcore.starcore.moderation.command;

import dev.starcore.starcore.moderation.ModerationService;
import dev.starcore.starcore.moderation.VanishService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理命令处理器
 * /mute, /unmute, /kick, /ban, /unban, /jail, /unjail, /vanish
 *
 * 中文别名:
 *   mute/禁言 → 禁言玩家
 *   unmute/解除禁言 → 解除禁言
 *   kick/踢出 → 踢出玩家
 *   ban/封禁 → 封禁玩家
 *   tempban/临时封禁 → 临时封禁
 *   unban/解封 → 解封玩家
 *   jail/监禁 → 监禁玩家
 *   unjail/释放 → 释放玩家
 *   vanish/隐身 → 隐身切换
 */
public final class ModerationCommand implements CommandExecutor, TabCompleter {
    private final ModerationService moderationService;
    private final VanishService vanishService;

    public ModerationCommand(ModerationService moderationService, VanishService vanishService) {
        this.moderationService = moderationService;
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        String cmdName = command.getName().toLowerCase();

        return switch (cmdName) {
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "kick" -> handleKick(sender, args);
            case "ban" -> handleBan(sender, args);
            case "tempban" -> handleTempBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "jail" -> handleJail(sender, args);
            case "unjail" -> handleUnjail(sender, args);
            case "vanish" -> handleVanish(sender);
            default -> true;
        };
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /mute <玩家> [时间] [原因]",
                NamedTextColor.YELLOW
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        Duration duration = args.length > 1 ? parseDuration(args[1]) : null;
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违反规则";

        moderationService.mutePlayer(
            target.getUniqueId(),
            duration,
            reason,
            sender.getName()
        );

        sender.sendMessage(Component.text(
            String.format("已禁言 %s%s - 原因: %s",
                target.getName(),
                duration != null ? " (" + formatDuration(duration) + ")" : " (永久)",
                reason),
            NamedTextColor.GREEN
        ));

        target.sendMessage(Component.text(
            String.format("你已被禁言%s\n原因: %s",
                duration != null ? " (" + formatDuration(duration) + ")" : " (永久)",
                reason),
            NamedTextColor.RED
        ));

        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /unmute <玩家>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        if (moderationService.unmutePlayer(target.getUniqueId())) {
            sender.sendMessage(Component.text("已解除 " + target.getName() + " 的禁言", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你的禁言已被解除", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("该玩家未被禁言", NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /kick <玩家> [原因]", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "违反规则";

        moderationService.kickPlayer(target, reason);
        sender.sendMessage(Component.text("已踢出 " + target.getName(), NamedTextColor.GREEN));

        return true;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /ban <玩家> [原因]", NamedTextColor.YELLOW));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "违反规则";

        moderationService.banPlayer(target, null, reason, sender.getName());
        sender.sendMessage(Component.text("已永久封禁 " + target.getName(), NamedTextColor.GREEN));

        return true;
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /tempban <玩家> <时间> [原因]", NamedTextColor.YELLOW));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Duration duration = parseDuration(args[1]);

        if (duration == null) {
            sender.sendMessage(Component.text("无效的时间格式", NamedTextColor.RED));
            return true;
        }

        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违反规则";

        moderationService.banPlayer(target, duration, reason, sender.getName());
        sender.sendMessage(Component.text(
            String.format("已封禁 %s %s", target.getName(), formatDuration(duration)),
            NamedTextColor.GREEN
        ));

        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /unban <玩家>", NamedTextColor.YELLOW));
            return true;
        }

        moderationService.unbanPlayer(args[0]);
        sender.sendMessage(Component.text("已解封 " + args[0], NamedTextColor.GREEN));

        return true;
    }

    private boolean handleJail(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /jail <玩家> [时间] [原因]", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        Duration duration = args.length > 1 ? parseDuration(args[1]) : null;
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违反规则";

        moderationService.jailPlayer(target.getUniqueId(), duration, reason, sender.getName());

        sender.sendMessage(Component.text(
            String.format("已关押 %s%s", target.getName(), duration != null ? " (" + formatDuration(duration) + ")" : ""),
            NamedTextColor.GREEN
        ));

        target.sendMessage(Component.text(
            String.format("你已被关入监狱%s\n原因: %s",
                duration != null ? " (" + formatDuration(duration) + ")" : "",
                reason),
            NamedTextColor.RED
        ));

        return true;
    }

    private boolean handleUnjail(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("用法: /unjail <玩家>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        if (moderationService.unjailPlayer(target.getUniqueId())) {
            sender.sendMessage(Component.text("已释放 " + target.getName(), NamedTextColor.GREEN));
            target.sendMessage(Component.text("你已被释放", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("该玩家不在监狱中", NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (!sender.hasPermission("starcore.vanish")) {
            sender.sendMessage(Component.text(
                "§c你没有权限使用隐身命令！",
                NamedTextColor.RED
            ));
            return true;
        }

        vanishService.toggleVanish(player);
        return true;
    }

    /**
     * 解析时间格式 (如: 1d, 2h, 30m, 10s)
     */
    private Duration parseDuration(String input) {
        try {
            char unit = input.charAt(input.length() - 1);
            long value = Long.parseLong(input.substring(0, input.length() - 1));

            return switch (unit) {
                case 's' -> Duration.ofSeconds(value);
                case 'm' -> Duration.ofMinutes(value);
                case 'h' -> Duration.ofHours(value);
                case 'd' -> Duration.ofDays(value);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化时间
     */
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return days + "天" + (hours > 0 ? hours + "小时" : "");
        } else if (hours > 0) {
            return hours + "小时" + (minutes > 0 ? minutes + "分钟" : "");
        } else {
            return minutes + "分钟";
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        String cmd = command.getName().toLowerCase();

        if (args.length == 1) {
            // 补全在线玩家名称
            List<String> players = new ArrayList<>();
            String current = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(current)) {
                    players.add(p.getName());
                }
            }
            return players;
        }

        if (args.length == 2) {
            if (cmd.equals("mute") || cmd.equals("tempban") || cmd.equals("jail")) {
                // 补全时间建议
                return List.of("1d", "12h", "6h", "1h", "30m", "15m", "5m", "perm");
            }
            if (cmd.equals("kick") || cmd.equals("ban") || cmd.equals("unban") || cmd.equals("unjail")) {
                // 补全原因建议
                return List.of("违反规则", "广告", "作弊", "骚扰", "刷屏");
            }
        }

        if (args.length >= 3) {
            if (cmd.equals("mute") || cmd.equals("tempban") || cmd.equals("jail") || cmd.equals("kick") || cmd.equals("ban")) {
                // 补全原因
                return List.of("违反规则", "广告", "作弊", "骚扰", "刷屏", "恶意破坏");
            }
        }

        return List.of();
    }
}
