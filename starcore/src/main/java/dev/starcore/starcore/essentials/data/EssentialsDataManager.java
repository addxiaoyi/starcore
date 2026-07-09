package dev.starcore.starcore.essentials.data;

import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.nickname.NicknameService;
import dev.starcore.starcore.essentials.social.SocialService;
import dev.starcore.starcore.essentials.warp.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Essentials 数据管理器
 * 负责保存和加载所有 Essentials 数据
 */
public final class EssentialsDataManager {
    private final Plugin plugin;
    private final File dataFolder;

    private final HomeService homeService;
    private final WarpService warpService;
    private final NicknameService nicknameService;
    private final SocialService socialService;
    private final BalTopService balTopService;

    public EssentialsDataManager(
        Plugin plugin,
        HomeService homeService,
        WarpService warpService,
        NicknameService nicknameService,
        SocialService socialService,
        BalTopService balTopService
    ) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "essentials");
        this.homeService = homeService;
        this.warpService = warpService;
        this.nicknameService = nicknameService;
        this.socialService = socialService;
        this.balTopService = balTopService;

        // 创建数据文件夹
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 加载所有数据
     */
    public void loadAll() {
        plugin.getLogger().info("正在加载 Essentials 数据...");

        loadWarps();
        loadPlayerData();
        loadBalTop();

        plugin.getLogger().info("✅ Essentials 数据加载完成");
    }

    /**
     * 保存所有数据
     */
    public void saveAll() {
        plugin.getLogger().info("正在保存 Essentials 数据...");

        saveWarps();
        saveAllPlayerData();
        saveBalTop();

        plugin.getLogger().info("✅ Essentials 数据保存完成");
    }

    /**
     * 加载 Warps
     */
    private void loadWarps() {
        File warpsFile = new File(dataFolder, "warps.yml");
        if (!warpsFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(warpsFile);
        ConfigurationSection warpsSection = config.getConfigurationSection("warps");

        if (warpsSection == null) {
            return;
        }

        Map<String, Location> warps = new HashMap<>();

        for (String warpName : warpsSection.getKeys(false)) {
            Location location = deserializeLocation(
                warpsSection.getConfigurationSection(warpName)
            );

            if (location != null) {
                warps.put(warpName, location);
            }
        }

        warpService.loadData(warps);
        plugin.getLogger().info("已加载 " + warps.size() + " 个传送点");
    }

    /**
     * 保存 Warps
     */
    private void saveWarps() {
        File warpsFile = new File(dataFolder, "warps.yml");
        YamlConfiguration config = new YamlConfiguration();

        Map<String, Location> warps = warpService.saveData();

        for (Map.Entry<String, Location> entry : warps.entrySet()) {
            String path = "warps." + entry.getKey();
            serializeLocation(config, path, entry.getValue());
        }

        try {
            config.save(warpsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存传送点失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家数据
     */
    private void loadPlayerData() {
        File playersFolder = new File(dataFolder, "players");
        if (!playersFolder.exists()) {
            return;
        }

        File[] playerFiles = playersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null) {
            return;
        }

        int loaded = 0;
        for (File playerFile : playerFiles) {
            String fileName = playerFile.getName();
            String uuidString = fileName.substring(0, fileName.length() - 4);

            try {
                UUID playerId = UUID.fromString(uuidString);
                loadPlayerData(playerId, playerFile);
                loaded++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的玩家文件: " + fileName);
            }
        }

        plugin.getLogger().info("已加载 " + loaded + " 个玩家数据");
    }

    /**
     * 加载单个玩家数据
     */
    public void loadPlayerData(UUID playerId, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // 加载家园
        ConfigurationSection homesSection = config.getConfigurationSection("homes");
        if (homesSection != null) {
            Map<String, Location> homes = new HashMap<>();

            for (String homeName : homesSection.getKeys(false)) {
                Location location = deserializeLocation(
                    homesSection.getConfigurationSection(homeName)
                );

                if (location != null) {
                    homes.put(homeName, location);
                }
            }

            homeService.loadPlayerData(playerId, homes);
        }

        // 加载昵称
        if (config.contains("nickname")) {
            String nickname = config.getString("nickname");
            nicknameService.loadPlayerData(playerId, nickname);
        }

        // 加载屏蔽列表
        if (config.contains("ignored")) {
            List<String> ignoredList = config.getStringList("ignored");
            Set<UUID> ignored = new HashSet<>();

            for (String uuidStr : ignoredList) {
                try {
                    ignored.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    // 跳过无效UUID
                }
            }

            socialService.loadPlayerData(playerId, ignored);
        }
    }

    /**
     * 保存所有玩家数据
     */
    private void saveAllPlayerData() {
        // 这里应该遍历所有在线玩家和缓存的玩家
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            savePlayerData(player.getUniqueId());
        }
    }

    /**
     * 保存单个玩家数据
     */
    public void savePlayerData(UUID playerId) {
        File playersFolder = new File(dataFolder, "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }

        File playerFile = new File(playersFolder, playerId.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        // 保存家园
        Map<String, Location> homes = homeService.getPlayerData(playerId);
        if (!homes.isEmpty()) {
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                String path = "homes." + entry.getKey();
                serializeLocation(config, path, entry.getValue());
            }
        }

        // 保存昵称
        String nickname = nicknameService.getPlayerData(playerId);
        if (nickname != null) {
            config.set("nickname", nickname);
        }

        // 保存屏蔽列表
        Set<UUID> ignored = socialService.getPlayerData(playerId);
        if (!ignored.isEmpty()) {
            List<String> ignoredList = new ArrayList<>();
            for (UUID uuid : ignored) {
                ignoredList.add(uuid.toString());
            }
            config.set("ignored", ignoredList);
        }

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe(
                "保存玩家数据失败 (" + playerId + "): " + e.getMessage()
            );
        }
    }

    /**
     * 序列化位置
     */
    private void serializeLocation(YamlConfiguration config, String path, Location location) {
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    /**
     * 反序列化位置
     */
    private Location deserializeLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            return null;
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * 加载财富排行榜数据
     */
    private void loadBalTop() {
        File balTopFile = new File(dataFolder, "baltop.yml");
        if (!balTopFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(balTopFile);
        balTopService.loadData(config);
        plugin.getLogger().info("已加载财富排行榜数据");
    }

    /**
     * 保存财富排行榜数据
     */
    private void saveBalTop() {
        File balTopFile = new File(dataFolder, "baltop.yml");
        YamlConfiguration config = new YamlConfiguration();
        balTopService.saveData(config);

        try {
            config.save(balTopFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存财富排行榜失败: " + e.getMessage());
        }
    }
}
