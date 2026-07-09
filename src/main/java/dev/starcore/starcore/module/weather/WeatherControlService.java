package dev.starcore.starcore.module.weather;

import dev.starcore.starcore.module.weather.model.WeatherEffect;
import dev.starcore.starcore.module.weather.model.WeatherResourceModifier;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Map;

/**
 * 天气控制服务接口
 * 提供国家级别的天气控制能力
 */
public interface WeatherControlService {

    // ==================== 核心天气操作 ====================

    /**
     * 设置指定世界的天气
     *
     * @param nationId  控制天气的国家ID
     * @param worldName 世界名称
     * @param weather   天气类型
     * @return 是否成功设置
     */
    boolean setWeather(NationId nationId, String worldName, WeatherType weather);

    /**
     * 设置国家天气（带持续时间）
     *
     * @param nationId 国家ID
     * @param weather  天气类型
     * @param duration 持续时间（tick），0表示永久
     * @return 是否成功设置
     */
    boolean setNationWeather(NationId nationId, WeatherType weather, long duration);

    /**
     * 获取国家当前的天气
     *
     * @param nationId 国家ID
     * @return 当前天气类型
     */
    WeatherType getNationWeather(NationId nationId);

    /**
     * 清除国家天气（设为晴朗）
     *
     * @param nationId 国家ID
     * @return 是否成功清除
     */
    boolean clearWeather(NationId nationId);

    /**
     * 获取天气效果
     *
     * @param nationId 国家ID
     * @return 天气效果
     */
    WeatherEffect getWeatherEffects(NationId nationId);

    /**
     * 检查国家是否有天气控制能力
     *
     * @param nationId 国家ID
     * @return 是否有控制能力
     */
    boolean hasWeatherControl(NationId nationId);

    /**
     * 获取国家当前的天气（兼容旧方法）
     *
     * @param nationId 国家ID
     * @return 当前天气类型
     */
    default WeatherType getCurrentWeather(NationId nationId) {
        return getNationWeather(nationId);
    }

    // ==================== 世界天气管理 ====================

    /**
     * 获取指定世界的天气状态
     *
     * @param worldName 世界名称
     * @return 世界天气状态
     */
    WorldWeatherState getWorldWeatherState(String worldName);

    /**
     * 获取所有世界的天气状态
     *
     * @return 世界名称到天气状态的映射
     */
    Map<String, WorldWeatherState> getAllWorldWeatherStates();

    /**
     * 注册一个世界到天气控制系统
     *
     * @param worldName 世界名称
     * @return 是否成功注册
     */
    boolean registerWorld(String worldName);

    /**
     * 从天气控制系统注销一个世界
     *
     * @param worldName 世界名称
     * @return 是否成功注销
     */
    boolean unregisterWorld(String worldName);

    /**
     * 让国家控制指定世界
     *
     * @param nationId  国家ID
     * @param worldName 世界名称
     * @return 是否成功
     */
    boolean controlWorld(NationId nationId, String worldName);

    /**
     * 释放对指定世界的控制
     *
     * @param worldName 世界名称
     * @return 是否成功
     */
    boolean releaseWorld(String worldName);

    // ==================== 权限管理 ====================

    /**
     * 检查国家是否有天气控制权限
     *
     * @param nationId 国家ID
     * @return 是否有权限
     */
    boolean hasWeatherControlPermission(NationId nationId);

    /**
     * 设置国家的天气控制权限等级
     *
     * @param nationId   国家ID
     * @param permission 权限等级
     * @return 是否成功设置
     */
    boolean setPermission(NationId nationId, NationWeatherPermission permission);

    /**
     * 获取国家的天气控制权限等级
     *
     * @param nationId 国家ID
     * @return 权限等级
     */
    NationWeatherPermission getPermission(NationId nationId);

    // ==================== 自动天气 ====================

    /**
     * 设置是否启用自动天气
     *
     * @param nationId 国家ID
     * @param auto     是否自动
     * @return 是否成功设置
     */
    boolean setAutoWeather(NationId nationId, boolean auto);

    /**
     * 检查是否启用自动天气
     *
     * @param nationId 国家ID
     * @return 是否自动
     */
    boolean isAutoWeather(NationId nationId);

    // ==================== 资源效果 ====================

    /**
     * 获取天气对资源的影响修改器
     *
     * @param weather 天气类型
     * @return 资源修改器
     */
    WeatherResourceModifier getResourceModifier(WeatherType weather);

    /**
     * 获取天气对各类资源的影响倍数
     *
     * @param weather 天气类型
     * @return 资源类型到倍数的映射
     */
    Map<String, Double> getResourceModifiers(WeatherType weather);

    // ==================== 服务信息 ====================

    /**
     * 获取服务摘要
     *
     * @return 摘要信息
     */
    String summary();
}
