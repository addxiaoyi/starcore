package dev.starcore.starcore.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 配置管理器
 * 统一管理所有配置文件
 */
public class ConfigManager {

    private final Plugin plugin;
    private final Map<String, FileConfiguration> configs = new ConcurrentHashMap<>();
    private final Map<String, File> configFiles = new ConcurrentHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        loadDefaultConfigs();
    }

    /**
     * 加载默认配置文件
     */
    private void loadDefaultConfigs() {
        // 主配置
        loadConfig("config", "config.yml");

        // Nation配置
        loadConfig("nation", "nation.yml");

        // Clan配置
        loadConfig("clan", "clan.yml");

        // City配置
        loadConfig("city", "city.yml");

        // 排行榜配置
        loadConfig("ranking", "ranking.yml");

        // 消息配置
        loadConfig("messages", "messages.yml");
    }

    /**
     * 加载配置文件
     */
    public void loadConfig(String name, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        // 如果文件不存在，从资源复制
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configs.put(name, config);
        configFiles.put(name, file);

        plugin.getLogger().info("已加载配置文件: " + fileName);
    }

    /**
     * 获取配置
     */
    public FileConfiguration getConfig(String name) {
        return configs.getOrDefault(name, plugin.getConfig());
    }

    /**
     * 保存配置
     */
    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        File file = configFiles.get(name);

        if (config != null && file != null) {
            try {
                config.save(file);
                plugin.getLogger().info("已保存配置文件: " + name);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "保存配置文件失败: " + name, e);
            }
        }
    }

    /**
     * 保存所有配置
     */
    public void saveAll() {
        configs.keySet().forEach(this::saveConfig);
    }

    /**
     * 重载配置
     */
    public void reloadConfig(String name) {
        File file = configFiles.get(name);
        if (file != null && file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            configs.put(name, config);
            plugin.getLogger().info("已重载配置文件: " + name);
        }
    }

    /**
     * 重载所有配置
     */
    public void reloadAll() {
        configs.keySet().forEach(this::reloadConfig);
    }

    // ==================== 快捷方法 ====================

    /**
     * 获取字符串
     */
    public String getString(String configName, String path, String def) {
        return getConfig(configName).getString(path, def);
    }

    /**
     * 获取整数
     */
    public int getInt(String configName, String path, int def) {
        return getConfig(configName).getInt(path, def);
    }

    /**
     * 获取双精度
     */
    public double getDouble(String configName, String path, double def) {
        return getConfig(configName).getDouble(path, def);
    }

    /**
     * 获取布尔值
     */
    public boolean getBoolean(String configName, String path, boolean def) {
        return getConfig(configName).getBoolean(path, def);
    }

    /**
     * 设置值
     */
    public void set(String configName, String path, Object value) {
        getConfig(configName).set(path, value);
    }

    // ==================== 主配置快捷方法 ====================

    public String getString(String path, String def) {
        return getString("config", path, def);
    }

    public int getInt(String path, int def) {
        return getInt("config", path, def);
    }

    public double getDouble(String path, double def) {
        return getDouble("config", path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return getBoolean("config", path, def);
    }

    // ==================== 常用配置获取 ====================

    /**
     * 获取数据库配置
     */
    public DatabaseConfig getDatabaseConfig() {
        FileConfiguration config = getConfig("config");
        return new DatabaseConfig(
            config.getString("database.host", "localhost"),
            config.getInt("database.port", 3306),
            config.getString("database.database", "starcore"),
            config.getString("database.username", "root"),
            config.getString("database.password", "password"),
            config.getInt("database.pool-size", 10)
        );
    }

    /**
     * 获取Nation配置
     */
    public NationConfig getNationConfig() {
        FileConfiguration config = getConfig("nation");
        return new NationConfig(
            config.getInt("max-territories", 100),
            config.getDouble("daily-upkeep", 100.0),
            config.getDouble("debt-limit", 10000.0),
            config.getDouble("territory-cost", 5.0)
        );
    }

    /**
     * 获取Clan配置
     */
    public ClanConfig getClanConfig() {
        FileConfiguration config = getConfig("clan");
        return new ClanConfig(
            config.getInt("max-members", 30),
            config.getInt("tag-length-min", 3),
            config.getInt("tag-length-max", 4),
            config.getBoolean("friendly-fire-default", false)
        );
    }

    /**
     * 获取City配置
     */
    public CityConfig getCityConfig() {
        FileConfiguration config = getConfig("city");
        return new CityConfig(
            config.getInt("max-level", 10),
            config.getInt("base-residents", 20),
            config.getDouble("base-upkeep", 20.0)
        );
    }

    /**
     * 获取WebMap配置
     */
    public WebMapConfig getWebMapConfig() {
        FileConfiguration config = getConfig("config");
        return new WebMapConfig(
            config.getString("webmap.host", "0.0.0.0"),
            config.getInt("webmap.port", 8080),
            config.getInt("webmap.update-interval", 100)
        );
    }

    // ==================== 配置数据类 ====================

    public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize
    ) {}

    public record NationConfig(
        int maxTerritories,
        double dailyUpkeep,
        double debtLimit,
        double territoryCost
    ) {}

    public record ClanConfig(
        int maxMembers,
        int tagLengthMin,
        int tagLengthMax,
        boolean friendlyFireDefault
    ) {}

    public record CityConfig(
        int maxLevel,
        int baseResidents,
        double baseUpkeep
    ) {}

    public record WebMapConfig(
        String host,
        int port,
        int updateInterval
    ) {}
}
