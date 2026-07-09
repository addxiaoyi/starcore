package dev.starcore.starcore.module.army.weather.storage;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsBoost;
import dev.starcore.starcore.module.weather.model.WeatherType;

import java.util.Map;

/**
 * 天气战术存储接口
 */
public interface WeatherTacticsStorage {

    /**
     * 初始化存储（创建必要的表等）
     */
    void initialize();

    /**
     * 保存所有战术数据
     */
    void save();

    /**
     * 加载所有战术数据
     */
    void load();

    /**
     * 保存国家的战术升级数据
     */
    void saveNationTactics(NationId nationId, Map<String, Integer> tactics);

    /**
     * 加载国家的战术升级数据
     */
    Map<String, Integer> loadNationTactics(NationId nationId);

    /**
     * 保存国家的战术加成
     */
    void saveTacticsBoost(NationId nationId, WeatherType weather, WeatherTacticsBoost boost);

    /**
     * 加载国家的战术加成
     */
    WeatherTacticsBoost loadTacticsBoost(NationId nationId, WeatherType weather);

    /**
     * 关闭存储
     */
    void close();
}