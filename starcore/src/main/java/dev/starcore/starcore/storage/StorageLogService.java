package dev.starcore.starcore.storage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 存储日志服务
 * 管理仓库操作日志的记录、查询和清理
 */
public class StorageLogService {
    private final Map<UUID, List<StorageLog>> logs; // 仓库ID -> 日志列表
    private final int retentionDays;
    private final Logger logger;
    private ScheduledExecutorService cleanupExecutor;

    // E-011: 清理后回调，由 StorageService 注入，触发异步 saveData 持久化清理结果,
    // 避免日志数据完全在内存、stop 错乱顺序时清理结果不被记录
    private volatile Runnable onStructureChanged;

    // E-013: 每仓库已排序缓存,volatile 单读多写;kv 用 list 避免并发遍历 CME,invalidate 时再 sort
    private final Map<UUID, List<StorageLog>> sortedCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * @param retentionDays 日志保留天数
     * @param logger 日志记录器
     */
    public StorageLogService(int retentionDays, Logger logger) {
        this.logs = new ConcurrentHashMap<>();
        this.retentionDays = Math.max(1, retentionDays);
        this.logger = logger;
    }

    /** E-011: 注入结构变化回调（通常为 StorageService.saveDataAsync）；null 则无效 */
    public void setStructureChangedCallback(Runnable callback) {
        this.onStructureChanged = callback;
    }

    private void notifyStructureChanged() {
        Runnable cb = onStructureChanged;
        if (cb != null) {
            try {
                cb.run();
            } catch (Exception e) {
                logger.warning("Structure-changed callback threw: " + e.getMessage());
            }
        }
    }

    /** E-013: 失效某仓库已排序缓存,下次 getLogs 再 sort */
    private void invalidateCache(UUID warehouseId) {
        sortedCache.remove(warehouseId);
    }

    // ==================== 日志记录 ====================

    /**
     * 添加日志
     * @param log 日志记录
     */
    public void addLog(StorageLog log) {
        if (log == null) {
            return;
        }

        logs.computeIfAbsent(log.getWarehouseId(), k -> new CopyOnWriteArrayList<>())
                .add(log);
        // E-013: 新增日志使对应仓库的已排序缓存失效
        invalidateCache(log.getWarehouseId());
    }

    /**
     * 批量添加日志
     * @param logList 日志列表
     */
    public void addLogs(List<StorageLog> logList) {
        for (StorageLog log : logList) {
            addLog(log);
        }
    }

    // ==================== 日志查询 ====================

    /**
     * 获取指定仓库的所有日志
     * @param warehouseId 仓库ID
     * @return 日志列表（按时间倒序）
     */
    public List<StorageLog> getLogs(UUID warehouseId) {
        List<StorageLog> warehouseLogs = logs.get(warehouseId);
        if (warehouseLogs == null) {
            return Collections.emptyList();
        }

        // E-013: 优先用已排序缓存,避免每次调用都 O(n log n) 全量排序
        List<StorageLog> cached = sortedCache.get(warehouseId);
        if (cached != null) {
            return cached;
        }

        synchronized (warehouseLogs) {
            // 双重检查,可能在等锁期间已被其他线程填好
            List<StorageLog> cached2 = sortedCache.get(warehouseId);
            if (cached2 != null) {
                return cached2;
            }
            List<StorageLog> result = new ArrayList<>(warehouseLogs);
            result.sort(Comparator.comparing(StorageLog::getTimestamp).reversed());
            // 缓存为不可变副本,后续读取直接用,写时 invalidate
            List<StorageLog> unmodifiable = Collections.unmodifiableList(result);
            sortedCache.put(warehouseId, unmodifiable);
            return unmodifiable;
        }
    }

    /**
     * 使用过滤器查询日志
     * @param filter 查询过滤器
     * @return 过滤后的日志列表
     */
    public List<StorageLog> queryLogs(LogQueryFilter filter) {
        // 如果指定了仓库ID，只查询该仓库
        if (filter.getWarehouseId() != null) {
            List<StorageLog> warehouseLogs = getLogs(filter.getWarehouseId());
            return filter.filter(warehouseLogs);
        }

        // 查询所有仓库
        List<StorageLog> allLogs = new ArrayList<>();
        for (List<StorageLog> warehouseLogs : logs.values()) {
            synchronized (warehouseLogs) {
                allLogs.addAll(warehouseLogs);
            }
        }

        // 按时间倒序排序
        allLogs.sort(Comparator.comparing(StorageLog::getTimestamp).reversed());

        // 应用过滤器
        return filter.filter(allLogs);
    }

    /**
     * 获取最近的N条日志
     * @param warehouseId 仓库ID
     * @param limit 数量限制
     * @return 日志列表
     */
    public List<StorageLog> getRecentLogs(UUID warehouseId, int limit) {
        LogQueryFilter filter = LogQueryFilter.builder()
                .warehouseId(warehouseId)
                .limit(limit)
                .build();
        return queryLogs(filter);
    }

