package dev.starcore.starcore.module.army.weather;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.weather.model.ArmyWeatherState;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsBoost;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsEffect;
import dev.starcore.starcore.module.army.weather.storage.WeatherTacticsStorage;
import dev.starcore.starcore.module.army.weather.storage.DatabaseWeatherTacticsStorage;
import dev.starcore.starcore.module.army.weather.event.ArmyWeatherTacticsEvent;
import dev.starcore.starcore.module.army.weather.event.WeatherTacticsUpgradedEvent;
import dev.starcore.starcore.module.army.weather.event.WeatherBattleAdvantageEvent;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.WeatherForecastService;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气战术服务实现类
 *
 * 功能特性：
 * - 天气对军队战斗力的影响计算
 * - 兵种对不同天气的适应性
 * - 天气战术科技升级系统
 * - 与 WeatherModule 集成获取实时天气
 */
public final class WeatherTacticsServiceImpl implements WeatherTacticsService {

    // ==================== 数据存储 ====================

    /** 国家天气战术加成映射 */
    private final Map<NationId, Map<WeatherType, WeatherTacticsBoost>> nationTacticsBoosts = new ConcurrentHashMap<>();

    /** 国家解锁的战术映射 */
    private final Map<NationId, Map<String, Integer>> nationUnlockedTactics = new ConcurrentHashMap<>();

    /** 军队当前天气状态 */
    private final Map<UUID, ArmyWeatherState> armyWeatherStates = new ConcurrentHashMap<>();

    /** 脏标记 */
    private volatile boolean dirty = false;

    // ==================== 服务依赖 ====================

    private WeatherControlService weatherControlService;
    private WeatherForecastService weatherForecastService;
    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;
    private WeatherTacticsStorage storage;
    private JavaPlugin plugin;

    // ==================== 配置常量 ====================

    /** 战术升级基础成本 */
    private static final double BASE_UPGRADE_COST = 5000.0;

    /** 战术升级成本倍数 */
    private static final double UPGRADE_COST_MULTIPLIER = 1.5;

    /** 最大战术等级 */
    private static final int MAX_TACTICS_LEVEL = 5;

    // ==================== 天气战术信息缓存 ====================

    private final Map<WeatherType, WeatherTacticsInfo> tacticsInfoCache = new EnumMap<>(WeatherType.class);

    // ==================== 兵种适应性矩阵 ====================

    /** 兵种对天气的适应性 (兵种 -> 天气 -> 适应性等级) */
    private final Map<String, Map<WeatherType, Double>> unitAdaptationMatrix = new ConcurrentHashMap<>();

    // ==================== 构造方法 ====================

