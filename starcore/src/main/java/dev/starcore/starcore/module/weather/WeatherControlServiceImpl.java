package dev.starcore.starcore.module.weather;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.NationWeatherSettings;
import dev.starcore.starcore.module.weather.model.WeatherEffect;
import dev.starcore.starcore.module.weather.model.WeatherForecastEntry;
import dev.starcore.starcore.module.weather.model.WeatherResourceModifier;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.storage.WeatherStateStorage;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.resource.ResourceService;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气控制服务实现类
 *
 * 提供国家级别的天气控制能力，包括：
 * - 天气设置与清除
 * - 天气效果计算
 * - 科技需求验证
 * - 定时天气刷新
 */
public final class WeatherControlServiceImpl implements WeatherControlService {

    // ==================== 数据存储 ====================

    /** 国家天气设置映射 */
    private final Map<NationId, NationWeatherSettings> nationWeatherSettings = new ConcurrentHashMap<>();

    /** 世界天气状态映射 */
    private final Map<String, WorldWeatherState> worldWeatherStates = new ConcurrentHashMap<>();

    /** 天气效果预计算缓存 */
    private final Map<WeatherType, WeatherEffect> weatherEffects = new EnumMap<>(WeatherType.class);

    /** 资源修改器映射 */
    private final Map<WeatherType, WeatherResourceModifier> resourceModifiers = new EnumMap<>(WeatherType.class);

    /** 天气预报缓存 */
    private final Map<NationId, List<WeatherForecastEntry>> weatherForecasts = new ConcurrentHashMap<>();

    // ==================== 服务依赖 ====================

    private NationService nationService;
    private PolicyService policyService;
    private ResourceService resourceService;
    private OnlinePlayerDirectory onlinePlayerDirectory;
    private EconomyService economyService;
    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;
    private ServiceRegistry serviceRegistry;
    private WeatherStateStorage stateStorage;
    private JavaPlugin plugin;

    // ==================== 配置常量 ====================

    /** 天气切换冷却时间（tick） */
    private static final long WEATHER_CHANGE_COOLDOWN_TICKS = 20L * 60; // 60秒

    /** 天气预报刷新间隔（tick） */
    private static final long FORECAST_REFRESH_TICKS = 20L * 60 * 30; // 30分钟

    /** 最大预报天数 */
    private static final int MAX_FORECAST_DAYS = 7;

    // ==================== 构造方法 ====================

