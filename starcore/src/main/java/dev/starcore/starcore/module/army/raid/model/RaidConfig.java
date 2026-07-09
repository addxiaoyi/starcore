package dev.starcore.starcore.module.army.raid.model;

import org.bukkit.Location;

import java.time.LocalTime;

/**
 * 突袭配置
 */
public record RaidConfig(
    boolean enabled,
    LocalTime windowStart,
    LocalTime windowEnd,
    int minSoldiers,
    int maxArmiesPerRaid,
    int maxRaidDistance,
    int nationCooldownHours,
    int targetProtectionHours,
    double baseCost,
    double costPerSoldier,
    double successRewardMultiplier,
    double failureLossRatio,
    double battleRadius,
    double defenderReinforcementRadius,
    int raidDurationSeconds,
    boolean notifyTarget,
    int warningSeconds,
    String requiredRank
) {
    /**
     * 默认配置
     */
    public static RaidConfig defaults() {
        return new RaidConfig(
            true,
            LocalTime.of(20, 0),     // 晚上 8 点
            LocalTime.of(6, 0),      // 凌晨 6 点
            50,
            3,
            500,
            12,
            6,
            1000.0,
            10.0,
            0.5,
            0.5,
            100.0,
            200.0,
            300,
            true,
            30,
            "officer"
        );
    }

    /**
     * 从配置节读取
     */
    public static RaidConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }

        // 解析时间窗口
        LocalTime startTime = parseTime(section.getString("raid-window.start", "20:00"));
        LocalTime endTime = parseTime(section.getString("raid-window.end", "06:00"));

        return new RaidConfig(
            section.getBoolean("enabled", true),
            startTime,
            endTime,
            section.getInt("requirements.min-soldiers", 50),
            section.getInt("requirements.max-armies-per-raid", 3),
            section.getInt("requirements.max-raid-distance", 500),
            section.getInt("cooldowns.nation-cooldown-hours", 12),
            section.getInt("cooldowns.target-protection-hours", 6),
            section.getDouble("economics.base-cost", 1000.0),
            section.getDouble("economics.cost-per-soldier", 10.0),
            section.getDouble("economics.success-reward-multiplier", 0.5),
            section.getDouble("economics.failure-loss-ratio", 0.5),
            section.getDouble("combat.battle-radius", 100.0),
            section.getDouble("combat.defender-reinforcement-radius", 200.0),
            section.getInt("combat.raid-duration-seconds", 300),
            section.getBoolean("alerts.notify-target", true),
            section.getInt("alerts.warning-seconds", 30),
            section.getString("permissions.required-rank", "officer")
        );
    }

    /**
     * 解析时间字符串
     */
    private static LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return LocalTime.of(20, 0);
        }
        String[] parts = timeStr.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return LocalTime.of(hour, minute);
    }

    /**
     * 检查当前是否在突袭时间窗口内
     */
    public boolean isWithinRaidWindow() {
        LocalTime now = LocalTime.now();
        if (windowStart().isAfter(windowEnd())) {
            // 跨午夜的情况（如 20:00 - 06:00）
            return now.isAfter(windowStart()) || now.isBefore(windowEnd());
        } else {
            // 同一天内（如 06:00 - 20:00）
            return now.isAfter(windowStart()) && now.isBefore(windowEnd());
        }
    }

    /**
     * 计算突袭成本
     */
    public double calculateCost(int soldiers) {
        return baseCost() + (costPerSoldier() * soldiers);
    }

    /**
     * 计算成功奖励
     */
    public double calculateSuccessReward(double enemyLosses) {
        return enemyLosses * successRewardMultiplier();
    }
}