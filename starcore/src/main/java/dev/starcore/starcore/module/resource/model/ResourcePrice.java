package dev.starcore.starcore.module.resource.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 资源价格信息
 * 记录资源的价格和市场信息
 */
public final class ResourcePrice {
    private final String resourceId;
    private final double basePrice;
    private double currentPrice;
    private double supply;
    private double demand;
    private Instant lastUpdateTime;

    public ResourcePrice(String resourceId, double basePrice) {
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.basePrice = Math.max(0.0, basePrice);
        this.currentPrice = basePrice;
        this.supply = 1.0;
        this.demand = 1.0;
        this.lastUpdateTime = Instant.now();
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取基础价格
     */
    public double basePrice() {
        return basePrice;
    }

    /**
     * 获取当前价格
     */
    public double currentPrice() {
        return currentPrice;
    }

    /**
     * 获取供应量
     */
    public double supply() {
        return supply;
    }

    /**
     * 获取需求量
     */
    public double demand() {
        return demand;
    }

    /**
     * 获取最后更新时间
     */
    public Instant lastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * 更新供应量
     */
    public void setSupply(double supply) {
        this.supply = Math.max(0.001, supply);
        updatePrice();
    }

    /**
     * 更新需求量
     */
    public void setDemand(double demand) {
        this.demand = Math.max(0.001, demand);
        updatePrice();
    }

    /**
     * 直接设置当前价格
     */
    public void setCurrentPrice(double price) {
        this.currentPrice = Math.max(0.0, price);
        this.lastUpdateTime = Instant.now();
    }

    /**
     * 根据供需关系更新价格
     * 价格 = 基础价格 * (需求 / 供应)
     */
    private void updatePrice() {
        double supplyDemandRatio = demand / supply;
        // 限制价格波动范围在基础价格的 0.1x 到 10x 之间
        supplyDemandRatio = Math.max(0.1, Math.min(10.0, supplyDemandRatio));
        this.currentPrice = basePrice * supplyDemandRatio;
        this.lastUpdateTime = Instant.now();
    }

    /**
     * 增加供应
     */
    public void addSupply(double amount) {
        this.supply += Math.max(0.0, amount);
        updatePrice();
    }

    /**
     * 增加需求
     */
    public void addDemand(double amount) {
        this.demand += Math.max(0.0, amount);
        updatePrice();
    }

    /**
     * 计算价格变化百分比
     */
    public double priceChangePercentage() {
        if (basePrice == 0.0) return 0.0;
        return ((currentPrice - basePrice) / basePrice) * 100.0;
    }

    /**
     * 获取市场状态
     */
    public MarketState marketState() {
        double ratio = demand / supply;
        if (ratio > 1.5) {
            return MarketState.SHORTAGE;
        } else if (ratio > 1.1) {
            return MarketState.HIGH_DEMAND;
        } else if (ratio < 0.7) {
            return MarketState.OVERSUPPLY;
        } else if (ratio < 0.9) {
            return MarketState.LOW_DEMAND;
        }
        return MarketState.BALANCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourcePrice that = (ResourcePrice) o;
        return resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }

    /**
     * 市场状态枚举
     */
    public enum MarketState {
        SHORTAGE("严重短缺"),
        HIGH_DEMAND("需求旺盛"),
        BALANCED("供需平衡"),
        LOW_DEMAND("需求疲软"),
        OVERSUPPLY("供过于求");

        private final String displayName;

        MarketState(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
