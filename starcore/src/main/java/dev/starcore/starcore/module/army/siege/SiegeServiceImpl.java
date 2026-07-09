package dev.starcore.starcore.module.army.siege;
import java.util.Optional;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.siege.event.*;
import dev.starcore.starcore.module.army.siege.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 攻城器械服务实现
 */
public final class SiegeServiceImpl implements SiegeService {
    private static final String PERSISTENCE_NAMESPACE = "siege";
    private static final String SIEGE_STATE_FILE = "sieges.dat";
    private static final String WALL_STATE_FILE = "walls.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final SiegeConfig config;
    private final Map<SiegeType, SiegeTypeConfig> typeConfigs;
    private final MessageService messages;
    private final PersistenceService persistenceService;

    // 所有攻城器械
    private final ConcurrentHashMap<UUID, SiegeUnit> sieges = new ConcurrentHashMap<>();
    // 国家的攻城器械索引
    private final ConcurrentHashMap<UUID, Set<UUID>> nationSieges = new ConcurrentHashMap<>();
    // 所有城墙
    private final ConcurrentHashMap<UUID, WallData> walls = new ConcurrentHashMap<>();
    // 城墙位置索引 (world,x,z -> wallId)
    private final ConcurrentHashMap<String, UUID> wallLocationIndex = new ConcurrentHashMap<>();
    // 部署冷却记录
    private final ConcurrentHashMap<UUID, Long> deploymentCooldowns = new ConcurrentHashMap<>();

    public SiegeServiceImpl(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        SiegeConfig config,
        Map<SiegeType, SiegeTypeConfig> typeConfigs,
        MessageService messages,
        PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.config = config;
        this.typeConfigs = typeConfigs;
        this.messages = messages;
        this.persistenceService = persistenceService;

        // 加载持久化数据
        if (persistenceService != null) {
            loadData();
        }

        startPeriodicTasks();
    }

    // ==================== 配置方法 ====================

    public SiegeConfig getConfig() {
        return config;
    }

    public SiegeTypeConfig getTypeConfig(SiegeType type) {
        return typeConfigs.getOrDefault(type, SiegeTypeConfig.defaults());
    }

    // ==================== 攻城器械创建和管理 ====================

    /**
     * 创建攻城器械
     */
    public SiegeUnit createSiege(UUID nationId, SiegeType type, int crewSize, Location location) {
        // 检查限制
        Set<UUID> nationSiegeIds = nationSieges.getOrDefault(nationId, Collections.emptySet());
        if (nationSiegeIds.size() >= config.maxSiegePerNation()) {
            throw new IllegalStateException("siege.error.max-limit");
        }

        SiegeTypeConfig typeConfig = getTypeConfig(type);
        if (nationSiegeIds.stream().map(sieges::get).filter(Objects::nonNull).filter(s -> s.type() == type).count() >= typeConfig.maxPerType()) {
            throw new IllegalStateException("siege.error.max-type-limit");
        }

        if (crewSize < config.minCrewSize()) {
            throw new IllegalArgumentException("siege.error.min-crew");
        }

        if (crewSize > config.maxCrewSize()) {
            throw new IllegalArgumentException("siege.error.max-crew");
        }

        // 计算成本并从国库扣除
        int cost = type.constructionCost() + (crewSize * 50);
        NationId nationIdObj = new NationId(nationId);

        BigDecimal treasuryBalance = treasuryService.balance(nationIdObj);
        if (treasuryBalance.compareTo(BigDecimal.valueOf(cost)) < 0) {
            throw new IllegalStateException("siege.error.insufficient-treasury");
        }

        if (!treasuryService.withdraw(nationIdObj, BigDecimal.valueOf(cost))) {
            throw new IllegalStateException("siege.error.withdraw-failed");
        }

        // 创建攻城器械
        SiegeUnit siege = SiegeUnit.create(nationId, type, crewSize, location);

        // 注册
        sieges.put(siege.id(), siege);
        nationSieges.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(siege.id());

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeCreatedEvent(siege));

        // 持久化
        persistSiege(siege);

