package dev.starcore.starcore.module.army;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.battle.BattleCalculator;
import dev.starcore.starcore.module.army.model.*;
import dev.starcore.starcore.module.army.storage.ArmyStateCodec;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 军队核心服务
 * 负责军队的创建、管理、移动、战斗等核心逻辑
 */
public final class ArmyService {
    private static final String PERSISTENCE_NAMESPACE = "army";
    private static final String ARMY_STATE_FILE = "armies.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final BattleCalculator battleCalculator;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final ArmyStateCodec stateCodec;
    private final ArmyConfig config;

    // 所有军队（内存中）
    private final ConcurrentHashMap<UUID, ArmyUnit> armies = new ConcurrentHashMap<>();
    // 国家的军队索引
    private final ConcurrentHashMap<UUID, Set<UUID>> nationArmies = new ConcurrentHashMap<>();

    public ArmyService(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        BattleCalculator battleCalculator,
        MessageService messages,
        ArmyConfig config
    ) {
        this(plugin, nationService, treasuryService, battleCalculator, messages, config, null);
    }

    public ArmyService(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        BattleCalculator battleCalculator,
        MessageService messages,
        ArmyConfig config,
        PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.battleCalculator = battleCalculator;
        this.messages = messages;
        this.config = config;
        this.stateCodec = new ArmyStateCodec();
        this.persistenceService = persistenceService;

        // 如果有持久化服务，加载已有数据
        if (persistenceService != null) {
            loadArmies();
        }

        startPeriodicTasks();
    }

    /**
     * 获取配置
     */
    public ArmyConfig getConfig() {
        return config;
    }

    /**
     * 创建军队
     * 消耗: 国库资金
     */
    public ArmyUnit createArmy(UUID nationId, ArmyType type, int soldiers, Location location) {
        // 检查限制
        Set<UUID> nationArmyIds = nationArmies.getOrDefault(nationId, Collections.emptySet());
        if (nationArmyIds.size() >= config.maxArmiesPerNation()) {
            throw new IllegalStateException("army.error.max-limit");
        }

        if (soldiers > config.maxSoldiersPerArmy()) {
            throw new IllegalArgumentException("army.error.too-many-soldiers");
        }

        if (soldiers < 10) {
            throw new IllegalArgumentException("army.error.min-soldiers");
        }

        // 计算成本并从国库扣除
        BigDecimal cost = BigDecimal.valueOf(type.totalCost(soldiers));
        NationId nationIdObj = new NationId(nationId);

        if (treasuryService.balance(nationIdObj).compareTo(cost) < 0) {
            throw new IllegalStateException("army.error.insufficient-treasury");
        }

        if (!treasuryService.withdraw(nationIdObj, cost)) {
            throw new IllegalStateException("army.error.withdraw-failed");
        }

        // 创建军队
        ArmyUnit army = ArmyUnit.create(nationId, type, soldiers, location);

        // 注册
        armies.put(army.id(), army);
        nationArmies.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(army.id());

        // 持久化
        persistArmy(army);

        return army;
    }

    /**
     * 解散军队
     */
    public void disbandArmy(UUID armyId) {
        ArmyUnit army = armies.remove(armyId);
        if (army != null) {
            Set<UUID> nationArmyIds = nationArmies.get(army.nationId());
            if (nationArmyIds != null) {
                nationArmyIds.remove(armyId);
            }
            // 从持久化存储中移除
            removePersistedArmy(armyId);
        }
    }

    /**
     * 获取军队
     */
    public Optional<ArmyUnit> getArmy(UUID armyId) {
        return Optional.ofNullable(armies.get(armyId));
    }

