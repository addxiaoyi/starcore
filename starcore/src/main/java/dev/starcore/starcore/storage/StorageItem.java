package dev.starcore.starcore.storage;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 存储物品记录
 * 记录存储在仓库中的物品信息
 */
public class StorageItem {
    private final UUID itemId;
    private final Material material;
    private final int amount;
    private final ItemStack itemStack;
    private final Instant depositTime;
    private final UUID depositedBy;
    private final int slot;

    /**
     * 完整构造函数
     * @param itemId 物品唯一ID
     * @param material 物品材质类型
     * @param amount 数量
     * @param itemStack 完整物品堆栈（包含附魔、lore等）
     * @param depositTime 存入时间
     * @param depositedBy 存入者UUID
     * @param slot 存储槽位
     */
    public StorageItem(UUID itemId, Material material, int amount, ItemStack itemStack,
                       Instant depositTime, UUID depositedBy, int slot) {
        this.itemId = Objects.requireNonNull(itemId, "itemId cannot be null");
        this.material = Objects.requireNonNull(material, "material cannot be null");
        this.amount = amount;
        this.itemStack = itemStack != null ? itemStack.clone() : null;
        this.depositTime = depositTime != null ? depositTime : Instant.now();
        this.depositedBy = depositedBy;
        this.slot = slot;
    }

    /**
     * 简化构造函数（从ItemStack创建，自动分配槽位和存入者）
     * @param itemStack 物品堆栈
     */
    public StorageItem(ItemStack itemStack) {
        this(
                UUID.randomUUID(),
                itemStack.getType(),
                itemStack.getAmount(),
                itemStack,
                Instant.now(),
                null,  // depositedBy
                -1    // slot (未分配)
        );
    }

    /**
     * 简化构造函数（从ItemStack创建）
     * @param itemStack 物品堆栈
     * @param depositedBy 存入者
     * @param slot 槽位
     */
    public StorageItem(ItemStack itemStack, UUID depositedBy, int slot) {
        this(
                UUID.randomUUID(),
                itemStack.getType(),
                itemStack.getAmount(),
                itemStack,
                Instant.now(),
                depositedBy,
                slot
        );
    }

    /**
     * 获取物品唯一ID
     * @return 物品ID
     */
    public UUID getItemId() {
        return itemId;
    }

    /**
     * 获取物品材质类型
     * @return 材质
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * 获取数量
     * @return 物品数量
     */
    public int getAmount() {
        return amount;
    }

    /**
     * 获取完整物品堆栈
     * @return 物品堆栈的副本
     */
    public ItemStack getItemStack() {
        return itemStack != null ? itemStack.clone() : new ItemStack(material, amount);
    }

    /**
     * 获取存入时间
     * @return 时间戳
     */
    public Instant getDepositTime() {
        return depositTime;
    }

    /**
     * 获取存入者UUID
     * @return 玩家UUID
     */
    public UUID getDepositedBy() {
        return depositedBy;
    }

    /**
     * 获取存储槽位
     * @return 槽位索引（0-based）
     */
    public int getSlot() {
        return slot;
    }

    /**
     * 创建指定数量的副本
     * @param newAmount 新数量
     * @return 新的StorageItem
     */
    public StorageItem withAmount(int newAmount) {
        ItemStack newStack = itemStack != null ? itemStack.clone() : new ItemStack(material);
        newStack.setAmount(newAmount);
        return new StorageItem(
                UUID.randomUUID(),
                material,
                newAmount,
                newStack,
                depositTime,
                depositedBy,
                slot
        );
    }

    /**
     * 创建指定槽位的副本
     * @param newSlot 新槽位
     * @return 新的StorageItem
     */
    public StorageItem withSlot(int newSlot) {
        return new StorageItem(
                itemId,
                material,
                amount,
                itemStack,
                depositTime,
                depositedBy,
                newSlot
        );
    }

    /**
     * 判断物品是否相似（可堆叠）
     * @param other 另一个物品
     * @return true如果可以堆叠
     */
    public boolean isSimilar(StorageItem other) {
        if (other == null) return false;
        if (this.itemStack != null && other.itemStack != null) {
            return this.itemStack.isSimilar(other.itemStack);
        }
        return this.material == other.material;
    }

    /**
     * 判断物品是否相似（与ItemStack比较）
     * @param stack 物品堆栈
     * @return true如果可以堆叠
     */
    public boolean isSimilar(ItemStack stack) {
        if (stack == null) return false;
        if (this.itemStack != null) {
            return this.itemStack.isSimilar(stack);
        }
        return this.material == stack.getType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageItem that = (StorageItem) o;
        return Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "StorageItem{" +
                "id=" + itemId +
                ", material=" + material +
                ", amount=" + amount +
                ", slot=" + slot +
                ", depositTime=" + depositTime +
                ", depositedBy=" + depositedBy +
                '}';
    }
}
