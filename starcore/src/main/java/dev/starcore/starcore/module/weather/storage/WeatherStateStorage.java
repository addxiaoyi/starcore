package dev.starcore.starcore.module.weather.storage;

import dev.starcore.starcore.module.weather.model.NationWeatherSettings;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Map;

/**
 * 天气状态存储接口
 */
public interface WeatherStateStorage {

    /**
     * 加载天气状态
     *
     * @return 国家ID到天气设置的映射
     */
    Map<NationId, NationWeatherSettings> load();

    /**
     * 异步保存天气状态
     *
     * @param states 国家ID到天气设置的映射
     */
    void saveAsync(Map<NationId, NationWeatherSettings> states);

    /**
     * 同步保存天气状态
     *
     * @param states 国家ID到天气设置的映射
     */
    void save(Map<NationId, NationWeatherSettings> states);

    /**
     * 保存单个国家的天气设置
     *
     * @param nationId 国家ID
     * @param settings 天气设置
     */
    void saveNation(NationId nationId, NationWeatherSettings settings);

    /**
     * 删除单个国家的天气设置
     *
     * @param nationId 国家ID
     */
    void deleteNation(NationId nationId);

    /**
     * 检查是否已初始化
     */
    boolean isInitialized();

    /**
     * 初始化存储
     */
    void initialize();
}
