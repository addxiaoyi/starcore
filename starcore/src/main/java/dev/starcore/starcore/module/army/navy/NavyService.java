package dev.starcore.starcore.module.army.navy;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.module.army.navy.model.NavyBattleResult;
import dev.starcore.starcore.module.army.navy.model.NavyState;
import dev.starcore.starcore.module.army.navy.model.NavyType;
import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import dev.starcore.starcore.module.army.navy.storage.NavyStateCodec;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 海军核心服务
 * 负责舰队的创建、管理、移动、战斗等核心逻辑
 */
public final class NavyService {
    private static final String PERSISTENCE_NAMESPACE = "navy";
    private static final String NAVY_STATE_FILE = "navies.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final NavyBattleCalculator battleCalculator;
    private final NavyServiceConfig config;
    private final NavyStateCodec stateCodec;
    private final dev.starcore.starcore.core.persistence.PersistenceService persistenceService;

    // 所有舰队（内存中）
    private final ConcurrentHashMap<UUID, NavyUnit> navies = new ConcurrentHashMap<>();
    // 国家的舰队索引
    private final ConcurrentHashMap<UUID, Set<UUID>> nationNavies = new ConcurrentHashMap<>();

    public NavyService(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        NavyBattleCalculator battleCalculator,
        NavyServiceConfig config,
        dev.starcore.starcore.core.persistence.PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.battleCalculator = battleCalculator;
        this.config = config;
        this.stateCodec = new NavyStateCodec();
        this.persistenceService = persistenceService;

        // 如果有持久化服务，加载已有数据
        if (persistenceService != null) {
            loadNavies();
        }

        startPeriodicTasks();
    }

    /**
     * 获取配置
     */
    public NavyServiceConfig getConfig() {
        return config;
    }

    /**
     * 创建舰队
     * 消耗: 国库资金
     */
    public NavyUnit createFleet(UUID nationId, NavyType type, int ships, Location location, String name) {
        // 检查限制
        Set<UUID> nationNavyIds = nationNavies.getOrDefault(nationId, Collections.emptySet());
        if (nationNavyIds.size() >= config.maxNaviesPerNation()) {
            throw new IllegalStateException("navy.error.max-limit");
        }

        if (ships > config.maxShipsPerNavy()) {
            throw new IllegalArgumentException("navy.error.too-many-ships");
        }

        if (ships < 1) {
            throw new IllegalArgumentException("navy.error.min-ships");
        }

        // 计算成本并从国库扣除
        BigDecimal cost = BigDecimal.valueOf(type.totalCost(ships));
        NationId nationIdObj = new NationId(nationId);

        if (treasuryService.balance(nationIdObj).compareTo(cost) < 0) {
            throw new IllegalStateException("navy.error.insufficient-treasury");
        }

        if (!treasuryService.withdraw(nationIdObj, cost)) {
            throw new IllegalStateException("navy.error.withdraw-failed");
        }

        // 创建舰队
        NavyUnit navy = NavyUnit.create(nationId, type, ships, location, name);

        // 注册
        navies.put(navy.id(), navy);
        nationNavies.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(navy.id());

        // 持久化
        persistNavy(navy);

        return navy;
    }

    /**
     * 解散舰队
     */
    public void disbandFleet(UUID navyId) {
        NavyUnit navy = navies.remove(navyId);
        if (navy != null) {
            Set<UUID> nationNavyIds = nationNavies.get(navy.nationId());
            if (nationNavyIds != null) {
                nationNavyIds.remove(navyId);
            }
            // 从持久化存储中移除
            removePersistedNavy(navyId);
        }
    }

    /**
     * 获取舰队
     */
    public Optional<NavyUnit> getFleet(UUID navyId) {
        return Optional.ofNullable(navies.get(navyId));
    }

    /**
     * 获取国家的所有舰队
     */
    public List<NavyUnit> getNationNavies(UUID nationId) {
        Set<UUID> navyIds = nationNavies.getOrDefault(nationId, Collections.emptySet());
        return navyIds.stream()
            .map(navies::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 移动舰队
     */
    public void moveFleet(UUID navyId, Location destination) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("Navy not found");
        }

        if (!navy.canMove()) {
            throw new IllegalStateException("Navy cannot move");
        }

        // 检查距离
        double distance = navy.location().distance(destination);
        double maxDistance = navy.type().mobility() * 100; // 每点机动性可移动100格

        if (distance > maxDistance) {
            throw new IllegalArgumentException("Destination too far");
        }

        // 移动
        navy.moveTo(destination);
    }

