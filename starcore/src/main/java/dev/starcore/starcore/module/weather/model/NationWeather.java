package dev.starcore.starcore.module.weather.model;

import dev.starcore.starcore.module.nation.model.NationId;

/**
 * 国家天气数据模型
 * 记录一个国家的天气状态
 *
 * @param nationId 国家ID
 * @param weather 当前天气类型
 * @param startedAt 开始时间戳
 * @param duration 持续时间（tick）
 * @param expiresAt 过期时间戳
 */
public record NationWeather(
    NationId nationId,
    WeatherType weather,
    long startedAt,
    long duration,
    long expiresAt
) {
    /**
     * 创建新的国家天气记录
     *
     * @param nationId 国家ID
     * @param weather 天气类型
     * @param durationMinutes 持续时间（分钟）
     * @return 新的国家天气记录
     */
    public static NationWeather create(NationId nationId, WeatherType weather, long durationMinutes) {
        long now = System.currentTimeMillis();
        long durationTicks = durationMinutes * 60 * 20; // 转换为tick
        long expiresAt = durationTicks > 0 ? now + (durationTicks * 50) : 0;
        return new NationWeather(nationId, weather, now, durationTicks, expiresAt);
    }

    /**
     * 创建永久天气
     *
     * @param nationId 国家ID
     * @param weather 天气类型
     * @return 永久天气记录
     */
    public static NationWeather permanent(NationId nationId, WeatherType weather) {
        return new NationWeather(nationId, weather, System.currentTimeMillis(), 0, 0);
    }

    /**
     * 检查天气是否已过期
     *
     * @return 是否过期
     */
    public boolean isExpired() {
        if (expiresAt == 0) {
            return false; // 永久天气
        }
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * 获取剩余时间（分钟）
     *
     * @return 剩余分钟数
     */
    public long getRemainingMinutes() {
        if (expiresAt == 0) {
            return Long.MAX_VALUE; // 永久
        }
        long remaining = (expiresAt - System.currentTimeMillis()) / 60000;
        return Math.max(0, remaining);
    }

    /**
     * 检查是否即将过期（1分钟内）
     *
     * @return 是否即将过期
     */
    public boolean isExpiringSoon() {
        return getRemainingMinutes() <= 1 && getRemainingMinutes() > 0;
    }

    /**
     * 获取天气图标
     *
     * @return 天气图标
     */
    public String getIcon() {
        return weather != null ? weather.getIcon() : "☀️";
    }

    /**
     * 获取天气显示名称
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return weather != null ? weather.getDisplayName() : "晴朗";
    }

    /**
     * 更新天气
     *
     * @param newWeather 新天气
     * @param newDurationMinutes 新持续时间
     * @return 更新后的记录
     */
    public NationWeather withWeather(WeatherType newWeather, long newDurationMinutes) {
        return create(nationId, newWeather, newDurationMinutes);
    }

    /**
     * 清除天气（设为晴朗）
     *
     * @return 晴朗天气记录
     */
    public NationWeather cleared() {
        return permanent(nationId, WeatherType.CLEAR);
    }
}
