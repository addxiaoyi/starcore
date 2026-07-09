package dev.starcore.starcore.module.nation.statusbar;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 国家状态栏命令
 * /nation statusbar [on|off|mode|refresh]
 */
public class NationStatusBarCommand implements CommandExecutor, TabCompleter {

    private final NationStatusBarService statusBarService;
    private final NationService nationService;
    private final MessageService messages;
    private final Plugin plugin;

    // 玩家状态栏开关状态
    private final java.util.Map<java.util.UUID, Boolean> playerEnabled = new java.util.concurrent.ConcurrentHashMap<>();

    public NationStatusBarCommand(
            NationStatusBarService statusBarService,
            NationService nationService,
            MessageService messages,
            Plugin plugin) {
        this.statusBarService = statusBarService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = plugin;

        // 注册命令 - 使用 JavaPlugin 类型获取命令
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            org.bukkit.command.PluginCommand command = javaPlugin.getCommand("nationstatusbar");
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
            org.bukkit.command.PluginCommand nsbCommand = javaPlugin.getCommand("nsb");
            if (nsbCommand != null) {
                nsbCommand.setExecutor(this);
                nsbCommand.setTabCompleter(this);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.player-only"));
            return true;
        }

        // 检查玩家是否在国家中
        if (nationService.nationOf(player.getUniqueId()).isEmpty()) {
            player.sendMessage(messages.format("nation.statusbar.not-in-nation"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on" -> {
                playerEnabled.put(player.getUniqueId(), true);
                statusBarService.showStatusBar(player);
                player.sendMessage(messages.format("nation.statusbar.enabled"));
            }
            case "off" -> {
                playerEnabled.put(player.getUniqueId(), false);
                statusBarService.hideStatusBar(player);
                player.sendMessage(messages.format("nation.statusbar.disabled"));
            }
            case "toggle" -> {
                boolean current = playerEnabled.getOrDefault(player.getUniqueId(), true);
                playerEnabled.put(player.getUniqueId(), !current);
                if (!current) {
                    statusBarService.showStatusBar(player);
                    player.sendMessage(messages.format("nation.statusbar.enabled"));
                } else {
                    statusBarService.hideStatusBar(player);
                    player.sendMessage(messages.format("nation.statusbar.disabled"));
                }
            }
            case "mode" -> {
                if (args.length < 2) {
                    showCurrentMode(player);
                } else {
                    setMode(player, args[1]);
                }
            }
            case "refresh" -> {
                statusBarService.updateStatusBar(player);
                player.sendMessage(messages.format("nation.statusbar.refreshed"));
            }
            case "list" -> {
                listModes(player);
            }
            case "reputation" -> {
                showReputation(player);
            }
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6========== 国家状态栏 ==========");
        // 分隔
        player.sendMessage("§e/nsb on §7- 开启状态栏");
        player.sendMessage("§e/nsb off §7- 关闭状态栏");
        player.sendMessage("§e/nsb toggle §7- 切换状态栏开关");
        // 分隔
        player.sendMessage("§e/nsb mode [模式] §7- 设置显示模式");
        player.sendMessage("§e/nsb list §7- 查看所有模式");
        player.sendMessage("§e/nsb refresh §7- 刷新状态栏");
        player.sendMessage("§e/nsb reputation §7- 查看声望信息");
        // 分隔
        player.sendMessage("§7当前模式: §f" + statusBarService.getDisplayMode().name().toLowerCase());
        player.sendMessage("§6================================");
    }

    /**
     * 显示当前模式
     */
    private void showCurrentMode(Player player) {
        NationStatusBarService.NationStatusBarMode mode = statusBarService.getDisplayMode();
        player.sendMessage("§6当前显示模式: §e" + mode.name().toLowerCase());
        // 分隔
        player.sendMessage("§7模式说明:");
        player.sendMessage("  §fstandard §7- 标准模式");
        player.sendMessage("  §fdetailed §7- 详细模式");
        player.sendMessage("  §fcompact §7- 紧凑模式");
        player.sendMessage("  §fmilitary §7- 军事模式");
        // 分隔
        player.sendMessage("§e/nsb mode [模式] §7- 切换模式");
    }

    /**
     * 设置显示模式
     */
    private void setMode(Player player, String modeName) {
        try {
            NationStatusBarService.NationStatusBarMode mode =
                    NationStatusBarService.NationStatusBarMode.valueOf(modeName.toUpperCase());
            statusBarService.setDisplayMode(mode);
            player.sendMessage(messages.format("nation.statusbar.mode-changed", mode.name().toLowerCase()));
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的模式: " + modeName);
            player.sendMessage("§e使用 /nsb list 查看可用模式");
        }
    }

    /**
     * 列出所有模式
     */
    private void listModes(Player player) {
        player.sendMessage("§6========== 可用模式 ==========");
        // 分隔
        for (NationStatusBarService.NationStatusBarMode mode :
                NationStatusBarService.NationStatusBarMode.values()) {
            String desc = switch (mode) {
                case STANDARD -> "§7国家名|国库|领土|军队|成员";
                case DETAILED -> "§7多行详细显示所有信息";
                case COMPACT -> "§7最小化显示关键数据";
                case MILITARY -> "§7侧重军事信息";
            };
            String prefix = statusBarService.getDisplayMode() == mode ? "§a▶ " : "  ";
            player.sendMessage(prefix + "§e" + mode.name().toLowerCase() + " §f- " + desc);
        }
        // 分隔
        player.sendMessage("§e/nsb mode [模式] §7- 切换模式");
        player.sendMessage("§6================================");
    }

    /**
     * 显示声望信息
     */
    private void showReputation(Player player) {
        // 获取玩家声望（需要 ReputationService）
        player.sendMessage("§6========== 声望信息 ==========");
        player.sendMessage("§7(详细声望信息需要声望系统支持)");
        // 分隔
        player.sendMessage("§e声望影响:");
        player.sendMessage("  §f- 国家成员招募效率");
        player.sendMessage("  §f- 外交关系初始好感度");
        player.sendMessage("  §f- 商店交易折扣");
        player.sendMessage("  §f- 特殊事件触发概率");
        player.sendMessage("§6================================");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off", "toggle", "mode", "refresh", "list", "reputation");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return Arrays.stream(NationStatusBarService.NationStatusBarMode.values())
                    .map(m -> m.name().toLowerCase())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 检查玩家是否开启了状态栏
     */
    public boolean isEnabled(Player player) {
        return playerEnabled.getOrDefault(player.getUniqueId(), true);
    }
}