    public WeatherTacticsServiceImpl(StarCoreContext context) {
        this.plugin = context.plugin();
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();

        // 获取天气服务
        this.weatherControlService = context.serviceRegistry().find(WeatherControlService.class).orElse(null);
        this.weatherForecastService = context.serviceRegistry().find(WeatherForecastService.class).orElse(null);

        // 初始化存储
        this.storage = new DatabaseWeatherTacticsStorage(
            "weather_tactics",
            context.databaseService(),
            context.persistenceService(),
            plugin
        );

        // 初始化战术信息
        initializeTacticsInfo();

        // 初始化兵种适应性矩阵
        initializeUnitAdaptationMatrix();
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化天气战术信息
     */
    private void initializeTacticsInfo() {
        // 晴天战术
        tacticsInfoCache.put(WeatherType.CLEAR, new WeatherTacticsInfo(
            WeatherType.CLEAR,
            "晴天：视野开阔，所有兵种正常作战。弓箭手获得额外远程优势。",
            1.0, 1.0, 1.0,
            new String[]{"archer", "cavalry"},
            new String[]{}
        ));

        // 雨天战术
        tacticsInfoCache.put(WeatherType.RAIN, new WeatherTacticsInfo(
            WeatherType.RAIN,
            "雨天：弓箭手远程能力下降，步兵和骑兵机动性降低，攻城器械使用受限。",
            0.8, 1.2, 0.7,
            new String[]{"infantry", "defensive"},
            new String[]{"archer", "siege"}
        ));

        // 雷暴战术
        tacticsInfoCache.put(WeatherType.THUNDER, new WeatherTacticsInfo(
            WeatherType.THUNDER,
            "雷暴：骑兵最危险，步兵和守军获得优势，弓箭手完全失效。",
            0.6, 1.5, 0.5,
            new String[]{"infantry", "defensive"},
            new String[]{"archer", "cavalry"}
        ));

        // 雪天战术
        tacticsInfoCache.put(WeatherType.SNOW, new WeatherTacticsInfo(
            WeatherType.SNOW,
            "雪天：骑兵完全无法移动，攻城器械失效，步兵和守军勉强维持战斗力。",
            0.5, 1.3, 0.3,
            new String[]{"infantry", "defensive"},
            new String[]{"cavalry", "siege", "archer"}
        ));

        // 暴风雨战术
        tacticsInfoCache.put(WeatherType.STORM, new WeatherTacticsInfo(
            WeatherType.STORM,
            "暴风雨：所有兵种战斗力大幅下降，守军获得地形优势。",
            0.4, 1.8, 0.2,
            new String[]{"defensive"},
            new String[]{"cavalry", "archer", "siege"}
        ));
    }

    /**
     * 初始化兵种适应性矩阵
     */
    private void initializeUnitAdaptationMatrix() {
        // 晴天 - 所有兵种正常
        Map<WeatherType, Double> clearAdaptation = new EnumMap<>(WeatherType.class);
        clearAdaptation.put(WeatherType.CLEAR, 1.0);
        clearAdaptation.put(WeatherType.RAIN, 0.8);
        clearAdaptation.put(WeatherType.THUNDER, 0.7);
        clearAdaptation.put(WeatherType.SNOW, 0.5);
        clearAdaptation.put(WeatherType.STORM, 0.4);

        // 步兵 - 适应大多数天气
        unitAdaptationMatrix.put("infantry", new EnumMap<>(clearAdaptation));

        // 骑兵 - 对恶劣天气适应性差
        Map<WeatherType, Double> cavalryAdaptation = new EnumMap<>(WeatherType.class);
        cavalryAdaptation.put(WeatherType.CLEAR, 1.0);
        cavalryAdaptation.put(WeatherType.RAIN, 0.7);
        cavalryAdaptation.put(WeatherType.THUNDER, 0.4);
        cavalryAdaptation.put(WeatherType.SNOW, 0.1);
        cavalryAdaptation.put(WeatherType.STORM, 0.0);
        unitAdaptationMatrix.put("cavalry", cavalryAdaptation);

        // 弓箭手 - 对雨天和雷暴最敏感
        Map<WeatherType, Double> archerAdaptation = new EnumMap<>(WeatherType.class);
        archerAdaptation.put(WeatherType.CLEAR, 1.3); // 远程优势
        archerAdaptation.put(WeatherType.RAIN, 0.5);
        archerAdaptation.put(WeatherType.THUNDER, 0.1);
        archerAdaptation.put(WeatherType.SNOW, 0.3);
        archerAdaptation.put(WeatherType.STORM, 0.0);
        unitAdaptationMatrix.put("archer", archerAdaptation);

        // 攻城器械 - 恶劣天气完全失效
        Map<WeatherType, Double> siegeAdaptation = new EnumMap<>(WeatherType.class);
        siegeAdaptation.put(WeatherType.CLEAR, 1.0);
        siegeAdaptation.put(WeatherType.RAIN, 0.4);
        siegeAdaptation.put(WeatherType.THUNDER, 0.2);
        siegeAdaptation.put(WeatherType.SNOW, 0.0);
        siegeAdaptation.put(WeatherType.STORM, 0.0);
        unitAdaptationMatrix.put("siege", siegeAdaptation);

        // 守军 - 恶劣天气反而有优势
        Map<WeatherType, Double> defensiveAdaptation = new EnumMap<>(WeatherType.class);
        defensiveAdaptation.put(WeatherType.CLEAR, 0.9);
        defensiveAdaptation.put(WeatherType.RAIN, 1.1);
        defensiveAdaptation.put(WeatherType.THUNDER, 1.2);
        defensiveAdaptation.put(WeatherType.SNOW, 1.1);
        defensiveAdaptation.put(WeatherType.STORM, 1.4);
        unitAdaptationMatrix.put("defensive", defensiveAdaptation);
    }

    // ==================== 生命周期 ====================

    /**
     * 启动服务
     */
    public void start() {
        loadState();
    }

    /**
     * 停止服务
     */
    public void stop() {
        saveState();
    }

    // ==================== WeatherTacticsService 接口实现 ====================

    @Override
    public WeatherTacticsEffect getTacticsEffect(UUID armyId) {
        ArmyWeatherState state = armyWeatherStates.get(armyId);
        if (state == null) {
            return WeatherTacticsEffect.defaultEffect();
        }
        return getTacticsEffect(armyId, state.currentWeather());
    }

    @Override
    public WeatherTacticsEffect getTacticsEffect(UUID armyId, WeatherType weather) {
        WeatherTacticsInfo info = tacticsInfoCache.get(weather);
        if (info == null) {
            return WeatherTacticsEffect.defaultEffect();
        }

        // 获取军队所属国家
        NationId nationId = findArmyNation(armyId);
        if (nationId == null) {
            return new WeatherTacticsEffect(
                weather,
                info.baseAttackModifier(),
                info.baseDefenseModifier(),
                info.movementModifier(),
                1.0,
                "未加入国家，无战术加成"
            );
        }

        // 获取国家战术加成
        WeatherTacticsBoost boost = getTacticsBoost(nationId, weather);
        int tacticsLevel = getTacticsLevel(nationId, "weather_mastery");

        // 计算最终修正
        double attackMod = info.baseAttackModifier() * boost.attackMultiplier() * (1.0 + tacticsLevel * 0.1);
        double defenseMod = info.baseDefenseModifier() * boost.defenseMultiplier() * (1.0 + tacticsLevel * 0.1);
        double movementMod = info.movementModifier() * boost.movementMultiplier();
        double moraleMod = boost.moraleBonus();

        // 兵种适应性
        String unitType = findArmyUnitType(armyId);
        if (unitType != null) {
            double adaptation = getUnitAdaptation(weather, unitType);
            attackMod *= adaptation;
            defenseMod *= adaptation;
        }

        return new WeatherTacticsEffect(
            weather,
            attackMod,
            defenseMod,
            movementMod,
            moraleMod,
            String.format("等级 %d 天气战术加成", tacticsLevel)
        );
    }

    @Override
    public double getUnitAdaptation(WeatherType weatherType, String attackType) {
        Map<WeatherType, Double> adaptation = unitAdaptationMatrix.get(attackType.toLowerCase());
        if (adaptation == null) {
            return 1.0;
        }
        return adaptation.getOrDefault(weatherType, 1.0);
    }

    @Override
    public WeatherBattleModifier calculateBattleModifier(ArmyUnit attacker, ArmyUnit defender, WeatherType weather) {
        WeatherTacticsEffect attackerEffect = getTacticsEffect(attacker.id(), weather);
        WeatherTacticsEffect defenderEffect = getTacticsEffect(defender.id(), weather);

        double attackerBonus = attackerEffect.attackModifier() / defenderEffect.defenseModifier();
        double defenderBonus = defenderEffect.defenseModifier() / attackerEffect.attackModifier();

        // 士气影响
        double attackerMorale = 1.0 + (attackerEffect.moraleModifier() - 1.0) * 0.5;
        double defenderMorale = 1.0 + (defenderEffect.moraleModifier() - 1.0) * 0.5;

        // 移动性影响（追击/撤退）
        double movementAdvantage = attackerEffect.movementModifier() / defenderEffect.movementModifier();

        String description = String.format("%s vs %s", weather.getDisplayName(), weather.getDisplayName());

        return new WeatherBattleModifier(
            attackerBonus * attackerMorale * movementAdvantage,
            defenderBonus * defenderMorale,
            (attackerMorale + defenderMorale) / 2.0,
            description
        );
    }

    @Override
    public boolean hasUnlockedTactics(NationId nationId, String tacticsType) {
        Map<String, Integer> tactics = nationUnlockedTactics.get(nationId);
        if (tactics == null) {
            return false;
        }
        return tactics.getOrDefault(tacticsType, 0) > 0;
    }

    @Override
    public boolean unlockTactics(NationId nationId, String tacticsType, int level) {
        if (level <= 0 || level > MAX_TACTICS_LEVEL) {
            return false;
        }

        Map<String, Integer> tactics = nationUnlockedTactics.computeIfAbsent(
            nationId, k -> new ConcurrentHashMap<>());

        int currentLevel = tactics.getOrDefault(tacticsType, 0);
        if (level <= currentLevel) {
            return false;
        }

        tactics.put(tacticsType, level);
        dirty = true;

        // 触发事件
        if (eventBus != null) {
            eventBus.publish(new WeatherTacticsUpgradedEvent(nationId, tacticsType, level));
        }

        return true;
    }

    @Override
    public Map<WeatherType, WeatherTacticsBoost> getNationTacticsBoosts(NationId nationId) {
        return nationTacticsBoosts.getOrDefault(nationId, Collections.emptyMap());
    }

    @Override
    public boolean setTacticsBoost(NationId nationId, WeatherType weather, WeatherTacticsBoost boost) {
        Map<WeatherType, WeatherTacticsBoost> boosts = nationTacticsBoosts.computeIfAbsent(
            nationId, k -> new ConcurrentHashMap<>());
        boosts.put(weather, boost);
        dirty = true;
        return true;
    }

    @Override
    public int getTacticsLevel(NationId nationId, String tacticsType) {
        Map<String, Integer> tactics = nationUnlockedTactics.get(nationId);
        if (tactics == null) {
            return 0;
        }
        return tactics.getOrDefault(tacticsType, 0);
    }

    @Override
    public int upgradeTactics(NationId nationId, String tacticsType) {
        Map<String, Integer> tactics = nationUnlockedTactics.computeIfAbsent(
            nationId, k -> new ConcurrentHashMap<>());

        int currentLevel = tactics.getOrDefault(tacticsType, 0);
        if (currentLevel >= MAX_TACTICS_LEVEL) {
            return -1;
        }

        int newLevel = currentLevel + 1;
        tactics.put(tacticsType, newLevel);
        dirty = true;

        // 触发事件
        if (eventBus != null) {
            eventBus.publish(new WeatherTacticsUpgradedEvent(nationId, tacticsType, newLevel));
        }

        return newLevel;
    }

    @Override
    public WeatherTacticsInfo getTacticsInfo(WeatherType weather) {
        return tacticsInfoCache.get(weather);
    }

    @Override
    public String[] getAvailableTacticsTypes() {
        return new String[]{
            "weather_mastery",      // 天气掌控
            "rain_warfare",         // 雨天战术
            "thunder_tactics",      // 雷暴战术
            "snow_operations",      // 雪地作战
            "storm_assault"         // 风暴突击
        };
    }

    @Override
    public double getUpgradeCost(String tacticsType, int currentLevel) {
        if (currentLevel >= MAX_TACTICS_LEVEL) {
            return -1;
        }
        return BASE_UPGRADE_COST * Math.pow(UPGRADE_COST_MULTIPLIER, currentLevel);
    }

    @Override
    public void triggerTacticsEvent(NationId nationId, WeatherType weather, WeatherTacticsEffect effect) {
        if (eventBus != null) {
            eventBus.publish(new ArmyWeatherTacticsEvent(nationId, weather, effect));
        }
    }

    @Override
    public String summary() {
        int nationsWithTactics = nationUnlockedTactics.size();
        int armiesWithStates = armyWeatherStates.size();
        return String.format("Weather Tactics: %d nations with tactics, %d armies with weather states",
            nationsWithTactics, armiesWithStates);
    }

    @Override
    public Map<String, Integer> getUnlockedTactics(NationId nationId) {
        return nationUnlockedTactics.getOrDefault(nationId, Collections.emptyMap());
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取国家战术加成
     */
    private WeatherTacticsBoost getTacticsBoost(NationId nationId, WeatherType weather) {
        Map<WeatherType, WeatherTacticsBoost> boosts = nationTacticsBoosts.get(nationId);
        if (boosts != null) {
            WeatherTacticsBoost boost = boosts.get(weather);
            if (boost != null) {
                return boost;
            }
        }
        return WeatherTacticsBoost.defaultBoost();
    }

    /**
     * 查找军队所属国家
     */
    private NationId findArmyNation(UUID armyId) {
        ArmyWeatherState state = armyWeatherStates.get(armyId);
        return state != null ? state.nationId() : null;
    }

    /**
     * 查找军队兵种类型
     */
    private String findArmyUnitType(UUID armyId) {
        ArmyWeatherState state = armyWeatherStates.get(armyId);
        return state != null ? state.unitType() : "infantry";
    }

    /**
     * 更新军队天气状态
     */
    public void updateArmyWeatherState(UUID armyId, NationId nationId, String unitType, WeatherType weather) {
        ArmyWeatherState state = armyWeatherStates.computeIfAbsent(
            armyId, k -> new ArmyWeatherState(armyId, nationId, unitType, weather));
        state.updateWeather(weather);
    }

    /**
     * 移除军队天气状态
     */
    public void removeArmyWeatherState(UUID armyId) {
        armyWeatherStates.remove(armyId);
    }

    // ==================== 持久化 ====================

    private void loadState() {
        if (storage != null) {
            try {
                storage.load();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load weather tactics state: " + e.getMessage());
            }
        }
    }

    private void saveState() {
        if (storage != null && dirty) {
            try {
                storage.save();
                dirty = false;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save weather tactics state: " + e.getMessage());
            }
        }
    }

    // ==================== 公开访问器 ====================

    /**
     * 获取所有军队天气状态
     */
    public Map<UUID, ArmyWeatherState> getAllArmyWeatherStates() {
        return Map.copyOf(armyWeatherStates);
    }

    /**
     * 获取天气战术信息缓存
     */
    public Map<WeatherType, WeatherTacticsInfo> getTacticsInfoCache() {
        return Map.copyOf(tacticsInfoCache);
    }

    /**
     * 通知天气变化事件
     */
    public void onWeatherChanged(NationId nationId, WeatherType newWeather, WeatherType oldWeather) {
        if (eventBus != null) {
            WeatherTacticsEffect effect = getTacticsEffect(UUID.randomUUID(), newWeather);
            eventBus.publish(new ArmyWeatherTacticsEvent(nationId, newWeather, effect));
        }
    }
}