    /**
     * 获取玩家的操作日志
     * @param playerId 玩家ID
     * @param limit 数量限制
     * @return 日志列表
     */
    public List<StorageLog> getPlayerLogs(UUID playerId, int limit) {
        LogQueryFilter filter = LogQueryFilter.builder()
                .playerId(playerId)
                .limit(limit)
                .build();
        return queryLogs(filter);
    }

    /**
     * 获取最近N天的日志
     * @param warehouseId 仓库ID
     * @param days 天数
     * @return 日志列表
     */
    public List<StorageLog> getLogsByDays(UUID warehouseId, int days) {
        LogQueryFilter filter = LogQueryFilter.builder()
                .warehouseId(warehouseId)
                .lastDays(days)
                .limit(1000)
                .build();
        return queryLogs(filter);
    }

    /**
     * 获取远程访问日志
     * @param warehouseId 仓库ID
     * @param limit 数量限制
     * @return 日志列表
     */
    public List<StorageLog> getRemoteAccessLogs(UUID warehouseId, int limit) {
        LogQueryFilter filter = LogQueryFilter.builder()
                .warehouseId(warehouseId)
                .onlyRemote()
                .limit(limit)
                .build();
        return queryLogs(filter);
    }

    /**
     * 按操作类型查询日志
     * @param warehouseId 仓库ID
     * @param action 操作类型
     * @param limit 数量限制
     * @return 日志列表
     */
    public List<StorageLog> getLogsByAction(UUID warehouseId, StorageLog.LogAction action, int limit) {
        LogQueryFilter filter = LogQueryFilter.builder()
                .warehouseId(warehouseId)
                .action(action)
                .limit(limit)
                .build();
        return queryLogs(filter);
    }

    // ==================== 日志统计 ====================

    /**
     * 获取日志总数
     * @param warehouseId 仓库ID
     * @return 日志数量
     */
    public int getLogCount(UUID warehouseId) {
        List<StorageLog> warehouseLogs = logs.get(warehouseId);
        return warehouseLogs != null ? warehouseLogs.size() : 0;
    }

    /**
     * 获取所有日志总数
     * @return 总数量
     */
    public int getTotalLogCount() {
        return logs.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 获取指定时间范围内的日志数量
     * @param warehouseId 仓库ID
     * @param start 开始时间
     * @param end 结束时间
     * @return 数量
     *
     * E-014: 与 cleanupExpiredLogs 共用同一 synchronized(warehouseLogs) 锁,数据本身不会破坏;
     * 但 cleanup 使用 Instant.now() 计算 cutoff,getLogCountInRange 使用调用方传入的 start/end,
     * long-running 场景下 cutoff 边界数据可能被误删/漏删,记号在此供调用方知晓时间一致性风险。
     */
    public int getLogCountInRange(UUID warehouseId, Instant start, Instant end) {
        List<StorageLog> warehouseLogs = logs.get(warehouseId);
        if (warehouseLogs == null) {
            return 0;
        }

        synchronized (warehouseLogs) {
            return (int) warehouseLogs.stream()
                    .filter(log -> {
                        Instant time = log.getTimestamp();
                        return !time.isBefore(start) && !time.isAfter(end);
                    })
                    .count();
        }
    }

    /**
     * 统计操作类型分布
     * @param warehouseId 仓库ID
     * @return 操作类型到数量的映射
     */
    public Map<StorageLog.LogAction, Long> getActionStatistics(UUID warehouseId) {
        List<StorageLog> warehouseLogs = logs.get(warehouseId);
        if (warehouseLogs == null) {
            return Collections.emptyMap();
        }

        synchronized (warehouseLogs) {
            return warehouseLogs.stream()
                    .collect(Collectors.groupingBy(
                            StorageLog::getAction,
                            Collectors.counting()
                    ));
        }
    }

    // ==================== 日志清理 ====================

    /**
     * 清理过期日志
     * @return 清理的日志数量
     */
    public int cleanupExpiredLogs() {
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int totalRemoved = 0;

        for (Map.Entry<UUID, List<StorageLog>> entry : logs.entrySet()) {
            UUID warehouseId = entry.getKey();
            List<StorageLog> warehouseLogs = entry.getValue();
            synchronized (warehouseLogs) {
                int beforeSize = warehouseLogs.size();
                warehouseLogs.removeIf(log -> log.getTimestamp().isBefore(cutoffTime));
                int removed = beforeSize - warehouseLogs.size();
                totalRemoved += removed;
                if (removed > 0) {
                    // E-013: 清理使已排序缓存失效
                    invalidateCache(warehouseId);
                }
            }
        }

        if (totalRemoved > 0) {
            logger.info("Cleaned up " + totalRemoved + " expired logs (older than " + retentionDays + " days)");
            // E-011: 清理结果立即触发结构变化回调,让 StorageService 异步持久化,避免下次重启后清理记录丢失
            notifyStructureChanged();
        }

        return totalRemoved;
    }

    /**
     * 清理指定仓库的所有日志
     * @param warehouseId 仓库ID
     * @return 清理的日志数量
     */
    public int clearWarehouseLogs(UUID warehouseId) {
        List<StorageLog> warehouseLogs = logs.remove(warehouseId);
        if (warehouseLogs != null) {
            invalidateCache(warehouseId);
            notifyStructureChanged();
        }
        return warehouseLogs != null ? warehouseLogs.size() : 0;
    }

    /**
     * 清理所有日志
     */
    public void clearAllLogs() {
        int total = getTotalLogCount();
        logs.clear();
        sortedCache.clear();
        logger.info("Cleared all logs (" + total + " entries)");
    }

    // ==================== 定时清理任务 ====================

    /**
     * 启动定时清理任务
     */
    public void startCleanupTask() {
        if (cleanupExecutor != null) {
            return;
        }

        cleanupExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "StorageLog-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        // 每天凌晨3点执行清理
        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toSeconds(1);

        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredLogs,
                initialDelay,
                period,
                TimeUnit.SECONDS
        );

        logger.info("Storage log cleanup task started (retention: " + retentionDays + " days)");
    }

