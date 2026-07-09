package dev.starcore.starcore.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * 配置加载器
 * 从warehouse_config.yml加载配置
 */
public class StorageConfigLoader {
    private final JavaPlugin plugin;
    private final Logger logger;
    private File configFile;
    private FileConfiguration config;

    /**
     * 构造函数
     */
    public StorageConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 加载配置
     * @return 配置对象
     */
    public StorageConfig load() {
        // 确保配置文件存在
        configFile = new File(plugin.getDataFolder(), "warehouse_config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("warehouse_config.yml", false);
            logger.info("Created default warehouse_config.yml");
        }

        // 加载配置
        config = YamlConfiguration.loadConfiguration(configFile);

        // 解析配置
        boolean remoteAccessEnabled = config.getBoolean("warehouse.remote_access.enabled", true);
        double maxRemoteDistance = config.getDouble("warehouse.remote_access.max_distance", 1000.0);
        BigDecimal remoteAccessCost = BigDecimal.valueOf(
                config.getDouble("warehouse.remote_access.cost_per_use", 100.0)
        );

        int personalMaxLevel = config.getInt("warehouse.personal.max_level", 10);
        int nationMaxLevel = config.getInt("warehouse.nation.max_level", 15);
        double upgradeCostMultiplier = config.getDouble("warehouse.personal.upgrade_cost_multiplier", 1.5);

        boolean logsEnabled = config.getBoolean("warehouse.logs.enabled", true);
        int logRetentionDays = config.getInt("warehouse.logs.retention_days", 30);

        boolean autoSort = config.getBoolean("warehouse.features.auto_sort", true);
        boolean allowSharing = config.getBoolean("warehouse.features.allow_sharing", true);

        logger.info("Warehouse configuration loaded successfully");
        logger.info("Remote access: " + remoteAccessEnabled + ", Max distance: " + maxRemoteDistance);
        logger.info("Personal max level: " + personalMaxLevel + ", Nation max level: " + nationMaxLevel);
        logger.info("Logs: " + logsEnabled + ", Retention: " + logRetentionDays + " days");

        return new StorageConfig(
                remoteAccessEnabled, maxRemoteDistance, remoteAccessCost,
                personalMaxLevel, nationMaxLevel, upgradeCostMultiplier,
                logsEnabled, logRetentionDays, autoSort, allowSharing
        );
    }

    /**
     * 保存配置
     */
    public void save() {
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
                logger.info("Warehouse configuration saved");
            } catch (IOException e) {
                logger.severe("Failed to save warehouse configuration: " + e.getMessage());
            }
        }
    }

    /**
     * 重载配置
     * @return 新的配置对象
     */
    public StorageConfig reload() {
        logger.info("Reloading warehouse configuration...");
        return load();
    }

    /**
     * 获取等级配置段
     * @return 配置段
     */
    public ConfigurationSection getLevelsSection() {
        return config.getConfigurationSection("warehouse.levels");
    }

    /**
     * 获取原始配置
     * @return FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
