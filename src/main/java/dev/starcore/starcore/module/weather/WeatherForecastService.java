package dev.starcore.starcore.module.weather;

import dev.starcore.starcore.module.weather.model.WeatherForecastEntry;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.List;
import java.util.Map;

/**
 * 天气预报服务接口
 */
public interface WeatherForecastService {

    /**
     * 获取国家的天气预报
     *
     * @param nationId 国家ID
     * @return 天气预报列表
     */
    List<WeatherForecastEntry> getForecast(NationId nationId);

    /**
     * 获取今日天气预报
     *
     * @param nationId 国家ID
     * @return 今日天气预报
     */
    WeatherForecastEntry getTodayForecast(NationId nationId);

    /**
     * 刷新天气预报
     *
     * @param nationId 国家ID
     */
    void refreshForecast(NationId nationId);

    /**
     * 获取指定日期的天气概率
     *
     * @param nationId  国家ID
     * @param dayIndex 天数索引（0=今天，1=明天...）
     * @return 天气类型到概率的映射
     */
    Map<WeatherType, Double> getWeatherProbabilities(NationId nationId, int dayIndex);
}
