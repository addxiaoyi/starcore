package dev.starcore.starcore.storage;

import com.google.gson.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * StorageItem 的 JSON 序列化适配器
 */
public class StorageItemAdapter implements JsonSerializer<StorageItem>, JsonDeserializer<StorageItem> {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger("StarCore-StorageItemAdapter");

    @Override
    public JsonElement serialize(StorageItem src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", src.getItemId().toString());
        json.addProperty("material", src.getMaterial().name());
        json.addProperty("amount", src.getAmount());

        // 序列化物物品堆栈详情
        if (src.getItemStack() != null) {
            JsonObject itemStackJson = new JsonObject();
            itemStackJson.addProperty("material", src.getItemStack().getType().name());
            itemStackJson.addProperty("amount", src.getItemStack().getAmount());
            itemStackJson.addProperty("durability", src.getItemStack().getDurability());

            ItemMeta meta = src.getItemStack().getItemMeta();
            if (meta != null) {
                // 序列lore
                if (meta.hasLore()) {
                    JsonArray loreArray = new JsonArray();
                    for (String lore : meta.getLore()) {
                        loreArray.add(lore);
                    }
                    itemStackJson.add("lore", loreArray);
                }

                // E-033: 完整序列化丢失项——附魔
                if (meta.hasEnchants()) {
                    JsonObject enchJson = new JsonObject();
                    for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                        enchJson.addProperty(e.getKey().getKey().toString(), e.getValue());
                    }
                    itemStackJson.add("enchantments", enchJson);
                }

                // customModelData
                if (meta.hasCustomModelData()) {
                    itemStackJson.addProperty("customModelData", meta.getCustomModelData());
                }

                // unbreakable
                if (meta.isUnbreakable()) {
                    itemStackJson.addProperty("unbreakable", true);
                }
            }

            json.add("itemStack", itemStackJson);
        }

        json.addProperty("depositTime", src.getDepositTime().toEpochMilli());
        if (src.getDepositedBy() != null) {
            json.addProperty("depositedBy", src.getDepositedBy().toString());
        }
        json.addProperty("slot", src.getSlot());

        return json;
    }

    @Override
    public StorageItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        UUID itemId = UUID.fromString(obj.get("itemId").getAsString());
        // E-032: 原 Material.valueOf 在版本升级后某些 material 名不存在会抛 IllegalArgumentException
        // 导致整个 warehouses 加载失败。改用 matchMaterial;失败时跳过该项并记录警告
        String materialName = obj.get("material").getAsString();
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LOGGER.warning("Skipping storage item: unknown material '" + materialName + "' (item " + itemId + ")");
            throw new JsonParseException("Unknown material: " + materialName);
        }
        int amount = obj.get("amount").getAsInt();

        ItemStack itemStack = null;
        if (obj.has("itemStack") && !obj.get("itemStack").isJsonNull()) {
            JsonObject stackJson = obj.getAsJsonObject("itemStack");
            Material stackMaterial = Material.matchMaterial(stackJson.get("material").getAsString());
            // E-032: 跳过未知 material
            if (stackMaterial == null) {
                LOGGER.warning("Skipping storage itemStack: unknown material '" + stackJson.get("material").getAsString()
                        + "' (item " + itemId + ")");
                throw new JsonParseException("Unknown itemStack material");
            }
            int stackAmount = stackJson.get("amount").getAsInt();
            short durability = stackJson.has("durability") ?
                    stackJson.get("durability").getAsShort() : 0;

            itemStack = new ItemStack(stackMaterial, stackAmount);
            itemStack.setDurability(durability);

            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                // 反序列化lore
                if (stackJson.has("lore")) {
                    JsonArray loreArray = stackJson.getAsJsonArray("lore");
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    for (JsonElement element : loreArray) {
                        lore.add(element.getAsString());
                    }
                    meta.setLore(lore);
                }

                // E-033: 还原附魔
                if (stackJson.has("enchantments")) {
                    JsonObject enchObj = stackJson.getAsJsonObject("enchantments");
                    for (Map.Entry<String, JsonElement> e : enchObj.entrySet()) {
                        try {
                            Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(e.getKey()));
                            if (ench != null) {
                                meta.addEnchant(ench, e.getValue().getAsInt(), true);
                            }
                        } catch (Exception ex) {
                            LOGGER.warning("Skipping enchantment '" + e.getKey() + "': " + ex.getMessage());
                        }
                    }
                }

                // customModelData
                if (stackJson.has("customModelData")) {
                    try {
                        meta.setCustomModelData(stackJson.get("customModelData").getAsInt());
                    } catch (Exception ignored) {
                        // 非法值忽略
                    }
                }

                // unbreakable
                if (stackJson.has("unbreakable") && stackJson.get("unbreakable").getAsBoolean()) {
                    meta.setUnbreakable(true);
                }

                itemStack.setItemMeta(meta);
            }
        }

        Instant depositTime = obj.has("depositTime") ?
                Instant.ofEpochMilli(obj.get("depositTime").getAsLong()) : Instant.now();

        UUID depositedBy = null;
        if (obj.has("depositedBy") && !obj.get("depositedBy").isJsonNull()) {
            depositedBy = UUID.fromString(obj.get("depositedBy").getAsString());
        }

        int slot = obj.has("slot") ? obj.get("slot").getAsInt() : -1;

        return new StorageItem(itemId, material, amount, itemStack, depositTime, depositedBy, slot);
    }
}
