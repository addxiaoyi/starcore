package dev.starcore.starcore.module.resource.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 资源定义类
 * 描述游戏中的各种资源及其属性
 */
public final class Resource {
    private final String id;
    private final String name;
    private final ResourceType type;
    private final ResourceRarity rarity;
    private final String description;
    private final List<String> originRegions;
    private final double basePrice;
    private final boolean tradeable;
    private final boolean storable;

    public Resource(String id, String name, ResourceType type, ResourceRarity rarity,
                    String description, List<String> originRegions, double basePrice,
                    boolean tradeable, boolean storable) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.description = Objects.requireNonNull(description, "description");
        this.originRegions = Collections.unmodifiableList(originRegions);
        this.basePrice = basePrice;
        this.tradeable = tradeable;
        this.storable = storable;
    }

    /**
     * 获取资源ID
     */
    public String id() {
        return id;
    }

    /**
     * 获取资源名称
     */
    public String name() {
        return name;
    }

    /**
     * 获取资源类型
     */
    public ResourceType type() {
        return type;
    }

    /**
     * 获取稀有度
     */
    public ResourceRarity rarity() {
        return rarity;
    }

    /**
     * 获取描述
     */
    public String description() {
        return description;
    }

    /**
     * 获取产地列表
     */
    public List<String> originRegions() {
        return originRegions;
    }

    /**
     * 获取基础价格
     */
    public double basePrice() {
        return basePrice;
    }

    /**
     * 是否可交易
     */
    public boolean isTradeable() {
        return tradeable;
    }

    /**
     * 是否可储存
     */
    public boolean isStorable() {
        return storable;
    }

    /**
     * 计算实际价格（基础价格 * 稀有度倍数）
     */
    public double effectivePrice() {
        return basePrice * rarity.priceMultiplier();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource resource = (Resource) o;
        return id.equals(resource.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", rarity=" + rarity +
                '}';
    }
}
