package dev.starcore.starcore.foundation.i18n.command;

import dev.starcore.starcore.foundation.i18n.I18nManager;
import dev.starcore.starcore.util.MessageUtil;
import org.bukkit.Bukkit;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 语言切换命令
 * 用法:
 * - /language - 查看当前语言
 * - /language list - 查看支持的语言
 * - /language set <locale> - 设置语言
 * - /language reload - 重载语言文件 (仅控制台/管理员)
 * - /language set <player> <locale> - 管理员设置玩家语言
 */
public class LanguageCommand implements CommandExecutor, TabCompleter {

    private final I18nManager i18nManager;

    public LanguageCommand(I18nManager i18nManager) {
        this.i18nManager = i18nManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            // 显示当前语言
            return showCurrentLanguage(sender);
        }

        switch (args[0].toLowerCase()) {
            case "list", "available" -> {
                return showAvailableLanguages(sender);
            }
            case "set" -> {
                return handleSetCommand(sender, args);
            }
            case "reload" -> {
                return handleReloadCommand(sender);
            }
            case "info" -> {
                return showLanguageInfo(sender);
            }
            default -> {
                MessageUtil.sendHeader(sender, "Language");
                MessageUtil.sendLines(sender,
                    "§e/language §7- Show current language",
                    "§e/language list §7- Show available languages",
                    "§e/language set <locale> §7- Set your language",
                    "§e/language info §7- Show language information"
                );
                if (sender.hasPermission("starcore.language.admin")) {
                    MessageUtil.sendLines(sender,
                        "",
                        "§c[Admin]",
                        "§e/language set <player> <locale> §7- Set player's language",
                        "§e/language reload §7- Reload language files"
                    );
                }
                return true;
            }
        }
    }

    /**
     * 显示当前语言
     */
    private boolean showCurrentLanguage(CommandSender sender) {
        Locale currentLocale;
        String displayName;

        if (sender instanceof Player player) {
            currentLocale = i18nManager.getPlayerLocale(player.getUniqueId());
            displayName = i18nManager.getLocaleDisplayName(currentLocale.toString());
            MessageUtil.send(sender, "§7Your current language: §e" + displayName + " §7(" + currentLocale.toString() + ")");
        } else {
            currentLocale = i18nManager.getDefaultLocale();
            displayName = i18nManager.getDefaultLocaleDisplayName();
            MessageUtil.send(sender, "§7Console default language: §e" + displayName + " §7(" + currentLocale.toString() + ")");
        }

        // 显示默认语言
        Locale defaultLocale = i18nManager.getDefaultLocale();
        String defaultDisplayName = i18nManager.getDefaultLocaleDisplayName();
        if (!currentLocale.equals(defaultLocale)) {
            MessageUtil.send(sender, "§7Server default language: §e" + defaultDisplayName);
        }

        return true;
    }

    /**
     * 显示可用的语言列表
     */
    private boolean showAvailableLanguages(CommandSender sender) {
        Map<Locale, String> locales = i18nManager.getSupportedLocalesWithDisplayNames();

        if (locales.isEmpty()) {
            MessageUtil.error(sender, "No languages available!");
            return true;
        }

        MessageUtil.sendHeader(sender, "Available Languages");
        MessageUtil.send(sender, "§7Server default: §e" + i18nManager.getDefaultLocaleDisplayName());

        for (Map.Entry<Locale, String> entry : locales.entrySet()) {
            String code = entry.getKey().toString();
            String name = entry.getValue();
            String marker = entry.getKey().equals(i18nManager.getDefaultLocale()) ? " §7[Default]" : "";
            String playerMarker = "";

            if (sender instanceof Player player) {
                Locale playerLocale = i18nManager.getPlayerLocale(player.getUniqueId());
                if (playerLocale.equals(entry.getKey())) {
                    playerMarker = " §a[Current]";
                }
            }

            MessageUtil.send(sender, "  §e" + code + " §7- §f" + name + marker + playerMarker);
        }

        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "§7Use §e/language set <code> §7to change your language");
        MessageUtil.send(sender, "§7Example: §e/language set en_US");

        return true;
    }

    /**
     * 处理设置语言命令
     */
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.error(sender, "Usage: /language set <locale> [player]");
            MessageUtil.send(sender, "§7Example: §e/language set en_US");
            return true;
        }

        String localeCode = args[1].toLowerCase();

        // 检查是否是管理员设置其他玩家语言
        if (args.length >= 3 && sender.hasPermission("starcore.language.admin")) {
            return setPlayerLanguage(sender, args[2], localeCode);
        }

        // 玩家设置自己的语言
        if (!(sender instanceof Player player)) {
            MessageUtil.error(sender, "Only players can set their language directly.");
            MessageUtil.send(sender, "§7Use: §e/language set <player> <locale>");
            return true;
        }

        return setLanguage(player, localeCode);
    }

    /**
     * 设置玩家语言
     */
    private boolean setLanguage(Player player, String localeCode) {
        if (!i18nManager.isLocaleSupported(localeCode)) {
            MessageUtil.error(player, "Language '" + localeCode + "' is not supported!");
            MessageUtil.send(player, "§7Use §e/language list §7to see available languages.");
            return true;
        }

        Locale locale = i18nManager.parseLocale(localeCode);
        if (locale != null) {
            i18nManager.setPlayerLocale(player.getUniqueId(), locale);
            String displayName = i18nManager.getLocaleDisplayName(localeCode);
            MessageUtil.success(player, "Language changed to: " + displayName + " (" + localeCode + ")");
        } else {
            MessageUtil.error(player, "Invalid language code: " + localeCode);
        }

        return true;
    }

    /**
     * 管理员设置玩家语言
     */
    private boolean setPlayerLanguage(CommandSender sender, String playerName, String localeCode) {
        if (!sender.hasPermission("starcore.language.admin")) {
            MessageUtil.noPermission(sender);
            return true;
        }

        Player target = Bukkit.getPlayer(playerName);
        UUID targetUUID;
        String targetName;

        if (target != null && target.isOnline()) {
            targetUUID = target.getUniqueId();
            targetName = target.getName();
        } else {
            // 尝试从离线玩家获取
            var offlinePlayer = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);

            if (offlinePlayer == null) {
                MessageUtil.error(sender, "Player not found: " + playerName);
                return true;
            }

            targetUUID = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName();
        }

        if (!i18nManager.isLocaleSupported(localeCode)) {
            MessageUtil.error(sender, "Language '" + localeCode + "' is not supported!");
            MessageUtil.send(sender, "§7Use §e/language list §7to see available languages.");
            return true;
        }

        Locale locale = i18nManager.parseLocale(localeCode);
        if (locale != null) {
            i18nManager.setPlayerLocale(targetUUID, locale);
            String displayName = i18nManager.getLocaleDisplayName(localeCode);

            MessageUtil.success(sender, "Set " + targetName + "'s language to: " + displayName);

            // 如果目标玩家在线，通知他们
            if (target != null && target.isOnline()) {
                MessageUtil.success(target, "Your language was set to: " + displayName + " by an administrator");
            }
        } else {
            MessageUtil.error(sender, "Invalid language code: " + localeCode);
        }

        return true;
    }

    /**
     * 处理重载命令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("starcore.language.admin")) {
            MessageUtil.noPermission(sender);
            return true;
        }

        try {
            i18nManager.reload();
            MessageUtil.success(sender, "Language files reloaded!");
            MessageUtil.send(sender, "§7Default language: §e" + i18nManager.getDefaultLocaleDisplayName());
        } catch (Exception e) {
            MessageUtil.error(sender, "Failed to reload language files!");
            sender.sendMessage("§cError: " + e.getMessage());
        }

        return true;
    }

    /**
     * 显示语言信息
     */
    private boolean showLanguageInfo(CommandSender sender) {
        MessageUtil.sendHeader(sender, "Language Information");

        // 服务器默认语言
        Locale defaultLocale = i18nManager.getDefaultLocale();
        MessageUtil.send(sender, "§7Server default: §e" + i18nManager.getDefaultLocaleDisplayName());
        MessageUtil.send(sender, "§7Default code: §f" + defaultLocale.toString());

        // 当前语言
        if (sender instanceof Player player) {
            Locale currentLocale = i18nManager.getPlayerLocale(player.getUniqueId());
            boolean hasCustom = i18nManager.hasPlayerCustomLocale(player.getUniqueId());

            MessageUtil.send(sender, "");
            MessageUtil.send(sender, "§7Your current language: §e" + i18nManager.getLocaleDisplayName(currentLocale.toString()));
            MessageUtil.send(sender, "§7Current code: §f" + currentLocale.toString());
            MessageUtil.send(sender, "§7Custom setting: " + (hasCustom ? "§ayes" : "§7no (using default)"));
        }

        // 支持的语言数量
        int supportedCount = i18nManager.getSupportedLocales().size();
        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "§7Supported languages: §e" + supportedCount);
        MessageUtil.send(sender, "§7Use §e/language list §7to see all");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("set");
            completions.add("info");
            if (sender.hasPermission("starcore.language.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            // 语言代码补全
            for (Locale locale : i18nManager.getSupportedLocales()) {
                completions.add(locale.toString().toLowerCase());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("starcore.language.admin")) {
            // 玩家名补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }

        // 过滤输入
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
    }
}
