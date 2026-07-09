package dev.starcore.starcore.module.army.weather;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.weather.command.WeatherTacticsCommand;
import dev.starcore.starcore.module.army.weather.listener.WeatherTacticsListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.WeatherForecastService;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.WeatherEffect;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.model.WeatherResourceModifier;
import dev.starcore.starcore.foundation.message.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

/**
 * 天气战术模块
 *
 * 功能特性：
 * - 天气对军队战斗力的影响计算
 * - 兵种对不同天气的适应性系统
 * - 天气战术科技升级
 * - 与 WeatherModule 和 ArmyModule 集成
 *
 * 依赖模块：
 * - nation (国家模块)
 * - army (军队模块)
 * - weather (天气模块)
 */
public final class WeatherTacticsModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "army-weather-tactics",
        "天气战术",
        ModuleLayer.MODULE,
        List.of("nation", "army", "weather"),
        List.of(WeatherTacticsService.class),
        "Weather-based military tactics with unit adaptation and battle modifiers."
    );

    // 核心服务
    private WeatherTacticsService tacticsService;
    private WeatherTacticsCommand tacticsCommand;
    private WeatherTacticsListener tacticsListener;

    // 服务依赖
    private ArmyService armyService;
    private NationService nationService;
    private WeatherControlService weatherService;
    private WeatherForecastService forecastService;
    private MessageService messages;
    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;
    private ServiceRegistry serviceRegistry;
    private JavaPlugin plugin;

    // 配置
    private static final long TACTICS_REFRESH_INTERVAL = 20L * 60 * 5; // 5分钟
    private static final long ARMY_STATE_CLEANUP_INTERVAL = 20L * 60 * 30; // 30分钟

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.serviceRegistry = context.serviceRegistry();

        // 获取依赖服务
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 获取可选服务
        this.armyService = context.serviceRegistry().find(ArmyService.class).orElse(null);
        this.weatherService = context.serviceRegistry().find(WeatherControlService.class).orElse(null);
        this.forecastService = context.serviceRegistry().find(WeatherForecastService.class).orElse(null);

        // 检查必要依赖
        if (armyService == null) {
            plugin.getLogger().warning("ArmyService not found, weather tactics will have limited functionality");
        }
        if (weatherService == null) {
            plugin.getLogger().warning("WeatherService not found, weather tactics will use default weather");
        }

        // 初始化服务
        initializeService(context);

        // 注册服务
        serviceRegistry.register(WeatherTacticsService.class, tacticsService);

        // 注册命令
        registerCommands();

        // 注册监听器
        registerListeners();

        // 启动定时任务
        startScheduledTasks();

        plugin.getLogger().info("WeatherTacticsModule enabled successfully");
    }

    /**
     * 初始化服务
     */
    private void initializeService(StarCoreContext context) {
        tacticsService = new WeatherTacticsServiceImpl(context);

        // 启动服务
        ((WeatherTacticsServiceImpl) tacticsService).start();
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 注册 /weathertactics 命令
        var wtCommand = plugin.getCommand("weathertactics");
        if (wtCommand != null) {
            tacticsCommand = new WeatherTacticsCommand(
                tacticsService,
                Optional.ofNullable(weatherService).orElse(createDummyWeatherService()),
                nationService,
                messages
            );
            wtCommand.setExecutor(tacticsCommand);
            wtCommand.setTabCompleter(tacticsCommand);
            plugin.getLogger().info("WeatherTactics command registered: /weathertactics");
        }

        // 注册 /wt 短命令
        var wtShortCommand = plugin.getCommand("wt");
        if (wtShortCommand != null) {
            wtShortCommand.setExecutor(tacticsCommand);
            wtShortCommand.setTabCompleter(tacticsCommand);
            plugin.getLogger().info("WeatherTactics short command registered: /wt");
        }
    }

    /**
     * 注册监听器
     */
    private void registerListeners() {
        tacticsListener = new WeatherTacticsListener(
            tacticsService,
            Optional.ofNullable(weatherService).orElse(createDummyWeatherService()),
            armyService,
            nationService,
            eventBus
        );
        plugin.getServer().getPluginManager().registerEvents(tacticsListener, plugin);
        plugin.getLogger().info("WeatherTactics listener registered");
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        if (scheduler == null) {
            return;
        }

        // 定期刷新战术状态
        scheduler.runSyncTimer(() -> refreshTactics(), TACTICS_REFRESH_INTERVAL, TACTICS_REFRESH_INTERVAL);

        // 清理过期的军队天气状态
        scheduler.runSyncTimer(() -> cleanupArmyStates(), ARMY_STATE_CLEANUP_INTERVAL, ARMY_STATE_CLEANUP_INTERVAL);

        plugin.getLogger().info("WeatherTactics scheduled tasks started");
    }

    /**
     * 刷新战术状态
     */
    private void refreshTactics() {
        if (tacticsService == null) {
            return;
        }

        // 保存脏数据
        if (tacticsService.isDirty()) {
            tacticsService.markClean();
            plugin.getLogger().fine("Weather tactics state saved");
        }

        // 清理监听器过期数据
        if (tacticsListener != null) {
            tacticsListener.cleanupExpiredCooldowns();
        }
    }

    /**
     * 清理过期的军队天气状态
     */
    private void cleanupArmyStates() {
        // 清理逻辑可以在这里扩展
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止服务
        if (tacticsService instanceof WeatherTacticsServiceImpl impl) {
            impl.stop();
        }

        // 清理监听器
        if (tacticsListener != null) {
            tacticsListener.cleanupExpiredCooldowns();
            tacticsListener = null;
        }

        // 清理引用
        tacticsService = null;
        tacticsCommand = null;

        plugin.getLogger().info("WeatherTacticsModule disabled");
    }

    // ==================== 公开访问器 ====================

    /**
     * 获取战术服务
     */
    public WeatherTacticsService getTacticsService() {
        return tacticsService;
    }

    /**
     * 获取战术命令
     */
    public WeatherTacticsCommand getTacticsCommand() {
        return tacticsCommand;
    }

    /**
     * 获取战术监听器
     */
    public WeatherTacticsListener getTacticsListener() {
        return tacticsListener;
    }

    // ==================== 虚拟服务（用于依赖缺失时） ====================

    /**
     * 创建虚拟天气服务
     */
    private WeatherControlService createDummyWeatherService() {
        return new WeatherControlService() {
            @Override
            public boolean setWeather(dev.starcore.starcore.module.nation.model.NationId nationId, String worldName, WeatherType weather) {
                return false;
            }

            @Override
            public boolean setNationWeather(dev.starcore.starcore.module.nation.model.NationId nationId, WeatherType weather, long duration) {
                return false;
            }

            @Override
            public WeatherType getNationWeather(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return WeatherType.CLEAR;
            }

            @Override
            public boolean clearWeather(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return false;
            }

            @Override
            public WeatherEffect getWeatherEffects(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return new WeatherEffect(WeatherType.CLEAR, 1.0, 1.0, 1.0);
            }

            @Override
            public boolean hasWeatherControl(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return false;
            }

            @Override
            public WorldWeatherState getWorldWeatherState(String worldName) {
                return new WorldWeatherState(worldName, WeatherType.CLEAR, System.currentTimeMillis(), 0, null);
            }

            @Override
            public java.util.Map<String, WorldWeatherState> getAllWorldWeatherStates() {
                return java.util.Map.of();
            }

            @Override
            public boolean registerWorld(String worldName) {
                return false;
            }

            @Override
            public boolean unregisterWorld(String worldName) {
                return false;
            }

            @Override
            public boolean controlWorld(dev.starcore.starcore.module.nation.model.NationId nationId, String worldName) {
                return false;
            }

            @Override
            public boolean releaseWorld(String worldName) {
                return false;
            }

            @Override
            public boolean hasWeatherControlPermission(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return false;
            }

            @Override
            public boolean setPermission(dev.starcore.starcore.module.nation.model.NationId nationId, NationWeatherPermission permission) {
                return false;
            }

            @Override
            public NationWeatherPermission getPermission(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return NationWeatherPermission.NONE;
            }

            @Override
            public boolean setAutoWeather(dev.starcore.starcore.module.nation.model.NationId nationId, boolean auto) {
                return false;
            }

            @Override
            public boolean isAutoWeather(dev.starcore.starcore.module.nation.model.NationId nationId) {
                return true;
            }

            @Override
            public WeatherResourceModifier getResourceModifier(WeatherType weather) {
                return null;
            }

            @Override
            public java.util.Map<String, Double> getResourceModifiers(WeatherType weather) {
                return java.util.Map.of();
            }

            @Override
            public String summary() {
                return "Dummy Weather Service";
            }
        };
    }
}