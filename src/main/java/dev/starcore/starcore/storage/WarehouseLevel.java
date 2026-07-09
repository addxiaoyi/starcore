package dev.starcore.starcore.storage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 仓库等级配置
 * 定义每个等级的容量、升级费用和所需材料
 */
public class WarehouseLevel {
    private final int level;
    private final int capacity;
    private final BigDecimal upgradeCost;
    private final Map<String, Integer> requiredMaterials;
    private final long upgradeTimeSeconds;

    /**
     * 构造函数
     * @param level 等级（1-20）
     * @param capacity 容量（格子数，必须是9的倍数）
     * @param upgradeCost 升级到下一级所需金钱
     * @param requiredMaterials 所需材料（格式："材料类型:数量"）
     * @param upgradeTimeSeconds 升级所需时间（秒）
     */
    public WarehouseLevel(int level, int capacity, BigDecimal upgradeCost,
                          Map<String, Integer> requiredMaterials, long upgradeTimeSeconds) {
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("Level must be between 1 and 20");
        }
        if (capacity % 9 != 0 || capacity < 9) {
            throw new IllegalArgumentException("Capacity must be a multiple of 9 and at least 9");
        }
        this.level = level;
        this.capacity = capacity;
        this.upgradeCost = upgradeCost != null ? upgradeCost : BigDecimal.ZERO;
        this.requiredMaterials = requiredMaterials != null ? new HashMap<>(requiredMaterials) : new HashMap<>();
        this.upgradeTimeSeconds = upgradeTimeSeconds;
    }

    /**
     * 获取等级
     * @return 等级数值
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取容量（格子数）
     * @return 该等级的仓库容量
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * 获取行数（用于GUI显示）
     * @return 仓库GUI的行数
     */
    public int getRows() {
        return capacity / 9;
    }

    /**
     * 获取升级费用
     * @return 升级到下一级所需金钱
     */
    public BigDecimal getUpgradeCost() {
        return upgradeCost;
    }

    /**
     * 获取所需材料
     * @return 材料类型到数量的映射（如 "CHEST" -> 4）
     */
    public Map<String, Integer> getRequiredMaterials() {
        return new HashMap<>(requiredMaterials);
    }

    /**
     * 获取升级时间
     * @return 升级所需时间（秒）
     */
    public long getUpgradeTimeSeconds() {
        return upgradeTimeSeconds;
    }

    /**
     * 是否为最大等级
     * @param maxLevel 该类型仓库的最大等级
     * @return true如果已达最大等级
     */
    public boolean isMaxLevel(int maxLevel) {
        return level >= maxLevel;
    }

    /**
     * 根据仓库类型创建默认等级配置
     * @param type 仓库类型
     * @param level 等级
     * @return 该等级的配置
     */
    public static WarehouseLevel createDefault(WarehouseType type, int level) {
        int baseCapacity = type.getBaseCapacity();
        // 每级增加9格（1行）
        int capacity = baseCapacity + (level - 1) * 9;

        // 升级费用按等级递增
        BigDecimal baseCost = BigDecimal.valueOf(1000);
        BigDecimal multiplier = BigDecimal.valueOf(1.5);
        BigDecimal upgradeCost = baseCost.multiply(multiplier.pow(level - 1));

        // 所需材料
        Map<String, Integer> materials = new HashMap<>();
        materials.put("CHEST", level * 4);
        if (level >= 3) {
            materials.put("IRON_BLOCK", (level - 2) * 2);
        }
        if (level >= 5) {
            materials.put("GOLD_BLOCK", (level - 4));
        }
        if (level >= 8) {
            materials.put("DIAMOND_BLOCK", (level - 7));
        }

        // 升级时间（秒）
        long upgradeTime = level * 60L; // 每级1分钟

        return new WarehouseLevel(level, capacity, upgradeCost, materials, upgradeTime);
    }

    /**
     * 创建完整的等级配置表
     * @param type 仓库类型
     * @return 等级到配置的映射
     */
    public static Map<Integer, WarehouseLevel> createLevelTable(WarehouseType type) {
        Map<Integer, WarehouseLevel> table = new HashMap<>();
        int maxLevel = type.getMaxLevel();
        for (int i = 1; i <= maxLevel; i++) {
            table.put(i, createDefault(type, i));
        }
        return table;
    }

    @Override
    public String toString() {
        return "WarehouseLevel{" +
                "level=" + level +
                ", capacity=" + capacity +
                ", rows=" + getRows() +
                ", upgradeCost=" + upgradeCost +
                ", materials=" + requiredMaterials.size() +
                ", upgradeTime=" + upgradeTimeSeconds + "s" +
                '}';
    }
}
