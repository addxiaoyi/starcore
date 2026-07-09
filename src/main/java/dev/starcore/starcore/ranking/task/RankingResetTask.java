package dev.starcore.starcore.ranking.task;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.database.MySQLRankingDatabase;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 排行榜自动重置系统
 * 根据周期自动重置排行榜
 */
public class RankingResetTask {

    private final Plugin plugin;
    private final MySQLRankingDatabase database;

    // 启用的周期
    private final Set<RankPeriod> enabledPeriods = EnumSet.of(
        RankPeriod.DAILY,
        RankPeriod.WEEKLY,
        RankPeriod.MONTHLY
    );

    // 重置记录
    private final Map<RankPeriod, ResetRecord> resetRecords = new ConcurrentHashMap<>();

    public RankingResetTask(Plugin plugin, MySQLRankingDatabase database) {
        this.plugin = plugin;
        this.database = database;
        loadConfiguration();
    }

    /**
     * 从配置文件加载设置
     */
    public void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        if (config == null) {
            return;
        }

        // 清空现有设置
        enabledPeriods.clear();

        // 加载启用的周期
        List<String> enabledList = config.getStringList("ranking.reset.periods");
        if (enabledList.isEmpty()) {
            // 默认启用
            enabledPeriods.add(RankPeriod.DAILY);
            enabledPeriods.add(RankPeriod.WEEKLY);
            enabledPeriods.add(RankPeriod.MONTHLY);
        } else {
            for (String periodName : enabledList) {
                try {
                    RankPeriod period = RankPeriod.valueOf(periodName.toUpperCase());
                    if (period != RankPeriod.ALLTIME) {
                        enabledPeriods.add(period);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 忽略无效的周期名称
                }
            }
        }

        plugin.getLogger().info("排行榜重置周期已加载: " + enabledPeriods);
    }

    /**
     * 保存配置到文件
     */
    public void saveConfiguration() {
        FileConfiguration config = plugin.getConfig();
        if (config == null) {
            return;
        }

        List<String> enabledList = enabledPeriods.stream()
            .map(Enum::name)
            .toList();
        config.set("ranking.reset.periods", enabledList);
        plugin.saveConfig();
    }

    /**
     * 启动自动重置
     */
    public void startAutoReset() {
        // 每小时检查一次
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndReset();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 72000L); // 每小时

