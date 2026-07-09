package dev.starcore.starcore.module.shop.npc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 商店物品记录
 * @param id 物品唯一ID
 * @param shopId 所属商店ID
 * @param material 物品材质
 * @param displayName 显示名称
 * @param amount 堆叠数量
 * @param buyPrice 购买价格
 * @param sellPrice 出售价格
 * @param stock 当前库存
 * @param maxStock 最大库存
 * @param infiniteStock 是否无限库存
 * @param createdAt 创建时间
 */
public record NpcShopItemData(
    UUID id,
    UUID shopId,
    String material,
    String displayName,
    int amount,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    int stock,
    int maxStock,
    boolean infiniteStock,
    Instant createdAt
) {
    /**
     * 创建商店物品
     */
    public static NpcShopItemData create(UUID shopId, String material, BigDecimal buyPrice, BigDecimal sellPrice, int stock) {
        return new NpcShopItemData(
            UUID.randomUUID(),
            shopId,
            material,
            null,
            1,
            buyPrice,
            sellPrice,
            stock,
            stock * 10,
            false,
            Instant.now()
        );
    }

    /**
     * 创建无限库存物品
     */
    public static NpcShopItemData createInfinite(UUID shopId, String material, BigDecimal buyPrice, BigDecimal sellPrice) {
        return new NpcShopItemData(
            UUID.randomUUID(),
            shopId,
            material,
            null,
            1,
            buyPrice,
            sellPrice,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            true,
            Instant.now()
        );
    }

    /**
     * 计算购买总价
     */
    public BigDecimal calculateBuyTotal(int quantity) {
        return buyPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 计算出售总价
     */
    public BigDecimal calculateSellTotal(int quantity) {
        return sellPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 检查是否有库存
     */
    public boolean hasStock(int quantity) {
        return infiniteStock || stock >= quantity;
    }
}