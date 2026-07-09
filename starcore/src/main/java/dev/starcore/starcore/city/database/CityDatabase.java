package dev.starcore.starcore.city.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.city.City;
import org.bukkit.Bukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * City数据库
 * 使用JSON文件存储City数据
 */
public class CityDatabase {

    private final Plugin plugin;
    private final Gson gson;
    private final File dataFolder;

    public CityDatabase(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = new File(plugin.getDataFolder(), "cities");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 保存City（异步）
     */
    public CompletableFuture<Boolean> saveCity(City city) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, city.getId() + ".json");
                CityData data = convertToData(city);

                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(data, writer);
                }

                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("保存City失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 加载City（异步）
     */
    public CompletableFuture<City> loadCity(UUID cityId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, cityId + ".json");
                if (!file.exists()) {
                    return null;
                }

                try (FileReader reader = new FileReader(file)) {
                    CityData data = gson.fromJson(reader, CityData.class);
                    return convertFromData(data);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("加载City失败: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * 删除City（异步）
     */
    public CompletableFuture<Boolean> deleteCity(UUID cityId) {
        return CompletableFuture.supplyAsync(() -> {
            File file = new File(dataFolder, cityId + ".json");
            return file.exists() && file.delete();
        });
    }

    /**
     * 加载所有City（异步）
     */
    public CompletableFuture<List<City>> loadAllCities() {
        return CompletableFuture.supplyAsync(() -> {
            List<City> cities = new ArrayList<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        CityData data = gson.fromJson(reader, CityData.class);
                        City city = convertFromData(data);
                        if (city != null) {
                            cities.add(city);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("加载City失败: " + file.getName());
                    }
                }
            }

            return cities;
        });
    }

    /**
     * 保存所有City（异步）
     */
    public CompletableFuture<Integer> saveAllCities(Collection<City> cities) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (City city : cities) {
                if (saveCity(city).join()) {
                    saved++;
                }
            }
            return saved;
        });
    }

    // ==================== 数据转换 ====================

    private CityData convertToData(City city) {
        CityData data = new CityData();
        data.id = city.getId().toString();
        data.name = city.getName();
        data.nationId = city.getNationId() != null ? city.getNationId().toString() : null;
        data.mayor = city.getMayor().toString();

        data.residents = city.getResidents().stream()
            .map(UUID::toString)
            .toList();

        data.territories = city.getTerritories().stream()
            .map(UUID::toString)
            .toList();

        data.level = city.getLevel();
        data.type = city.getType().name();
        data.treasury = city.getTreasury();
        data.dailyUpkeep = city.getDailyUpkeep();
        data.taxRate = city.getTaxRate();
        data.pvpEnabled = city.isPvpEnabled();
        data.publicSpawn = city.isPublicSpawn();
        data.openRecruitment = city.isOpenRecruitment();
        data.createdTime = city.getCreatedTime();

        // 保存出生点
        if (city.getSpawnPoint() != null) {
            Location spawn = city.getSpawnPoint();
            org.bukkit.World world = spawn.getWorld();
            if (world != null) {
                data.spawnWorld = world.getName();
                data.spawnX = spawn.getX();
                data.spawnY = spawn.getY();
                data.spawnZ = spawn.getZ();
                data.spawnYaw = spawn.getYaw();
                data.spawnPitch = spawn.getPitch();
            }
        }

        return data;
    }

    private City convertFromData(CityData data) {
        try {
            UUID id = UUID.fromString(data.id);
            UUID nationId = data.nationId != null ? UUID.fromString(data.nationId) : null;
            UUID mayor = UUID.fromString(data.mayor);

            City city = new City(id, data.name, nationId, mayor);

            // 添加居民
            for (String residentStr : data.residents) {
                UUID residentId = UUID.fromString(residentStr);
                if (!residentId.equals(mayor)) {
                    city.addResident(residentId);
                }
            }

            // 添加Territory
            for (String territoryStr : data.territories) {
                city.addTerritory(UUID.fromString(territoryStr));
            }

            // 设置属性
            city.setLevel(data.level);
            city.deposit(data.treasury);
            city.setTaxRate(data.taxRate);
            city.setPvpEnabled(data.pvpEnabled);
            city.setPublicSpawn(data.publicSpawn);
            city.setOpenRecruitment(data.openRecruitment);

            // 恢复出生点
            if (data.spawnWorld != null) {
                Location spawn = new Location(
                    Bukkit.getWorld(data.spawnWorld),
                    data.spawnX, data.spawnY, data.spawnZ,
                    data.spawnYaw, data.spawnPitch
                );
                city.setSpawnPoint(spawn);
            }

            return city;
        } catch (Exception e) {
            plugin.getLogger().severe("转换City数据失败: " + e.getMessage());
            return null;
        }
    }

    // ==================== 数据类 ====================

    private static class CityData {
        String id;
        String name;
        String nationId;
        String mayor;
        List<String> residents;
        List<String> territories;
        int level;
        String type;
        double treasury;
        double dailyUpkeep;
        double taxRate;
        boolean pvpEnabled;
        boolean publicSpawn;
        boolean openRecruitment;
        long createdTime;
        String spawnWorld;
        double spawnX;
        double spawnY;
        double spawnZ;
        float spawnYaw;
        float spawnPitch;
    }
}
