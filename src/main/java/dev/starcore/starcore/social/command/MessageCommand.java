package dev.starcore.starcore.social.command;

import dev.starcore.starcore.social.chat.PrivateMessageService;
import dev.starcore.starcore.social.gui.SocialMenuGui;
import dev.starcore.starcore.social.gui.SocialMenuListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.stream.Collectors;

/**
 * 私聊命令（星际通讯） /msg 和 /r
 */
public final class MessageCommand implements CommandExecutor, TabCompleter {
    private final PrivateMessageService service;
    private SocialMenuListener socialMenuListener;

    public MessageCommand(PrivateMessageService service) {
        this.service = service;
    }

    /**
     * 设置社交菜单监听器（由 SocialModule 调用）
     */
    public void setSocialMenuListener(SocialMenuListener listener) {
        this.socialMenuListener = listener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用私聊命令", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("socialgui") || cmdName.equals("social")) {
            return handleSocialGui(player);
        }

        UUID self = player.getUniqueId();

        if (cmdName.equals("msg") || cmdName.equals("tell") || cmdName.equals("whisper")) {
            // /msg <玩家> <消息>
            return handleMsg(player, args);
        } else if (cmdName.equals("r") || cmdName.equals("reply")) {
            // /r <消息>
            return handleReply(player, args);
        }

        return false;
    }

    /**
     * 处理 /msg 命令
     */
    private boolean handleMsg(Player sender, String[] args) {
        if (args.length < 2) {
            sendMsgHelp(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + args[0], NamedTextColor.RED));
            return true;
        }

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("不能给自己发消息", NamedTextColor.RED));
            return true;
        }

        // 合并剩余参数为消息
        StringBuilder msgBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) msgBuilder.append(" ");
            msgBuilder.append(args[i]);
        }
        String message = msgBuilder.toString();

        try {
            service.sendMessage(sender.getUniqueId(), target.getUniqueId(), message);

            // 发送确认消息给发送者
            Component senderMsg = buildSenderMessage(sender, target, message);
            sender.sendMessage(senderMsg);

            // 发送给目标
            Component targetMsg = buildTargetMessage(sender, target, message);
            target.sendMessage(targetMsg);
        } catch (IllegalStateException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 处理 /r 命令
     */
    private boolean handleReply(Player sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("用法: /r <消息>", NamedTextColor.YELLOW));
            return true;
        }

        UUID lastPartner = service.getLastChatPartner(sender.getUniqueId());
        if (lastPartner == null) {
            sender.sendMessage(Component.text("没有最近的聊天对象", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(lastPartner);
        if (target == null) {
            sender.sendMessage(Component.text("对方不在线", NamedTextColor.RED));
            return true;
        }

        // 合并所有参数为消息
        StringBuilder msgBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) msgBuilder.append(" ");
            msgBuilder.append(args[i]);
        }
        String message = msgBuilder.toString();

        try {
            service.sendMessage(sender.getUniqueId(), target.getUniqueId(), message);

            // 发送确认消息给发送者
            Component senderMsg = buildSenderMessage(sender, target, message);
            sender.sendMessage(senderMsg);

            // 发送给目标
            Component targetMsg = buildTargetMessage(sender, target, message);
            target.sendMessage(targetMsg);
        } catch (IllegalStateException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 处理 /socialgui 命令
     */
    private boolean handleSocialGui(Player sender) {
        if (socialMenuListener != null) {
            socialMenuListener.openMenu(sender);
        } else {
            sender.sendMessage(Component.text("社交菜单暂不可用", NamedTextColor.RED));
        }
        return true;
    }

    /**
     * 构建发送者看到的消息格式
     */
    private Component buildSenderMessage(Player sender, Player target, String message) {
        return Component.text()
            .append(Component.text("[我 -> " + target.getName() + "] ", NamedTextColor.GRAY))
            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(message))
            .build();
    }

    /**
     * 构建目标看到的消息格式
     */
    private Component buildTargetMessage(Player sender, Player target, String message) {
        return Component.text()
            .append(Component.text("[" + sender.getName() + " -> 我] ", NamedTextColor.AQUA))
            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(message))
            .build();
    }

    /**
     * 发送 /msg 帮助信息
     */
    private void sendMsgHelp(Player player) {
        player.sendMessage(Component.text("=== 私聊(星际通讯)命令 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/msg <玩家> <消息> - 发送私聊", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/r <消息> - 回复最近私聊", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/msghistory - 查看聊天记录", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/socialgui - 打开社交菜单", NamedTextColor.GRAY));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("msg") || cmdName.equals("tell") || cmdName.equals("whisper")) {
            if (args.length == 1) {
                // 补全在线玩家
                return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
