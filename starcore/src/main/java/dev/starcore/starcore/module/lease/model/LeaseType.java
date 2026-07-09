package dev.starcore.starcore.module.lease.model;

/**
 * 租约类型枚举
 */
public enum LeaseType {
    /**
     * 领土租约 - 租用整块领土
     */
    TERRITORY("领土", "Territory", 1.0),

    /**
     * 资源点租约 - 租用资源采集点
     */
    RESOURCE_NODE("资源点", "Resource Node", 1.5),

    /**
     * 建筑租约 - 租用特定建筑
     */
    BUILDING("建筑", "Building", 1.2),

    /**
     * 军事基地租约 - 租用军事设施
     */
    MILITARY_BASE("军事基地", "Military Base", 2.0),

    /**
     * 港口租约 - 租用港口设施
     */
    PORT("港口", "Port", 1.8),

    /**
     * 贸易站租约 - 租用贸易站点
     */
    TRADE_POST("贸易站", "Trade Post", 1.3),

    /**
     * 农田租约 - 租用农业用地
     */
    FARM("农田", "Farm", 0.8),

    /**
     * 矿场租约 - 租用矿场设施
     */
    MINE("矿场", "Mine", 1.4);

    private final String zhName;
    private final String enName;
    private final double priceMultiplier;

    LeaseType(String zhName, String enName, double priceMultiplier) {
        this.zhName = zhName;
        this.enName = enName;
        this.priceMultiplier = priceMultiplier;
    }

    public String getZhName() {
        return zhName;
    }

    public String getEnName() {
        return enName;
    }

    public String getDisplayName(boolean chinese) {
        return chinese ? zhName : enName;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }
}