    /**
     * 获取国家的所有军队
     */
    public List<ArmyUnit> getNationArmies(UUID nationId) {
        Set<UUID> armyIds = nationArmies.getOrDefault(nationId, Collections.emptySet());
        return armyIds.stream()
            .map(armies::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 移动军队
     */
    public void moveArmy(UUID armyId, Location destination) {
        ArmyUnit army = armies.get(armyId);
        if (army == null) {
            throw new IllegalArgumentException("Army not found");
        }

        if (!army.canMove()) {
            throw new IllegalStateException("Army cannot move");
        }

        // 检查距离
        double distance = army.location().distance(destination);
        double maxDistance = army.type().mobility() * 10; // 每点机动性可移动10格

        if (distance > maxDistance) {
            throw new IllegalArgumentException("Destination too far");
        }

        // 移动
        army.moveTo(destination);
    }

    /**
     * 发起战斗
     */
    public BattleResult attack(UUID attackerId, UUID defenderId) {
        ArmyUnit attacker = armies.get(attackerId);
        ArmyUnit defender = armies.get(defenderId);

        if (attacker == null || defender == null) {
            throw new IllegalArgumentException("Army not found");
        }

        if (!attacker.canFight()) {
            throw new IllegalStateException("Attacker cannot fight");
        }

        // 检查距离（必须相邻）
        double distance = attacker.location().distance(defender.location());
        if (distance > 100) { // 100格内可以战斗
            throw new IllegalArgumentException("Armies too far apart");
        }

        // 执行战斗
        BattleResult result = battleCalculator.calculateBattle(attacker, defender);

        // 清理被消灭的军队
        if (!attacker.isAlive()) {
            disbandArmy(attackerId);
        }
        if (!defender.isAlive()) {
            disbandArmy(defenderId);
        }

        return result;
    }

    /**
     * 补给军队
     * 消耗: 国库资金
     */
    public void resupplyArmy(UUID armyId) {
        ArmyUnit army = armies.get(armyId);
        if (army == null) {
            throw new IllegalArgumentException("army.error.not-found");
        }

        // 计算补给成本
        int supplyNeeded = 100 - army.supply();
        if (supplyNeeded <= 0) {
            return; // 已经满补给
        }

        BigDecimal cost = BigDecimal.valueOf(supplyNeeded).multiply(BigDecimal.valueOf(10));
        NationId nationIdObj = new NationId(army.nationId());

        if (!treasuryService.withdraw(nationIdObj, cost)) {
            throw new IllegalStateException("army.error.insufficient-treasury-for-supply");
        }

        // 补给
        army.resupply(supplyNeeded);
    }

    /**
     * 预测战斗结果
     */
    public BattleCalculator.BattlePrediction predictBattle(UUID attackerId, UUID defenderId) {
        ArmyUnit attacker = armies.get(attackerId);
        ArmyUnit defender = armies.get(defenderId);

        if (attacker == null || defender == null) {
            throw new IllegalArgumentException("Army not found");
        }

        return battleCalculator.predictBattle(attacker, defender);
    }

    /**
     * 获取某位置附近的军队
     */
    public List<ArmyUnit> getArmiesNear(Location location, double radius) {
        return armies.values().stream()
            .filter(army -> army.location().getWorld().equals(location.getWorld()))
            .filter(army -> army.location().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每分钟执行一次 - 更新状态并持久化
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            armies.values().forEach(this::updateArmy);
        }, 20L * 60, 20L * 60); // 每分钟

        // 每5分钟保存一次所有军队
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllArmies();
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟
    }

    /**
     * 更新军队状态
     */
    private void updateArmy(ArmyUnit army) {
        // 审计 A-115: 使用 double 计算后再取整，避免 supplyConsumptionPerHour < 60 时为 0
        double consumption = army.state().supplyConsumptionPerHour() / 60.0;
        army.consumeSupply((int) Math.ceil(consumption));

        // 补给不足影响士气
        if (army.supply() < 20) {
            army.changeMorale(-2);
        }

        // 士气过低可能溃散
        if (army.morale() < 10 && ThreadLocalRandom.current().nextDouble() < 0.1) {
            // 审计 A-116: 溃散时广播通知给国家成员
            broadcastArmyDisbanded(army);
            plugin.getLogger().warning("军队 " + army.id() + " 溃散，士气: " + army.morale());
            disbandArmy(army.id());
        } else {
            // 持久化更新后的状态
            persistArmy(army);
        }
    }

    /**
     * 广播军队溃散消息给国家成员
     */
    private void broadcastArmyDisbanded(ArmyUnit army) {
        nationService.nationById(NationId.of(army.nationId())).ifPresent(nation -> {
            String armyTypeName = switch (army.type().key()) {
                case "infantry" -> "步兵";
                case "cavalry" -> "骑兵";
                case "archer" -> "弓箭手";
                case "siege" -> "攻城器械";
                case "defensive" -> "守军";
                default -> army.type().key();
            };
            String message = messages.format("army.disbanded.notification",
                armyTypeName, String.valueOf(army.soldiers()));
            Component component = Component.text(message, NamedTextColor.RED);
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(component);
                }
            });
        });
    }

    // ==================== 持久化 ====================

    /**
     * 加载所有军队
     */
    private void loadArmies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    ArmyUnit army = stateCodec.decode(json);
                    armies.put(army.id(), army);
                    nationArmies.computeIfAbsent(army.nationId(), k -> ConcurrentHashMap.newKeySet()).add(army.id());
                    plugin.getLogger().info("Loaded army: " + army.id() + " (" + army.type().key() + ")");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load army from key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + armies.size() + " armies from persistence");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load armies: " + e.getMessage());
        }
    }

    /**
     * 保存所有军队
     */
    private void saveAllArmies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            for (Map.Entry<UUID, ArmyUnit> entry : armies.entrySet()) {
                String key = entry.getKey().toString();
                String json = stateCodec.encode(entry.getValue());
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save armies: " + e.getMessage());
        }
    }

    /**
     * 持久化单个军队
     */
    private void persistArmy(ArmyUnit army) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = army.id().toString();
            String json = stateCodec.encode(army);

            // 加载现有数据
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE);
            props.setProperty(key, json);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist army " + army.id() + ": " + e.getMessage());
        }
    }

    /**
     * 从持久化存储中移除军队
     */
    private void removePersistedArmy(UUID armyId) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = armyId.toString();
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE);
            props.remove(key);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, ARMY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove army " + armyId + " from persistence: " + e.getMessage());
        }
    }

    /**
     * 保存所有军队（供外部调用，如插件关闭时）
     */
    public void shutdown() {
        saveAllArmies();
    }

    /**
     * 军队配置
     */
    public record ArmyConfig(
        int maxArmiesPerNation,
        int maxSoldiersPerArmy,
        boolean autoSupplyInTerritory
    ) {
        public static ArmyConfig defaults() {
            return new ArmyConfig(10, 1000, true);
        }

        /**
         * 从配置节读取
         */
        public static ArmyConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new ArmyConfig(
                section.getInt("max-armies-per-nation", 10),
                section.getInt("max-soldiers-per-army", 1000),
                section.getBoolean("auto-supply-in-territory", true)
            );
        }
    }
}
