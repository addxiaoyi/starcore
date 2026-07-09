package dev.starcore.starcore.foundation.i18n;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.platform.PlatformAdapter;
import dev.starcore.starcore.foundation.message.MessageFormatter;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.message.YamlMessageLoader;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 国际化管理器 - 多语言支持
 * 支持动态语言切换、热重载和玩家语言偏好持久化
 *
 * 支持的语言:
 * - zh_CN: 简体中文
 * - en_US: English
 * - zh_TW: 繁体中文
 * - ja_JP: 日本語
 * - ko_KR: 한국어
 */
public final class I18nManager implements MessageService {
    private final Plugin plugin;
    private final PlatformAdapter platformAdapter;
    private final File dataFolder;
    private final Map<Locale, MessageBundle> bundles = new ConcurrentHashMap<>();
    private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;
    private DatabaseService databaseService;

    // 支持的语言列表
    public static final Map<String, Locale> SUPPORTED_LOCALES = Map.of(
        "zh_cn", Locale.SIMPLIFIED_CHINESE,
        "zh_CN", Locale.SIMPLIFIED_CHINESE,
        "en_us", Locale.US,
        "en_US", Locale.US,
        "zh_tw", Locale.TRADITIONAL_CHINESE,
        "zh_TW", Locale.TRADITIONAL_CHINESE,
        "ja_jp", Locale.JAPANESE,
        "ja_JP", Locale.JAPANESE,
        "ko_kr", Locale.KOREAN,
        "ko_KR", Locale.KOREAN
    );

    // 语言显示名称
    private static final Map<Locale, String> LOCALE_DISPLAY_NAMES = Map.of(
        Locale.SIMPLIFIED_CHINESE, "简体中文",
        Locale.US, "English",
        Locale.TRADITIONAL_CHINESE, "繁體中文",
        Locale.JAPANESE, "日本語",
        Locale.KOREAN, "한국어"
    );

    public I18nManager(Plugin plugin, PlatformAdapter platformAdapter) {
        this.plugin = plugin;
        this.platformAdapter = platformAdapter;
        this.dataFolder = new File(plugin.getDataFolder(), "lang");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        loadDefaultBundles();
    }

    /**
     * 设置默认语言
     */
    public void setDefaultLocale(String localeCode) {
        Locale locale = parseLocale(localeCode);
        if (locale != null && bundles.containsKey(locale)) {
            this.defaultLocale = locale;
        }
    }

    /**
     * 获取默认语言
     */
    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * 获取默认语言的显示名称
     */
    public String getDefaultLocaleDisplayName() {
        return LOCALE_DISPLAY_NAMES.getOrDefault(defaultLocale, defaultLocale.getDisplayName());
    }

    /**
     * 设置数据库服务（可选，用于持久化玩家语言设置）
     */
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * 加载默认语言包
     */
    private void loadDefaultBundles() {
        // 支持的语言
        List<Locale> supportedLocales = List.of(
            Locale.SIMPLIFIED_CHINESE,
            Locale.US,
            Locale.TRADITIONAL_CHINESE,
            Locale.JAPANESE,
            Locale.KOREAN
        );

        for (Locale locale : supportedLocales) {
            String filename = getFilename(locale);

            // 从jar中复制默认文件
            File file = new File(dataFolder, filename);
            if (!file.exists()) {
                saveResource(filename);
            }

            // 加载语言包
            loadBundle(locale, file);
        }
    }

    /**
     * 加载语言包
     */
    private void loadBundle(Locale locale, File file) {
        if (!file.exists()) {
            return;
        }

        try {
            bundles.put(locale, new MessageBundle(locale, YamlMessageLoader.load(file.toPath())));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language file: " + file.getName());
        }
    }

    /**
     * 获取消息
     */
    public String getMessage(Locale locale, String key, Object... args) {
        MessageBundle bundle = bundles.get(locale);

        if (bundle == null) {
            bundle = bundles.get(defaultLocale);
        }

        if (bundle == null) {
            return key;
        }

        String message = bundle.getMessage(key, args);
        if (message.equals(key) && !bundle.locale().equals(defaultLocale)) {
            MessageBundle fallback = bundles.get(defaultLocale);
            if (fallback != null) {
                return fallback.getMessage(key, args);
            }
        }
        return message;
    }

    /**
     * 获取消息（使用默认语言）
     */
    public String getMessage(String key, Object... args) {
        return getMessage(defaultLocale, key, args);
    }

    /**
     * 获取玩家的语言
     */
    public Locale getPlayerLocale(UUID playerId) {
        // 先从数据库加载
        Locale dbLocale = loadLocaleFromDatabase(playerId);
        if (dbLocale != null) {
            return dbLocale;
        }
        // 默认返回系统默认语言
        return defaultLocale;
    }

    /**
     * 设置玩家的语言
     */
    public void setPlayerLocale(UUID playerId, Locale locale) {
        // 保存到数据库
        saveLocaleToDatabase(playerId, locale);
    }

