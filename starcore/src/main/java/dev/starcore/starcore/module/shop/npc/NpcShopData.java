package dev.starcore.starcore.module.shop.npc;

import org.bukkit.Location;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * NPC商店数据模型
 * 使用Java 21 record类型定义不可变数据结构
 */

/**
 * NPC商店记录
 * @param id 商店唯一ID
 * @param name 商店名称
 * @param ownerId 拥有者UUID
 * @param ownerName 拥有者名称
 * @param location 商店位置
 * @param npcId 绑定的NPC ID
 * @param infiniteStock 是否无限库存
 * @param buyEnabled 是否启用购买
 * @param sellEnabled 是否启用出售
 * @param createdAt 创建时间
 * @param description 商店描述
 */
public record NpcShopData(
    UUID id,
    String name,
    UUID ownerId,
    String ownerName,
    Location location,
    Integer npcId,
    boolean infiniteStock,
    boolean buyEnabled,
    boolean sellEnabled,
    Instant createdAt,
    String description
) {
    /**
     * 创建NPC商店数据
     */
    public static NpcShopData create(String name, UUID ownerId, String ownerName, Location location) {
        return new NpcShopData(
            UUID.randomUUID(),
            name,
            ownerId,
            ownerName,
            location,
            null,
            true,  // 默认无限库存
            true,  // 默认启用购买
            false, // 默认禁用出售
            Instant.now(),
            null
        );
    }

    /**
     * 创建带描述的商店
     */
    public static NpcShopData create(String name, UUID ownerId, String ownerName, Location location, String description) {
        return new NpcShopData(
            UUID.randomUUID(),
            name,
            ownerId,
            ownerName,
            location,
            null,
            true,
            true,
            false,
            Instant.now(),
            description
        );
    }

    /**
     * 绑定NPC
     */
    public NpcShopData withNpcId(int npcId) {
        return new NpcShopData(
            this.id, this.name, this.ownerId, this.ownerName,
            this.location, npcId, this.infiniteStock,
            this.buyEnabled, this.sellEnabled, this.createdAt, this.description
        );
    }

    /**
     * 设置无限库存
     */
    public NpcShopData withInfiniteStock(boolean infinite) {
        return new NpcShopData(
            this.id, this.name, this.ownerId, this.ownerName,
            this.location, this.npcId, infinite,
            this.buyEnabled, this.sellEnabled, this.createdAt, this.description
        );
    }

    /**
     * 设置购买启用
     */
    public NpcShopData withBuyEnabled(boolean enabled) {
        return new NpcShopData(
            this.id, this.name, this.ownerId, this.ownerName,
            this.location, this.npcId, this.infiniteStock,
            enabled, this.sellEnabled, this.createdAt, this.description
        );
    }

    /**
     * 设置出售启用
     */
    public NpcShopData withSellEnabled(boolean enabled) {
        return new NpcShopData(
            this.id, this.name, this.ownerId, this.ownerName,
            this.location, this.npcId, this.infiniteStock,
            this.buyEnabled, enabled, this.createdAt, this.description
        );
    }

    /**
     * 检查是否有绑定的NPC
     */
    public boolean hasNpc() {
        return npcId != null;
    }
}
