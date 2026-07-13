package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Manages asynchronous research operations with scheduled completion.
 * Supports persistence via saveState()/loadState() for server restart recovery.
 */
public final class ResearchScheduler {
    private final ConcurrentMap<NationId, Map<String, ResearchProgress>> activeResearch = new ConcurrentHashMap<>();
    private final TechnologyModule technologyModule;
    private final ResearchCompletionListener completionListener;
    private final JavaPlugin plugin;

    // Persistence support
    private ResearchStateStorage stateStorage;
    private boolean persistenceEnabled = false;

    /**
     * Callback interface for research completion events.
     */
    @FunctionalInterface
    public interface ResearchCompletionListener {
        void onResearchComplete(NationId nationId, String technologyKey, boolean success, String message);
    }

    /**
     * Creates a ResearchScheduler without persistence (for testing or temporary use).
     */
    public ResearchScheduler(TechnologyModule technologyModule, JavaPlugin plugin, ResearchCompletionListener listener) {
        this.technologyModule = technologyModule;
        this.plugin = plugin;
        this.completionListener = listener;
    }

    /**
     * Creates a ResearchScheduler with persistence support.
     */
    ResearchScheduler(TechnologyModule technologyModule, JavaPlugin plugin, ResearchCompletionListener listener,
                      ResearchStateStorage stateStorage) {
        this(technologyModule, plugin, listener);
        this.stateStorage = stateStorage;
        this.persistenceEnabled = stateStorage != null;
    }

    /**
     * Initializes persistence with the given context.
     * Called by TechnologyModule during enable().
     */
    void initializePersistence(StarCoreContext context) {
        if (context == null) return;
        this.stateStorage = new DatabaseAwareResearchStateStorage(
            "technology",
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        this.persistenceEnabled = true;
    }

    /**
     * Saves the current research state to persistent storage.
     * Call this periodically or when the server is shutting down.
     */
    public void saveState() {
        if (!persistenceEnabled || stateStorage == null) {
            return;
        }

        try {
            Properties props = ResearchStateCodec.toProperties(activeResearch);
            stateStorage.saveAsync(props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save research state: " + e.getMessage());
        }
    }

    /**
     * Loads research state from persistent storage and reschedules tasks.
     * Call this during module enable().
     */
    public void loadState() {
        if (!persistenceEnabled || stateStorage == null) {
            return;
        }

        try {
            Properties props = stateStorage.load();
            Map<NationId, Map<String, ResearchProgress>> loaded = ResearchStateCodec.fromProperties(props);

            // Reschedule each loaded research
            for (Map.Entry<NationId, Map<String, ResearchProgress>> nationEntry : loaded.entrySet()) {
                NationId nationId = nationEntry.getKey();
                Map<String, ResearchProgress> researchMap = nationEntry.getValue();

                for (Map.Entry<String, ResearchProgress> researchEntry : researchMap.entrySet()) {
                    String techKey = researchEntry.getKey();
                    ResearchProgress savedProgress = researchEntry.getValue();

                    // Check if already completed (remaining <= 0)
                    if (savedProgress.remainingTicks() <= 0) {
                        // Research should have completed, trigger completion
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            completeResearch(nationId, techKey);
                        });
                        continue;
                    }

                    // Check if estimated completion time has passed
                    Instant now = Instant.now();
                    if (now.isAfter(savedProgress.estimatedCompletion())) {
                        // Time has passed, complete immediately
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            completeResearch(nationId, techKey);
                        });
                        continue;
                    }

                    // Reschedule the research with remaining time
                    rescheduleResearch(nationId, techKey, savedProgress);
                }
            }

