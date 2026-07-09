package dev.starcore.starcore.storage;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Warehouse 的 JSON 序列化适配器
 */
public class WarehouseAdapter implements JsonSerializer<Warehouse>, JsonDeserializer<Warehouse> {
    private static final Logger LOGGER = Logger.getLogger("StarCore-WarehouseAdapter");
    private final StorageItemAdapter itemAdapter = new StorageItemAdapter();

    @Override
    public JsonElement serialize(Warehouse src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("warehouseId", src.getWarehouseId().toString());
        json.addProperty("name", src.getName());
        json.addProperty("type", src.getType().name());
        json.addProperty("ownerId", src.getOwnerId().toString());
        json.addProperty("level", src.getLevel());
        json.addProperty("createdTime", src.getCreatedTime().toEpochMilli());
        json.addProperty("lastAccessTime", src.getLastAccessTime().toEpochMilli());
        json.addProperty("locked", src.isLocked());

        // 序列化物品
        JsonArray itemsArray = new JsonArray();
        Map<Integer, StorageItem> items = src.getItems();
        for (Map.Entry<Integer, StorageItem> entry : items.entrySet()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("slot", entry.getKey());
            itemJson.add("item", itemAdapter.serialize(entry.getValue(), StorageItem.class, context));
            itemsArray.add(itemJson);
        }
        json.add("items", itemsArray);

        return json;
    }

    @Override
    public Warehouse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        UUID warehouseId = UUID.fromString(obj.get("warehouseId").getAsString());
        String name = obj.get("name").getAsString();
        WarehouseType type = WarehouseType.valueOf(obj.get("type").getAsString());
        UUID ownerId = UUID.fromString(obj.get("ownerId").getAsString());
        int level = obj.get("level").getAsInt();

        Instant createdTime = obj.has("createdTime") && !obj.get("createdTime").isJsonNull() ?
                Instant.ofEpochMilli(obj.get("createdTime").getAsLong()) : Instant.now();
        Instant lastAccessTime = obj.has("lastAccessTime") && !obj.get("lastAccessTime").isJsonNull() ?
                Instant.ofEpochMilli(obj.get("lastAccessTime").getAsLong()) : Instant.now();
        boolean locked = obj.has("locked") && obj.get("locked").getAsBoolean();

        // 创建仓库
        Warehouse warehouse = new Warehouse(warehouseId, name, type, ownerId, level, createdTime);
        warehouse.setLocked(locked);

        // 反序列化物品
        if (obj.has("items") && !obj.get("items").isJsonNull()) {
            JsonArray itemsArray = obj.getAsJsonArray("items");
            for (JsonElement element : itemsArray) {
                // E-032: 单个物品反序列化失败（如版本升级后 material 不存在）时跳过该项并记录警告,
                // 避免整个 warehouses Map 加载失败导致全部仓库不可用
                try {
                    JsonObject itemObj = element.getAsJsonObject();
                    int slot = itemObj.get("slot").getAsInt();
                    StorageItem item = itemAdapter.deserialize(itemObj.get("item"), StorageItem.class, context);
                    warehouse.setItem(slot, item);
                } catch (Exception ex) {
                    LOGGER.warning("Skipping corrupt storage item entry in warehouse "
                            + warehouseId + ": " + ex.getMessage());
                }
            }
        }

        return warehouse;
    }
}