    /**
     * 停止定时清理任务
     */
    public void stopCleanupTask() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cleanupExecutor = null;
            logger.info("Storage log cleanup task stopped");
        }
    }

    /**
     * 计算到下一个凌晨3点（本地时区）的延迟（秒）
     * E-012: 原实现用 Instant.truncatedTo(DAYS) 是 UTC 起点,国内服务器会把"凌晨3点"变成 UTC 11:00
     * (即北京时间 19:00)执行。改用 ZonedDateTime + 系统默认时区计算。
     */
    private long calculateInitialDelay() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime tomorrow3AM = now.truncatedTo(ChronoUnit.DAYS)
                .plus(1, ChronoUnit.DAYS)
                .plus(3, ChronoUnit.HOURS);

        if (now.isAfter(tomorrow3AM)) {
            tomorrow3AM = tomorrow3AM.plus(1, ChronoUnit.DAYS);
        }

        return ChronoUnit.SECONDS.between(now, tomorrow3AM);
    }

    // ==================== 日志导出 ====================

    /**
     * 获取所有日志（用于持久化）
     * @return 所有日志列表
     */
    public List<StorageLog> getAllLogs() {
        List<StorageLog> allLogs = new ArrayList<>();
        for (List<StorageLog> warehouseLogs : logs.values()) {
            synchronized (warehouseLogs) {
                allLogs.addAll(warehouseLogs);
            }
        }
        return allLogs;
    }

    /**
     * 导出日志为文本格式
     * @param warehouseId 仓库ID
     * @return 格式化的日志文本
     */
    public String exportLogs(UUID warehouseId) {
        List<StorageLog> warehouseLogs = getLogs(warehouseId);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 仓库日志导出 ===\n");
        sb.append("仓库ID: ").append(warehouseId).append("\n");
        sb.append("导出时间: ").append(Instant.now()).append("\n");
        sb.append("日志数量: ").append(warehouseLogs.size()).append("\n");
        sb.append("========================\n\n");

        for (StorageLog log : warehouseLogs) {
            sb.append("[").append(log.getTimestamp()).append("] ");
            sb.append(log.getFormattedDescription());
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取日志摘要
     * @param warehouseId 仓库ID
     * @param days 统计最近N天
     * @return 摘要信息
     */
    public String getLogSummary(UUID warehouseId, int days) {
        Instant startTime = Instant.now().minus(days, ChronoUnit.DAYS);
        List<StorageLog> recentLogs = getLogsByDays(warehouseId, days);

        Map<StorageLog.LogAction, Long> actionStats = recentLogs.stream()
                .collect(Collectors.groupingBy(
                        StorageLog::getAction,
                        Collectors.counting()
                ));

        long remoteAccessCount = recentLogs.stream()
                .filter(StorageLog::isRemoteAccess)
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("=== 最近 ").append(days).append(" 天日志摘要 ===\n");
        sb.append("总操作数: ").append(recentLogs.size()).append("\n");
        sb.append("远程访问: ").append(remoteAccessCount).append("\n");
        sb.append("\n操作分布:\n");

        for (Map.Entry<StorageLog.LogAction, Long> entry : actionStats.entrySet()) {
            sb.append("  ").append(entry.getKey().getDisplayName())
                    .append(": ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }
}