        return siege;
    }

    /**
     * 部署攻城器械
     */
    public void deploySiege(UUID siegeId, Location targetLocation) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        if (!siege.canMove()) {
            throw new IllegalStateException("Siege cannot move in current state");
        }

        // 检查部署冷却
        if (!canDeploy(siege.nationId())) {
            throw new IllegalStateException("siege.error.deployment-cooldown");
        }

        // 部署到目标位置
        siege.deploy(targetLocation);

        // 设置冷却
        deploymentCooldowns.put(siege.nationId(), System.currentTimeMillis() + (config.deploymentCooldownSeconds() * 1000));

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeDeployedEvent(siege, targetLocation));

        // 持久化
        persistSiege(siege);
    }

    /**
     * 开始攻城
     */
    public SiegeResult startSiege(UUID siegeId, UUID targetWallId) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "攻城器械不存在!");
        }

        WallData wall = walls.get(targetWallId);
        if (wall == null) {
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "城墙不存在!");
        }

        // 检查是否同阵营
        if (wall.nationId().equals(siege.nationId())) {
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "不能攻击己方城墙!");
        }

        // 检查距离
        if (!siege.isInRange(wall.location())) {
            return SiegeResult.outOfRange();
        }

        // 检查状态
        if (siege.state() != SiegeState.READY && siege.state() != SiegeState.IDLE) {
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "攻城器械未部署就绪!");
        }

        // 开始攻城
        siege.setSiegeTarget(wall.id());
        siege.setState(SiegeState.BESIEGING);
        wall.startSiege(siege.id());

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeStartedEvent(siege, wall));

        // 持久化
        persistSiege(siege);
        persistWall(wall);

        return SiegeResult.hit(0, wall.currentHealth(), wall.maxHealth(), List.of("攻城开始!"));
    }

    /**
     * 攻城器械开火
     */
    public double fireSiege(UUID siegeId, Location targetLocation) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        if (!siege.canFire()) {
            throw new IllegalStateException("siege.error.cannot-fire");
        }

        // 消耗弹药
        if (!siege.useAmmunition(1)) {
            throw new IllegalStateException("siege.error.no-ammunition");
        }

        // 计算伤害
        SiegeTypeConfig typeConfig = getTypeConfig(siege.type());
        double damage = siege.effectiveAttack() * typeConfig.siegeDamageMultiplier();

        // 检查是否命中城墙
        Optional<WallData> targetWall = getNearestWall(targetLocation, 10);
        if (targetWall.isPresent() && siege.isInRange(targetLocation)) {
            WallData wall = targetWall.get();
            double actualDamage = damage * wall.defensePower();
            wall.takeDamage(actualDamage);

            // 攻城器械获得经验
            siege.addExperience((int) (actualDamage / 10));

            // 检查城墙是否被摧毁
            if (wall.isDestroyed()) {
                // 触发摧毁事件
                plugin.getServer().getPluginManager().callEvent(new WallDestroyedEvent(wall, siege));

                // 结束攻城
                siege.setState(SiegeState.READY);
                wall.endSiege();
            }

            // 触发攻击事件
            plugin.getServer().getPluginManager().callEvent(new SiegeFiredEvent(siege, targetLocation, actualDamage));

            // 持久化
            persistSiege(siege);
            persistWall(wall);

            return actualDamage;
        }

        // 未命中
        siege.changeMorale(-1);
        persistSiege(siege);

        return 0;
    }

    /**
     * 执行攻城攻击
     */
    public SiegeResult performSiegeAttack(UUID siegeId) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null || !siege.isBesieging()) {
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "攻城器械未在攻城状态!");
        }

        WallData wall = walls.get(siege.siegeTarget());
        if (wall == null) {
            siege.setState(SiegeState.READY);
            return SiegeResult.failed(SiegeResult.SiegeResultType.INVALID_TARGET, "目标城墙已不存在!");
        }

        // 开火
        double damage = fireSiege(siegeId, wall.location());

        // 检查结果
        if (wall.isDestroyed()) {
            return SiegeResult.wallDestroyed(wall.maxHealth(), List.of("城墙已被摧毁!"));
        }

        if (wall.type().isGate() && wall.isHeavilyDamaged()) {
            return SiegeResult.gateOpened(wall.currentHealth(), wall.maxHealth(), List.of("城门已打开!"));
        }

        List<String> effects = new ArrayList<>();
        if (damage > 0) {
            effects.add(String.format("造成 %.1f 点伤害", damage));
        }
        if (siege.ammunition() < 5) {
            effects.add("警告: 弹药不足!");
        }

        return SiegeResult.hit(damage, wall.currentHealth(), wall.maxHealth(), effects);
    }

    /**
     * 修复攻城器械
     */
    public void repairSiege(UUID siegeId, double repairAmount) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        siege.repair(repairAmount);
        persistSiege(siege);
    }

    /**
     * 补充攻城器械弹药
     */
    public void reloadSiege(UUID siegeId, int amount) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        siege.reload(amount);
        persistSiege(siege);

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeReloadedEvent(siege, amount));
    }

    /**
     * 移动攻城器械
     */
    public void moveSiege(UUID siegeId, Location targetLocation) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        if (!siege.canMove()) {
            throw new IllegalStateException("Siege cannot move in current state");
        }

        siege.moveTo(targetLocation);
        persistSiege(siege);

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeMovedEvent(siege, targetLocation));
    }

    /**
     * 撤退攻城器械
     */
    public void retreatSiege(UUID siegeId) {
        SiegeUnit siege = sieges.get(siegeId);
        if (siege == null) {
            throw new IllegalArgumentException("Siege not found");
        }

        siege.setState(SiegeState.RETREATING);
        persistSiege(siege);

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new SiegeRetreatedEvent(siege));
    }

    /**
     * 解散攻城器械
     */
    public void disbandSiege(UUID siegeId) {
        SiegeUnit siege = sieges.remove(siegeId);
        if (siege != null) {
            Set<UUID> nationSiegeIds = nationSieges.get(siege.nationId());
            if (nationSiegeIds != null) {
                nationSiegeIds.remove(siegeId);
            }

            // 如果正在攻城，结束攻城
            if (siege.siegeTarget() != null) {
                WallData wall = walls.get(siege.siegeTarget());
                if (wall != null && wall.besiegingSiegeId() != null && wall.besiegingSiegeId().equals(siegeId)) {
                    wall.endSiege();
                    persistWall(wall);
                }
            }

            // 触发事件
            plugin.getServer().getPluginManager().callEvent(new SiegeDestroyedEvent(siege));

            // 从持久化移除
            removePersistedSiege(siegeId);
        }
    }

    // ==================== 攻城器械查询 ====================

    public Optional<SiegeUnit> getSiege(UUID siegeId) {
        return Optional.ofNullable(sieges.get(siegeId));
    }

    public List<SiegeUnit> getNationSieges(UUID nationId) {
        Set<UUID> siegeIds = nationSieges.getOrDefault(nationId, Collections.emptySet());
        return siegeIds.stream()
            .map(sieges::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<SiegeUnit> getSiegesNear(Location location, double radius) {
        return sieges.values().stream()
            .filter(siege -> siege.location().getWorld().equals(location.getWorld()))
            .filter(siege -> siege.location().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    // ==================== 城墙管理 ====================

    /**
     * 创建城墙
     */
    public WallData createWall(Location location, UUID nationId, WallType type) {
        WallData wall = WallData.create(nationId, type, location, 1);

        walls.put(wall.id(), wall);
        wallLocationIndex.put(wallLocationKey(location), wall.id());

        // 触发事件
        plugin.getServer().getPluginManager().callEvent(new WallCreatedEvent(wall));

        persistWall(wall);

        return wall;
    }

    public Optional<WallData> getWall(UUID wallId) {
        return Optional.ofNullable(walls.get(wallId));
    }

    public Optional<WallData> getNearestWall(Location location, double maxDistance) {
        String world = location.getWorld().getName();
        int bx = location.getBlockX();
        int bz = location.getBlockZ();

        return walls.values().stream()
            .filter(wall -> wall.world().equals(world))
            .filter(wall -> {
                int dx = Math.abs(wall.blockX() - bx);
                int dz = Math.abs(wall.blockZ() - bz);
                return Math.sqrt(dx * dx + dz * dz) <= maxDistance;
            })
            .min(Comparator.comparingDouble(wall -> wall.distanceSquared(location)))
            .filter(wall -> wall.distanceSquared(location) <= maxDistance * maxDistance);
    }

    public void repairWall(UUID wallId, double repairAmount) {
        WallData wall = walls.get(wallId);
        if (wall != null) {
            wall.repair((int) repairAmount);
            persistWall(wall);
        }
    }

    // ==================== 冷却检查 ====================

    public boolean canDeploy(UUID nationId) {
        Long cooldownEnd = deploymentCooldowns.get(nationId);
        if (cooldownEnd == null) {
            return true;
        }
        return System.currentTimeMillis() >= cooldownEnd;
    }

    public long getDeploymentCooldownRemaining(UUID nationId) {
        Long cooldownEnd = deploymentCooldowns.get(nationId);
        if (cooldownEnd == null) {
            return 0;
        }
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    // ==================== 定时任务 ====================

    private void startPeriodicTasks() {
        // 每分钟更新攻城器械状态
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            sieges.values().forEach(this::updateSiege);
        }, 20L * 60, 20L * 60);

        // 每5分钟保存所有数据
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAll();
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void updateSiege(SiegeUnit siege) {
        // 攻城状态下自动攻击
        if (siege.isBesieging()) {
            performSiegeAttack(siege.id());
        }

        // 士气恢复
        if (siege.crewMorale() < 100) {
            siege.changeMorale(1);
        }

        persistSiege(siege);
    }

    // ==================== 持久化 ====================

    private String wallLocationKey(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockZ();
    }

    private void loadData() {
        // 加载攻城器械数据
        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE);
            SiegeStateCodec codec = new SiegeStateCodec();
            for (String key : props.stringPropertyNames()) {
                try {
                    SiegeUnit siege = codec.decode(props.getProperty(key));
                    sieges.put(siege.id(), siege);
                    nationSieges.computeIfAbsent(siege.nationId(), k -> ConcurrentHashMap.newKeySet()).add(siege.id());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load siege: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load sieges: " + e.getMessage());
        }

        // 加载城墙数据
        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, WALL_STATE_FILE);
            WallStateCodec codec = new WallStateCodec();
            for (String key : props.stringPropertyNames()) {
                try {
                    WallData wall = codec.decode(props.getProperty(key));
                    walls.put(wall.id(), wall);
                    wallLocationIndex.put(wallLocationKey(wall.location()), wall.id());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load wall: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load walls: " + e.getMessage());
        }

        plugin.getLogger().info("Loaded " + sieges.size() + " sieges and " + walls.size() + " walls");
    }

    public void saveAll() {
        if (persistenceService == null) return;

        try {
            // 保存攻城器械
            var siegeProps = new Properties();
            SiegeStateCodec siegeCodec = new SiegeStateCodec();
            for (SiegeUnit siege : sieges.values()) {
                siegeProps.setProperty(siege.id().toString(), siegeCodec.encode(siege));
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE, siegeProps);

            // 保存城墙
            var wallProps = new Properties();
            WallStateCodec wallCodec = new WallStateCodec();
            for (WallData wall : walls.values()) {
                wallProps.setProperty(wall.id().toString(), wallCodec.encode(wall));
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, WALL_STATE_FILE, wallProps);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save siege data: " + e.getMessage());
        }
    }

    private void persistSiege(SiegeUnit siege) {
        if (persistenceService == null) return;

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE);
            props.setProperty(siege.id().toString(), new SiegeStateCodec().encode(siege));
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist siege: " + e.getMessage());
        }
    }

    private void persistWall(WallData wall) {
        if (persistenceService == null) return;

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, WALL_STATE_FILE);
            props.setProperty(wall.id().toString(), new WallStateCodec().encode(wall));
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, WALL_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist wall: " + e.getMessage());
        }
    }

    private void removePersistedSiege(UUID siegeId) {
        if (persistenceService == null) return;

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE);
            props.remove(siegeId.toString());
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SIEGE_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove siege: " + e.getMessage());
        }
    }

    public void shutdown() {
        saveAll();
    }
}