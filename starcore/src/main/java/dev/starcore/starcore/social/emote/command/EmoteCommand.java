package dev.starcore.starcore.social.emote.command;
import java.util.Optional;

import dev.starcore.starcore.social.emote.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动作命令处理器
 */
public class EmoteCommand implements CommandExecutor, TabCompleter {
    private final EmoteService emoteService;
    private final CustomEmoteManager customEmoteManager;
    private final EmoteCooldownManager cooldownManager;

    public EmoteCommand(EmoteService emoteService, CustomEmoteManager customEmoteManager,
                        EmoteCooldownManager cooldownManager) {
        this.emoteService = emoteService;
        this.customEmoteManager = customEmoteManager;
        this.cooldownManager = cooldownManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "ls" -> sendEmoteList(player);
            case "info", "i" -> handleInfo(player, args);
            case "cooldown", "cd" -> sendCooldownInfo(player);
            case "create", "add", "new" -> handleCreate(player, args);
            case "delete", "del", "remove" -> handleDelete(player, args);
            case "my" -> sendPlayerEmotes(player);
            case "public" -> sendPublicEmotes(player);
            case "reload" -> handleReload(player);
            case "clearcd" -> handleClearCooldown(player, args);
            default -> {
                // 尝试执行动作
                handleEmoteExecute(player, subCommand, args);
            }
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== 动作系统帮助 ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/emote list", NamedTextColor.GREEN)
            .append(Component.text(" - 查看所有动作", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote <动作名>", NamedTextColor.GREEN)
            .append(Component.text(" - 执行动作", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote <动作名> <玩家>", NamedTextColor.GREEN)
            .append(Component.text(" - 对玩家执行动作", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote info <动作名>", NamedTextColor.GREEN)
            .append(Component.text(" - 查看动作详情", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote cooldown", NamedTextColor.GREEN)
            .append(Component.text(" - 查看冷却状态", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote create", NamedTextColor.YELLOW)
            .append(Component.text(" - 创建自定义动作", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote my", NamedTextColor.YELLOW)
            .append(Component.text(" - 查看我的自定义动作", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/emote public", NamedTextColor.YELLOW)
            .append(Component.text(" - 查看公开动作", NamedTextColor.GRAY)));
    }

    /**
     * 发送动作列表
     */
    private void sendEmoteList(Player player) {
        player.sendMessage(Component.text("=== 动作列表 ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        Map<String, List<EmoteDefinition>> categorized = new TreeMap<>();
        for (EmoteDefinition emote : emoteService.getAllEmotes()) {
            if (player.hasPermission("starcore.emote.*") ||
                emote.getPermission().isEmpty() ||
                player.hasPermission(emote.getPermission())) {
                categorized.computeIfAbsent(emote.getCategory(), k -> new ArrayList<>()).add(emote);
            }
        }

        for (Map.Entry<String, List<EmoteDefinition>> entry : categorized.entrySet()) {
            player.sendMessage(Component.text("【" + entry.getKey().toUpperCase() + "】",
                NamedTextColor.YELLOW, TextDecoration.ITALIC));

            String emoteList = entry.getValue().stream()
                .map(e -> e.getName() + " (§e" + e.getId() + "§f)")
                .collect(Collectors.joining(" §7| "));
            player.sendMessage(Component.text("  " + emoteList, NamedTextColor.GREEN));
        }

        player.sendMessage(Component.text("\n提示: 使用 §e/emote <动作名> §f执行动作",
            NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    /**
     * 处理动作详情
     */
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /emote info <动作名>", NamedTextColor.RED));
            return;
        }

        String emoteId = args[1].toLowerCase();
        Optional<EmoteDefinition> emoteOpt = emoteService.getEmote(emoteId);

        if (emoteOpt.isEmpty()) {
            player.sendMessage(Component.text("未找到动作: " + emoteId, NamedTextColor.RED));
            return;
        }

        EmoteDefinition emote = emoteOpt.get();
        player.sendMessage(Component.text("=== " + emote.getName() + " ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("ID: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.getId(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("分类: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.getCategory(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("描述: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.getDescription(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("动画类型: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.getAnimationType(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("冷却时间: ", NamedTextColor.YELLOW)
            .append(Component.text(cooldownManager.formatCooldownTime(emote.getCooldownSeconds()),
                NamedTextColor.WHITE)));
        player.sendMessage(Component.text("持续时间: ", NamedTextColor.YELLOW)
            .append(Component.text((emote.getDurationTicks() / 20.0) + "秒", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("需要目标: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.requiresTarget() ? "是" : "否", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("全局可见: ", NamedTextColor.YELLOW)
            .append(Component.text(emote.isGlobal() ? "是" : "否", NamedTextColor.WHITE)));
    }

    /**
     * 发送冷却信息
     */
    private void sendCooldownInfo(Player player) {
        Map<String, Integer> cooldowns = cooldownManager.getPlayerCooldowns(player.getUniqueId());

        if (cooldowns.isEmpty()) {
            player.sendMessage(Component.text("你没有正在冷却的动作", NamedTextColor.GREEN));
            return;
        }

        player.sendMessage(Component.text("=== 冷却中的动作 ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            String emoteName = emoteService.getEmote(entry.getKey())
                .map(EmoteDefinition::getName)
                .orElse(entry.getKey());
            player.sendMessage(Component.text("  " + emoteName + ": ", NamedTextColor.YELLOW)
                .append(Component.text(cooldownManager.formatCooldownTime(entry.getValue()),
                    NamedTextColor.RED)));
        }
    }

    /**
     * 处理动作执行
     */
    private void handleEmoteExecute(Player player, String emoteId, String[] args) {
        Player target = null;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("未找到玩家: " + args[1], NamedTextColor.RED));
                return;
            }
        }

        EmoteService.EmoteResult result = emoteService.executeEmote(player, emoteId, target);

        switch (result) {
            case SUCCESS -> {
                EmoteDefinition emote = emoteService.getEmote(emoteId).orElse(null);
                if (emote != null) {
                    if (target != null) {
                        player.sendMessage(Component.text("你对 " + target.getName() + " 执行了 " +
                            emote.getName(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("你执行了 " + emote.getName(), NamedTextColor.GREEN));
                    }
                }
            }
            case EMOTE_NOT_FOUND ->
                player.sendMessage(Component.text("未找到动作: " + emoteId, NamedTextColor.RED));
            case NO_PERMISSION ->
                player.sendMessage(Component.text("你没有权限使用该动作", NamedTextColor.RED));
            case ON_COOLDOWN -> {
                int remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), emoteId);
                player.sendMessage(Component.text("该动作冷却中，剩余: " +
                    cooldownManager.formatCooldownTime(remaining), NamedTextColor.YELLOW));
            }
            case TARGET_REQUIRED ->
                player.sendMessage(Component.text("该动作需要指定目标玩家", NamedTextColor.YELLOW));
            case INVALID_TARGET ->
                player.sendMessage(Component.text("不能对自己使用该动作", NamedTextColor.RED));
            case PLAYER_NOT_FOUND ->
                player.sendMessage(Component.text("未找到目标玩家", NamedTextColor.RED));
        }
    }

    /**
     * 处理创建自定义动作
     */
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /emote create <名称> [动画类型] [持续秒数]",
                NamedTextColor.YELLOW));
            player.sendMessage(Component.text("示例: /emote create 我的动作 pose 5",
                NamedTextColor.GRAY));
            return;
        }

        String name = args[1];
        String animationType = args.length > 2 ? args[2] : "pose";
        int duration = args.length > 3 ? Math.max(1, Math.min(60, Integer.parseInt(args[3]))) : 5;
        int cooldown = args.length > 4 ? Integer.parseInt(args[4]) : 10;

        CustomEmoteManager.CustomEmote customEmote = customEmoteManager.createCustomEmote(
            player.getUniqueId(),
            name,
            "玩家 " + player.getName() + " 创建的自定义动作",
            animationType,
            animationType.toUpperCase(),
            duration * 20,
            cooldown,
            true // 默认公开
        );

        player.sendMessage(Component.text("自定义动作创建成功!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("ID: " + customEmote.getId(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("使用 /emote " + customEmote.getId() + " 执行", NamedTextColor.GRAY));
    }

    /**
     * 处理删除自定义动作
     */
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /emote delete <动作ID>", NamedTextColor.YELLOW));
            return;
        }

        String emoteId = args[1];
        if (customEmoteManager.deleteCustomEmote(emoteId, player.getUniqueId())) {
            player.sendMessage(Component.text("自定义动作已删除", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("删除失败：你不是该动作的创建者",
                NamedTextColor.RED));
        }
    }

    /**
     * 发送玩家的自定义动作
     */
    private void sendPlayerEmotes(Player player) {
        List<CustomEmoteManager.CustomEmote> playerEmotes =
            customEmoteManager.getPlayerCustomEmotes(player.getUniqueId());

        if (playerEmotes.isEmpty()) {
            player.sendMessage(Component.text("你还没有创建自定义动作", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("使用 /emote create <名称> 创建", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 我的自定义动作 ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (CustomEmoteManager.CustomEmote emote : playerEmotes) {
            player.sendMessage(Component.text("  " + emote.getName() + " (", NamedTextColor.GREEN)
                .append(Component.text(emote.getId(), NamedTextColor.YELLOW))
                .append(Component.text(") - " + emote.getDescription(), NamedTextColor.GRAY)));
        }
    }

    /**
     * 发送公开动作列表
     */
    private void sendPublicEmotes(Player player) {
        List<CustomEmoteManager.CustomEmote> publicEmotes =
            customEmoteManager.getPublicCustomEmotes();

        if (publicEmotes.isEmpty()) {
            player.sendMessage(Component.text("当前没有公开的自定义动作", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 公开自定义动作 ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (CustomEmoteManager.CustomEmote emote : publicEmotes) {
            String creatorName = Bukkit.getOfflinePlayer(emote.getCreatorId()).getName();
            player.sendMessage(Component.text("  " + emote.getName() + " (", NamedTextColor.GREEN)
                .append(Component.text(emote.getId(), NamedTextColor.YELLOW))
                .append(Component.text(") - by " + creatorName, NamedTextColor.GRAY)));
        }
    }

    /**
     * 处理重载
     */
    private void handleReload(Player player) {
        if (!player.hasPermission("starcore.admin")) {
            player.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return;
        }

        cooldownManager.cleanupExpiredCooldowns();
        player.sendMessage(Component.text("动作系统已重载", NamedTextColor.GREEN));
    }

    /**
     * 处理清除冷却
     */
    private void handleClearCooldown(Player player, String[] args) {
        if (!player.hasPermission("starcore.admin")) {
            player.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return;
        }

        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                cooldownManager.clearPlayerCooldowns(target.getUniqueId());
                player.sendMessage(Component.text("已清除 " + target.getName() + " 的所有冷却",
                    NamedTextColor.GREEN));
            }
        } else {
            cooldownManager.clearPlayerCooldowns(player.getUniqueId());
            player.sendMessage(Component.text("已清除你的所有冷却", NamedTextColor.GREEN));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "list", "info", "cooldown", "create", "delete", "my", "public", "reload"
            ));
            // 添加所有动作名
            for (EmoteDefinition emote : emoteService.getAllEmotes()) {
                completions.add(emote.getId());
            }
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "info", "delete" -> {
                    for (EmoteDefinition emote : emoteService.getAllEmotes()) {
                        completions.add(emote.getId());
                    }
                }
                case "clearcd" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
                case "create" -> {
                    completions.addAll(Arrays.asList("pose", "arm", "fullbody", "particle"));
                }
                default -> {
                    // 动作名作为玩家名补全
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
            }
            return filterStartsWith(completions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            completions.addAll(Arrays.asList("3", "5", "10", "15", "30"));
            return filterStartsWith(completions, args[2]);
        }

        return completions;
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .sorted()
            .collect(Collectors.toList());
    }
}
