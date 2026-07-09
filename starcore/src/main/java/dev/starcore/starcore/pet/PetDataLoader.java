package dev.starcore.starcore.pet;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 宠物数据持久化加载器
 */
public class PetDataLoader {
    private final File dataFile;
    private FileConfiguration data;

    public PetDataLoader(org.bukkit.plugin.Plugin plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "pet_data.yml");
    }

    /**
     * 加载所有玩家宠物数据
     */
    public Map<UUID, PlayerPets> loadAll() {
        Map<UUID, PlayerPets> result = new HashMap<>();

        if (!dataFile.exists()) {
            return result;
        }

        try {
            data = YamlConfiguration.loadConfiguration(dataFile);

            if (data.contains("players")) {
                for (String playerIdStr : data.getConfigurationSection("players").getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(playerIdStr);
                        PlayerPets playerPets = loadPlayerPets(playerId);
                        result.put(playerId, playerPets);
                    } catch (Exception e) {
                        Bukkit.getLogger().log(Level.WARNING,
                            "Failed to load pet data for player " + playerIdStr, e);
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load pet data", e);
        }

        return result;
    }

    /**
     * 加载单个玩家宠物数据
     */
    @SuppressWarnings("unchecked")
    private PlayerPets loadPlayerPets(UUID playerId) {
        String path = "players." + playerId.toString();

        if (!data.contains(path)) {
            return new PlayerPets(playerId);
        }

        try {
            Map<String, Object> playerData = data.getConfigurationSection(path).getValues(false);
            return PlayerPets.fromMap(playerData);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING,
                "Failed to load pet data for player " + playerId, e);
            return new PlayerPets(playerId);
        }
    }

    /**
     * 保存所有玩家宠物数据
     */
    public void saveAll(Map<UUID, PlayerPets> allPlayerPets) {
        try {
            data = new YamlConfiguration();

            for (Map.Entry<UUID, PlayerPets> entry : allPlayerPets.entrySet()) {
                String path = "players." + entry.getKey().toString();
                data.createSection(path, entry.getValue().toMap());
            }

            data.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to save pet data", e);
        }
    }

    /**
     * 保存单个玩家宠物数据
     */
    public void savePlayer(UUID playerId, PlayerPets playerPets) {
        if (data == null) {
            loadAll();
        }

        try {
            String path = "players." + playerId.toString();
            data.set(path, playerPets.toMap());
            data.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to save pet data for player " + playerId, e);
        }
    }

    /**
     * 删除玩家宠物数据
     */
    public void deletePlayer(UUID playerId) {
        if (data == null) {
            loadAll();
        }

        try {
            String path = "players." + playerId.toString();
            data.set(path, null);
            data.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to delete pet data for player " + playerId, e);
        }
    }
}
