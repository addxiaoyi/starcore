package dev.starcore.starcore.module.weather;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.NationWeather;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.model.NationWeatherSettings;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.storage.WeatherStateStorage;
import dev.starcore.starcore.module.weather.storage.DatabaseAwareWeatherStateStorage;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 天气数据存储管理类
 * 提供天气数据的内存缓存和持久化功能
 */
public final class WeatherStorage {

    /** 内存缓存：国家天气映射 */
    private final Map<NationId, NationWeather> nationWeatherCache = new ConcurrentHashMap<>();

    /** 持久化存储 */
    private final WeatherStateStorage stateStorage;

    /** 脏标记：需要保存的数据 */
    private final Set<NationId> dirtyNationIds = ConcurrentHashMap.newKeySet();

    /** 日志记录器 */
    private final Logger logger;

    /** 是否已初始化 */
    private volatile boolean initialized = false;

    // ==================== 构造方法 ====================

    /**
     * 创建天气存储
     *
     * @param context StarCore上下文
     */
    public WeatherStorage(StarCoreContext context) {
        this.logger = context.plugin().getLogger();
        this.stateStorage = new DatabaseAwareWeatherStateStorage(
            "weather",
            context.databaseService(),
            context.persistenceService(),
            logger
        );
    }

    /**
     * 创建天气存储（带指定存储实现）
     *
     * @param namespace 命名空间
     * @param databaseService 数据库服务
     * @param persistenceService 持久化服务
     * @param logger 日志记录器
     */
    public WeatherStorage(
        String namespace,
        DatabaseService databaseService,
        PersistenceService persistenceService,
        Logger logger
    ) {
        this.logger = logger;
        this.stateStorage = new DatabaseAwareWeatherStateStorage(
            namespace,
            databaseService,
            persistenceService,
            logger
        );
    }

    // ==================== 生命周期 ====================

    /**
     * 初始化存储
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        // 确保存储已初始化
        if (!stateStorage.isInitialized()) {
            stateStorage.initialize();
        }

        // 加载数据
        load();

        initialized = true;
        logger.info("WeatherStorage initialized successfully");
    }

    /**
     * 关闭存储
     */
    public void shutdown() {
        flush();
        initialized = false;
        logger.info("WeatherStorage shutdown completed");
    }

    // ==================== 数据操作 ====================

    /**
     * 加载所有天气数据
     */
    public void load() {
        nationWeatherCache.clear();

        try {
            Map<NationId, NationWeatherSettings> settings = stateStorage.load();

            settings.forEach((nationId, setting) -> {
                NationWeather weather = NationWeather.create(
                    nationId,
                    setting.getCurrentWeather(),
                    setting.getWeatherDurationMinutes()
                );
                nationWeatherCache.put(nationId, weather);
            });

            logger.info("Loaded " + nationWeatherCache.size() + " nation weather records");
        } catch (Exception e) {
            logger.warning("Failed to load weather data: " + e.getMessage());
        }
    }

    /**
     * 保存所有数据
     */
    public void save() {
        flush();
    }

    /**
     * 刷新脏数据
     */
    public void flush() {
        if (dirtyNationIds.isEmpty()) {
            return;
        }

        try {
            Set<NationId> toSave = new HashSet<>(dirtyNationIds);
            Map<NationId, NationWeatherSettings> settingsMap = new HashMap<>();

            toSave.forEach(nationId -> {
                NationWeather weather = nationWeatherCache.get(nationId);
                if (weather != null) {
                    NationWeatherSettings settings = createSettingsFromWeather(weather);
                    settingsMap.put(nationId, settings);
                }
            });

            if (!settingsMap.isEmpty()) {
                stateStorage.save(settingsMap);
            }

            dirtyNationIds.removeAll(toSave);
            logger.fine("Flushed " + toSave.size() + " weather records");
        } catch (Exception e) {
            logger.warning("Failed to flush weather data: " + e.getMessage());
        }
    }

