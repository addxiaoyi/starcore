package dev.starcore.starcore.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.UUID;

/**
 * 聊天命令
 * 提供聊天频道切换和聊天设置功能
 */
public class ChatCommand implements CommandExecutor, TabCompleter {

    private final ChatFormatterService chatFormatter;
    private final BubbleChatVisualizer bubbleVisualizer;

    public ChatCommand(ChatFormatterService chatFormatter, BubbleChatVisualizer bubbleVisualizer) {
        this.chatFormatter = chatFormatter;
        this.bubbleVisualizer = bubbleVisualizer;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行！");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "channel", "ch" -> handleChannelCommand(player, args);
            case "bubble" -> handleBubbleCommand(player, args);
            case "highlight" -> handleHighlightCommand(player, args);
            case "reload" -> handleReloadCommand(player);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * 处理频道切换命令
     */
    private void handleChannelCommand(Player player, String[] args) {
        if (args.length < 2) {
            // 显示当前频道状态
            ChatFormatterService.ChatChannel currentChannel =
                chatFormatter.getPlayerChannel(player.getUniqueId());

            Component message = Component.text()
                .append(Component.text("[STARCORE] ", NamedTextColor.GOLD))
                .append(Component.text("当前聊天频道: ", NamedTextColor.GRAY))
                .append(Component.text(currentChannel.getDisplayName(), NamedTextColor.YELLOW))
                .build();
            player.sendMessage(message);

            // 显示频道切换按钮
            sendChannelButtons(player);
            return;
        }

        String channelName = args[1].toLowerCase();
        ChatFormatterService.ChatChannel targetChannel = switch (channelName) {
            case "global", "g", "全局" -> ChatFormatterService.ChatChannel.GLOBAL;
            case "nation", "n", "国家" -> ChatFormatterService.ChatChannel.NATION;
            case "local", "l", "本地" -> ChatFormatterService.ChatChannel.LOCAL;
            case "party", "p", "小队" -> ChatFormatterService.ChatChannel.PARTY;
            case "guild", "星座" -> ChatFormatterService.ChatChannel.GUILD;
            default -> null;
        };

        if (targetChannel == null) {
            player.sendMessage(Component.text("[STARCORE] ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("未知频道: " + channelName, NamedTextColor.RED)));
            sendChannelButtons(player);
            return;
        }

        chatFormatter.setPlayerChannel(player.getUniqueId(), targetChannel);

        player.sendMessage(Component.text()
            .append(Component.text("[STARCORE] ", NamedTextColor.GOLD))
            .append(Component.text("已切换到 ", NamedTextColor.GRAY))
            .append(Component.text(targetChannel.getDisplayName(), NamedTextColor.YELLOW))
            .append(Component.text(" 频道", NamedTextColor.GRAY))
            .build());
    }

    /**
     * 处理气泡聊天命令
     */
    private void handleBubbleCommand(Player player, String[] args) {
        if (args.length < 2) {
            // 显示气泡聊天状态
            boolean enabled = chatFormatter.isBubbleChatEnabled();
            Component status = Component.text()
                .append(Component.text("[STARCORE] ", NamedTextColor.GOLD))
                .append(Component.text("气泡聊天: ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "已启用" : "已禁用",
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .appendNewline()
                .append(Component.text("显示范围: ", NamedTextColor.GRAY))
                .append(Component.text(chatFormatter.getBubbleChatRadius() + " 格",
                    NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("持续时间: ", NamedTextColor.GRAY))
                .append(Component.text(chatFormatter.getBubbleChatDuration() + " 秒",
                    NamedTextColor.AQUA))
                .build();
            player.sendMessage(status);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "on", "enable", "开", "启用" -> {
                // 需要修改配置，这里只提示
                player.sendMessage(Component.text("[STARCORE] ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("气泡聊天由管理员控制开关",
                        NamedTextColor.GRAY)));
            }
            case "off", "disable", "关", "禁用" -> {
                player.sendMessage(Component.text("[STARCORE] ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("气泡聊天由管理员控制开关",
                        NamedTextColor.GRAY)));
            }
            default -> {
                player.sendMessage(Component.text("[STARCORE] ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("用法: /chat bubble [on|off]",
                        NamedTextColor.RED)));
            }
        }
    }

    /**
     * 处理关键词高亮命令
     */
    private void handleHighlightCommand(Player player, String[] args) {
        if (args.length < 2) {
            // 显示高亮状态
            boolean enabled = chatFormatter.isKeywordHighlightEnabled();
            Component status = Component.text()
                .append(Component.text("[STARCORE] ", NamedTextColor.GOLD))
                .append(Component.text("关键词高亮: ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "已启用" : "已禁用",
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .appendNewline()
                .append(Component.text("可用高亮规则: ", NamedTextColor.GRAY))
                .build();

            player.sendMessage(status);

            // 显示关键词列表
            for (ChatFormatterService.KeywordHighlight kw :
                    chatFormatter.getKeywordHighlights()) {
                player.sendMessage(Component.text()
                    .appendSpace()
                    .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(kw.name(), kw.color())
                        .hoverEvent(HoverEvent.showText(
                            Component.text("正则: " + kw.pattern().pattern())
                                .color(NamedTextColor.GRAY)))));
            }
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "list" -> {
                player.sendMessage(Component.text("[STARCORE] 关键词高亮规则:")
                    .color(NamedTextColor.GOLD));
                for (ChatFormatterService.KeywordHighlight kw :
                        chatFormatter.getKeywordHighlights()) {
                    player.sendMessage(Component.text()
                        .append(Component.text(kw.name() + ": ").color(kw.color()))
                        .append(Component.text(kw.pattern().pattern(),
                            NamedTextColor.GRAY)));
                }
            }
            default -> {
                player.sendMessage(Component.text("[STARCORE] ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("用法: /chat highlight [list]",
                        NamedTextColor.RED)));
            }
        }
    }

    /**
     * 处理重载命令（管理员）
     */
    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("starcore.admin")) {
            player.sendMessage(Component.text("[STARCORE] ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("你没有权限执行此命令",
                    NamedTextColor.RED)));
            return;
        }

        chatFormatter.reload();
        player.sendMessage(Component.text("[STARCORE] ")
            .color(NamedTextColor.GOLD)
            .append(Component.text("聊天配置已重载",
                NamedTextColor.GREEN)));
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        Component help = Component.text()
            .append(Component.text("=== STARCORE 聊天系统 ===", NamedTextColor.GOLD))
            .appendNewline()
            .append(Component.text("/chat channel [频道] ", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(
                    Component.text("切换聊天频道\n可用: global, nation, local, party, guild")))
                .clickEvent(ClickEvent.suggestCommand("/chat channel ")))
            .append(Component.text("- 切换聊天频道", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/chat bubble ", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(
                    Component.text("查看气泡聊天设置")))
                .clickEvent(ClickEvent.suggestCommand("/chat bubble ")))
            .append(Component.text("- 气泡聊天设置", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/chat highlight [list] ", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(
                    Component.text("查看关键词高亮规则")))
                .clickEvent(ClickEvent.suggestCommand("/chat highlight list")))
            .append(Component.text("- 关键词高亮", NamedTextColor.GRAY))
            .build();

        player.sendMessage(help);

        // 显示当前状态
        ChatFormatterService.ChatChannel currentChannel =
            chatFormatter.getPlayerChannel(player.getUniqueId());
        player.sendMessage(Component.text()
            .append(Component.text("当前频道: ", NamedTextColor.GRAY))
            .append(Component.text(currentChannel.getDisplayName(), NamedTextColor.YELLOW)));
    }

    /**
     * 发送频道切换按钮
     */
    private void sendChannelButtons(Player player) {
        Component buttons = Component.text()
            .append(Component.text("切换频道: ", NamedTextColor.GOLD))
            .appendNewline()
            .build();

        player.sendMessage(buttons);

        for (ChatFormatterService.ChatChannel channel : ChatFormatterService.ChatChannel.values()) {
            ChatFormatterService.ChatChannel current =
                chatFormatter.getPlayerChannel(player.getUniqueId());

            NamedTextColor color = channel == current ?
                NamedTextColor.GREEN : NamedTextColor.GRAY;
            String prefix = channel == current ? ">" : " ";

            Component button = Component.text()
                .append(Component.text(prefix + " ", NamedTextColor.WHITE))
                .append(Component.text("[" + channel.getDisplayName() + "] ", color)
                    .clickEvent(ClickEvent.runCommand("/chat channel " +
                        channel.name().toLowerCase()))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("点击切换到 " + channel.getDisplayName() + " 频道")
                            .color(NamedTextColor.GRAY))))
                .build();

            player.sendMessage(button);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
            @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("channel", "bubble", "highlight"));
            if (sender.hasPermission("starcore.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            switch (subCmd) {
                case "channel", "ch" -> {
                    completions.addAll(Arrays.asList(
                        "global", "nation", "local", "party", "guild"));
                }
                case "bubble" -> {
                    completions.addAll(Arrays.asList("on", "off"));
                }
                case "highlight" -> {
                    completions.add("list");
                }
            }
        }

        // 过滤匹配
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .toList();
    }
}