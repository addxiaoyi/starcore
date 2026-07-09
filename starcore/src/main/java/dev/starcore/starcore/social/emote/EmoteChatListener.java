package dev.starcore.starcore.social.emote;

import dev.starcore.starcore.social.emote.gui.EmoteMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天整合：识别动作指令并在聊天中显示
 */
public class EmoteChatListener implements Listener {
    private final EmoteService emoteService;
    private final EmoteMenu emoteMenu;
    private final Map<String, String> emoteAliases = new ConcurrentHashMap<>();
    private final Pattern emotePattern = Pattern.compile(":([a-zA-Z0-9_]+):");
    private final Set<String> blockedCommands = Set.of(
        "msg", "tell", "w", "r", "reply", "party", "guild", "friend"
    );

    public EmoteChatListener(EmoteService emoteService, EmoteMenu emoteMenu) {
        this.emoteService = emoteService;
        this.emoteMenu = emoteMenu;
        registerAliases();
    }

    /**
     * 注册动作别名（快捷方式）
     */
    private void registerAliases() {
        // 问候
        emoteAliases.put("wave", "wave");
        emoteAliases.put("hi", "wave");
        emoteAliases.put("hello", "wave");
        emoteAliases.put("bow", "bow");
        emoteAliases.put("respect", "bow");
        emoteAliases.put("salute", "salute");

        // 情感
        emoteAliases.put("laugh", "laugh");
        emoteAliases.put("lol", "laugh");
        emoteAliases.put("lmao", "laugh");
        emoteAliases.put("cry", "cry");
        emoteAliases.put("sad", "cry");
        emoteAliases.put("angry", "angry");
        emoteAliases.put("mad", "angry");
        emoteAliases.put("clap", "clap");
        emoteAliases.put("applause", "clap");

        // 社交
        emoteAliases.put("hug", "hug");
        emoteAliases.put("kiss", "kiss");
        emoteAliases.put("love", "kiss");
        emoteAliases.put("dance", "dance");
        emoteAliases.put("sit", "sit");

        // 动作
        emoteAliases.put("point", "point");
        emoteAliases.put("thumbsup", "thumbsup");
        emoteAliases.put("like", "thumbsup");
        emoteAliases.put("facepalm", "facepalm");
        emoteAliases.put("sleep", "sleep");
        emoteAliases.put("zzz", "sleep");
        emoteAliases.put("eat", "eat");
        emoteAliases.put("drink", "drink");
        emoteAliases.put("spin", "spin");

        // 战斗
        emoteAliases.put("sword", "sword");
        emoteAliases.put("shield", "shield");
        emoteAliases.put("attack", "attack");
    }

    /**
     * 处理聊天消息，识别动作
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(PlayerChatEvent event) {
        String message = event.getMessage();
        Player player = event.getPlayer();

        // 检查是否包含动作快捷方式
        Matcher matcher = emotePattern.matcher(message);
        if (matcher.find()) {
            // 发现动作标记，提取并处理
            String emoteName = matcher.group(1).toLowerCase();
            String resolvedId = resolveEmoteAlias(emoteName);

            if (resolvedId != null) {
                EmoteService.EmoteResult result = emoteService.executeEmote(player, resolvedId, null);

                if (result == EmoteService.EmoteResult.SUCCESS) {
                    // 替换消息中的动作标记为格式化文本
                    String formattedEmote = formatEmoteInChat(player, resolvedId);
                    String newMessage = message.replaceAll(":" + Pattern.quote(emoteName) + ":", formattedEmote);
                    event.setMessage(newMessage);
                } else {
                    // 发送错误消息
                    sendEmoteError(player, result);
                    // 移除无效的动作标记
                    event.setMessage(message.replaceAll(":" + Pattern.quote(emoteName) + ":", ""));
                }
            }
        }
    }

    /**
     * 处理命令前的动作快捷指令（如 /wave, /hug player）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        // 检查是否是动作命令（以 /e 或 /emote 开头）
        if (command.startsWith("/e ") || command.startsWith("/emote ")) {
            event.setCancelled(true);
            String[] args = command.substring(command.indexOf(' ') + 1).split(" ", 2);

            String emoteName = args[0];
            String targetName = args.length > 1 ? args[1] : null;

            Player target = targetName != null ? Bukkit.getPlayer(targetName) : null;

            executeEmoteCommand(player, emoteName, target);
        }
    }

    /**
     * 执行动作命令
     */
    private void executeEmoteCommand(Player player, String emoteName, Player target) {
        String resolvedId = resolveEmoteAlias(emoteName);
        if (resolvedId == null) {
            resolvedId = emoteName;
        }

        EmoteService.EmoteResult result = emoteService.executeEmote(player, resolvedId, target);
        sendEmoteFeedback(player, resolvedId, target, result);
    }

