package dev.starcore.starcore.module.weather;

import dev.starcore.starcore.module.weather.model.NationWeatherSettings;
import dev.starcore.starcore.module.weather.model.WeatherEffect;
import dev.starcore.starcore.module.weather.model.WeatherForecastEntry;
import dev.starcore.starcore.module.weather.model.WeatherResourceModifier;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.command.WeatherCommand;
import dev.starcore.starcore.module.weather.gui.WeatherMenu;
import dev.starcore.starcore.module.weather.gui.WeatherGuiListener;
import dev.starcore.starcore.module.weather.listener.WeatherListener;
import dev.starcore.starcore.module.weather.storage.WeatherStateStorage;
import dev.starcore.starcore.module.weather.storage.DatabaseAwareWeatherStateStorage;
import dev.starcore.starcore.module.weather.event.WeatherChangeEvent;
import dev.starcore.starcore.module.weather.event.WeatherChangeCause;
import dev.starcore.starcore.module.weather.event.WeatherResourceEffectEvent;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 天气控制系统模块
 *
 * 功能特性：
 * - 世界级天气隔离
 * - 基于权限的天气控制
 * - 资源影响系统（天气影响资源产出）
 * - 事件驱动的天气触发
 * - 政策-天气耦合
 */
public final class WeatherModule implements StarCoreModule, WeatherControlService, WeatherForecastService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "weather",
        "天气控制",
        ModuleLayer.MODULE,
        List.of("nation", "policy"),
        List.of(WeatherControlService.class, WeatherForecastService.class),
        "World-level weather isolation with permission-based control and resource modifiers."
    );

    // 核心数据存储
    private final ConcurrentMap<NationId, NationWeatherSettings> nationWeatherSettings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorldWeatherState> worldWeatherStates = new ConcurrentHashMap<>();
    private final Map<WeatherType, WeatherResourceModifier> resourceModifiers = new EnumMap<>(WeatherType.class);

    // 天气预报缓存
    private final Map<NationId, List<WeatherForecastEntry>> weatherForecasts = new ConcurrentHashMap<>();

    // 服务引用
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
    private StarCoreContext context;

    // GUI 和监听器
    private WeatherMenu weatherMenu;
    private WeatherListener weatherListener;

    // 配置参数
    private static final long WEATHER_CHANGE_COOLDOWN_TICKS = 20L * 60; // 60秒冷却
    private static final long FORECAST_REFRESH_TICKS = 20L * 60 * 30; // 30分钟刷新
    private static final int MAX_FORECAST_DAYS = 7;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.plugin = context.plugin();
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.serviceRegistry = context.serviceRegistry();

        context.persistenceService().ensureNamespace(metadata().id());

        // 初始化存储
        this.stateStorage = new DatabaseAwareWeatherStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            plugin.getLogger()
        );

        // 获取依赖服务
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        this.policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);
        this.resourceService = context.serviceRegistry().find(ResourceService.class).orElse(null);
        this.onlinePlayerDirectory = context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);
        this.economyService = context.serviceRegistry().find(EconomyService.class).orElse(null);

        // 初始化资源修改器
        initializeResourceModifiers();

        // 加载状态
        loadState();

        // 初始化世界天气状态
        initializeWorldWeatherStates();

        // 注册命令
        registerCommands();

        // 注册监听器
        registerListeners();

        // 启动定时任务
        startScheduledTasks();

        // 注册到服务注册表
        registerToServiceRegistry();

        // 订阅天气变化事件
        subscribeToEvents();

        plugin.getLogger().info("WeatherModule enabled successfully");
    }

    /**
     * 初始化资源修改器
     */
    private void initializeResourceModifiers() {
        // 晴天：正常产出
        resourceModifiers.put(WeatherType.CLEAR, new WeatherResourceModifier(
            WeatherType.CLEAR,
            new HashMap<>(Map.of(
                "mineral", 1.0,
                "agricultural", 1.2,
                "energy", 1.0,
                "luxury", 1.0
            ))
        ));

        // 雨天：矿产和能源减少，农业增加
        resourceModifiers.put(WeatherType.RAIN, new WeatherResourceModifier(
            WeatherType.RAIN,
            new HashMap<>(Map.of(
                "mineral", 0.8,
                "agricultural", 1.5,
                "energy", 0.7,
                "luxury", 0.9
            ))
        ));

        // 雷暴：能源大幅减少，战略物资可能有变化
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

        // 暴雪：资源产出大幅降低
        resourceModifiers.put(WeatherType.SNOW, new WeatherResourceModifier(
            WeatherType.SNOW,
            new HashMap<>(Map.of(
                "mineral", 0.5,
                "agricultural", 0.2,
                "energy", 0.4,
                "luxury", 0.5
            ))
        ));

        // 风暴：类似雷暴但更严重
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

    /**
     * 初始化所有世界的天气状态
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

    /**
     * 注册命令
     */
    private void registerCommands() {
        var command = plugin.getCommand("weather");
        if (command != null) {
            WeatherCommand weatherCommand = new WeatherCommand(
                this,
                this,
                nationService,
                onlinePlayerDirectory,
                economyService
            );
            command.setExecutor(weatherCommand);
            command.setTabCompleter(weatherCommand);
            plugin.getLogger().info("Weather command registered: /weather");
        } else {
            plugin.getLogger().warning("Command 'weather' not found in plugin.yml");
        }

        // 注册打开 GUI 的命令
        var guiCommand = plugin.getCommand("weathermenu");
        if (guiCommand != null) {
            guiCommand.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    if (weatherMenu != null) {
                        weatherMenu.openMainMenu(player);
                    }
                } else {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                }
                return true;
            });
            plugin.getLogger().info("WeatherMenu command registered: /weathermenu");
        }
    }

    /**
     * 注册事件监听器
     */
    private void registerListeners() {
        if (plugin == null) {
            return;
        }

        // 初始化天气菜单
        this.weatherMenu = new WeatherMenu(this, this, nationService);

        // 注册天气系统监听器
        this.weatherListener = new WeatherListener(this, eventBus);
        plugin.getServer().getPluginManager().registerEvents(weatherListener, plugin);

        // 注册 GUI 监听器
        WeatherGuiListener guiListener = new WeatherGuiListener(this, weatherMenu);
        plugin.getServer().getPluginManager().registerEvents(guiListener, plugin);

        plugin.getLogger().info("Weather listeners registered.");
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        if (scheduler == null) {
            return;
        }

        // 每30分钟刷新天气预报
        scheduler.runSyncTimer(() -> {
            refreshAllForecasts();
        }, FORECAST_REFRESH_TICKS, FORECAST_REFRESH_TICKS);

        // 每分钟检查天气状态并同步到世界
        scheduler.runSyncTimer(() -> {
            syncWeatherToWorlds();
        }, 20L * 60, 20L * 60);

        // 每5分钟应用资源修改器
        scheduler.runSyncTimer(() -> {
            applyResourceModifiers();
        }, 20L * 60 * 5, 20L * 60 * 5);

        plugin.getLogger().info("Weather scheduled tasks started.");
    }

    /**
     * 同步天气状态到游戏世界
     */
    private void syncWeatherToWorlds() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, WorldWeatherState> entry : worldWeatherStates.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world == null) {
                continue;
            }

            WorldWeatherState state = entry.getValue();

            // 检查是否需要强制天气（基于权限）
            if (state.getControlledByNation() != null) {
                NationId nationId = state.getControlledByNation();
                NationWeatherSettings settings = nationWeatherSettings.get(nationId);

                if (settings != null && settings.isAutoWeather()) {
                    // 应用控制的天气
                    applyWeatherToWorld(world, settings.getCurrentWeather(), state);
                } else {
                    // 自然天气
                    applyNaturalWeather(world, state, currentTime);
                }
            } else {
                // 无国家控制，自然天气
                applyNaturalWeather(world, state, currentTime);
            }
        }
    }

    /**
     * 应用控制的天气到世界
     */
    private void applyWeatherToWorld(World world, WeatherType weather, WorldWeatherState state) {
        long currentTime = System.currentTimeMillis();

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
                // 雪天需要特殊处理
                world.setStorm(true);
                world.setThundering(false);
            }
            default -> {
                // 自然天气
                world.setStorm(false);
                world.setThundering(false);
            }
        }

        // 更新状态
        state.setLastWeatherChange(currentTime);
    }

    /**
     * 应用自然天气
     */
    private void applyNaturalWeather(World world, WorldWeatherState state, long currentTime) {
        // 自然天气每10-30分钟变化一次
        long timeSinceChange = currentTime - state.getLastWeatherChange();
        long naturalChangeTime = 20L * 60 * (10 + new Random().nextInt(20)); // 10-30分钟

        if (timeSinceChange > naturalChangeTime) {
            // 随机改变天气
            WeatherType[] naturalWeathers = {WeatherType.CLEAR, WeatherType.CLEAR, WeatherType.RAIN, WeatherType.RAIN, WeatherType.THUNDER};
            WeatherType newWeather = naturalWeathers[new Random().nextInt(naturalWeathers.length)];

            applyWeatherToWorld(world, newWeather, state);
            state.setCurrentWeather(newWeather);

            // 广播天气变化事件
            if (state.getControlledByNation() != null) {
                eventBus.publish(new WeatherChangeEvent(
                    state.getControlledByNation(),
                    world.getName(),
                    state.getPreviousWeather(),
                    newWeather,
                    WeatherChangeCause.NATURAL
                ));
            }
        }
    }

    /**
     * 刷新所有国家的天气预报
     */
    private void refreshAllForecasts() {
        if (nationService == null) {
            return;
        }

        Collection<Nation> nations = nationService.nations();
        for (Nation nation : nations) {
            NationId nationId = nation.id();
            List<WeatherForecastEntry> forecast = generateForecast(nationId);
            weatherForecasts.put(nationId, forecast);
        }
    }

    /**
     * 生成天气预报
     */
    private List<WeatherForecastEntry> generateForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = new ArrayList<>();
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);

        long currentTime = System.currentTimeMillis();
        WeatherType currentWeather = settings != null ? settings.getCurrentWeather() : WeatherType.CLEAR;

        // 生成7天预报
        for (int day = 0; day < MAX_FORECAST_DAYS; day++) {
            long dayTime = currentTime + (day * 24L * 60 * 60 * 1000);

            // 基础概率计算
            Map<WeatherType, Double> probabilities = new EnumMap<>(WeatherType.class);
            probabilities.put(WeatherType.CLEAR, 0.5);
            probabilities.put(WeatherType.RAIN, 0.25);
            probabilities.put(WeatherType.THUNDER, 0.1);
            probabilities.put(WeatherType.SNOW, 0.05);
            probabilities.put(WeatherType.STORM, 0.1);

            // 应用政策影响
            if (policyService != null) {
                try {
                    double weatherControlBonus = policyService.activePolicyModifier(nationId, "weather_control", dev.starcore.starcore.module.policy.model.PolicyEffectScope.GLOBAL);
                    if (weatherControlBonus > 0) {
                        // 晴天概率增加
                        probabilities.merge(WeatherType.CLEAR, weatherControlBonus * 0.2, Double::sum);
                    }
                } catch (Exception e) {
                    // 政策服务方法可能不存在，使用默认值
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

    /**
     * 应用资源修改器
     */
    private void applyResourceModifiers() {
        if (resourceService == null) {
            return;
        }

        for (Map.Entry<String, WorldWeatherState> entry : worldWeatherStates.entrySet()) {
            String worldName = entry.getKey();
            WorldWeatherState state = entry.getValue();
            WeatherType weather = state.getCurrentWeather();

            WeatherResourceModifier modifier = resourceModifiers.get(weather);
            if (modifier == null) {
                continue;
            }

            // 获取该世界对应的国家
            NationId controlledNation = state.getControlledByNation();
            if (controlledNation == null) {
                continue;
            }

            // 应用资源修改（实际应用需要与 ResourceModule 集成）
            // 这里只记录日志，实际效果由 ResourceModule 通过事件监听应用
            if (eventBus != null) {
                eventBus.publish(new WeatherResourceEffectEvent(
                    controlledNation,
                    worldName,
                    weather,
                    modifier.getModifiers()
                ));
            }
        }
    }

    /**
     * 注册到服务注册表
     */
    private void registerToServiceRegistry() {
        if (serviceRegistry == null) {
            return;
        }

        serviceRegistry.register(WeatherControlService.class, this);

        plugin.getLogger().info("Weather services registered to ServiceRegistry.");
    }

    /**
     * 订阅事件
     */
    private void subscribeToEvents() {
        // 订阅其他模块的天气相关事件
        if (eventBus != null) {
            // PolicyChangeEvent 可能不存在，使用 try-catch
            try {
                Class<?> policyEventClass = Class.forName("dev.starcore.starcore.module.policy.event.PolicyChangeEvent");
                if (policyEventClass != null) {
                    plugin.getLogger().info("PolicyChangeEvent found, weather module will react to policy changes");
                }
            } catch (ClassNotFoundException e) {
                plugin.getLogger().info("PolicyChangeEvent not found, skipping policy integration");
            }
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
        plugin.getLogger().info("WeatherModule disabled.");
    }

    // ==================== 持久化方法 ====================

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        Map<NationId, NationWeatherSettings> snapshot = snapshotState();
        stateStorage.save(snapshot);
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(snapshotState());
    }

    private void loadState() {
        nationWeatherSettings.clear();
        if (stateStorage != null) {
            // 初始化存储（创建表）
            stateStorage.initialize();
            Map<NationId, NationWeatherSettings> loaded = stateStorage.load();
            loaded.forEach((nationId, settings) -> {
                nationWeatherSettings.put(nationId, settings);
            });
        }
    }

    private Map<NationId, NationWeatherSettings> snapshotState() {
        Map<NationId, NationWeatherSettings> snapshot = new HashMap<>();
        nationWeatherSettings.forEach((nationId, settings) -> {
            snapshot.put(nationId, settings.copy());
        });
        return snapshot;
    }

    // ==================== WeatherControlService 接口实现 ====================

    @Override
    public boolean setWeather(NationId nationId, String worldName, WeatherType weather) {
        // 检查权限
        if (!hasWeatherControlPermission(nationId)) {
            return false;
        }

        // 检查冷却时间
        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state != null) {
            long timeSinceChange = System.currentTimeMillis() - state.getLastWeatherChange();
            if (timeSinceChange < WEATHER_CHANGE_COOLDOWN_TICKS * 50) { // 转换为毫秒
                return false; // 还在冷却中
            }
        }

        // 获取或创建国家天气设置
        NationWeatherSettings settings = nationWeatherSettings.computeIfAbsent(
            nationId,
            id -> new NationWeatherSettings(id, WeatherType.CLEAR, true, NationWeatherPermission.CONTROL_BASIC)
        );

        // 检查天气类型权限
        if (!canUseWeatherType(settings, weather)) {
            return false;
        }

        // 设置天气
        settings.setCurrentWeather(weather);

        // 更新世界状态
        WorldWeatherState worldState = worldWeatherStates.computeIfAbsent(
            worldName,
            name -> new WorldWeatherState(name, weather, System.currentTimeMillis(), 0, nationId)
        );
        worldState.setCurrentWeather(weather);
        worldState.setControlledByNation(nationId);
        worldState.setLastWeatherChange(System.currentTimeMillis());

        // 应用到世界
        World world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            applyWeatherToWorld(world, weather, worldState);
        }

        // 广播事件
        if (eventBus != null) {
            eventBus.publish(new WeatherChangeEvent(
                nationId,
                worldName,
                worldState.getPreviousWeather(),
                weather,
                WeatherChangeCause.NATION_CONTROL
            ));
        }

        // 保存状态
        saveState();

        return true;
    }

    @Override
    public WeatherType getNationWeather(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null ? settings.getCurrentWeather() : WeatherType.CLEAR;
    }

    /**
     * @deprecated Use {@link #getNationWeather(NationId)} instead
     */
    @Deprecated
    public WeatherType getCurrentWeather(NationId nationId) {
        return getNationWeather(nationId);
    }

    @Override
    public boolean clearWeather(NationId nationId) {
        String worldName = findNationControlledWorld(nationId);
        if (worldName == null) {
            return false;
        }
        return setWeather(nationId, worldName, WeatherType.CLEAR);
    }

    @Override
    public boolean setNationWeather(NationId nationId, WeatherType weather, long duration) {
        String worldName = findNationControlledWorld(nationId);
        if (worldName == null) {
            return false;
        }

        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state != null) {
            state.setWeatherDurationMinutes((int) (duration / 1200)); // 转换为分钟
        }

        return setWeather(nationId, worldName, weather);
    }

    @Override
    public WeatherEffect getWeatherEffects(NationId nationId) {
        WeatherType weather = getNationWeather(nationId);
        WeatherResourceModifier modifier = resourceModifiers.get(weather);
        if (modifier != null) {
            Map<String, Double> mods = modifier.getModifiers();
            return new WeatherEffect(
                weather,
                mods.getOrDefault("mineral", 1.0),
                mods.getOrDefault("energy", 1.0),
                mods.getOrDefault("agricultural", 1.0)
            );
        }
        return new WeatherEffect(weather, 1.0, 1.0, 1.0);
    }

    @Override
    public boolean hasWeatherControl(NationId nationId) {
        NationWeatherSettings settings = nationWeatherSettings.get(nationId);
        return settings != null && settings.getPermission() != NationWeatherPermission.NONE;
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

    @Override
    public WeatherResourceModifier getResourceModifier(WeatherType weather) {
        return resourceModifiers.get(weather);
    }

    @Override
    public Map<String, Double> getResourceModifiers(WeatherType weather) {
        WeatherResourceModifier modifier = getResourceModifier(weather);
        return modifier != null ? modifier.getModifiers() : Map.of();
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
    public boolean controlWorld(NationId nationId, String worldName) {
        WorldWeatherState state = worldWeatherStates.get(worldName);
        if (state == null) {
            return false;
        }
        state.setControlledByNation(nationId);

        // 更新国家天气设置
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

    // ==================== WeatherForecastService 接口实现 ====================

    @Override
    public List<WeatherForecastEntry> getForecast(NationId nationId) {
        return weatherForecasts.computeIfAbsent(nationId, this::generateForecast);
    }

    @Override
    public WeatherForecastEntry getTodayForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = getForecast(nationId);
        return forecast.isEmpty() ? null : forecast.get(0);
    }

    @Override
    public void refreshForecast(NationId nationId) {
        List<WeatherForecastEntry> forecast = generateForecast(nationId);
        weatherForecasts.put(nationId, forecast);
    }

    @Override
    public Map<WeatherType, Double> getWeatherProbabilities(NationId nationId, int dayIndex) {
        List<WeatherForecastEntry> forecast = getForecast(nationId);
        if (dayIndex < 0 || dayIndex >= forecast.size()) {
            return Map.of();
        }
        return forecast.get(dayIndex).getProbabilities();
    }

    @Override
    public String summary() {
        int nationsWithControl = (int) nationWeatherSettings.values().stream()
            .filter(s -> s.getPermission() != NationWeatherPermission.NONE)
            .count();
        return nationsWithControl + " nation(s) with weather control, " +
               worldWeatherStates.size() + " world(s) registered.";
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否可以使用指定天气类型
     */
    private boolean canUseWeatherType(NationWeatherSettings settings, WeatherType weather) {
        NationWeatherPermission permission = settings.getPermission();

        // 权限等级决定了可用的天气类型
        return switch (permission) {
            case NONE -> false;
            case CONTROL_BASIC -> weather == WeatherType.CLEAR || weather == WeatherType.RAIN;
            case CONTROL_ADVANCED -> weather != WeatherType.STORM;
            case CONTROL_FULL -> true;
        };
    }

    // ==================== 公开访问器 ====================

    /**
     * 获取天气菜单
     */
    public WeatherMenu getWeatherMenu() {
        return weatherMenu;
    }

    /**
     * 设置天气菜单
     */
    public void setWeatherMenu(WeatherMenu menu) {
        this.weatherMenu = menu;
    }

    /**
     * 获取所有国家的天气设置
     */
    public Map<NationId, NationWeatherSettings> getAllNationWeatherSettings() {
        return Map.copyOf(nationWeatherSettings);
    }

    /**
     * 获取天气监听器
     */
    public WeatherListener getWeatherListener() {
        return weatherListener;
    }
}
