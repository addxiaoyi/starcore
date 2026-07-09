package dev.starcore.starcore.pet;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 宠物配置文件
 */
public class PetConfig {
    private static final Logger LOGGER = Logger.getLogger(PetConfig.class.getName());
    private final File configFile;
    private FileConfiguration config;

    // 价格倍率
    private final Map<PetType, Double> priceMultipliers;
    private final Map<PetCategory, Double> categoryMultipliers;

    // 经验配置
    private long feedExp;
    private long killExpMultiplier;
    private long levelUpExpBase;

    // 功能开关
    private boolean summonEnabled;
    private boolean rideEnabled;
    private boolean shopEnabled;
    private boolean upgradeEnabled;

    // 限制配置
    private int defaultMaxPets;
    private double maxRideSpeed;

    // 稀有度升级费用
    private final Map<PetRarity, Double> rarityUpgradeCosts;

    public PetConfig(File dataFolder) {
        this.configFile = new File(dataFolder, "pets.yml");

        this.priceMultipliers = new EnumMap<>(PetType.class);
        this.categoryMultipliers = new EnumMap<>(PetCategory.class);
        this.rarityUpgradeCosts = new EnumMap<>(PetRarity.class);

        load();
    }

    /**
     * 加载配置
     */
    public void load() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // 加载价格倍率
        loadPriceMultipliers();

        // 加载经验配置
        feedExp = config.getLong("experience.feed", 10);
        killExpMultiplier = config.getLong("experience.kill-multiplier", 5);
        levelUpExpBase = config.getLong("experience.level-up-base", 100);

        // 加载功能开关
        summonEnabled = config.getBoolean("features.summon", true);
        rideEnabled = config.getBoolean("features.ride", true);
        shopEnabled = config.getBoolean("features.shop", true);
        upgradeEnabled = config.getBoolean("features.upgrade", true);

        // 加载限制配置
        defaultMaxPets = config.getInt("limits.default-max-pets", 5);
        maxRideSpeed = config.getDouble("limits.max-ride-speed", 0.5);

        // 加载稀有度升级费用
        loadRarityUpgradeCosts();
    }

    /**
     * 加载价格倍率
     */
    private void loadPriceMultipliers() {
        // 类别倍率
        categoryMultipliers.put(PetCategory.COMPANION, config.getDouble("prices.category.companion", 1.0));
        categoryMultipliers.put(PetCategory.MOUNT, config.getDouble("prices.category.mount", 1.5));
        categoryMultipliers.put(PetCategory.FLYING, config.getDouble("prices.category.flying", 2.0));
        categoryMultipliers.put(PetCategory.AQUATIC, config.getDouble("prices.category.aquatic", 1.2));
        categoryMultipliers.put(PetCategory.SPECIAL, config.getDouble("prices.category.special", 3.0));

        // 类型倍率
        for (PetType type : PetType.values()) {
            double multiplier = config.getDouble("prices.type." + type.name().toLowerCase(), 1.0);
            priceMultipliers.put(type, multiplier);
        }
    }

    /**
     * 加载稀有度升级费用
     */
    private void loadRarityUpgradeCosts() {
        rarityUpgradeCosts.put(PetRarity.COMMON, config.getDouble("upgrade-costs.common", 5000));
        rarityUpgradeCosts.put(PetRarity.UNCOMMON, config.getDouble("upgrade-costs.uncommon", 20000));
        rarityUpgradeCosts.put(PetRarity.RARE, config.getDouble("upgrade-costs.rare", 80000));
        rarityUpgradeCosts.put(PetRarity.EPIC, config.getDouble("upgrade-costs.epic", 300000));
        rarityUpgradeCosts.put(PetRarity.LEGENDARY, config.getDouble("upgrade-costs.legendary", 1000000));
        rarityUpgradeCosts.put(PetRarity.MYTHIC, config.getDouble("upgrade-costs.mythic", 0));
    }

    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        config = new YamlConfiguration();

        // 价格配置
        config.set("prices.category.companion", 1.0);
        config.set("prices.category.mount", 1.5);
        config.set("prices.category.flying", 2.0);
        config.set("prices.category.aquatic", 1.2);
        config.set("prices.category.special", 3.0);

        // 经验配置
        config.set("experience.feed", 10);
        config.set("experience.kill-multiplier", 5);
        config.set("experience.level-up-base", 100);

        // 功能开关
        config.set("features.summon", true);
        config.set("features.ride", true);
        config.set("features.shop", true);
        config.set("features.upgrade", true);

        // 限制配置
        config.set("limits.default-max-pets", 5);
        config.set("limits.max-ride-speed", 0.5);

        // 稀有度升级费用
        config.set("upgrade-costs.common", 5000);
        config.set("upgrade-costs.uncommon", 20000);
        config.set("upgrade-costs.rare", 80000);
        config.set("upgrade-costs.epic", 300000);
        config.set("upgrade-costs.legendary", 1000000);
        config.set("upgrade-costs.mythic", 0);

        try {
            config.save(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save pet config", e);
        }
    }

    /**
     * 保存配置
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save pet config", e);
        }
    }

    /**
     * 获取宠物价格倍率
     */
    public double getPriceMultiplier(PetType type) {
        double typeMultiplier = priceMultipliers.getOrDefault(type, 1.0);
        double categoryMultiplier = categoryMultipliers.getOrDefault(type.getCategory(), 1.0);
        return typeMultiplier * categoryMultiplier;
    }

    /**
     * 获取稀有度升级费用
     */
    public double getRarityUpgradeCost(PetRarity currentRarity) {
        return rarityUpgradeCosts.getOrDefault(currentRarity, 0.0);
    }

    // Getters
    public long getFeedExp() {
        return feedExp;
    }

    public long getKillExpMultiplier() {
        return killExpMultiplier;
    }

    public long getLevelUpExpBase() {
        return levelUpExpBase;
    }

    public boolean isSummonEnabled() {
        return summonEnabled;
    }

    public boolean isRideEnabled() {
        return rideEnabled;
    }

    public boolean isShopEnabled() {
        return shopEnabled;
    }

    public boolean isUpgradeEnabled() {
        return upgradeEnabled;
    }

    public int getDefaultMaxPets() {
        return defaultMaxPets;
    }

    public double getMaxRideSpeed() {
        return maxRideSpeed;
    }
}