    /**
     * 创建天气控制服务实现
     *
     * @param context StarCore上下文
     */
    public WeatherControlServiceImpl(StarCoreContext context) {
        this.plugin = context.plugin();
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.serviceRegistry = context.serviceRegistry();

        // 获取依赖服务
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        this.policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);
        this.resourceService = context.serviceRegistry().find(ResourceService.class).orElse(null);
        this.onlinePlayerDirectory = context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);
        this.economyService = context.serviceRegistry().find(EconomyService.class).orElse(null);

        // 初始化存储
        this.stateStorage = createStateStorage(context);

        // 初始化天气效果
        initializeWeatherEffects();

        // 初始化资源修改器
        initializeResourceModifiers();
    }

    /**
     * 创建状态存储实例
     */
    private WeatherStateStorage createStateStorage(StarCoreContext context) {
        return new dev.starcore.starcore.module.weather.storage.DatabaseAwareWeatherStateStorage(
            "weather",
            context.databaseService(),
            context.persistenceService(),
            plugin.getLogger()
        );
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化天气效果
     */
    private void initializeWeatherEffects() {
        // 晴朗效果
        weatherEffects.put(WeatherType.CLEAR, new WeatherEffect(
            WeatherType.CLEAR,
            1.0,   // 速度修正: 正常
            1.0,   // 伤害修正: 正常
            1.2    // 农业加成: 晴天有利于农业
        ));

        // 雨天效果
        weatherEffects.put(WeatherType.RAIN, new WeatherEffect(
            WeatherType.RAIN,
            0.9,   // 速度修正: 略微减慢
            1.0,   // 伤害修正: 正常
            1.5    // 农业加成: 雨水滋润
        ));

        // 雷暴效果
        weatherEffects.put(WeatherType.THUNDER, new WeatherEffect(
            WeatherType.THUNDER,
            0.7,   // 速度修正: 明显减慢
            1.2,   // 伤害修正: 增加（雷电伤害）
            1.3    // 农业加成: 雨水和雷电
        ));

        // 降雪效果
        weatherEffects.put(WeatherType.SNOW, new WeatherEffect(
            WeatherType.SNOW,
            0.8,   // 速度修正: 雪地行走困难
            1.1,   // 伤害修正: 寒冷伤害
            0.2    // 农业加成: 严重影响农业
        ));

        // 暴风雨效果
        weatherEffects.put(WeatherType.STORM, new WeatherEffect(
            WeatherType.STORM,
            0.6,   // 速度修正: 严重阻碍
            1.3,   // 伤害修正: 高伤害风险
            0.3    // 农业加成: 严重损害
        ));
    }

    /**
     * 初始化资源修改器
     */
    private void initializeResourceModifiers() {
        // 晴天
        resourceModifiers.put(WeatherType.CLEAR, new WeatherResourceModifier(
            WeatherType.CLEAR,
            new HashMap<>(Map.of(
                "mineral", 1.0,
                "agricultural", 1.2,
                "energy", 1.0,
                "luxury", 1.0
            ))
        ));

        // 雨天
        resourceModifiers.put(WeatherType.RAIN, new WeatherResourceModifier(
            WeatherType.RAIN,
            new HashMap<>(Map.of(
                "mineral", 0.8,
                "agricultural", 1.5,
                "energy", 0.7,
                "luxury", 0.9
            ))
        ));

        // 雷暴
        resourceModifiers.put(WeatherType.THUNDER, new WeatherResourceModifier(
            WeatherType.THUNDER,
            new HashMap<>(Map.of(
                "mineral", 0.6,
                "agricultural", 1.3,
                "energy", 0.3,
                "luxury", 0.7,
                "strategic", 0.8
            ))
        ));

        // 降雪
        resourceModifiers.put(WeatherType.SNOW, new WeatherResourceModifier(
            WeatherType.SNOW,
            new HashMap<>(Map.of(
                "mineral", 0.5,
                "agricultural", 0.2,
                "energy", 0.4,
                "luxury", 0.5
            ))
        ));

        // 暴风雨
        resourceModifiers.put(WeatherType.STORM, new WeatherResourceModifier(
            WeatherType.STORM,
            new HashMap<>(Map.of(
                "mineral", 0.4,
                "agricultural", 0.3,
                "energy", 0.2,
                "luxury", 0.4,
                "strategic", 0.5
            ))
        ));
    }

    // ==================== 生命周期方法 ====================

    /**
     * 启动服务
     */
    public void start() {
        // 加载持久化数据
        loadState();

        // 初始化世界天气状态
        initializeWorldWeatherStates();

        // 启动定时任务
        startScheduledTasks();
    }

    /**
     * 停止服务
     */
    public void stop() {
        saveState();
    }

    // ==================== WeatherControlService 接口实现 ====================

    /**
     * 设置国家天气
     *
     * @param nationId 国家ID
     * @param weather  天气类型
     * @param duration 持续时间（tick），0表示永久
     * @return 是否成功设置
     */
    @Override
    public boolean setWeather(NationId nationId, String worldName, WeatherType weather) {
        // 检查权限
        if (!hasWeatherControlPermission(nationId)) {
            return false;
        }

        // 检查冷却
        if (isInCooldown(nationId)) {
            return false;
        }

        // 获取或创建设置
        NationWeatherSettings settings = nationWeatherSettings.computeIfAbsent(
            nationId,
            id -> new NationWeatherSettings(id, WeatherType.CLEAR, true, NationWeatherPermission.CONTROL_BASIC)
        );

        // 检查天气类型权限
        if (!canUseWeatherType(settings.getPermission(), weather)) {
            return false;
        }

        // 更新设置
        settings.setCurrentWeather(weather);
        settings.setLastWeatherChangeTime(System.currentTimeMillis());
        settings.setLastControlledWorld(worldName);

        // 更新世界状态
        WorldWeatherState worldState = worldWeatherStates.computeIfAbsent(
            worldName,
            name -> new WorldWeatherState(name, weather, System.currentTimeMillis(), 0, nationId)
        );
        worldState.setCurrentWeather(weather);
        worldState.setControlledByNation(nationId);

        // 应用到游戏世界
        World world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            applyWeatherToWorld(world, weather);
        }

        // 保存状态
        saveState();

        return true;
    }

    @Override
    public boolean setNationWeather(NationId nationId, WeatherType weather, long duration) {
        // 获取国家控制的第一个世界
        String worldName = findNationControlledWorld(nationId);
        if (worldName == null) {
            return false;
        }

        // 获取世界状态
        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state != null) {
            state.setWeatherDurationMinutes((int) (duration / 1200)); // 转换为分钟
        }

        return setWeather(nationId, worldName, weather);
    }

    @Override
    public WeatherType getNationWeather(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null ? settings.getCurrentWeather() : WeatherType.CLEAR;
    }

    @Override
    public boolean clearWeather(NationId nationId) {
        String worldName = findNationControlledWorld(nationId);
        if (worldName == null) {
            return false;
        }

        // 清除为晴天
        return setWeather(nationId, worldName, WeatherType.CLEAR);
    }

    @Override
    public WeatherEffect getWeatherEffects(NationId nationId) {
        WeatherType weather = getNationWeather(nationId);
        return weatherEffects.get(weather);
    }

    @Override
    public boolean hasWeatherControl(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null && settings.getPermission() != NationWeatherPermission.NONE;
    }

    // ==================== WeatherControlService 世界天气接口 ====================

    @Override
    public boolean registerWorld(String worldName) {
        if (worldWeatherStates.containsKey(worldName)) {
            return false;
        }
        WorldWeatherState state = new WorldWeatherState(
            worldName,
            WeatherType.CLEAR,
            System.currentTimeMillis(),
            0,
            null
        );
        worldWeatherStates.put(worldName, state);
        return true;
    }

    @Override
    public boolean unregisterWorld(String worldName) {
        return worldWeatherStates.remove(worldName) != null;
    }

    @Override
    public WorldWeatherState getWorldWeatherState(String worldName) {
        return worldWeatherStates.get(worldName);
    }

    @Override
    public Map<String, WorldWeatherState> getAllWorldWeatherStates() {
        return Map.copyOf(worldWeatherStates);
    }

    @Override
    public boolean controlWorld(NationId nationId, String worldName) {
        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state == null) {
            return false;
        }

        state.setControlledByNation(nationId);

        // 更新国家设置
        NationWeatherSettings settings = nationWeatherSettings.computeIfAbsent(
            nationId,
            id -> new NationWeatherSettings(id, WeatherType.CLEAR, true, NationWeatherPermission.CONTROL_BASIC)
        );

        saveState();
        return true;
    }

    @Override
    public boolean releaseWorld(String worldName) {
        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state == null) {
            return false;
        }

        state.setControlledByNation(null);
        state.setCurrentWeather(WeatherType.CLEAR);
        saveState();
        return true;
    }

    // ==================== 权限管理 ====================

    @Override
    public boolean hasWeatherControlPermission(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null &&
               settings.getPermission().ordinal() >= NationWeatherPermission.CONTROL_BASIC.ordinal();
    }

    @Override
    public boolean setPermission(NationId nationId, NationWeatherPermission permission) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        if (settings == null) {
            settings = new NationWeatherSettings(nationId, WeatherType.CLEAR, true, permission);
            nationWeatherSettings.put(nationId, settings);
        } else {
            settings.setPermission(permission);
        }
        saveState();
        return true;
    }

    @Override
    public NationWeatherPermission getPermission(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null ? settings.getPermission() : NationWeatherPermission.NONE;
    }

    // ==================== 自动天气 ====================

    @Override
    public boolean setAutoWeather(NationId nationId, boolean auto) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        if (settings == null) {
            return false;
        }
        settings.setAutoWeather(auto);
        saveState();
        return true;
    }

    @Override
    public boolean isAutoWeather(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null && settings.isAutoWeather();
    }

    // ==================== 资源修改器 ====================

    @Override
    public WeatherResourceModifier getResourceModifier(WeatherType weather) {
        return resourceModifiers.get(weather);
    }

    @Override
    public Map<String, Double> getResourceModifiers(WeatherType weather) {
        WeatherResourceModifier modifier = resourceModifiers.get(weather);
        return modifier != null ? modifier.getModifiers() : Map.of();
    }

    // ==================== 天气预报 ====================

    /**
     * 获取天气预报
     *
     * @param nationId 国家ID
     * @return 天气预报列表
     */
    public List<WeatherForecastEntry> getForecast(NationId nationId) {
        return weatherForecasts.computeIfAbsent(nationId, this::generateForecast);
    }

    /**
     * 获取今日预报
     *
     * @param nationId 国家ID
     * @return 今日预报
     */
    public WeatherForecastEntry getTodayForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = getForecast(nationId);
        return forecast.isEmpty() ? null : forecast.get(0);
    }

    /**
     * 刷新预报
     *
     * @param nationId 国家ID
     */
    public void refreshForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = generateForecast(nationId);
        weatherForecasts.put(nationId, forecast);
    }

    /**
     * 获取天气概率
     *
     * @param nationId 国家ID
     * @param dayIndex 天数索引
     * @return 天气类型到概率的映射
     */
    public Map<WeatherType, Double> getWeatherProbabilities(NationId nationId, int dayIndex) {
        List<WeatherForecastEntry> forecast = getForecast(nationId);
        if (dayIndex < 0 || dayIndex >= forecast.size()) {
            return Map.of();
        }
        return forecast.get(dayIndex).getProbabilities();
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否在冷却中
     */
    private boolean isInCooldown(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        if (settings == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - settings.getLastWeatherChangeTime();
        return elapsed < WEATHER_CHANGE_COOLDOWN_TICKS * 50; // 转换为毫秒
    }

    /**
     * 检查是否可以使用指定天气类型
     */
    private boolean canUseWeatherType(NationWeatherPermission permission, WeatherType weather) {
        return switch (permission) {
            case NONE -> false;
            case CONTROL_BASIC -> weather == WeatherType.CLEAR || weather == WeatherType.RAIN;
            case CONTROL_ADVANCED -> weather != WeatherType.STORM;
            case CONTROL_FULL -> true;
        };
    }

    /**
     * 查找国家控制的世界
     */
    private String findNationControlledWorld(NationId nationId) {
        for (Map.Entry<String, WorldWeatherState> entry : worldWeatherStates.entrySet()) {
            if (nationId.equals(entry.getValue().getControlledByNation())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 应用天气到游戏世界
     */
    private void applyWeatherToWorld(World world, WeatherType weather) {
        switch (weather) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case THUNDER, STORM -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            case SNOW -> {
                world.setStorm(true);
                world.setThundering(false);
            }
        }

        // 设置天气持续时间
        world.setWeatherDuration(6000); // 默认10分钟
    }

    /**
     * 初始化世界天气状态
     */
    private void initializeWorldWeatherStates() {
        for (World world : plugin.getServer().getWorlds()) {
            String worldName = world.getName();
            if (!worldWeatherStates.containsKey(worldName)) {
                WorldWeatherState state = new WorldWeatherState(
                    worldName,
                    WeatherType.CLEAR,
                    System.currentTimeMillis(),
                    0,
                    null
                );
                worldWeatherStates.put(worldName, state);
            }
        }
    }

    // ==================== 定时任务 ====================

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        if (scheduler == null) {
            return;
        }

        // 刷新天气预报
        scheduler.runSyncTimer(() -> refreshAllForecasts(), FORECAST_REFRESH_TICKS, FORECAST_REFRESH_TICKS);

        // 同步天气到世界
        scheduler.runSyncTimer(() -> syncWeatherToWorlds(), 20L * 60, 20L * 60);

        // 应用资源修改器
        scheduler.runSyncTimer(() -> applyResourceModifiers(), 20L * 60 * 5, 20L * 60 * 5);
    }

    /**
     * 刷新所有预报
     */
    private void refreshAllForecasts() {
        if (nationService == null) {
            return;
        }

        nationService.nations().forEach(nation -> {
            NationId nationId = nation.id();
            List<WeatherForecastEntry> forecast = generateForecast(nationId);
            weatherForecasts.put(nationId, forecast);
        });
    }

    /**
     * 同步天气到世界
     */
    private void syncWeatherToWorlds() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, WorldWeatherState> entry : worldWeatherStates.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world == null) {
                continue;
            }

            WorldWeatherState state = entry.getValue();

            if (state.getControlledByNation() != null) {
                NationWeatherSettings settings = nationWeatherSettings.get(state.getControlledByNation());

                if (settings != null && settings.isAutoWeather()) {
                    applyWeatherToWorld(world, settings.getCurrentWeather());
                } else {
                    applyNaturalWeatherChange(world, state, currentTime);
                }
            } else {
                applyNaturalWeatherChange(world, state, currentTime);
            }
        }
    }

    /**
     * 应用自然天气变化
     */
    private void applyNaturalWeatherChange(World world, WorldWeatherState state, long currentTime) {
        long timeSinceChange = currentTime - state.getLastWeatherChange();
        long naturalChangeTime = 20L * 60 * (10 + new Random().nextInt(20)); // 10-30分钟

        if (timeSinceChange > naturalChangeTime) {
            WeatherType[] naturalWeathers = {
                WeatherType.CLEAR, WeatherType.CLEAR,
                WeatherType.RAIN, WeatherType.RAIN,
                WeatherType.THUNDER
            };
            WeatherType newWeather = naturalWeathers[new Random().nextInt(naturalWeathers.length)];

            applyWeatherToWorld(world, newWeather);
            state.setCurrentWeather(newWeather);
        }
    }

    /**
     * 应用资源修改器
     */
    private void applyResourceModifiers() {
        if (resourceService == null || eventBus == null) {
            return;
        }

        for (Map.Entry<String, WorldWeatherState> entry : worldWeatherStates.entrySet()) {
            String worldName = entry.getKey();
            WorldWeatherState state = entry.getValue();
            NationId controlledNation = state.getControlledByNation();

            if (controlledNation == null) {
                continue;
            }

            WeatherResourceModifier modifier = resourceModifiers.get(state.getCurrentWeather());
            if (modifier == null) {
                continue;
            }

            // 发布资源效果事件
            eventBus.publish(new dev.starcore.starcore.module.weather.event.WeatherResourceEffectEvent(
                controlledNation,
                worldName,
                state.getCurrentWeather(),
                modifier.getModifiers()
            ));
        }
    }

    // ==================== 持久化 ====================

    /**
     * 加载状态
     */
    private void loadState() {
        nationWeatherSettings.clear();
        if (stateStorage != null) {
            Map<NationId, NationWeatherSettings> loaded = stateStorage.load();
            loaded.forEach(nationWeatherSettings::put);
        }
    }

    /**
     * 保存状态
     */
    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(snapshotState());
    }

    /**
     * 快照当前状态
     */
    private Map<NationId, NationWeatherSettings> snapshotState() {
        Map<NationId, NationWeatherSettings> snapshot = new HashMap<>();
        nationWeatherSettings.forEach((id, settings) ->
            snapshot.put(id, settings.copy())
        );
        return snapshot;
    }

    // ==================== 天气预报生成 ====================

    /**
     * 生成天气预报
     */
    private List<WeatherForecastEntry> generateForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = new ArrayList<>();
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);

        long currentTime = System.currentTimeMillis();
        WeatherType currentWeather = settings != null ? settings.getCurrentWeather() : WeatherType.CLEAR;

        for (int day = 0; day < MAX_FORECAST_DAYS; day++) {
            long dayTime = currentTime + (day * 24L * 60 * 60 * 1000);

            // 基础概率
            Map<WeatherType, Double> probabilities = new EnumMap<>(WeatherType.class);
            probabilities.put(WeatherType.CLEAR, 0.5);
            probabilities.put(WeatherType.RAIN, 0.25);
            probabilities.put(WeatherType.THUNDER, 0.1);
            probabilities.put(WeatherType.SNOW, 0.05);
            probabilities.put(WeatherType.STORM, 0.1);

            // 应用政策影响
            if (policyService != null) {
                try {
                    double weatherControlBonus = policyService.activePolicyModifier(
                        nationId, "weather_control",
                        dev.starcore.starcore.module.policy.model.PolicyEffectScope.GLOBAL
                    );
                    if (weatherControlBonus > 0) {
                        probabilities.merge(WeatherType.CLEAR, weatherControlBonus * 0.2, Double::sum);
                    }
                } catch (Exception ignored) {
                    // 政策服务方法可能不存在
                }
            }

            // 归一化概率
            double total = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
            probabilities.replaceAll((k, v) -> v / total);

            // 根据概率选择天气
            WeatherType forecastWeather = selectWeatherByProbability(probabilities);

            forecast.add(new WeatherForecastEntry(
                dayTime,
                forecastWeather,
                probabilities,
                generateWeatherDescription(forecastWeather)
            ));
        }

        return forecast;
    }

    /**
     * 根据概率选择天气
     */
    private WeatherType selectWeatherByProbability(Map<WeatherType, Double> probabilities) {
        double rand = new Random().nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<WeatherType, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }

        return WeatherType.CLEAR;
    }

    /**
     * 生成天气描述
     */
    private String generateWeatherDescription(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> "晴朗";
            case RAIN -> "小雨";
            case THUNDER -> "雷暴";
            case SNOW -> "降雪";
            case STORM -> "暴风雨";
        };
    }

    // ==================== 摘要 ====================

    @Override
    public String summary() {
        long nationsWithControl = nationWeatherSettings.values().stream()
            .filter(s -> s.getPermission() != NationWeatherPermission.NONE)
            .count();
        return nationsWithControl + " nation(s) with weather control, " +
               worldWeatherStates.size() + " world(s) registered.";
    }

    /**
     * 获取所有国家的天气设置
     */
    public Map<NationId, NationWeatherSettings> getAllNationWeatherSettings() {
        return Map.copyOf(nationWeatherSettings);
    }

    /**
     * 获取天气效果
     */
    public WeatherEffect getWeatherEffect(WeatherType weather) {
        return weatherEffects.get(weather);
    }
}
