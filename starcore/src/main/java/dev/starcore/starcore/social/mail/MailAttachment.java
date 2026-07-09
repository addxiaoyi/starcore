package dev.starcore.starcore.social.mail;

import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.util.UUID;

/**
 * 邮件附件数据模型
 */
public final class MailAttachment implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String itemId;
    private final int amount;
    private final String serializedItem;

    public MailAttachment(String itemId, int amount, String serializedItem) {
        this.itemId = itemId;
        this.amount = amount;
        this.serializedItem = serializedItem;
    }

    public MailAttachment(ItemStack item) {
        this.itemId = item.getType().name();
        this.amount = item.getAmount();
        this.serializedItem = serializeItem(item);
    }

    public String getItemId() { return itemId; }
    public int getAmount() { return amount; }
    public String getSerializedItem() { return serializedItem; }

    /**
     * 序列化物品为字符串
     */
    public static String serializeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("material:").append(item.getType().name());
        if (item.getAmount() > 1) {
            sb.append(",amount:").append(item.getAmount());
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            sb.append(",display:").append(item.getItemMeta().getDisplayName().replace("§", "&"));
        }
        return sb.toString();
    }

    /**
     * 反序列化为物品
     */
    public ItemStack deserializeItem() {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return null;
        }
        try {
            String[] parts = serializedItem.split(",");
            String materialName = parts[0].replace("material:", "");
            int amount = 1;

            for (String part : parts) {
                if (part.startsWith("amount:")) {
                    amount = Integer.parseInt(part.replace("amount:", ""));
                }
            }

            org.bukkit.Material material = org.bukkit.Material.valueOf(materialName);
            ItemStack item = new ItemStack(material, amount);
            return item;
        } catch (Exception e) {
            return new ItemStack(org.bukkit.Material.STONE);
        }
    }

    @Override
    public String toString() {
        return itemId + " x" + amount;
    }
}
