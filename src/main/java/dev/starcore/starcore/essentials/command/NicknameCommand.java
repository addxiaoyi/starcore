package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.nickname.NicknameService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 昵称命令
 * /nick, /realname
 */
public final class NicknameCommand implements CommandExecutor, TabCompleter {
    private final NicknameService nicknameService;

    public NicknameCommand(NicknameService nicknameService) {
        this.nicknameService = nicknameService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("nick")) {
            handleNick(sender, args);
        } else if (commandName.equals("realname")) {
            handleRealName(sender, args);
        }

        return true;
    }

    /**
     * 设置昵称
     */
    private void handleNick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("starcore.nick")) {
            player.sendMessage(Component.text(
                "你没有权限设置昵称",
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /nick <昵称> 或 /nick off",
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 移除昵称
        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
            boolean success = nicknameService.removeNickname(player);

            if (success) {
                player.sendMessage(Component.text(
                    "已移除昵称",
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(
                    "你没有设置昵称",
                    NamedTextColor.YELLOW
                ));
            }
            return;
        }

        // 组合昵称（支持空格）
        StringBuilder nickname = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) nickname.append(" ");
            nickname.append(args[i]);
        }

        String finalNickname = nickname.toString();

        // 颜色代码权限检查
        if (finalNickname.contains("§") && !player.hasPermission("starcore.nick.color")) {
            player.sendMessage(Component.text(
                "你没有权限使用颜色代码",
                NamedTextColor.RED
            ));
            return;
        }

        // 设置昵称
        boolean success = nicknameService.setNickname(player, finalNickname);

        if (success) {
            player.sendMessage(Component.text(
                "已设置昵称为: ",
                NamedTextColor.GREEN
            ).append(Component.text(finalNickname)));
        } else {
            player.sendMessage(Component.text(
                "设置昵称失败（可能已被使用或格式无效）",
                NamedTextColor.RED
            ));
            player.sendMessage(Component.text(
                "昵称要求: 2-16个字符，仅字母数字下划线",
                NamedTextColor.GRAY
            ));
        }
    }

    /**
     * 查询真实名称
     */
    private void handleRealName(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /realname <昵称>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        String nickname = args[0];
        UUID playerId = nicknameService.getPlayerByNickname(nickname).orElse(null);

        if (playerId == null) {
            sender.sendMessage(Component.text(
                "找不到使用此昵称的玩家: " + nickname,
                NamedTextColor.RED
            ));
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            sender.sendMessage(Component.text(
                "昵称 ",
                NamedTextColor.GRAY
            ).append(Component.text(nickname))
            .append(Component.text(" 的真实名称是: ", NamedTextColor.GRAY))
            .append(Component.text(player.getName(), NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text(
                "该玩家不在线",
                NamedTextColor.YELLOW
            ));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (command.getName().equalsIgnoreCase("nick") && args.length == 1) {
            return List.of("off", "reset", "<昵称>");
        }

        if (command.getName().equalsIgnoreCase("realname") && args.length == 1) {
            // 返回所有在线玩家的昵称
            List<String> nicknames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                nicknameService.getNickname(p.getUniqueId())
                    .ifPresent(nicknames::add);
            }
            return nicknames;
        }

        return List.of();
    }
}
