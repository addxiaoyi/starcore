package dev.starcore.starcore.foundation.tooltip;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 物品提示上下文
 * 包含生成提示所需的所有上下文信息
 */
public class TooltipContext {

    private final Player player;
    private final ItemStack item;
    private final Material material;
    private final long timestamp;
    private final Map<String, Object> metadata;

    public TooltipContext(@NotNull Player player, @NotNull ItemStack item) {
        this.player = player;
        this.item = item;
        this.material = item.getType();
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }

    /**
     * 获取玩家
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取玩家UUID
     */
    @NotNull
    public UUID getPlayerId() {
        return player.getUniqueId();
    }

    /**
     * 获取玩家名称
     */
    @NotNull
    public String getPlayerName() {
        return player.getName();
    }

    /**
     * 获取物品
     */
    @NotNull
    public ItemStack getItem() {
        return item;
    }

    /**
     * 获取物品材质
     */
    @NotNull
    public Material getMaterial() {
        return material;
    }

    /**
     * 获取物品数量
     */
    public int getAmount() {
        return item.getAmount();
    }

    /**
     * 获取物品名称（自定义名称）
     */
    @Nullable
    public String getCustomName() {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        }
        return null;
    }

    /**
     * 获取物品的本地化名称
     */
    @NotNull
    public String getItemName() {
        String customName = getCustomName();
        if (customName != null) {
            return customName;
        }
        return getMaterialKey();
    }

    /**
     * 获取材质键（如 minecraft:diamond_sword）
     */
    @NotNull
    public String getMaterialKey() {
        return material.getKey().toString();
    }

    /**
     * 获取物品的Lore描述
     */
    @NotNull
    public java.util.List<String> getLore() {
        var meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            return meta.lore().stream()
                .map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(c))
                .collect(java.util.stream.Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }

    /**
     * 检查物品是否有自定义Lore
     */
    public boolean hasLore() {
        var meta = item.getItemMeta();
        return meta != null && meta.hasLore() && !meta.lore().isEmpty();
    }

    /**
     * 获取创建上下文的时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 检查物品是否是特殊物品（命令方块、屏障等）
     */
    public boolean isSpecialItem() {
        return material == Material.COMMAND_BLOCK ||
               material == Material.COMMAND_BLOCK_MINECART ||
               material == Material.CHAIN_COMMAND_BLOCK ||
               material == Material.REPEATING_COMMAND_BLOCK ||
               material == Material.BARRIER ||
               material == Material.STRUCTURE_VOID ||
               material == Material.KNOWLEDGE_BOOK ||
               material == Material.SPAWNER;
    }

    /**
     * 检查物品是否被命名
     */
    public boolean isNamed() {
        return getCustomName() != null;
    }

    /**
     * 检查物品是否有附魔
     */
    public boolean hasEnchantments() {
        var meta = item.getItemMeta();
        return meta != null && !meta.getEnchants().isEmpty();
    }

    /**
     * 检查物品是否无法破坏
     */
    public boolean isUnbreakable() {
        var meta = item.getItemMeta();
        return meta != null && meta.isUnbreakable();
    }

    /**
     * 检查是否显示耐久度（默认不显示）
     */
    public boolean isShowDurability() {
        return Boolean.TRUE.equals(metadata.get("showDurability"));
    }

    /**
     * 检查是否显示Lore（默认显示）
     */
    public boolean isShowLore() {
        Object showLore = metadata.get("showLore");
        return showLore == null || Boolean.TRUE.equals(showLore);
    }

    /**
     * 获取元数据值
     */
    @Nullable
    public Object getMetadata(@NotNull String key) {
        return metadata.get(key);
    }

    /**
     * 设置元数据值
     */
    public void setMetadata(@NotNull String key, @NotNull Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取所有元数据
     */
    @NotNull
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * 复制上下文（包含新的时间戳）
     */
    @NotNull
    public TooltipContext copy() {
        TooltipContext copy = new TooltipContext(player, item);
        copy.metadata.putAll(this.metadata);
        return copy;
    }

    @Override
    public String toString() {
        return "TooltipContext{" +
               "player=" + player.getName() +
               ", material=" + material +
               ", customName=" + getCustomName() +
               '}';
    }
}
