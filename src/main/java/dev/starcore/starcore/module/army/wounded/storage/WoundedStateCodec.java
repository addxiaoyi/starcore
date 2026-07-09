package dev.starcore.starcore.module.army.wounded.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedSeverity;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.UUID;

/**
 * 伤兵状态编解码器
 * 用于将伤兵记录序列化为 JSON 格式
 */
public final class WoundedStateCodec {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Location.class, new LocationAdapter())
        .registerTypeAdapter(WoundedRecord.class, new WoundedRecordAdapter())
        .setPrettyPrinting()
        .create();

    /**
     * 编码为 JSON 字符串
     */
    public String encode(WoundedRecord record) {
        return GSON.toJson(record);
    }

    /**
     * 从 JSON 字符串解码
     */
    public WoundedRecord decode(String json) {
        return GSON.fromJson(json, WoundedRecord.class);
    }

    /**
     * Location 适配器
     */
    private static class LocationAdapter implements JsonSerializer<Location>, JsonDeserializer<Location> {
        @Override
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (src == null) {
                return com.google.gson.JsonNull.INSTANCE;
            }
            obj.addProperty("world", src.getWorld() != null ? src.getWorld().getName() : null);
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            obj.addProperty("yaw", src.getYaw());
            obj.addProperty("pitch", src.getPitch());
            return obj;
        }

        @Override
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            JsonObject obj = json.getAsJsonObject();
            String worldName = obj.get("world") != null ? obj.get("world").getAsString() : null;
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            double x = obj.get("x") != null ? obj.get("x").getAsDouble() : 0;
            double y = obj.get("y") != null ? obj.get("y").getAsDouble() : 0;
            double z = obj.get("z") != null ? obj.get("z").getAsDouble() : 0;
            float yaw = obj.get("yaw") != null ? obj.get("yaw").getAsFloat() : 0;
            float pitch = obj.get("pitch") != null ? obj.get("pitch").getAsFloat() : 0;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    /**
     * WoundedRecord 适配器
     * 由于 WoundedRecord 是 record 类型，需要手动处理序列化
     */
    private static class WoundedRecordAdapter implements JsonSerializer<WoundedRecord>, JsonDeserializer<WoundedRecord> {
        @Override
        public JsonElement serialize(WoundedRecord src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.id().toString());
            obj.addProperty("nationId", src.nationId().toString());
            obj.addProperty("armyId", src.armyId() != null ? src.armyId().toString() : null);
            obj.addProperty("playerId", src.playerId() != null ? src.playerId().toString() : null);
            obj.addProperty("originalSoldiers", src.originalSoldiers());
            obj.addProperty("currentWounded", src.currentWounded());
            obj.addProperty("severity", src.severity().name());
            obj.addProperty("status", src.status().name());
            obj.addProperty("injuryLocation", context.serialize(src.injuryLocation()).toString());
            obj.addProperty("hospitalLocation", src.hospitalLocation() != null ? context.serialize(src.hospitalLocation()).toString() : null);
            obj.addProperty("injuredAt", src.injuredAt());
            obj.addProperty("healingStartedAt", src.healingStartedAt());
            obj.addProperty("expectedRecoveryAt", src.expectedRecoveryAt());
            obj.addProperty("healingProgress", src.healingProgress());
            return obj;
        }

        @Override
        public WoundedRecord deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            JsonObject obj = json.getAsJsonObject();

            UUID id = UUID.fromString(obj.get("id").getAsString());
            UUID nationId = UUID.fromString(obj.get("nationId").getAsString());
            UUID armyId = obj.get("armyId") != null && !obj.get("armyId").isJsonNull()
                ? UUID.fromString(obj.get("armyId").getAsString()) : null;
            UUID playerId = obj.get("playerId") != null && !obj.get("playerId").isJsonNull()
                ? UUID.fromString(obj.get("playerId").getAsString()) : null;
            int originalSoldiers = obj.get("originalSoldiers").getAsInt();
            int currentWounded = obj.get("currentWounded").getAsInt();
            WoundedSeverity severity = WoundedSeverity.valueOf(obj.get("severity").getAsString());
            WoundedStatus status = WoundedStatus.valueOf(obj.get("status").getAsString());

            Location injuryLocation = context.deserialize(obj.get("injuryLocation"), Location.class);
            Location hospitalLocation = null;
            if (obj.has("hospitalLocation") && !obj.get("hospitalLocation").isJsonNull()) {
                hospitalLocation = context.deserialize(obj.get("hospitalLocation"), Location.class);
            }

            long injuredAt = obj.get("injuredAt").getAsLong();
            long healingStartedAt = obj.get("healingStartedAt").getAsLong();
            long expectedRecoveryAt = obj.get("expectedRecoveryAt").getAsLong();
            double healingProgress = obj.get("healingProgress").getAsDouble();

            return new WoundedRecord(
                id, nationId, armyId, playerId,
                originalSoldiers, currentWounded,
                severity, status,
                injuryLocation, hospitalLocation,
                injuredAt, healingStartedAt, expectedRecoveryAt,
                healingProgress
            );
        }
    }
}