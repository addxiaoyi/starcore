package dev.starcore.starcore.module.dungeon;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

/**
 * 副本配置加载器
 */
public class DungeonConfig {
    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private Map<String, DungeonDefinition> dungeons;
    private Map<DungeonDifficulty, DifficultySettings> difficultySettings;

    public DungeonConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dungeons = new HashMap<>();
        this.difficultySettings = new HashMap<>();
    }

    /**
     * 加载配置
     */
    public void load() {
        // 保存默认配置
        if (!plugin.isEnabled()) return;

        plugin.saveResource("dungeons.yml", false);

        File file = new File(plugin.getDataFolder(), "dungeons.yml");
        if (!file.exists()) {
            plugin.saveResource("dungeons.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);

        // 加载难度设置
        loadDifficultySettings();

        // 加载副本定义
        loadDungeons();

        logger.info("已加载 " + dungeons.size() + " 个副本配置");
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        load();
    }

    /**
     * 加载难度设置
     */
    private void loadDifficultySettings() {
        ConfigurationSection diffSection = config.getConfigurationSection("difficulties");
        if (diffSection == null) {
            // 使用默认设置
            difficultySettings.put(DungeonDifficulty.EASY, new DifficultySettings(1, 2, 0.5, 0.5, 0.5, 30));
            difficultySettings.put(DungeonDifficulty.NORMAL, new DifficultySettings(1, 4, 1.0, 1.0, 1.0, 60));
            difficultySettings.put(DungeonDifficulty.HARD, new DifficultySettings(3, 5, 1.5, 1.5, 1.5, 120));
            difficultySettings.put(DungeonDifficulty.NIGHTMARE, new DifficultySettings(4, 5, 2.0, 2.0, 2.5, 240));
            return;
        }

        for (DungeonDifficulty difficulty : DungeonDifficulty.values()) {
            ConfigurationSection section = diffSection.getConfigurationSection(difficulty.getId());
            if (section != null) {
                int minPlayers = section.getInt("max-party-size", 1);
                int maxPlayers = section.getInt("max-party-size", 4);
                double mobScaling = section.getDouble("mob-scaling", 1.0);
                double bossHealthMultiplier = section.getDouble("boss-health-multiplier", 1.0);
                double rewardMultiplier = section.getDouble("completion-reward-multiplier", 1.0);
                int cooldown = section.getInt("entry-cooldown-minutes", 60);

                difficultySettings.put(difficulty, new DifficultySettings(
                    Math.max(1, minPlayers / 2),
                    maxPlayers,
                    mobScaling,
                    bossHealthMultiplier,
                    rewardMultiplier,
                    cooldown
                ));
            }
        }
    }

    /**
     * 加载副本定义
     */
    private void loadDungeons() {
        dungeons.clear();

        ConfigurationSection dungeonSection = config.getConfigurationSection("dungeons");
        if (dungeonSection == null) {
            logger.warning("未找到副本配置!");
            return;
        }

        for (String dungeonId : dungeonSection.getKeys(false)) {
            ConfigurationSection section = dungeonSection.getConfigurationSection(dungeonId);
            if (section == null) continue;

            try {
                DungeonDefinition definition = loadDungeonDefinition(dungeonId, section);
                dungeons.put(dungeonId, definition);
                logger.info("已加载副本: " + definition.name());
            } catch (Exception e) {
                logger.warning("加载副本 " + dungeonId + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 加载单个副本定义
     */
    private DungeonDefinition loadDungeonDefinition(String id, ConfigurationSection section) {
        String difficultyStr = section.getString("difficulty", "normal");
        DungeonDifficulty difficulty = DungeonDifficulty.fromId(difficultyStr);

        // 加载描述
        List<String> description = section.getStringList("description");

        // 加载图标
        String iconStr = section.getString("icon", "CHEST");
        Material icon = Material.valueOf(iconStr.toUpperCase());

        // 加载玩家数量
        int minPlayers = section.getInt("min-players", 1);
        int maxPlayers = section.getInt("max-players", 4);

        // 加载推荐等级
        int recommendedLevel = section.getInt("recommended-level", 10);

        // 加载入场费
        int entryFee = section.getInt("entry-fee", 0);

        // 加载奖励
        DungeonRewards rewards = loadRewards(section.getConfigurationSection("rewards"), difficulty);

        // 加载房间
        List<DungeonRoom> rooms = loadRooms(section.getConfigurationSection("rooms"));

        // 加载入口点
        DungeonEntrance entrance = loadEntrance(section.getConfigurationSection("entrance"));

        // 加载模板世界
        String templateWorld = section.getString("template-world", "");

        // 验证模板世界是否存在
        if (!templateWorld.isEmpty() && !validateTemplateWorld(templateWorld)) {
            logger.warning("副本 " + id + " 的模板世界 '" + templateWorld + "' 不存在，将使用默认设置");
        }

        return new DungeonDefinition(
            id,
            section.getString("name", id),
            description,
            difficulty,
            icon.name(),
            minPlayers,
            maxPlayers,
            recommendedLevel,
            entryFee,
            rewards,
            rooms,
            entrance,
            templateWorld,
            Map.of()
        );
    }

    /**
     * 加载奖励
     */
    private DungeonRewards loadRewards(ConfigurationSection section, DungeonDifficulty difficulty) {
        if (section == null) {
            return new DungeonRewards(0, 0, List.of(), Map.of(), List.of(), 1.0);
        }

        int experience = section.getInt("experience", 100);
        int gold = section.getInt("gold", 50);
        double multiplier = difficultySettings.getOrDefault(difficulty,
            new DifficultySettings(1, 4, 1.0, 1.0, 1.0, 60)).rewardMultiplier();

        List<DungeonRewards.RewardItem> items = new ArrayList<>();
        List<?> itemList = section.getList("items", List.of());
        for (Object item : itemList) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) item;
                String material = (String) itemMap.get("material");
                int amount = ((Number) itemMap.getOrDefault("amount", 1)).intValue();
                items.add(new DungeonRewards.RewardItem(material, amount, null, 1.0));
            }
        }

        return new DungeonRewards(
            experience,
            gold,
            items,
            Map.of(),
            List.of(),
            multiplier
        );
    }

    /**
     * 加载房间列表
     */
    private List<DungeonRoom> loadRooms(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<DungeonRoom> rooms = new ArrayList<>();
        for (String roomId : section.getKeys(false)) {
            ConfigurationSection roomSection = section.getConfigurationSection(roomId);
            if (roomSection == null) continue;

            DungeonRoom room = loadRoom(roomId, roomSection);
            rooms.add(room);
        }
        return rooms;
    }

    /**
     * 加载单个房间
     */
    private DungeonRoom loadRoom(String id, ConfigurationSection section) {
        String typeStr = section.getString("type", "SPAWNER");
        DungeonRoomType type = DungeonRoomType.valueOf(typeStr.toUpperCase());

        String displayName = section.getString("name", id);

        // 根据房间类型加载不同数据
        String mobType = section.getString("mob-type", "ZOMBIE");
        int mobCount = section.getInt("mob-count", 10);

        List<String> traps = section.getStringList("traps");

        String puzzleType = section.getString("puzzle-type", "NONE");
        int puzzleTimeLimit = section.getInt("puzzle-time-limit-seconds", 120);

        DungeonBoss boss = null;
        if (type == DungeonRoomType.BOSS) {
            boss = loadBoss(section.getConfigurationSection("boss"));
        }

        int waveCount = section.getInt("wave-count", 5);
        List<String> allowedMobTypes = section.getStringList("mob-types");

        DungeonRoomClearCondition condition = loadClearCondition(section);

        return new DungeonRoom(
            id,
            type,
            displayName,
            mobType,
            mobCount,
            traps,
            puzzleType,
            puzzleTimeLimit,
            boss,
            waveCount,
            allowedMobTypes,
            condition,
            Map.of()
        );
    }

    /**
     * 加载BOSS定义
     */
    private DungeonBoss loadBoss(ConfigurationSection section) {
        // 使用默认 BOSS 配置，确保不会返回 null
        String id = section != null ? section.getName() : "default_boss";
        String name = section != null ? section.getString("name", "BOSS") : "BOSS";
        String entityType = section != null ? section.getString("entity-type", "ZOMBIE") : "ZOMBIE";
        int baseHealth = section != null ? section.getInt("health", 500) : 500;
        double healthMultiplier = section != null ? section.getDouble("health-multiplier", 1.0) : 1.0;

        List<String> abilities = section != null ? section.getStringList("abilities") : List.of();
        List<DungeonBoss.BossPhase> phases = loadBossPhases(section);

        DungeonBoss.BossDefeatCondition condition = new DungeonBoss.BossDefeatCondition(
            DungeonClearType.DEFEAT_BOSS,
            0,
            false
        );

        return new DungeonBoss(
            id,
            name,
            entityType,
            baseHealth,
            healthMultiplier,
            abilities,
            phases,
            condition,
            List.of(),
            60
        );
    }

    /**
     * 加载BOSS阶段列表
     */
    private List<DungeonBoss.BossPhase> loadBossPhases(ConfigurationSection section) {
        List<DungeonBoss.BossPhase> phases = new ArrayList<>();
        if (section == null) {
            // 添加默认阶段：血量 50% 时激活
            phases.add(new DungeonBoss.BossPhase(0.5, List.of("ENRAGE"), null, 1.5));
            return phases;
        }

        List<?> phaseList = section.getList("phases", List.of());
        if (phaseList.isEmpty()) {
            // 配置为空时添加默认阶段
            phases.add(new DungeonBoss.BossPhase(0.5, List.of("ENRAGE"), null, 1.5));
            return phases;
        }

        for (Object phaseObj : phaseList) {
            if (phaseObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> phaseMap = (Map<String, Object>) phaseObj;
                double threshold = ((Number) phaseMap.getOrDefault("threshold", 0.5)).doubleValue();
                @SuppressWarnings("unchecked")
                List<String> phaseAbilities = (List<String>) phaseMap.getOrDefault("abilities", List.of());
                double damageMultiplier = ((Number) phaseMap.getOrDefault("damage-multiplier", 1.0)).doubleValue();
                phases.add(new DungeonBoss.BossPhase(threshold, phaseAbilities, null, damageMultiplier));
            }
        }
        return phases;
    }

    /**
     * 加载清除条件
     */
    private DungeonRoomClearCondition loadClearCondition(ConfigurationSection section) {
        String conditionStr = section.getString("clear-condition", "kill_all");

        return switch (conditionStr.toLowerCase()) {
            case "kill_all" -> DungeonRoomClearCondition.killAll();
            case "survive" -> DungeonRoomClearCondition.survive(
                section.getInt("survive-duration-seconds", 30));
            case "solve_puzzle" -> DungeonRoomClearCondition.solvePuzzle();
            case "defeat_boss" -> DungeonRoomClearCondition.defeatBoss();
            case "survive_waves" -> DungeonRoomClearCondition.surviveWaves(
                section.getInt("wave-count", 5));
            default -> DungeonRoomClearCondition.killAll();
        };
    }

    /**
     * 加载入口点
     */
    private DungeonEntrance loadEntrance(ConfigurationSection section) {
        if (section == null) {
            return new DungeonEntrance("world", 0, 64, 0, 0, 0);
        }

        String world = section.getString("world", "world");
        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 64);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        return new DungeonEntrance(world, x, y, z, yaw, pitch);
    }

    /**
     * 获取所有副本
     */
    public Collection<DungeonDefinition> getAllDungeons() {
        return dungeons.values();
    }

    /**
     * 获取副本定义
     */
    public Optional<DungeonDefinition> getDungeon(String id) {
        return Optional.ofNullable(dungeons.get(id));
    }

    /**
     * 获取难度设置
     */
    public DifficultySettings getDifficultySettings(DungeonDifficulty difficulty) {
        return difficultySettings.getOrDefault(difficulty,
            new DifficultySettings(1, 4, 1.0, 1.0, 1.0, 60));
    }

    /**
     * 检查模块是否启用
     */
    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    /**
     * 获取世界配置前缀
     */
    public String getWorldPrefix() {
        return config.getString("world.prefix", "dungeon_");
    }

    /**
     * 获取清理超时时间(分钟)
     */
    public int getCleanupTimeoutMinutes() {
        return config.getInt("world.cleanup-timeout-minutes", 60);
    }

    /**
     * 获取最大并发实例数
     */
    public int getMaxConcurrentInstances() {
        return config.getInt("max-concurrent-instances", 10);
    }

    /**
     * 获取自动保存间隔(秒)
     */
    public int getAutoSaveInterval() {
        return config.getInt("auto-save-interval", 300);
    }

    /**
     * 检查PvP是否禁用
     */
    public boolean isPvpDisabled() {
        return config.getBoolean("protection.disable-pvp", true);
    }

    /**
     * 检查方块交互是否禁用
     */
    public boolean isBlockInteractionDisabled() {
        return config.getBoolean("protection.block-interaction", true);
    }

    /**
     * 难度设置
     */
    public record DifficultySettings(
        int minPlayers,
        int maxPlayers,
        double mobScaling,
        double bossHealthMultiplier,
        double rewardMultiplier,
        int cooldownMinutes
    ) {}

    /**
     * 验证模板世界是否存在
     * @param worldName 世界名称
     * @return 是否存在
     */
    private boolean validateTemplateWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return false;
        }
        File worldFolder = new File(plugin.getServer().getWorldContainer(), worldName);
        boolean exists = worldFolder.exists() && worldFolder.isDirectory();
        if (!exists) {
            logger.fine("模板世界验证失败: " + worldName + " (路径: " + worldFolder.getAbsolutePath() + ")");
        }
        return exists;
    }

    /**
     * 获取模板世界的有效路径
     * @param worldName 世界名称
     * @return 有效路径，如果不存在返回 null
     */
    public File getTemplateWorldFolder(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        File worldFolder = new File(plugin.getServer().getWorldContainer(), worldName);
        return (worldFolder.exists() && worldFolder.isDirectory()) ? worldFolder : null;
    }
}