            plugin.getLogger().info("Loaded " + loaded.size() + " nations with active research");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load research state: " + e.getMessage());
        }
    }

    /**
     * Reschedules a research task with the given progress.
     */
    private void rescheduleResearch(NationId nationId, String techKey, ResearchProgress savedProgress) {
        int remainingTicks = savedProgress.remainingTicks();
        if (remainingTicks <= 0) {
            return;
        }

        // Schedule completion task
        BukkitTask completionTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> completeResearch(nationId, techKey),
            remainingTicks
        );

        // Create new progress with scheduled task
        ResearchProgress newProgress = new ResearchProgress(
            techKey,
            savedProgress.startTime(),
            savedProgress.estimatedCompletion(),
            savedProgress.totalTicks(),
            remainingTicks,
            completionTask
        );

        activeResearch.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>())
            .put(techKey, newProgress);
    }

    /**
     * Starts an asynchronous research operation for a nation.
     *
     * @param nationId The nation conducting the research
     * @param technologyKey The technology being researched
     * @param researchTimeSeconds The time in seconds for research completion
     * @param onTick Optional callback for progress updates (every second), receives (nationId, progress 0.0-1.0)
     * @return true if research started successfully, false if already researching this tech
     */
    public boolean startResearch(NationId nationId, String technologyKey, int researchTimeSeconds,
                                  Consumer<ResearchProgress> onTick) {
        // 设计决策：startResearch 不校验/扣除成本，调用方需先在 TechnologyValidator 校验并原子扣成本
        // 整合扣成本到 startResearch 是后续 API 重构工作
        String normalized = normalizeKey(technologyKey);

        // Check if already researching this technology
        Map<String, ResearchProgress> nationResearch = activeResearch.get(nationId);
        if (nationResearch != null && nationResearch.containsKey(normalized)) {
            return false;
        }

        // audit B-098: researchTimeSeconds * 20 在 int 范围内可溢出（int 上限 ~2.1e9，*20 后 ~1e8 即触发）
        //   将演算结果用 long 保存并夹紧到 Bukkit 接受的安全范围；超大值改为最早可达的 Long.MAX_VALUE/20 上限
        long totalTicksLong = (long) researchTimeSeconds * 20L;
        if (totalTicksLong < 0 || totalTicksLong > Integer.MAX_VALUE - 1) {
            plugin.getLogger().warning(
                "ResearchScheduler.startResearch researchTimeSeconds too large, capping: " + researchTimeSeconds
                + " (nation=" + nationId + ", tech=" + technologyKey + ")");
            totalTicksLong = Integer.MAX_VALUE - 1;
        }
        final int totalTicks = (int) totalTicksLong;
        Instant now = Instant.now();
        Instant completion = now.plusSeconds(researchTimeSeconds);

        // Create progress tracker
        int[] remainingTicks = {totalTicks};
        int[] tickCounter = {0};

        // 设计决策：progressTask 未存入 ResearchProgress，崩重启后无法恢复
        // forceComplete/cancel 后旧 progressTask 仍运行修改 remainingTicks[0]
        // 改造方向：在 ResearchProgress 增 progressiveTask 字段，或用调度器单 key 互斥
        BukkitTask progressTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                remainingTicks[0]--;
                tickCounter[0]++;
                // Only update progress every second (20 ticks), not every tick
                if (onTick != null && tickCounter[0] % 20 == 0) {
                    ResearchProgress progress = new ResearchProgress(
                        normalized, now, completion, totalTicks, remainingTicks[0], null
                    );
                    onTick.accept(progress);
                }
            },
            1L, // Initial delay: 1 tick
            1L  // Repeat every tick to decrement remainingTicks
        );

        // Schedule completion task
        BukkitTask completionTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> completeResearch(nationId, normalized),
            totalTicks
        );

        // Store the progress
        ResearchProgress progress = new ResearchProgress(
            normalized, now, completion, totalTicks, totalTicks, completionTask
        );

        activeResearch.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>())
            .put(normalized, progress);

        // Persist state after starting research
        saveState();

        return true;
    }

    /**
     * Cancels an ongoing research operation.
     *
     * @param nationId The nation conducting the research
     * @param technologyKey The technology being researched
     * @return true if research was cancelled, false if not found
     */
    public boolean cancelResearch(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        Map<String, ResearchProgress> nationResearch = activeResearch.get(nationId);

        if (nationResearch == null) {
            return false;
        }

        ResearchProgress progress = nationResearch.remove(normalized);
        if (progress == null) {
            return false;
        }

        // Cancel the scheduled tasks
        if (progress.scheduledTask() != null) {
            progress.scheduledTask().cancel();
        }

        // Persist state after cancelling research
        saveState();

        return true;
    }

    /**
     * Gets the current research progress for a nation's technology.
     *
     * @param nationId The nation conducting the research
     * @param technologyKey The technology being researched
     * @return The research progress, or null if not researching
     */
    public ResearchProgress getProgress(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        Map<String, ResearchProgress> nationResearch = activeResearch.get(nationId);
        return nationResearch != null ? nationResearch.get(normalized) : null;
    }

    /**
     * Checks if a nation is currently researching a specific technology.
     *
     * @param nationId The nation conducting the research
     * @param technologyKey The technology being researched
     * @return true if researching, false otherwise
     */
    public boolean isResearching(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        Map<String, ResearchProgress> nationResearch = activeResearch.get(nationId);
        return nationResearch != null && nationResearch.containsKey(normalized);
    }

    /**
     * Gets all ongoing research operations for a nation.
     *
     * @param nationId The nation
     * @return Map of technology keys to their research progress
     */
    public Map<String, ResearchProgress> getNationResearch(NationId nationId) {
        return Map.copyOf(activeResearch.getOrDefault(nationId, Map.of()));
    }

    /**
     * Gets all active research operations across all nations.
     *
     * @return Map of nation IDs to their research maps
     */
    public Map<NationId, Map<String, ResearchProgress>> getAllActiveResearch() {
        return Map.copyOf(activeResearch);
    }

    /**
     * Forces completion of a research operation (for admin commands).
     *
     * @param nationId The nation conducting the research
     * @param technologyKey The technology being researched
     * @return true if research was completed, false if not found
     */
    public boolean forceComplete(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        if (!isResearching(nationId, normalized)) {
            return false;
        }
        return completeResearch(nationId, normalized);
    }

    private boolean completeResearch(NationId nationId, String technologyKey) {
        Map<String, ResearchProgress> nationResearch = activeResearch.get(nationId);
        if (nationResearch == null) {
            return false;
        }

        ResearchProgress progress = nationResearch.remove(technologyKey);
        if (progress == null) {
            return false;
        }

        // Cancel any remaining scheduled tasks
        if (progress.scheduledTask() != null) {
            progress.scheduledTask().cancel();
        }

        // Unlock the technology
        boolean success = technologyModule.unlock(nationId, technologyKey);
        if (!success) {
            // audit B-102: unlock 失败时（如 definition 已被 admin 删除）玩家时间已投但科技未解锁，
            //   这里把 research 放回 activeResearch（保留进度与 estimatedCompletion），等待 admin
            //   修复 definition 后重启可再次触发完成；不调 saveState 避免覆盖错误的"已移除"状态。
            //   注意：BukkitTask 已 cancel 无法恢复，重启时由 loadState 重新调度完成。
            nationResearch.put(technologyKey, progress);
            if (completionListener != null) {
                completionListener.onResearchComplete(
                    nationId, technologyKey, false,
                    "Research completed but failed to unlock (definition missing): " + technologyKey);
            }
            return false;
        }

        // Notify completion
        if (completionListener != null) {
            String message = "Research completed: " + technologyKey;
            completionListener.onResearchComplete(nationId, technologyKey, success, message);
        }

        // Persist state after completing research
        saveState();

        return success;
    }

    /**
     * Removes all research data for a nation (e.g., when nation is deleted).
     *
     * @param nationId The nation
     */
    public void clearNationResearch(NationId nationId) {
        Map<String, ResearchProgress> nationResearch = activeResearch.remove(nationId);
        if (nationResearch != null) {
            // Cancel all pending tasks
            for (ResearchProgress progress : nationResearch.values()) {
                if (progress.scheduledTask() != null) {
                    progress.scheduledTask().cancel();
                }
            }
        }

        // Persist state after clearing research
        saveState();
    }

    /**
     * Flushes all pending state saves synchronously.
     * Call this during module disable() to ensure all data is saved.
     */
    public void flushState() {
        if (!persistenceEnabled || stateStorage == null) {
            return;
        }

        try {
            Properties props = ResearchStateCodec.toProperties(activeResearch);
            stateStorage.save(props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to flush research state: " + e.getMessage());
        }
    }

    private static String normalizeKey(String technologyKey) {
        if (technologyKey == null) {
            return "";
        }
        return technologyKey.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
