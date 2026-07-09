package dev.starcore.starcore.module.army.navy.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.starcore.starcore.module.army.navy.model.NavyState;
import dev.starcore.starcore.module.army.navy.model.NavyType;
import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

/**
 * 海军舰队状态编解码器
 * 用于序列化和反序列化舰队数据
 */
public final class NavyStateCodec {
    private static final Gson GSON = new Gson();

    /**
     * 编码为 JSON
     */
    public String encode(NavyUnit navy) {
        JsonObject json = new JsonObject();
        json.addProperty("id", navy.id().toString());
        json.addProperty("nationId", navy.nationId().toString());
        json.addProperty("type", navy.type().key());
        json.addProperty("ships", navy.ships());
        json.addProperty("health", navy.health());
        json.addProperty("morale", navy.morale());
        json.addProperty("state", navy.state().key());
        json.addProperty("supply", navy.supply());
        json.addProperty("name", navy.name());
        json.addProperty("embarkedUnits", navy.embarkedUnits());
        json.addProperty("createdAt", navy.createdAt().toEpochMilli());
        json.addProperty("lastUpdated", navy.lastUpdated().toEpochMilli());

        // 位置
        Location loc = navy.location();
        JsonObject locJson = new JsonObject();
        locJson.addProperty("world", loc.getWorld().getName());
        locJson.addProperty("x", loc.getX());
        locJson.addProperty("y", loc.getY());
        locJson.addProperty("z", loc.getZ());
        locJson.addProperty("yaw", loc.getYaw());
        locJson.addProperty("pitch", loc.getPitch());
        json.add("location", locJson);

        return GSON.toJson(json);
    }

    /**
     * 从 JSON 解码
     */
    public NavyUnit decode(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);

        UUID id = UUID.fromString(obj.get("id").getAsString());
        UUID nationId = UUID.fromString(obj.get("nationId").getAsString());
        NavyType type = NavyType.fromString(obj.get("type").getAsString());
        int ships = obj.get("ships").getAsInt();
        double health = obj.get("health").getAsDouble();
        double morale = obj.get("morale").getAsDouble();
        NavyState state = NavyState.fromString(obj.get("state").getAsString());
        int supply = obj.get("supply").getAsInt();
        String name = obj.get("name").getAsString();
        int embarkedUnits = obj.has("embarkedUnits") ? obj.get("embarkedUnits").getAsInt() : 0;
        Instant createdAt = Instant.ofEpochMilli(obj.get("createdAt").getAsLong());

        // 位置
        JsonObject locJson = obj.getAsJsonObject("location");
        String worldName = locJson.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0); // 默认世界
        }
        float yaw = locJson.has("yaw") ? locJson.get("yaw").getAsFloat() : 0f;
        float pitch = locJson.has("pitch") ? locJson.get("pitch").getAsFloat() : 0f;
        Location location = new Location(
            world,
            locJson.get("x").getAsDouble(),
            locJson.get("y").getAsDouble(),
            locJson.get("z").getAsDouble(),
            yaw,
            pitch
        );

        return new NavyUnit(
            id,
            nationId,
            type,
            ships,
            health,
            morale,
            location,
            state,
            supply,
            name,
            embarkedUnits,
            createdAt
        );
    }
}
