package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 工厂
 * 用于资源加工的生产设施
 */
public final class Factory {
    private final UUID factoryId;
    private final NationId ownerNationId;
    private final String factoryName;
    private final FactoryType type;
    private int level;
    private double efficiency;
    private final Instant buildTime;
    private boolean operational;
    private String currentRecipeId;
    private Instant processingStartTime;
    private int processingBatches;

    public Factory(UUID factoryId, NationId ownerNationId, String factoryName, FactoryType type) {
        this.factoryId = Objects.requireNonNull(factoryId, "factoryId");
        this.ownerNationId = Objects.requireNonNull(ownerNationId, "ownerNationId");
        this.factoryName = Objects.requireNonNull(factoryName, "factoryName");
        this.type = Objects.requireNonNull(type, "type");
        this.level = 1;
        this.efficiency = 1.0;
        this.buildTime = Instant.now();
        this.operational = true;
        this.currentRecipeId = null;
        this.processingStartTime = null;
        this.processingBatches = 0;
    }

    /**
     * 获取工厂ID
     */
    public UUID factoryId() {
        return factoryId;
    }

    /**
     * 获取所有者国家ID
     */
    public NationId ownerNationId() {
        return ownerNationId;
    }

    /**
     * 获取工厂名称
     */
    public String factoryName() {
        return factoryName;
    }

    /**
     * 获取工厂类型
     */
    public FactoryType type() {
        return type;
    }

    /**
     * 获取工厂等级
     */
    public int level() {
        return level;
    }

    /**
     * 设置工厂等级
     */
    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(10, level));
    }

    /**
     * 升级工厂
     */
    public boolean upgrade() {
        if (level >= 10) {
            return false;
        }
        level++;
        // 升级后效率提升5%
        efficiency = Math.min(2.0, efficiency + 0.05);
        return true;
    }

    /**
     * 获取效率（0.5-2.0）
     */
    public double efficiency() {
        return efficiency;
    }

    /**
     * 设置效率
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.5, Math.min(2.0, efficiency));
    }

    /**
     * 获取建造时间
     */
    public Instant buildTime() {
        return buildTime;
    }

    /**
     * 是否运营中
     */
    public boolean isOperational() {
        return operational;
    }

    /**
     * 设置运营状态
     */
    public void setOperational(boolean operational) {
        this.operational = operational;
    }

    /**
     * 获取当前加工配方ID
     */
    public String currentRecipeId() {
        return currentRecipeId;
    }

    /**
     * 获取加工开始时间
     */
    public Instant processingStartTime() {
        return processingStartTime;
    }

    /**
     * 获取加工批次数
     */
    public int processingBatches() {
        return processingBatches;
    }

    /**
     * 开始加工
     */
    public void startProcessing(String recipeId, int batches) {
        this.currentRecipeId = recipeId;
        this.processingStartTime = Instant.now();
        this.processingBatches = batches;
    }

    /**
     * 完成加工
     */
    public void finishProcessing() {
        this.currentRecipeId = null;
        this.processingStartTime = null;
        this.processingBatches = 0;
    }

    /**
     * 是否正在加工
     */
    public boolean isProcessing() {
        return currentRecipeId != null && processingStartTime != null;
    }

    /**
     * 计算加工时间倍数（效率越高，时间越短）
     */
    public double processingTimeMultiplier() {
        return 1.0 / efficiency;
    }

    /**
     * 计算升级成本
     */
    public double upgradeCost() {
        return type.baseUpgradeCost() * level * level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Factory factory = (Factory) o;
        return factoryId.equals(factory.factoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(factoryId);
    }

    /**
     * 工厂类型
     */
    public enum FactoryType {
        /**
         * 冶炼厂 - 处理矿产资源
         */
        SMELTER("冶炼厂", 1000.0),

        /**
         * 加工厂 - 处理农业资源
         */
        PROCESSING_PLANT("加工厂", 800.0),

        /**
         * 化工厂 - 处理化学资源
         */
        CHEMICAL_PLANT("化工厂", 1500.0),

        /**
         * 装配厂 - 组装复杂产品
         */
        ASSEMBLY_PLANT("装配厂", 2000.0),

        /**
         * 精炼厂 - 精炼能源
         */
        REFINERY("精炼厂", 2500.0),

        /**
         * 综合工厂 - 可处理多种资源
         */
        INTEGRATED_FACTORY("综合工厂", 5000.0);

        private final String displayName;
        private final double baseUpgradeCost;

        FactoryType(String displayName, double baseUpgradeCost) {
            this.displayName = displayName;
            this.baseUpgradeCost = baseUpgradeCost;
        }

        public String displayName() {
            return displayName;
        }

        public double baseUpgradeCost() {
            return baseUpgradeCost;
        }
    }
}