    /**
     * 解析动作别名
     */
    private String resolveEmoteAlias(String alias) {
        return emoteAliases.get(alias.toLowerCase());
    }

    /**
     * 在聊天中格式化动作显示
     */
    private String formatEmoteInChat(Player player, String emoteId) {
        return emoteService.getEmote(emoteId)
            .map(emote -> String.format("§6*%s %s§6*",
                player.getName(), emote.getName()))
            .orElse("*执行动作*");
    }

    /**
     * 发送动作执行反馈
     */
    private void sendEmoteFeedback(Player player, String emoteId, Player target, EmoteService.EmoteResult result) {
        String prefix = "§6[动作] §f";

        switch (result) {
            case SUCCESS -> {
                emoteService.getEmote(emoteId).ifPresent(emote -> {
                    if (target != null) {
                        player.sendMessage(prefix + "你对 " + target.getName() + " §f执行了 §a" + emote.getName() + "§f!");
                    } else {
                        player.sendMessage(prefix + "你执行了 §a" + emote.getName() + "§f!");
                    }
                });
            }
            case EMOTE_NOT_FOUND -> player.sendMessage(prefix + "§c未找到该动作");
            case NO_PERMISSION -> player.sendMessage(prefix + "§c你没有权限使用该动作");
            case ON_COOLDOWN -> player.sendMessage(prefix + "§e该动作正在冷却中");
            case TARGET_REQUIRED -> player.sendMessage(prefix + "§e该动作需要指定目标玩家");
            case INVALID_TARGET -> player.sendMessage(prefix + "§c不能对自己使用该动作");
            case PLAYER_NOT_FOUND -> player.sendMessage(prefix + "§c未找到目标玩家");
        }
    }

    /**
     * 发送动作错误
     */
    private void sendEmoteError(Player player, EmoteService.EmoteResult result) {
        String prefix = "§6[动作] §f";
        switch (result) {
            case ON_COOLDOWN -> player.sendMessage(prefix + "§e该动作正在冷却中");
            case NO_PERMISSION -> player.sendMessage(prefix + "§c你没有权限使用该动作");
            default -> player.sendMessage(prefix + "§c无法执行该动作");
        }
    }

    /**
     * 阻止移动时打断某些动作
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 对于坐下、睡觉等姿势动作，如果玩家移动则取消
        Player player = event.getPlayer();
        emoteService.getPlayerState(player.getUniqueId()).ifPresent(state -> {
            if (state.isAnimating() && state.getCurrentEmoteId() != null) {
                emoteService.getEmote(state.getCurrentEmoteId()).ifPresent(emote -> {
                    // 移动取消的动作类型
                    if (emote.getAnimationData().equalsIgnoreCase("SIT") ||
                        emote.getAnimationData().equalsIgnoreCase("SLEEP")) {
                        if (event.getFrom().getX() != event.getTo().getX() ||
                            event.getFrom().getZ() != event.getTo().getZ()) {
                            state.clearEmote();
                            player.sendMessage("§6[动作] §f姿势已被移动打断");
                        }
                    }
                });
            }
        });
    }

    /**
     * 构建动作菜单（用于GUI）
     */
    public Component buildEmoteMenu() {
        Component menu = Component.text("=== 动作菜单 ===", NamedTextColor.GOLD, TextDecoration.BOLD)
            .appendNewline();

        Map<String, List<EmoteDefinition>> categorized = new TreeMap<>();
        for (EmoteDefinition emote : emoteService.getAllEmotes()) {
            categorized.computeIfAbsent(emote.getCategory(), k -> new ArrayList<>()).add(emote);
        }

        for (Map.Entry<String, List<EmoteDefinition>> entry : categorized.entrySet()) {
            String category = entry.getKey();
            menu = menu.appendNewline()
                .append(Component.text("【" + category.toUpperCase() + "】", NamedTextColor.YELLOW, TextDecoration.ITALIC))
                .appendNewline();

            for (EmoteDefinition emote : entry.getValue()) {
                String clickHint = "/e " + emote.getId();
                Component emoteItem = Component.text("  - " + emote.getName(), NamedTextColor.GREEN)
                    .hoverEvent(HoverEvent.showText(Component.text(emote.getDescription() + "\n点击执行: " + clickHint)))
                    .clickEvent(ClickEvent.suggestCommand(clickHint));
                menu = menu.append(emoteItem).appendNewline();
            }
        }

        return menu;
    }
}
