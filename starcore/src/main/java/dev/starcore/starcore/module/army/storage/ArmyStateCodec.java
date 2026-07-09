package dev.starcore.starcore.module.army.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.starcore.starcore.module.army.model.ArmyState;
import dev.starcore.starcore.module.army.model.ArmyType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

/**
 * 军队状态编解码器
 * 用于序列化和反序列化军队数据
 */
public final class ArmyStateCodec {
    private static final Gson GSON = new Gson();

    /**
     * 编码为 JSON
     */
    public String encode(ArmyUnit army) {
        JsonObject json = new JsonObject();
        json.addProperty("id", army.id().toString());
        json.addProperty("nationId", army.nationId().toString());
        json.addProperty("type", army.type().key());
        json.addProperty("soldiers", army.soldiers());
        json.addProperty("health", army.health());
        json.addProperty("morale", army.morale());
        json.addProperty("state", army.state().key());
        json.addProperty("supply", army.supply());
        json.addProperty("createdAt", army.createdAt().toEpochMilli());
        json.addProperty("lastUpdated", army.lastUpdated().toEpochMilli());

        // 位置
        Location loc = army.location();
        JsonObject locJson = new JsonObject();
        locJson.addProperty("world", loc.getWorld().getName());
        locJson.addProperty("x", loc.getX());
        locJson.addProperty("y", loc.getY());
        locJson.addProperty("z", loc.getZ());
        json.add("location", locJson);

        return GSON.toJson(json);
    }

    /**
     * 从 JSON 解码
     */
    public ArmyUnit decode(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);

        UUID id = UUID.fromString(obj.get("id").getAsString());
        UUID nationId = UUID.fromString(obj.get("nationId").getAsString());
        ArmyType type = ArmyType.fromString(obj.get("type").getAsString());
        int soldiers = obj.get("soldiers").getAsInt();
        double health = obj.get("health").getAsDouble();
        double morale = obj.get("morale").getAsDouble();
        ArmyState state = ArmyState.fromString(obj.get("state").getAsString());
        int supply = obj.get("supply").getAsInt();
        Instant createdAt = Instant.ofEpochMilli(obj.get("createdAt").getAsLong());

        // 位置
        JsonObject locJson = obj.getAsJsonObject("location");
        String worldName = locJson.get("world").getAsString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0); // 默认世界
        }
        Location location = new Location(
            world,
            locJson.get("x").getAsDouble(),
            locJson.get("y").getAsDouble(),
            locJson.get("z").getAsDouble()
        );

        return new ArmyUnit(
            id,
            nationId,
            type,
            soldiers,
            health,
            morale,
            location,
            state,
            supply,
            createdAt
        );
    }
}
