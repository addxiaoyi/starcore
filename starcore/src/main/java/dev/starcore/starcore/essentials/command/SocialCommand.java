package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.social.SocialService;
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
 * 社交命令处理器
 * /msg, /reply, /ignore
 */
public final class SocialCommand implements CommandExecutor, TabCompleter {
    private final SocialService socialService;

    public SocialCommand(SocialService socialService) {
        this.socialService = socialService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "msg", "tell", "whisper" -> handleMessage(player, args);
            case "reply", "r" -> handleReply(player, args);
            case "ignore" -> handleIgnore(player, args);
            case "unignore" -> handleUnignore(player, args);
        }

        return true;
    }

    private void handleMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                "用法: /msg <玩家> <消息>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(
                "玩家不在线: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text(
                "你不能给自己发消息",
                NamedTextColor.RED
            ));
            return;
        }

        // 组合消息
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) message.append(" ");
            message.append(args[i]);
        }

        // 发送消息
        boolean sent = socialService.sendMessage(player, target, message.toString());

        if (!sent) {
            player.sendMessage(Component.text(
                "该玩家屏蔽了你",
                NamedTextColor.RED
            ));
        }
    }

    private void handleReply(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /reply <消息>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID targetId = socialService.getLastMessaged(player.getUniqueId()).orElse(null);
        if (targetId == null) {
            player.sendMessage(Component.text(
                "没有可回复的玩家",
                NamedTextColor.RED
            ));
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(
                "该玩家已离线",
                NamedTextColor.RED
            ));
            return;
        }

        // 组合消息
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) message.append(" ");
            message.append(args[i]);
        }

        // 发送消息
        boolean sent = socialService.sendMessage(player, target, message.toString());

        if (!sent) {
            player.sendMessage(Component.text(
                "该玩家屏蔽了你",
                NamedTextColor.RED
            ));
        }
    }

    private void handleIgnore(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /ignore <玩家>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text(
                "找不到玩家: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = socialService.ignorePlayer(
            player.getUniqueId(),
            target.getUniqueId()
        );

        if (success) {
            player.sendMessage(Component.text(
                "已屏蔽玩家: " + target.getName(),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                "你不能屏蔽自己",
                NamedTextColor.RED
            ));
        }
    }

    private void handleUnignore(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /unignore <玩家>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text(
                "找不到玩家: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = socialService.unignorePlayer(
            player.getUniqueId(),
            target.getUniqueId()
        );

        if (success) {
            player.sendMessage(Component.text(
                "已取消屏蔽玩家: " + target.getName(),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                "该玩家未被屏蔽",
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
        if (args.length == 1 && !command.getName().equalsIgnoreCase("reply")) {
            // 返回在线玩家列表
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) {
                    players.add(p.getName());
                }
            }
            return players;
        }

        return List.of();
    }
}