    /**
     * 设置舰队状态
     */
    public void setFleetState(UUID navyId, NavyState newState) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("Navy not found");
        }
        navy.setState(newState);
    }

    /**
     * 发起海战
     */
    public NavyBattleResult engage(UUID attackerId, UUID defenderId) {
        NavyUnit attacker = navies.get(attackerId);
        NavyUnit defender = navies.get(defenderId);

        if (attacker == null || defender == null) {
            throw new IllegalArgumentException("Navy not found");
        }

        if (!attacker.canFight()) {
            throw new IllegalStateException("Attacker cannot fight");
        }

        // 检查距离（海战范围更大）
        double distance = attacker.location().distance(defender.location());
        if (distance > attacker.type().attackRange() + 50) {
            throw new IllegalArgumentException("Navies too far apart for battle");
        }

        // 执行战斗
        NavyBattleResult result = battleCalculator.calculateBattle(attacker, defender);

        // 清理被消灭的舰队
        if (!attacker.isAlive()) {
            disbandFleet(attackerId);
        }
        if (!defender.isAlive()) {
            disbandFleet(defenderId);
        }

        return result;
    }

    /**
     * 补给舰队
     * 消耗: 国库资金
     */
    public void resupplyFleet(UUID navyId) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("navy.error.not-found");
        }

        // 计算补给成本
        int supplyNeeded = 100 - navy.supply();
        if (supplyNeeded <= 0) {
            return; // 已经满补给
        }

        BigDecimal cost = BigDecimal.valueOf(supplyNeeded).multiply(BigDecimal.valueOf(15));
        NationId nationIdObj = new NationId(navy.nationId());

        if (!treasuryService.withdraw(nationIdObj, cost)) {
            throw new IllegalStateException("navy.error.insufficient-treasury-for-supply");
        }

        // 补给
        navy.resupply(supplyNeeded);
    }

    /**
     * 重命名舰队
     */
    public void renameFleet(UUID navyId, String newName) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("Navy not found");
        }
        navy.setName(newName);
    }

    /**
     * 搭载陆军单位
     */
    public boolean embarkUnits(UUID navyId, int count) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("Navy not found");
        }
        return navy.embarkUnits(count);
    }

    /**
     * 卸载陆军单位
     */
    public int disembarkUnits(UUID navyId, int count) {
        NavyUnit navy = navies.get(navyId);
        if (navy == null) {
            throw new IllegalArgumentException("Navy not found");
        }
        return navy.disembarkUnits(count);
    }

    /**
     * 预测海战结果
     */
    public NavyBattleCalculator.BattlePrediction predictBattle(UUID attackerId, UUID defenderId) {
        NavyUnit attacker = navies.get(attackerId);
        NavyUnit defender = navies.get(defenderId);

        if (attacker == null || defender == null) {
            throw new IllegalArgumentException("Navy not found");
        }

        return battleCalculator.predictBattle(attacker, defender);
    }

    /**
     * 获取某位置附近的舰队
     */
    public List<NavyUnit> getNaviesNear(Location location, double radius) {
        return navies.values().stream()
            .filter(navy -> navy.location().getWorld().equals(location.getWorld()))
            .filter(navy -> navy.location().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    /**
     * 获取在港口的舰队
     */
    public List<NavyUnit> getNaviesAtPort(Location portLocation, double portRadius) {
        return navies.values().stream()
            .filter(navy -> navy.location().getWorld().equals(portLocation.getWorld()))
            .filter(navy -> navy.location().distance(portLocation) <= portRadius)
            .filter(navy -> navy.state() == NavyState.ANCHORED)
            .collect(Collectors.toList());
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每分钟执行一次 - 更新状态并持久化
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            navies.values().forEach(this::updateNavy);
        }, 20L * 60, 20L * 60); // 每分钟

        // 每5分钟保存一次所有舰队
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllNavies();
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟
    }

    /**
     * 更新舰队状态
     */
    private void updateNavy(NavyUnit navy) {
        // 消耗补给
        int consumption = navy.state().supplyConsumptionPerHour() / 60; // 每分钟
        navy.consumeSupply(consumption);

        // 补给不足影响士气
        if (navy.supply() < 20) {
            navy.changeMorale(-2);
        }

        // 士气过低可能叛逃
        if (navy.morale() < 10 && ThreadLocalRandom.current().nextDouble() < 0.1) {
            disbandFleet(navy.id());
        } else {
            // 持久化更新后的状态
            persistNavy(navy);
        }
    }

    // ==================== 持久化 ====================

    /**
     * 加载所有舰队
     */
    private void loadNavies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    NavyUnit navy = stateCodec.decode(json);
                    navies.put(navy.id(), navy);
                    nationNavies.computeIfAbsent(navy.nationId(), k -> ConcurrentHashMap.newKeySet()).add(navy.id());
                    plugin.getLogger().info("Loaded navy: " + navy.id() + " (" + navy.name() + ")");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load navy from key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + navies.size() + " navies from persistence");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load navies: " + e.getMessage());
        }
    }

    /**
     * 保存所有舰队
     */
    private void saveAllNavies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            for (Map.Entry<UUID, NavyUnit> entry : navies.entrySet()) {
                String key = entry.getKey().toString();
                String json = stateCodec.encode(entry.getValue());
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save navies: " + e.getMessage());
        }
    }

    /**
     * 持久化单个舰队
     */
    private void persistNavy(NavyUnit navy) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = navy.id().toString();
            String json = stateCodec.encode(navy);

            // 加载现有数据
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE);
            props.setProperty(key, json);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist navy " + navy.id() + ": " + e.getMessage());
        }
    }

    /**
     * 从持久化存储中移除舰队
     */
    private void removePersistedNavy(UUID navyId) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = navyId.toString();
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE);
            props.remove(key);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, NAVY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove navy " + navyId + " from persistence: " + e.getMessage());
        }
    }

    /**
     * 保存所有舰队（供外部调用，如插件关闭时）
     */
    public void shutdown() {
        saveAllNavies();
    }

    /**
     * 海军配置
     */
    public record NavyServiceConfig(
        int maxNaviesPerNation,
        int maxShipsPerNavy,
        boolean autoSupplyAtPort,
        double battleRangeBonus
    ) {
        public static NavyServiceConfig defaults() {
            return new NavyServiceConfig(5, 50, true, 1.0);
        }

        /**
         * 从配置节读取
         */
        public static NavyServiceConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new NavyServiceConfig(
                section.getInt("max-navies-per-nation", 5),
                section.getInt("max-ships-per-navy", 50),
                section.getBoolean("auto-supply-at-port", true),
                section.getDouble("battle-range-bonus", 1.0)
            );
        }
    }
}
