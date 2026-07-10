package dev.starcore.starcore.module.dungeon;

import dev.starcore.starcore.foundation.util.RandomProvider;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 副本世界管理器
 * 负责副本世界的创建、克隆和管理
 */
public class DungeonWorldManager {
    private final JavaPlugin plugin;
    private final DungeonConfig config;
    private final Logger logger;
    private final Set<String> activeWorlds;
    private final Map<String, String> worldToInstance;
    private final Map<String, WorldSnapshot> worldSnapshots;

    // 副本实例管理
    private final Map<String, DungeonInstance> dungeonInstances;
    // 怪物生成配置
    private final Map<String, MobSpawnConfig> mobSpawnConfigs;
    // 奖励配置
    private final Map<String, DungeonReward> dungeonRewards;

    // 定时任务ID
    private int spawnTaskId = -1;

    public DungeonWorldManager(JavaPlugin plugin, DungeonConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.activeWorlds = ConcurrentHashMap.newKeySet();
        this.worldToInstance = new ConcurrentHashMap<>();
        this.worldSnapshots = new ConcurrentHashMap<>();
        this.dungeonInstances = new ConcurrentHashMap<>();
        this.mobSpawnConfigs = new ConcurrentHashMap<>();
        this.dungeonRewards = new ConcurrentHashMap<>();

        // 初始化默认怪物配置
        initDefaultMobConfigs();
        // 启动怪物生成任务
        startMobSpawnTask();
    }

    /**
     * 初始化默认怪物生成配置
     */
    private void initDefaultMobConfigs() {
        // 普通怪物配置
        mobSpawnConfigs.put("zombie", new MobSpawnConfig(EntityType.ZOMBIE, 10, 300, 3, 1.0));
        mobSpawnConfigs.put("skeleton", new MobSpawnConfig(EntityType.SKELETON, 10, 300, 3, 1.0));
        mobSpawnConfigs.put("spider", new MobSpawnConfig(EntityType.SPIDER, 8, 300, 2, 1.0));
        mobSpawnConfigs.put("creeper", new MobSpawnConfig(EntityType.CREEPER, 5, 300, 2, 0.8));
        mobSpawnConfigs.put("enderman", new MobSpawnConfig(EntityType.ENDERMAN, 3, 300, 1, 0.5));

        // BOSS配置
        mobSpawnConfigs.put("wither_boss", new MobSpawnConfig(EntityType.WITHER, 1, 1800, 1, 2.0));
        mobSpawnConfigs.put("ender_dragon", new MobSpawnConfig(EntityType.ENDER_DRAGON, 1, 1800, 1, 2.0));
    }