        plugin.getLogger().info("排行榜自动重置系统已启动");
    }

    /**
     * 检查并重置
     */
    public void checkAndReset() {
        LocalDateTime now = LocalDateTime.now();

        for (RankPeriod period : enabledPeriods) {
            if (shouldReset(period, now)) {
                resetPeriod(period);
            }
        }
    }

    /**
     * 检查是否应该重置
     */
    private boolean shouldReset(RankPeriod period, LocalDateTime now) {
        ResetRecord lastReset = resetRecords.get(period);

        return switch (period) {
            case HOURLY -> {
                // 每小时0分重置
                if (lastReset == null) {
                    yield now.getMinute() == 0;
                }
                yield now.getMinute() == 0 &&
                       ChronoUnit.HOURS.between(lastReset.time(), now) >= 1;
            }
            case DAILY -> {
                // 每天0点重置
                if (lastReset == null) {
                    yield now.getHour() == 0;
                }
                yield now.getHour() == 0 &&
                       ChronoUnit.DAYS.between(lastReset.time().toLocalDate(), now.toLocalDate()) >= 1;
            }
            case WEEKLY -> {
                // 每周一0点重置
                if (lastReset == null) {
                    yield now.getDayOfWeek() == DayOfWeek.MONDAY && now.getHour() == 0;
                }
                yield now.getDayOfWeek() == DayOfWeek.MONDAY &&
                       now.getHour() == 0 &&
                       ChronoUnit.WEEKS.between(lastReset.time(), now) >= 1;
            }
            case MONTHLY -> {
                // 每月1号0点重置
                if (lastReset == null) {
                    yield now.getDayOfMonth() == 1 && now.getHour() == 0;
                }
                yield now.getDayOfMonth() == 1 &&
                       now.getHour() == 0 &&
                       ChronoUnit.MONTHS.between(lastReset.time(), now) >= 1;
            }
            case YEARLY -> {
                // 每年1月1号重置
                if (lastReset == null) {
                    yield now.getMonth() == Month.JANUARY &&
                           now.getDayOfMonth() == 1 &&
                           now.getHour() == 0;
                }
                yield now.getMonth() == Month.JANUARY &&
                       now.getDayOfMonth() == 1 &&
                       now.getHour() == 0 &&
                       ChronoUnit.YEARS.between(lastReset.time(), now) >= 1;
            }
            case ALLTIME -> false; // 永不重置
        };
    }

    /**
     * 重置周期
     */
    public void resetPeriod(RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            plugin.getLogger().warning("不能重置ALLTIME排行榜");
            return;
        }

        plugin.getLogger().info("正在重置 " + period.getDisplayName() + " 排行榜...");

        try {
            // 执行数据库重置
            database.resetPeriod("kills", period);
            database.resetPeriod("deaths", period);
            database.resetPeriod("playtime", period);

            // 记录重置
            resetRecords.put(period, new ResetRecord(
                period,
                LocalDateTime.now(),
                true
            ));

            // 通知在线玩家
            broadcastReset(period);

            plugin.getLogger().info(period.getDisplayName() + " 排行榜重置成功");

        } catch (Exception e) {
            plugin.getLogger().severe("重置 " + period.getDisplayName() + " 排行榜失败: " + e.getMessage());

            resetRecords.put(period, new ResetRecord(
                period,
                LocalDateTime.now(),
                false
            ));
        }
    }

    /**
     * 广播重置消息
     */
    private void broadcastReset(RankPeriod period) {
        String message = String.format(
            "§6§l[排行榜] §e%s §7已重置！",
            period.getDisplayName()
        );

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(message);
        });
    }

    /**
     * 手动重置周期
     */
    public boolean manualReset(RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            return false;
        }

        resetPeriod(period);
        return true;
    }

    /**
     * 启用/禁用周期
     */
    public void setPeriodEnabled(RankPeriod period, boolean enabled) {
        if (period == RankPeriod.ALLTIME) {
            return;
        }

        if (enabled) {
            enabledPeriods.add(period);
        } else {
            enabledPeriods.remove(period);
        }
    }

    /**
     * 检查周期是否启用
     */
    public boolean isPeriodEnabled(RankPeriod period) {
        return enabledPeriods.contains(period);
    }

    /**
     * 获取重置记录
     */
    public ResetRecord getResetRecord(RankPeriod period) {
        return resetRecords.get(period);
    }

    /**
     * 获取下次重置时间
     */
    public LocalDateTime getNextResetTime(RankPeriod period) {
        LocalDateTime now = LocalDateTime.now();

        return switch (period) {
            case HOURLY -> now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
            case DAILY -> now.plusDays(1).truncatedTo(ChronoUnit.DAYS);
            case WEEKLY -> {
                LocalDateTime nextMonday = now.with(DayOfWeek.MONDAY);
                if (!nextMonday.isAfter(now)) {
                    nextMonday = nextMonday.plusWeeks(1);
                }
                yield nextMonday.truncatedTo(ChronoUnit.DAYS);
            }
            case MONTHLY -> {
                LocalDateTime firstDay = now.withDayOfMonth(1).plusMonths(1);
                yield firstDay.truncatedTo(ChronoUnit.DAYS);
            }
            case YEARLY -> {
                LocalDateTime firstDay = now.withMonth(1).withDayOfMonth(1).plusYears(1);
                yield firstDay.truncatedTo(ChronoUnit.DAYS);
            }
            case ALLTIME -> null; // 永不重置
        };
    }

    /**
     * 获取距离下次重置的时间
     */
    public Duration getTimeUntilReset(RankPeriod period) {
        LocalDateTime next = getNextResetTime(period);
        if (next == null) {
            return null;
        }

        return Duration.between(LocalDateTime.now(), next);
    }

    /**
     * 获取统计信息
     */
    public ResetStats getStats() {
        int totalResets = resetRecords.values().stream()
            .filter(ResetRecord::success)
            .mapToInt(r -> 1)
            .sum();

        int failedResets = resetRecords.values().stream()
            .filter(r -> !r.success())
            .mapToInt(r -> 1)
            .sum();

        return new ResetStats(
            enabledPeriods.size(),
            totalResets,
            failedResets
        );
    }

    // ==================== 数据类 ====================

    /**
     * 重置记录
     */
    public record ResetRecord(
        RankPeriod period,
        LocalDateTime time,
        boolean success
    ) {
        @Override
        public String toString() {
            return String.format(
                "ResetRecord[period=%s, time=%s, success=%b]",
                period.getDisplayName(), time, success
            );
        }
    }

    /**
     * 统计信息
     */
    public record ResetStats(
        int enabledPeriods,
        int totalResets,
        int failedResets
    ) {
        @Override
        public String toString() {
            return String.format(
                "ResetStats[enabled=%d, total=%d, failed=%d]",
                enabledPeriods, totalResets, failedResets
            );
        }
    }
}
