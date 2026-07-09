package dev.starcore.starcore.protection.integration;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 自定义 NBT 物品保护
 *
 * 提供对带有自定义 NBT 标签的物品的通用保护
 * 用于保护不属于主流插件但使用 NBT 标记的自定义物品
 */
public class CustomNBTProtection implements CustomItemProtection {

    private final Plugin plugin;
    private final Logger logger;
    private final NamespacedKey customItemKey;
    private final NamespacedKey restrictedKey;

    public CustomNBTProtection(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.customItemKey = new NamespacedKey(plugin, "custom_item");
        this.restrictedKey = new NamespacedKey(plugin, "restricted");
    }

    // ==================== 物品保护 ====================

    @Override
    public boolean canUse(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }

        // 检查是否为受限物品
        if (isRestricted(item)) {
            if (!player.hasPermission("starcore.protection.nbt.use.restricted")) {
                logger.fine(String.format("玩家 %s 无权使用受限的 NBT 物品", player.getName()));
                return false;
            }
        }

        // 检查基本使用权限
        if (isCustomItem(item)) {
            if (!player.hasPermission("starcore.protection.nbt.use")) {
                logger.fine(String.format("玩家 %s 无权使用自定义 NBT 物品", player.getName()));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean canPickup(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }

        // 检查是否为受限物品
        if (isRestricted(item)) {
            if (!player.hasPermission("starcore.protection.nbt.pickup.restricted")) {
                logger.fine(String.format("玩家 %s 无权拾取受限的 NBT 物品", player.getName()));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean canDrop(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }

        // 检查是否为受限物品（防止丢弃）
        if (isRestricted(item)) {
            if (!player.hasPermission("starcore.protection.nbt.drop.restricted")) {
                logger.fine(String.format("玩家 %s 无权丢弃受限的 NBT 物品", player.getName()));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean canCraft(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }

        // NBT 物品通常不能通过普通工作台制作
        if (isCustomItem(item)) {
            if (!player.hasPermission("starcore.protection.nbt.craft")) {
                logger.fine(String.format("玩家 %s 无权制作自定义 NBT 物品", player.getName()));
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // 检查是否有自定义 NBT 标记
        if (meta.getPersistentDataContainer().has(customItemKey, PersistentDataType.STRING)) {
            return true;
        }

        // 检查是否有受限标记
        if (meta.getPersistentDataContainer().has(restrictedKey, PersistentDataType.BYTE)) {
            return true;
        }

        // 检查是否有自定义模型数据
        if (meta.hasCustomModelData()) {
            return true;
        }

        // 检查是否有未知的 PDC 键（可能来自其他插件）
        if (!meta.getPersistentDataContainer().isEmpty()) {
            return true;
        }

        return false;
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        // 尝试获取自定义物品ID
        if (meta.getPersistentDataContainer().has(customItemKey, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(customItemKey, PersistentDataType.STRING);
        }

        // 使用自定义模型数据作为ID
        if (meta.hasCustomModelData()) {
            return "custom_model_" + meta.getCustomModelData();
        }

        return "unknown_nbt_item";
    }

    // ==================== NBT 工具方法 ====================

    /**
     * 检查物品是否被标记为受限
     */
    public boolean isRestricted(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(restrictedKey, PersistentDataType.BYTE);
    }

    /**
     * 标记物品为自定义物品
     */
    public void markAsCustomItem(ItemStack item, String itemId) {
        if (item == null || itemId == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
    }

    /**
     * 标记物品为受限物品
     */
    public void markAsRestricted(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(restrictedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    /**
     * 移除物品的受限标记
     */
    public void removeRestriction(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().remove(restrictedKey);
        item.setItemMeta(meta);
    }

    /**
     * 检查物品是否有任何自定义 NBT 数据
     */
    public boolean hasCustomNBT(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return !meta.getPersistentDataContainer().isEmpty();
    }
}