    /**
     * 异步保存
     */
    public void saveAsync() {
        new Thread(this::flush).start();
    }

    // ==================== 国家天气操作 ====================

    /**
     * 获取国家天气
     *
     * @param nationId 国家ID
     * @return 国家天气记录
     */
    public Optional<NationWeather> getNationWeather(NationId nationId) {
        NationWeather weather = nationWeatherCache.get(nationId);
        return Optional.ofNullable(weather);
    }

    /**
     * 设置国家天气
     *
     * @param nationId 国家ID
     * @param weather 天气类型
     * @param durationMinutes 持续时间（分钟）
     * @return 更新后的天气记录
     */
    public NationWeather setNationWeather(NationId nationId, WeatherType weather, long durationMinutes) {
        NationWeather newWeather = NationWeather.create(nationId, weather, durationMinutes);
        nationWeatherCache.put(nationId, newWeather);
        dirtyNationIds.add(nationId);
        return newWeather;
    }

    /**
     * 清除国家天气
     *
     * @param nationId 国家ID
     * @return 晴朗天气记录
     */
    public NationWeather clearWeather(NationId nationId) {
        NationWeather cleared = NationWeather.permanent(nationId, WeatherType.CLEAR);
        nationWeatherCache.put(nationId, cleared);
        dirtyNationIds.add(nationId);
        return cleared;
    }

    /**
     * 删除国家天气数据
     *
     * @param nationId 国家ID
     */
    public void deleteNation(NationId nationId) {
        nationWeatherCache.remove(nationId);
        dirtyNationIds.add(nationId);
        stateStorage.deleteNation(nationId);
    }

    /**
     * 获取所有国家天气
     *
     * @return 国家ID到天气的映射
     */
    public Map<NationId, NationWeather> getAllNationWeather() {
        return Map.copyOf(nationWeatherCache);
    }

    /**
     * 获取所有有效（非过期）的天气
     *
     * @return 有效天气映射
     */
    public Map<NationId, NationWeather> getActiveWeather() {
        Map<NationId, NationWeather> active = new HashMap<>();
        nationWeatherCache.forEach((nationId, weather) -> {
            if (!weather.isExpired()) {
                active.put(nationId, weather);
            }
        });
        return active;
    }

    // ==================== 权限操作 ====================

    /**
     * 获取国家天气权限
     *
     * @param nationId 国家ID
     * @return 权限等级
     */
    public NationWeatherPermission getPermission(NationId nationId) {
        return nationWeatherCache.get(nationId) != null ?
            NationWeatherPermission.CONTROL_BASIC : NationWeatherPermission.NONE;
    }

    /**
     * 设置国家天气权限
     *
     * @param nationId 国家ID
     * @param permission 权限等级
     */
    public void setPermission(NationId nationId, NationWeatherPermission permission) {
        NationWeather current = nationWeatherCache.get(nationId);
        if (current != null) {
            // 权限存储在设置中，这里简化处理
            dirtyNationIds.add(nationId);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从天气记录创建设置对象
     */
    private NationWeatherSettings createSettingsFromWeather(NationWeather weather) {
        return new NationWeatherSettings(
            weather.nationId(),
            weather.weather(),
            true,
            NationWeatherPermission.CONTROL_BASIC
        );
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取脏数据数量
     */
    public int getDirtyCount() {
        return dirtyNationIds.size();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return nationWeatherCache.size();
    }

    /**
     * 清理过期天气
     */
    public int cleanupExpired() {
        List<NationId> expiredIds = new ArrayList<>();

        nationWeatherCache.forEach((nationId, weather) -> {
            if (weather.isExpired()) {
                expiredIds.add(nationId);
            }
        });

        expiredIds.forEach(id -> {
            nationWeatherCache.remove(id);
            dirtyNationIds.add(id);
        });

        if (!expiredIds.isEmpty()) {
            flush();
        }

        return expiredIds.size();
    }
}