    /**
     * 从数据库加载玩家语言设置
     */
    private Locale loadLocaleFromDatabase(UUID playerId) {
        if (databaseService == null || !databaseService.isRunning()) {
            return null;
        }

        return databaseService.dataSource().map(ds -> {
            String sql = "SELECT locale FROM starcore_player_locale WHERE player_uuid = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String localeStr = rs.getString("locale");
                        return parseLocale(localeStr);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player locale from database", e);
            }
            return null;
        }).orElse(null);
    }

    /**
     * 保存玩家语言设置到数据库
     */
    private void saveLocaleToDatabase(UUID playerId, Locale locale) {
        if (databaseService == null || !databaseService.isRunning()) {
            return;
        }

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

                String sql;
                if (isSQLite) {
                    sql = """
                        INSERT INTO starcore_player_locale (player_uuid, locale, last_updated)
                        VALUES (?, ?, ?)
                        ON CONFLICT(player_uuid) DO UPDATE SET locale = excluded.locale, last_updated = excluded.last_updated
                        """;
                } else {
                    sql = """
                        INSERT INTO starcore_player_locale (player_uuid, locale, last_updated)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE locale = VALUES(locale), last_updated = VALUES(last_updated)
                        """;
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, locale.toString());
                    stmt.setLong(3, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save player locale to database", e);
            }
        });
    }

    /**
     * 解析语言字符串为 Locale
     */
    public Locale parseLocale(String localeStr) {
        if (localeStr == null || localeStr.isEmpty()) {
            return null;
        }

        try {
            // 尝试解析 "language_COUNTRY" 格式
            if (localeStr.contains("_")) {
                String[] parts = localeStr.split("_", 2);
                return new Locale(parts[0], parts.length > 1 ? parts[1] : "");
            } else {
                return new Locale(localeStr);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse locale: " + localeStr);
            return null;
        }
    }

    /**
     * 重载所有语言包
     */
    public void reload() {
        bundles.clear();
        loadDefaultBundles();
    }

    /**
     * 获取支持的语言列表
     */
    public List<Locale> getSupportedLocales() {
        return new ArrayList<>(bundles.keySet());
    }

    /**
     * 获取支持的语言列表（带显示名称）
     */
    public Map<Locale, String> getSupportedLocalesWithDisplayNames() {
        Map<Locale, String> result = new LinkedHashMap<>();
        for (Locale locale : bundles.keySet()) {
            result.put(locale, LOCALE_DISPLAY_NAMES.getOrDefault(locale, locale.getDisplayName()));
        }
        return result;
    }

    /**
     * 检查语言是否支持
     */
    public boolean isLocaleSupported(String localeCode) {
        Locale locale = parseLocale(localeCode);
        return locale != null && bundles.containsKey(locale);
    }

    /**
     * 获取语言代码对应的显示名称
     */
    public String getLocaleDisplayName(String localeCode) {
        Locale locale = parseLocale(localeCode);
        if (locale == null) {
            return localeCode;
        }
        return LOCALE_DISPLAY_NAMES.getOrDefault(locale, locale.getDisplayName());
    }

    /**
     * 检查玩家是否有自定义语言设置
     */
    public boolean hasPlayerCustomLocale(UUID playerId) {
        return loadLocaleFromDatabase(playerId) != null;
    }

    /**
     * 清除玩家的语言设置
     */
    public void clearPlayerLocale(UUID playerId) {
        if (databaseService == null || !databaseService.isRunning()) {
            return;
        }

        databaseService.dataSource().ifPresent(ds -> {
            String sql = "DELETE FROM starcore_player_locale WHERE player_uuid = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear player locale from database", e);
            }
        });
    }

    /**
     * 发送本地化消息给玩家
     */
    public void sendMessage(org.bukkit.command.CommandSender sender, UUID playerId, String key, Object... args) {
        Locale locale = playerId != null ? getPlayerLocale(playerId) : defaultLocale;
        String message = getMessage(locale, key, args);
        sender.sendMessage(dev.starcore.starcore.util.MessageUtil.colorize(message));
    }

    /**
     * 发送本地化消息（使用默认语言）
     */
    public void sendMessage(org.bukkit.command.CommandSender sender, String key, Object... args) {
        String message = getMessage(key, args);
        sender.sendMessage(dev.starcore.starcore.util.MessageUtil.colorize(message));
    }

    // ========== MessageService 接口实现 ==========

    @Override
    public String format(String key, Object... args) {
        return getMessage(key, args);
    }

    private String getFilename(Locale locale) {
        return "messages_" + locale.toString().toLowerCase() + ".yml";
    }

    private void saveResource(String filename) {
        try (InputStream in = plugin.getResource("lang/" + filename)) {
            if (in != null) {
                File outFile = new File(dataFolder, filename);
                Files.copy(in, outFile.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + filename);
        }
    }

    /**
     * 消息包
     */
    private record MessageBundle(
        Locale locale,
        Map<String, String> messages
    ) {
        String getMessage(String key, Object... args) {
            String message = messages.get(key);

            if (message == null) {
                return key;
            }

            // 格式化参数
            if (args.length > 0) {
                message = MessageFormatter.format(message, args);
            }

            return message;
        }
    }
}
