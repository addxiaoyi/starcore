package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeProcess;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages asynchronous timed upgrade processes.
 * 管理异步定时升级进程
 */
public class UpgradeScheduler {
    private static final long CHECK_INTERVAL_TICKS = 20; // 每秒检查一次

    private final JavaPlugin plugin;
    private final StarCoreScheduler scheduler;
    private final Map<String, BukkitTask> scheduledTasks;
    private final Map<String, UpgradeProcess> activeProcesses;
    private final Consumer<BiConsumer<NationId, String>> onCompleteCallback;
    private BukkitTask checkTask;

    public UpgradeScheduler(
            JavaPlugin plugin,
            StarCoreScheduler scheduler,
            BiConsumer<NationId, String> onCompleteCallback) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.activeProcesses = new ConcurrentHashMap<>();
        this.onCompleteCallback = null; // 不再使用
    }

    /**
     * Start a new upgrade process.
     */
    public boolean startUpgrade(NationId nationId, String pathId, int currentExp, int targetExp) {
        String key = createKey(nationId, pathId);

        // 如果已有进行中的升级，不允许开始新的
        if (activeProcesses.containsKey(key)) {
            return false;
        }

        // 创建升级进程
        UpgradeProcess process = new UpgradeProcess(
            pathId,
            0, // 目标等级稍后设置
            currentExp,
            targetExp,
            Instant.now(),
            null,
            false
        );

        activeProcesses.put(key, process);

        // 调度定期检查
        BukkitTask task = scheduler.runSyncTimer(
            () -> checkProgress(nationId, pathId),
            CHECK_INTERVAL_TICKS,
            CHECK_INTERVAL_TICKS
        );

        scheduledTasks.put(key, task);

        plugin.getLogger().info("Started upgrade process for nation " + nationId + " on path " + pathId);
        return true;
    }

    /**
     * Add experience to an active upgrade.
     */
    public UpgradeProcess addExp(NationId nationId, String pathId, int exp) {
        String key = createKey(nationId, pathId);
        UpgradeProcess process = activeProcesses.get(key);

        if (process == null) {
            return null;
        }

        UpgradeProcess updated = process.addExp(exp);
        activeProcesses.put(key, updated);

        // 检查是否完成
        if (updated.isCompleted()) {
            completeUpgrade(nationId, pathId, updated);
        }

        return updated;
    }

    /**
     * Cancel an ongoing upgrade.
     */
    public boolean cancelUpgrade(NationId nationId, String pathId) {
        String key = createKey(nationId, pathId);
        BukkitTask task = scheduledTasks.remove(key);
        if (task != null) {
            task.cancel();
        }

        UpgradeProcess removed = activeProcesses.remove(key);
        return removed != null;
    }

    /**
     * Get current progress for a nation and path.
     */
    public UpgradeProcess getProgress(NationId nationId, String pathId) {
        return activeProcesses.get(createKey(nationId, pathId));
    }

    /**
     * Check if upgrade is in progress.
     */
    public boolean isUpgrading(NationId nationId, String pathId) {
        UpgradeProcess process = activeProcesses.get(createKey(nationId, pathId));
        return process != null && !process.isCompleted();
    }

    /**
     * Force complete an upgrade.
     */
    public boolean forceComplete(NationId nationId, String pathId) {
        String key = createKey(nationId, pathId);
        UpgradeProcess process = activeProcesses.get(key);

        if (process == null) {
            return false;
        }

        // 创建已完成的进程
        UpgradeProcess completed = new UpgradeProcess(
            process.pathId(),
            process.pathId().hashCode(), // 临时目标等级
            process.targetExp(),
            process.targetExp(),
            process.startedAt(),
            Instant.now(),
            true
        );

        activeProcesses.put(key, completed);
        completeUpgrade(nationId, pathId, completed);
        return true;
    }

    /**
     * Clear all upgrades for a nation.
     */
    public void clearNationUpgrades(NationId nationId) {
        // 找到所有属于该国家的升级并取消
        activeProcesses.keySet().stream()
            .filter(key -> key.startsWith(nationId.toString() + ":"))
            .forEach(key -> {
                BukkitTask task = scheduledTasks.remove(key);
                if (task != null) {
                    task.cancel();
                }
                activeProcesses.remove(key);
            });
    }

    /**
     * Shutdown scheduler and cancel all tasks.
     */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }

        scheduledTasks.values().forEach(BukkitTask::cancel);
        scheduledTasks.clear();
        activeProcesses.clear();
    }

    private void checkProgress(NationId nationId, String pathId) {
        // 这个方法在异步线程中执行
        // 实际的经验检查在主服务中进行
    }

    private void completeUpgrade(NationId nationId, String pathId, UpgradeProcess process) {
        String key = createKey(nationId, pathId);

        // 取消定期检查任务
        BukkitTask task = scheduledTasks.remove(key);
        if (task != null) {
            task.cancel();
        }

        // 从活跃进程中移除
        activeProcesses.remove(key);

        // 通知完成回调
        if (onCompleteCallback != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                onCompleteCallback.accept((nId, pId) -> {
                    notifyUpgradeComplete(nId, pId);
                });
            });
        }

        plugin.getLogger().info("Completed upgrade for nation " + nationId + " on path " + pathId);
    }

    private void notifyUpgradeComplete(NationId nationId, String pathId) {
        // 通知在线的国家成员
        // 这个需要在主服务中处理
    }

    private String createKey(NationId nationId, String pathId) {
        return nationId.toString() + ":" + pathId;
    }

    /**
     * Get all active processes.
     */
    public Map<String, UpgradeProcess> getAllActiveProcesses() {
        return Map.copyOf(activeProcesses);
    }

    /**
     * Get active process count.
     */
    public int getActiveCount() {
        return activeProcesses.size();
    }
}