    /**
     * 启动怪物生成任务
     */
    private void startMobSpawnTask() {
        spawnTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String worldName : activeWorlds) {
                spawnMobsInWorld(worldName);
            }
        }, 20L * 60, 20L * 30).getTaskId(); // 1分钟后开始，每30秒检查
    }

    /**
     * 在世界中生成怪物
     */
    private void spawnMobsInWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }

        DungeonInstance instance = dungeonInstances.get(worldName);
        if (instance == null) {
            return;
        }

        String difficulty = instance.getDifficulty();
        int currentMobs = countMobs(world);

        if (currentMobs >= instance.getMaxMobs()) {
            return; // 怪物数量已达上限
        }

        // 根据难度和配置生成怪物
        int mobsToSpawn = Math.min(instance.getMaxMobs() - currentMobs, 5);
        for (int i = 0; i < mobsToSpawn; i++) {
            spawnRandomMob(world, difficulty);
        }
    }

    /**
     * 生成随机怪物
     */
    private void spawnRandomMob(World world, String difficulty) {
        List<MobSpawnConfig> eligibleMobs = new ArrayList<>();

        for (MobSpawnConfig cfg : mobSpawnConfigs.values()) {
            if (cfg.isBoss()) {
                continue; // BOSS单独生成
            }
            eligibleMobs.add(cfg);
        }

        if (eligibleMobs.isEmpty()) {
            return;
        }

        MobSpawnConfig config = eligibleMobs.get(RandomProvider.nextInt(eligibleMobs.size()));

        // 在玩家附近生成
        for (Player player : world.getPlayers()) {
            Location spawnLoc = getRandomSpawnLocation(player.getLocation(), 20, 50);
            if (spawnLoc != null) {
                Entity entity = world.spawnEntity(spawnLoc, config.entityType());

                // 添加药水效果（困难模式）
                if (entity instanceof org.bukkit.entity.LivingEntity living && difficulty.equals("HARD")) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 0));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                }
                break;
            }
        }
    }

    /**
     * 获取随机生成位置
     */
    private Location getRandomSpawnLocation(Location center, double minDist, double maxDist) {
        double angle = RandomProvider.nextDouble() * 2 * Math.PI;
        double distance = minDist + RandomProvider.nextDouble() * (maxDist - minDist);

        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(center.getWorld(), x, y, z);
    }

    /**
     * 计算世界中的怪物数量
     */
    private int countMobs(World world) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getType().isAlive() && !(entity instanceof Player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成BOSS
     */
    public boolean spawnBoss(String worldName, String bossType) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }

        MobSpawnConfig bossConfig = mobSpawnConfigs.get(bossType);
        if (bossConfig == null || !bossConfig.isBoss()) {
            return false;
        }

        // 在世界中心生成BOSS
        Location center = new Location(world, 0, world.getHighestBlockYAt(0, 0) + 1, 0);
        Entity boss = world.spawnEntity(center, bossConfig.entityType());

        if (boss instanceof org.bukkit.entity.LivingEntity living) {
            // 设置BOSS属性
            living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 4));
            living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }

        logger.info("已在 " + worldName + " 生成BOSS: " + bossType);
        return true;
    }

    /**
     * 创建副本实例
     */
    public Optional<DungeonInstance> createDungeonInstance(String dungeonId, Player owner, String difficulty) {
        UUID instanceId = UUID.randomUUID();
        String worldName = "dungeon_" + instanceId.toString().substring(0, 8);

        // 获取模板世界配置
        String templateWorld = null;
        Optional<DungeonDefinition> dungeonOpt = config.getDungeon(dungeonId);
        if (dungeonOpt.isPresent()) {
            templateWorld = dungeonOpt.get().templateWorld();
        }

        // 创建世界（使用模板世界）
        Optional<World> worldOpt = createDungeonWorld(worldName, templateWorld);
        if (worldOpt.isEmpty()) {
            return Optional.empty();
        }
        World world = worldOpt.get();

        // 创建实例
        DungeonInstance instance = new DungeonInstance(
            instanceId,
            dungeonId,
            worldName,
            owner.getUniqueId(),
            difficulty,
            System.currentTimeMillis(),
            0
        );
        instance.setMaxMobs(getMaxMobsForDifficulty(difficulty));

        dungeonInstances.put(worldName, instance);
        worldToInstance.put(worldName, instanceId.toString());

        // 生成初始怪物
        spawnInitialMobs(world, difficulty);

        // 生成BOSS
        spawnBoss(worldName, "wither_boss");

        logger.info("已创建副本实例: " + worldName + " (难度: " + difficulty + ")");
        return Optional.of(instance);
    }

    /**
     * 根据难度获取最大怪物数量
     */
    private int getMaxMobsForDifficulty(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> 20;
            case "NORMAL" -> 40;
            case "HARD" -> 80;
            default -> 30;
        };
    }

    /**
     * 生成初始怪物
     */
    private void spawnInitialMobs(World world, String difficulty) {
        int initialMobs = getMaxMobsForDifficulty(difficulty) / 4;
        for (int i = 0; i < initialMobs; i++) {
            spawnRandomMob(world, difficulty);
        }
    }

    /**
     * 完成副本
     */
    public Optional<DungeonReward> completeDungeon(String worldName, Player winner) {
        DungeonInstance instance = dungeonInstances.remove(worldName);
        if (instance == null) {
            return Optional.empty();
        }

        // 计算奖励
        DungeonReward reward = calculateReward(instance, winner);

        // 保存快照
        saveSnapshot(worldName);

        // 30秒后删除世界
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deleteWorldInstance(worldName);
        }, 20L * 30);

        logger.info("副本完成: " + worldName + " 奖励: " + reward.totalValue());
        return Optional.of(reward);
    }

    /**
     * 计算奖励
     */
    private DungeonReward calculateReward(DungeonInstance instance, Player winner) {
        double baseReward = switch (instance.getDifficulty()) {
            case "EASY" -> 1000;
            case "NORMAL" -> 2500;
            case "HARD" -> 5000;
            default -> 1500;
        };

        // 时间奖励（越快完成奖励越高）
        long elapsed = (System.currentTimeMillis() - instance.getCreatedAt()) / 1000;
        double timeBonus = elapsed < 600 ? 1.5 : (elapsed < 1800 ? 1.0 : 0.75);

        double totalReward = baseReward * timeBonus;

        DungeonReward reward = new DungeonReward(
            instance.getInstanceId(),
            winner.getUniqueId(),
            (int) totalReward,
            10 + (int) (timeBonus * 20), // 经验
            new ArrayList<>(),
            totalReward
        );

        dungeonRewards.put(instance.getInstanceId().toString(), reward);
        return reward;
    }

    /**
     * 获取副本实例
     */
    public DungeonInstance getDungeonInstance(String worldName) {
        return dungeonInstances.get(worldName);
    }

    /**
     * 获取所有活跃副本实例
     */
    public Collection<DungeonInstance> getActiveInstances() {
        return dungeonInstances.values();
    }

    /**
     * 获取玩家的副本实例
     */
    public Optional<DungeonInstance> getPlayerInstance(UUID playerId) {
        for (DungeonInstance instance : dungeonInstances.values()) {
            if (instance.getOwnerId().equals(playerId)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        if (spawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(spawnTaskId);
        }
        cleanup();
    }

    // ==================== 内部类 ====================

    /**
     * 怪物生成配置
     */
    public record MobSpawnConfig(
        EntityType entityType,
        int maxCount,
        int respawnTime,
        int spawnGroupSize,
        double difficultyMultiplier
    ) {
        public boolean isBoss() {
            return entityType == EntityType.WITHER ||
                   entityType == EntityType.ENDER_DRAGON ||
                   entityType == EntityType.ELDER_GUARDIAN;
        }
    }

    /**
     * 副本实例
     */
    public static class DungeonInstance {
        private final UUID instanceId;
        private final String dungeonId;
        private final String worldName;
        private final UUID ownerId;
        private final String difficulty;
        private final long createdAt;
        private int currentMobs;
        private int maxMobs;
        private boolean completed;
        private Set<UUID> participants;

        public DungeonInstance(UUID instanceId, String dungeonId, String worldName,
                              UUID ownerId, String difficulty, long createdAt, int currentMobs) {
            this.instanceId = instanceId;
            this.dungeonId = dungeonId;
            this.worldName = worldName;
            this.ownerId = ownerId;
            this.difficulty = difficulty;
            this.createdAt = createdAt;
            this.currentMobs = currentMobs;
            this.maxMobs = 30;
            this.completed = false;
            this.participants = new HashSet<>();
        }

        public UUID getInstanceId() { return instanceId; }
        public String getDungeonId() { return dungeonId; }
        public String getWorldName() { return worldName; }
        public UUID getOwnerId() { return ownerId; }
        public String getDifficulty() { return difficulty; }
        public long getCreatedAt() { return createdAt; }
        public int getCurrentMobs() { return currentMobs; }
        public void setCurrentMobs(int count) { this.currentMobs = count; }
        public int getMaxMobs() { return maxMobs; }
        public void setMaxMobs(int max) { this.maxMobs = max; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public Set<UUID> getParticipants() { return participants; }
    }

    /**
     * 副本奖励
     */
    public record DungeonReward(
        UUID instanceId,
        UUID winnerId,
        int gold,
        int experience,
        List<String> items,
        double totalValue
    ) {}

    /**
     * 创建副本世界
     * @param worldName 世界名称
     * @param templateWorld 模板世界（如果为空则创建空世界）
     * @return 创建的世界，如果失败返回空Optional
     */
    public Optional<World> createDungeonWorld(String worldName, String templateWorld) {
        try {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                // 世界已存在，直接返回
                activeWorlds.add(worldName);
                return Optional.of(world);
            }

            // 创建世界
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.createWorld();

            world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("无法创建副本世界: " + worldName);
                return Optional.empty();
            }

            world.setGameRule(GameRules.SPAWN_MOBS, true);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setGameRule(GameRules.MOB_GRIEFING, false);
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
            world.setGameRule(GameRules.RESPAWN_RADIUS, 0);

            // 保存世界
            world.save();

            activeWorlds.add(worldName);

            // 如果有模板世界，复制方块数据
            if (templateWorld != null && !templateWorld.isEmpty()) {
                copyFromTemplate(worldName, templateWorld);
            }

            logger.info("已创建副本世界: " + worldName);
            return Optional.of(world);

        } catch (Exception e) {
            logger.warning("创建副本世界失败: " + worldName + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从模板世界复制
     */
    private void copyFromTemplate(String targetWorld, String templateWorld) {
        try {
            File templateDir = new File(Bukkit.getWorldContainer(), templateWorld);
            File targetDir = new File(Bukkit.getWorldContainer(), targetWorld);

            if (!templateDir.exists()) {
                logger.warning("模板世界不存在: " + templateWorld);
                return;
            }

            // 复制世界文件
            copyDirectory(templateDir.toPath(), targetDir.toPath());

            // 重新加载世界
            Bukkit.createWorld(new WorldCreator(targetWorld));

            logger.info("已从模板复制世界: " + templateWorld + " -> " + targetWorld);

        } catch (Exception e) {
            logger.warning("复制模板世界失败: " + e.getMessage());
        }
    }

    /**
     * 复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
            .forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    logger.warning("复制文件失败: " + e.getMessage());
                }
            });
    }

    /**
     * 卸载副本世界
     */
    public void unloadDungeonWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // 清理所有实体
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove();
            }
        }

        // 保存世界
        world.save();

        // 卸载世界
        Bukkit.unloadWorld(world, true);

        activeWorlds.remove(worldName);
        worldToInstance.remove(worldName);

        logger.info("已卸载副本世界: " + worldName);
    }

    /**
     * 创建世界快照
     */
    public WorldSnapshot createSnapshot(String worldName, UUID instanceId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        WorldSnapshot snapshot = new WorldSnapshot(
            instanceId,
            worldName,
            System.currentTimeMillis(),
            world.getWorldFolder().getAbsolutePath()
        );

        worldSnapshots.put(worldName, snapshot);

        // 同时保存到 worldToInstance 映射
        worldToInstance.put(worldName, instanceId.toString());

        return snapshot;
    }

    /**
     * 恢复世界快照
     */
    public boolean restoreSnapshot(String worldName) {
        WorldSnapshot snapshot = worldSnapshots.get(worldName);
        if (snapshot == null) {
            logger.warning("快照不存在: " + worldName);
            return false;
        }

        // 检查快照目录是否存在
        Path snapshotPath = Paths.get(snapshot.worldFolder());
        if (!Files.exists(snapshotPath)) {
            logger.warning("快照目录不存在: " + snapshotPath);
            return false;
        }

        try {
            // 卸载当前世界
            World currentWorld = Bukkit.getWorld(worldName);
            if (currentWorld != null) {
                // 清理所有实体（保留玩家）
                for (Entity entity : currentWorld.getEntities()) {
                    if (!(entity instanceof org.bukkit.entity.Player)) {
                        entity.remove();
                    }
                }
                // 保存并卸载
                currentWorld.save();
                Bukkit.unloadWorld(currentWorld, false);
            }

            // 删除当前世界目录
            File currentWorldDir = new File(Bukkit.getWorldContainer(), worldName);
            if (currentWorldDir.exists()) {
                deleteDirectory(currentWorldDir);
            }

            // 从快照复制
            Path targetPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
            copyDirectory(snapshotPath, targetPath);

            // 重新加载世界
            WorldCreator creator = new WorldCreator(worldName);
            World newWorld = creator.createWorld();

            if (newWorld != null) {
                activeWorlds.add(worldName);
                logger.info("已恢复世界快照: " + worldName);
                return true;
            }

            return false;
        } catch (Exception e) {
            logger.warning("恢复世界快照失败: " + worldName + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存世界快照（将当前状态保存到快照）
     */
    public boolean saveSnapshot(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("世界不存在，无法保存快照: " + worldName);
            return false;
        }

        try {
            // 保存世界
            world.save();

            // 更新快照信息
            WorldSnapshot snapshot = new WorldSnapshot(
                UUID.randomUUID(),
                worldName,
                System.currentTimeMillis(),
                world.getWorldFolder().getAbsolutePath()
            );

            worldSnapshots.put(worldName, snapshot);
            logger.info("已保存世界快照: " + worldName);
            return true;
        } catch (Exception e) {
            logger.warning("保存世界快照失败: " + worldName + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 创建世界实例（用于副本）
     * @return 世界实例名称，失败时返回 Optional.empty()
     */
    public Optional<String> createWorldInstance(String baseWorldName, UUID instanceId) {
        String instanceName = baseWorldName + "_" + instanceId.toString().substring(0, 8);

        World templateWorld = Bukkit.getWorld(baseWorldName);
        if (templateWorld == null) {
            logger.warning("模板世界不存在: " + baseWorldName);
            return Optional.empty();
        }

        try {
            // 复制世界
            Path templatePath = templateWorld.getWorldFolder().toPath();
            Path instancePath = Bukkit.getWorldContainer().toPath().resolve(instanceName);

            // 删除已存在的实例目录
            File existingDir = instancePath.toFile();
            if (existingDir.exists()) {
                deleteDirectory(existingDir);
            }

            // 复制文件
            copyDirectory(templatePath, instancePath);

            // 创建世界
            WorldCreator creator = new WorldCreator(instanceName);
            World instanceWorld = creator.createWorld();

            if (instanceWorld != null) {
                activeWorlds.add(instanceName);
                worldToInstance.put(instanceName, instanceId.toString());
                logger.info("已创建世界实例: " + instanceName);
                return Optional.of(instanceName);
            }

            return Optional.empty();
        } catch (Exception e) {
            logger.warning("创建世界实例失败: " + baseWorldName + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 删除世界实例
     */
    public void deleteWorldInstance(String instanceName) {
        unloadDungeonWorld(instanceName);

        // 删除世界目录
        File worldDir = new File(Bukkit.getWorldContainer(), instanceName);
        if (worldDir.exists()) {
            deleteDirectory(worldDir);
            logger.info("已删除世界实例: " + instanceName);
        }
    }

    /**
     * 获取世界实例的拥有者ID
     */
    public Optional<UUID> getWorldInstanceOwner(String instanceName) {
        String ownerStr = worldToInstance.get(instanceName);
        if (ownerStr != null) {
            try {
                return Optional.of(UUID.fromString(ownerStr));
            } catch (IllegalArgumentException e) {
                // 可能只是被截断的ID
            }
        }
        return Optional.empty();
    }

    /**
     * 清理所有副本世界
     */
    public void cleanup() {
        for (String worldName : new HashSet<>(activeWorlds)) {
            unloadDungeonWorld(worldName);
        }
        worldSnapshots.clear();
    }

    /**
     * 检查世界是否活跃
     */
    public boolean isWorldActive(String worldName) {
        return activeWorlds.contains(worldName);
    }

    /**
     * 获取所有活跃世界
     */
    public Set<String> getActiveWorlds() {
        return Set.copyOf(activeWorlds);
    }

    /**
     * 获取世界中的玩家数量
     */
    public int getPlayerCount(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return 0;
        return world.getPlayers().size();
    }

    /**
     * 世界快照
     */
    public record WorldSnapshot(
        UUID instanceId,
        String worldName,
        long createdAt,
        String worldFolder
    ) {}
}
