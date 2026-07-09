package dev.starcore.starcore.module.shop.model;

import java.time.Instant;

/**
 * 物品价格数据
 * 用于市场均价计算和价格保护
 */
public class ItemPriceData {

    private final String itemId;
    private final double avgPrice;
    private final double minPrice;
    private final double maxPrice;
    private final int sampleCount;
    private final Instant lastUpdated;
    private final double volatility;

    public ItemPriceData(
            String itemId,
            double avgPrice,
            double minPrice,
            double maxPrice,
            int sampleCount,
            Instant lastUpdated,
            double volatility
    ) {
        this.itemId = itemId;
        this.avgPrice = avgPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.sampleCount = sampleCount;
        this.lastUpdated = lastUpdated;
        this.volatility = volatility;
    }

    /**
     * 创建新的价格数据
     */
    public static ItemPriceData create(String itemId, double price) {
        return new ItemPriceData(
            itemId,
            price,
            price,
            price,
            1,
            Instant.now(),
            0.0
        );
    }

    /**
     * 更新价格数据
     */
    public ItemPriceData update(double newPrice) {
        // 计算新的平均值（增量更新）
        double newAvg = ((avgPrice * sampleCount) + newPrice) / (sampleCount + 1);
        double newMin = Math.min(minPrice, newPrice);
        double newMax = Math.max(maxPrice, newPrice);

        // 计算波动率（使用标准差）
        double variance = volatility * volatility;
        double newVariance = variance + (newPrice - avgPrice) * (newPrice - newAvg) / (sampleCount + 1);
        double newVolatility = Math.sqrt(newVariance);

        return new ItemPriceData(
            itemId,
            newAvg,
            newMin,
            newMax,
            sampleCount + 1,
            Instant.now(),
            newVolatility
        );
    }

    // ==================== Getters ====================

    public String getItemId() {
        return itemId;
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public double getVolatility() {
        return volatility;
    }

    /**
     * 获取价格范围
     */
    public double getPriceRange() {
        return maxPrice - minPrice;
    }

    /**
     * 获取相对波动率（相对于平均价格的百分比）
     */
    public double getRelativeVolatility() {
        if (avgPrice <= 0) {
            return 0;
        }
        return (volatility / avgPrice) * 100;
    }

    /**
     * 检查价格是否在合理范围内（±50%）
     */
    public boolean isPriceReasonable(double price) {
        double lowerBound = avgPrice * 0.5;
        double upperBound = avgPrice * 1.5;
        return price >= lowerBound && price <= upperBound;
    }

    /**
     * 获取价格状态
     */
    public PriceStatus getPriceStatus(double price) {
        double ratio = price / avgPrice;
        if (ratio < 0.5) {
            return PriceStatus.VERY_LOW;
        } else if (ratio < 0.8) {
            return PriceStatus.LOW;
        } else if (ratio < 1.2) {
            return PriceStatus.NORMAL;
        } else if (ratio < 1.5) {
            return PriceStatus.HIGH;
        } else {
            return PriceStatus.VERY_HIGH;
        }
    }

    public enum PriceStatus {
        VERY_LOW("极低", "§a"),
        LOW("偏低", "§2"),
        NORMAL("正常", "§e"),
        HIGH("偏高", "§c"),
        VERY_HIGH("极高", "§4");

        private final String displayName;
        private final String colorCode;

        PriceStatus(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    @Override
    public String toString() {
        return String.format("ItemPriceData{item=%s, avg=%.2f, min=%.2f, max=%.2f, samples=%d}",
            itemId, avgPrice, minPrice, maxPrice, sampleCount);
    }
}
