package dev.starcore.starcore.module.resource.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 资源分布配置
 * 定义资源在哪些区域可以生成
 */
public final class ResourceDistribution {
    private final String resourceId;
    private final List<String> availableRegions;
    private final double spawnRate;
    private final int minAmount;
    private final int maxAmount;

    public ResourceDistribution(String resourceId, List<String> availableRegions,
                                double spawnRate, int minAmount, int maxAmount) {
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.availableRegions = Collections.unmodifiableList(availableRegions);
        this.spawnRate = Math.max(0.0, Math.min(1.0, spawnRate));
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(minAmount, maxAmount);
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取可用区域列表
     */
    public List<String> availableRegions() {
        return availableRegions;
    }

    /**
     * 获取生成率（0.0-1.0）
     */
    public double spawnRate() {
        return spawnRate;
    }

    /**
     * 获取最小生成数量
     */
    public int minAmount() {
        return minAmount;
    }

    /**
     * 获取最大生成数量
     */
    public int maxAmount() {
        return maxAmount;
    }

    /**
     * 检查该资源是否在指定区域可用
     */
    public boolean isAvailableInRegion(String region) {
        return availableRegions.contains(region);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceDistribution that = (ResourceDistribution) o;
        return resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }
}
