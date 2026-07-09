package dev.starcore.starcore.module.city;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.city.model.CityRank;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * 基于 JSON 文件的城市存储实现
 * 每个城市存储为一个 UUID.json 文件
 */
public final class JsonCityStorage implements CityStateStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Plugin plugin;
    private final Path dataDir;

    public JsonCityStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath().resolve("cities");
        initDataDir();
    }

    private void initDataDir() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create cities directory: " + e.getMessage());
        }
    }

    @Override
    public Collection<City> loadAll() {
        List<City> cities = new ArrayList<>();
        File[] files = dataDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null) {
            return cities;
        }

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                City city = decodeCity(json);
                if (city != null) {
                    cities.add(city);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load city from " + file.getName() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + cities.size() + " cities from storage");
        return cities;
    }

    @Override
    public void saveAll(Collection<City> cities) {
        for (City city : cities) {
            save(city);
        }
    }

    @Override
    public void save(City city) {
        Path filePath = dataDir.resolve(city.id().toString() + ".json");
        try (Writer writer = new FileWriter(filePath.toFile())) {
            JsonObject json = encodeCity(city);
            GSON.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save city " + city.id() + ": " + e.getMessage());
        }
    }

    @Override
    public void delete(UUID cityId) {
        Path filePath = dataDir.resolve(cityId.toString() + ".json");
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete city file " + cityId + ": " + e.getMessage());
        }
    }

    @Override
    public Optional<City> find(UUID cityId) {
        Path filePath = dataDir.resolve(cityId.toString() + ".json");
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try (Reader reader = new FileReader(filePath.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return Optional.ofNullable(decodeCity(json));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load city " + cityId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== 编解码 ====================

    private JsonObject encodeCity(City city) {
        JsonObject json = new JsonObject();

        json.addProperty("id", city.id().toString());
        json.addProperty("nationId", city.nationId().value().toString());
        json.addProperty("name", city.name());
        json.addProperty("treasury", city.treasury());
        json.addProperty("createdAt", city.createdAt().toEpochMilli());
        json.addProperty("lastUpdated", city.lastUpdated().toEpochMilli());

        // 编码出生点
        if (city.spawnChunk() != null) {
            Location loc = city.spawnChunk();
            JsonObject spawn = new JsonObject();
            if (loc.getWorld() != null) {
                spawn.addProperty("world", loc.getWorld().getName());
                spawn.addProperty("x", loc.getBlockX());
                spawn.addProperty("y", loc.getBlockY());
                spawn.addProperty("z", loc.getBlockZ());
                spawn.addProperty("yaw", loc.getYaw());
                spawn.addProperty("pitch", loc.getPitch());
                json.add("spawnChunk", spawn);
            }
        }

        // 编码居民
        JsonObject residents = new JsonObject();
        for (Map.Entry<UUID, CityRank> entry : city.residents().entrySet()) {
            residents.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        json.add("residents", residents);

        // 编码市长等级
        json.addProperty("mayorRank", city.mayorRank().name());

        // 编码等级系统
        json.addProperty("level", city.level());
        json.addProperty("experience", city.experience());

        // 编码城市设置
        json.addProperty("pvpEnabled", city.isPvpEnabled());
        json.addProperty("publicSpawn", city.isPublicSpawn());
        json.addProperty("openRecruitment", city.isOpenRecruitment());

        // 编码公告
        if (city.announcement() != null) {
            json.addProperty("announcement", city.announcement());
        }

        // 编码领土
        if (!city.claims().isEmpty()) {
            json.add("claims", GSON.toJsonTree(city.claims()));
        }

        return json;
    }

    private City decodeCity(JsonObject json) {
        try {
            UUID id = UUID.fromString(json.get("id").getAsString());
            UUID nationIdUuid = UUID.fromString(json.get("nationId").getAsString());
            NationId nationId = NationId.of(nationIdUuid);
            String name = json.get("name").getAsString();
            double treasury = json.has("treasury") ? json.get("treasury").getAsDouble() : 0.0;
            long createdAt = json.get("createdAt").getAsLong();
            long lastUpdated = json.has("lastUpdated") ? json.get("lastUpdated").getAsLong() : createdAt;

            // 解码出生点
            Location spawnChunk = null;
            if (json.has("spawnChunk")) {
                JsonObject spawn = json.getAsJsonObject("spawnChunk");
                String worldName = spawn.get("world").getAsString();
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    spawnChunk = new Location(
                        world,
                        spawn.get("x").getAsDouble(),
                        spawn.get("y").getAsDouble(),
                        spawn.get("z").getAsDouble(),
                        spawn.has("yaw") ? spawn.get("yaw").getAsFloat() : 0f,
                        spawn.has("pitch") ? spawn.get("pitch").getAsFloat() : 0f
                    );
                }
            }

            // 解码居民
            Map<UUID, CityRank> residents = new HashMap<>();
            if (json.has("residents")) {
                JsonObject residentsJson = json.getAsJsonObject("residents");
                for (String key : residentsJson.keySet()) {
                    UUID playerId = UUID.fromString(key);
                    CityRank rank = CityRank.valueOf(residentsJson.get(key).getAsString());
                    residents.put(playerId, rank);
                }
            }

            // 解码市长等级
            CityRank mayorRank = CityRank.MAYOR;
            if (json.has("mayorRank")) {
                mayorRank = CityRank.valueOf(json.get("mayorRank").getAsString());
            }

            // 创建 City 对象（使用新的完整构造函数）
            City city = new City(
                id,
                nationId,
                name,
                spawnChunk,
                residents,
                mayorRank,
                treasury,
                Instant.ofEpochMilli(createdAt),
                Instant.ofEpochMilli(lastUpdated)
            );

            // 加载等级数据
            if (json.has("level")) {
                // 使用反射来设置等级（因为没有公开的 setter）
                try {
                    var levelField = City.class.getDeclaredField("level");
                    levelField.setAccessible(true);
                    levelField.setInt(city, json.get("level").getAsInt());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load city level: " + e.getMessage());
                }
            }
            if (json.has("experience")) {
                try {
                    var expField = City.class.getDeclaredField("experience");
                    expField.setAccessible(true);
                    expField.setInt(city, json.get("experience").getAsInt());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load city experience: " + e.getMessage());
                }
            }
            if (json.has("pvpEnabled")) {
                city.setPvpEnabled(json.get("pvpEnabled").getAsBoolean());
            }
            if (json.has("publicSpawn")) {
                city.setPublicSpawn(json.get("publicSpawn").getAsBoolean());
            }
            if (json.has("openRecruitment")) {
                city.setOpenRecruitment(json.get("openRecruitment").getAsBoolean());
            }
            if (json.has("announcement")) {
                city.setAnnouncement(json.get("announcement").getAsString());
            }
            if (json.has("claims")) {
                json.getAsJsonArray("claims").forEach(el -> {
                    String claim = el.getAsString();
                    String[] parts = claim.split(",");
                    if (parts.length >= 3) {
                        city.addClaim(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    }
                });
            }

            return city;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to decode city: " + e.getMessage());
            return null;
        }
    }
}
