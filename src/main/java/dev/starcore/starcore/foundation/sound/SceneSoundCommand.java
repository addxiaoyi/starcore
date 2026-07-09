package dev.starcore.starcore.foundation.sound;

import dev.starcore.starcore.foundation.sound.SceneSoundService.SoundScene;
import dev.starcore.starcore.foundation.sound.SceneSoundService.SoundSceneGroup;
import dev.starcore.starcore.foundation.sound.SceneSoundService.SoundPreferences;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 场景化音效命令处理器
 * 提供玩家配置界面和管理命令
 */
public final class SceneSoundCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final SceneSoundService soundService;
    private final SceneSoundConfig soundConfig;
    private final Logger logger;

    // 命令常量
    private static final String CMD_ROOT = "scsound";
    private static final String CMD_ALIAS = "starsound";

    public SceneSoundCommand(JavaPlugin plugin, SceneSoundService soundService, SceneSoundConfig soundConfig) {
        this.plugin = plugin;
        this.soundService = soundService;
        this.soundConfig = soundConfig;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "help" -> sendHelp(sender);
            case "test" -> handleTest(sender, args);
            case "play" -> handlePlay(sender, args);
            case "list" -> handleList(sender, args);
            case "volume" -> handleVolume(sender, args);
            case "pitch" -> handlePitch(sender, args);
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "custom" -> handleCustom(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUnknownCommand(sender);
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== §eStarCore 音效系统 §6==========");
        // 分隔
        sender.sendMessage("§e/scsound test <场景> §7- 测试指定场景的音效");
        sender.sendMessage("§e/scsound play <场景> §7- 播放指定场景的音效");
        sender.sendMessage("§e/scsound list [分组] §7- 列出所有场景或指定分组");
        sender.sendMessage("§e/scsound volume <0.0-2.0> §7- 设置主音量");
        sender.sendMessage("§e/scsound pitch <-1.0-1.0> §7- 设置音调偏移");
        sender.sendMessage("§e/scsound mute [分组] §7- 静音全部或指定分组");
        sender.sendMessage("§e/scsound unmute [分组] §7- 取消静音全部或指定分组");
        sender.sendMessage("§e/scsound custom <场景> <音效名> §7- 自定义场景音效");
        sender.sendMessage("§e/scsound reset §7- 重置所有音效偏好");
        sender.sendMessage("§e/scsound reload §7- 重载音效配置 (仅管理员)");
        // 分隔
        sender.sendMessage("§7场景分组: §fUI, NAVIGATION, NATION, TERRITORY, RESOURCE");
        sender.sendMessage("§7           DIPLOMACY, WAR, ECONOMY, TECHNOLOGY, POLICY");
        sender.sendMessage("§7           OFFICER, SOCIAL, ACHIEVEMENT, ITEM, MOVEMENT");
        sender.sendMessage("§7           NOTIFICATION, BIOME, SPECIAL");
        sender.sendMessage("§6========================================");
    }

    /**
     * 处理测试命令
     */
    private void handleTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /scsound test <场景名>");
            player.sendMessage("§7使用 /scsound list 查看所有场景");
            return;
        }

        SoundScene scene = parseScene(args[1]);
        if (scene == null) {
            player.sendMessage("§c未知的场景: " + args[1]);
            player.sendMessage("§7使用 /scsound list 查看所有场景");
            return;
        }

        soundService.playSound(player, scene);
        player.sendMessage("§a已播放测试音效: §e" + scene.name() + " §7(" + scene.getDefaultSound() + ")");
    }

    /**
     * 处理播放命令
     */
    private void handlePlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /scsound play <场景名>");
            return;
        }

        SoundScene scene = parseScene(args[1]);
        if (scene == null) {
            player.sendMessage("§c未知的场景: " + args[1]);
            return;
        }

        soundService.playSound(player, scene);
        player.sendMessage("§a已播放: §e" + scene.getConfigKey());
    }

    /**
     * 处理列表命令
     */
    private void handleList(CommandSender sender, String[] args) {
        sender.sendMessage("§6========== 场景音效列表 ==========");

        if (args.length > 1) {
            // 列出指定分组
            SoundSceneGroup group = parseGroup(args[1]);
            if (group == null) {
                sender.sendMessage("§c未知的分组: " + args[1]);
                return;
            }

            sender.sendMessage("§e分组: " + group.name());
            for (SoundScene scene : group.getScenes()) {
                sender.sendMessage("§7  " + scene.getConfigKey() + " §f-> §e" + scene.getDefaultSound());
            }
        } else {
            // 列出所有分组
            for (SoundSceneGroup group : SoundSceneGroup.values()) {
                StringBuilder scenes = new StringBuilder();
                for (SoundScene scene : group.getScenes()) {
                    if (scenes.length() > 0) scenes.append("§7, ");
                    scenes.append("§e").append(scene.getConfigKey());
                }
                sender.sendMessage("§b[" + group.name() + "] §f" + scenes);
            }
        }

        sender.sendMessage("§6================================");
    }

    /**
     * 处理音量命令
     */
    private void handleVolume(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length < 2) {
            SoundPreferences prefs = soundService.getPreferences(player);
            player.sendMessage("§6当前音效设置:");
            player.sendMessage("§e主音量: §f" + String.format("%.1f", prefs.getMasterVolume() * 100) + "%");
            player.sendMessage("§eUI音量: §f" + String.format("%.1f", prefs.getUiVolume() * 100) + "%");
            player.sendMessage("§e世界音量: §f" + String.format("%.1f", prefs.getWorldVolume() * 100) + "%");
            player.sendMessage("§e通知音量: §f" + String.format("%.1f", prefs.getNotificationVolume() * 100) + "%");
            return;
        }

        try {
            float volume = Float.parseFloat(args[1]);
            if (volume < 0f || volume > 2f) {
                player.sendMessage("§c音量必须在 0.0 - 2.0 之间");
                return;
            }

            soundService.setMasterVolume(player, volume);
            player.sendMessage("§a主音量已设置为: §e" + String.format("%.1f", volume * 100) + "%");
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数值: " + args[1]);
        }
    }

    /**
     * 处理音调命令
     */
    private void handlePitch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length < 2) {
            SoundPreferences prefs = soundService.getPreferences(player);
            player.sendMessage("§e当前音调偏移: §f" + String.format("%.2f", prefs.getPitchOffset()));
            return;
        }

        try {
            float pitch = Float.parseFloat(args[1]);
            if (pitch < -1f || pitch > 1f) {
                player.sendMessage("§c音调偏移必须在 -1.0 - 1.0 之间");
                return;
            }

            soundService.setPitchOffset(player, pitch);
            player.sendMessage("§a音调偏移已设置为: §e" + String.format("%.2f", pitch));
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数值: " + args[1]);
        }
    }

    /**
     * 处理静音命令
     */
    private void handleMute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length > 1) {
            SoundSceneGroup group = parseGroup(args[1]);
            if (group == null) {
                player.sendMessage("§c未知的分组: " + args[1]);
                return;
            }

            soundService.muteGroup(player, group);
            player.sendMessage("§e已静音分组: §f" + group.name());
        } else {
            soundService.setMuted(player, true);
            player.sendMessage("§c已静音所有音效");
        }
    }

    /**
     * 处理取消静音命令
     */
    private void handleUnmute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length > 1) {
            SoundSceneGroup group = parseGroup(args[1]);
            if (group == null) {
                player.sendMessage("§c未知的分组: " + args[1]);
                return;
            }

            soundService.unmuteGroup(player, group);
            player.sendMessage("§a已取消静音分组: §f" + group.name());
        } else {
            soundService.setMuted(player, false);
            player.sendMessage("§a已取消静音所有音效");
        }
    }

    /**
     * 处理自定义音效命令
     */
    private void handleCustom(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§c用法: /scsound custom <场景> <音效名>");
            player.sendMessage("§7使用 /scsound list 查看所有场景");
            player.sendMessage("§7音效名使用 Bukkit Sound 枚举名，如 ENTITY_EXPERIENCE_ORB_PICKUP");
            return;
        }

        SoundScene scene = parseScene(args[1]);
        if (scene == null) {
            player.sendMessage("§c未知的场景: " + args[1]);
            return;
        }

        String soundName = args[2].toUpperCase();
        if (!soundService.isValidSound(soundName)) {
            player.sendMessage("§c无效的音效名: " + soundName);
            player.sendMessage("§7请使用有效的 Minecraft 音效名称");
            return;
        }

        soundService.setCustomSound(player, scene, soundName);
        player.sendMessage("§a已为场景 §e" + scene.getConfigKey() + " §a设置自定义音效: §f" + soundName);

        // 测试播放
        soundService.playSound(player, scene);
    }

    /**
     * 处理重置命令
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        soundService.resetPreferences(player);
        player.sendMessage("§a已重置所有音效偏好为默认值");
    }

    /**
     * 处理重载命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        soundConfig.load();
        sender.sendMessage("§a音效配置已重载");
    }

    /**
     * 发送未知命令提示
     */
    private void sendUnknownCommand(CommandSender sender) {
        sender.sendMessage("§c未知命令，使用 /scsound help 查看帮助");
    }

    /**
     * 解析场景名称
     */
    private SoundScene parseScene(String name) {
        // 尝试直接匹配
        try {
            return SoundScene.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.fine("Scene not found by enum name: " + name + " - falling back to config key match");
        }

        // 尝试匹配配置键
        for (SoundScene scene : SoundScene.values()) {
            if (scene.getConfigKey().equalsIgnoreCase(name) ||
                scene.getConfigKey().replace(".", "_").equalsIgnoreCase(name)) {
                return scene;
            }
        }

        return null;
    }

    /**
     * 解析分组名称
     */
    private SoundSceneGroup parseGroup(String name) {
        try {
            return SoundSceneGroup.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.fine("Group not found by enum name: " + name);
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0], "help", "test", "play", "list", "volume", "pitch",
                                    "mute", "unmute", "custom", "reset", "reload");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "test", "play", "custom" -> getSceneCompletions(args[1]);
                case "mute", "unmute" -> getGroupCompletions(args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("custom")) {
            return getSoundCompletions(args[2]);
        }

        return Collections.emptyList();
    }

    /**
     * 获取场景自动补全
     */
    private List<String> getSceneCompletions(String prefix) {
        return Arrays.stream(SoundScene.values())
            .map(SoundScene::getConfigKey)
            .filter(key -> key.startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * 获取分组自动补全
     */
    private List<String> getGroupCompletions(String prefix) {
        return Arrays.stream(SoundSceneGroup.values())
            .map(SoundSceneGroup::name)
            .filter(name -> name.startsWith(prefix.toUpperCase()))
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    }

    /**
     * 获取音效自动补全
     */
    private List<String> getSoundCompletions(String prefix) {
        return BukkitSoundResolver.names()
            .filter(name -> name.startsWith(prefix.toUpperCase()))
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    }

    /**
     * 过滤以指定前缀开头的选项
     */
    private List<String> filterStartsWith(String prefix, String... options) {
        return Arrays.stream(options)
            .filter(opt -> opt.startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * 注册命令
     */
    public void register() {
        plugin.getCommand(CMD_ROOT).setExecutor(this);
        plugin.getCommand(CMD_ROOT).setTabCompleter(this);

        // 注册别名
        if (plugin.getCommand(CMD_ALIAS) != null) {
            plugin.getCommand(CMD_ALIAS).setExecutor(this);
            plugin.getCommand(CMD_ALIAS).setTabCompleter(this);
        }
    }

    /**
     * 注销命令
     */
    public void unregister() {
        plugin.getCommand(CMD_ROOT).setExecutor(null);
        plugin.getCommand(CMD_ROOT).setTabCompleter(null);

        if (plugin.getCommand(CMD_ALIAS) != null) {
            plugin.getCommand(CMD_ALIAS).setExecutor(null);
            plugin.getCommand(CMD_ALIAS).setTabCompleter(null);
        }
    }
}
