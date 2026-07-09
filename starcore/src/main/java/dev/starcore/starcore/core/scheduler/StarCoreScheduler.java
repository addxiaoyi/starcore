package dev.starcore.starcore.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StarCoreScheduler {
    private final JavaPlugin plugin;
    private final ExecutorService asyncExecutor;
    private final ExecutorService ioExecutor;  // Separate executor for I/O operations
    private final AtomicInteger taskIdGenerator = new AtomicInteger(1);
    private volatile boolean shutdown = false;

    // Metrics
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong totalTaskDurationNanos = new AtomicLong(0);

    public StarCoreScheduler(JavaPlugin plugin, int asyncThreads) {
        this(plugin, asyncThreads, Math.max(2, asyncThreads / 2)); // Default I/O threads = half of async threads
    }

    public StarCoreScheduler(JavaPlugin plugin, int asyncThreads, int ioThreads) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.asyncExecutor = Executors.newFixedThreadPool(asyncThreads, new StarCoreThreadFactory("async"));
        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, new StarCoreThreadFactory("io"));
    }

    /**
     * Gets the number of completed tasks.
     */
    public long getCompletedTaskCount() {
        return completedTasks.get();
    }

    /**
     * Gets the number of failed tasks.
     */
    public long getFailedTaskCount() {
        return failedTasks.get();
    }

    /**
     * Gets the average task duration in milliseconds.
     */
    public double getAverageTaskDurationMs() {
        long completed = completedTasks.get();
        if (completed == 0) return 0.0;
        return (double) totalTaskDurationNanos.get() / completed / 1_000_000.0;
    }

    /**
     * 获取异步执行器
     * @return 异步任务的 ExecutorService
     */
    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    /**
     * 异步执行任务
     * @param task 要执行的任务
     * @return 任务 ID，可用于取消
     */
    public int runAsync(Runnable task) {
        if (shutdown) return -1;
        int taskId = taskIdGenerator.getAndIncrement();
        long startNanos = System.nanoTime();
        CompletableFuture.runAsync(task, asyncExecutor)
            .whenComplete((result, ex) -> {
                recordTaskCompletion(startNanos, ex == null);
            })
            .exceptionally(ex -> {
                plugin.getLogger().warning("Async task " + taskId + " failed: " + ex.getMessage());
                return null;
            });
        return taskId;
    }

    /**
     * 异步执行 I/O 密集型任务（数据库、文件操作等）
     * 使用独立的 I/O 线程池，避免阻塞计算任务
     * @param task 要执行的任务
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> runIoAsync(Supplier<T> task) {
        if (shutdown) return CompletableFuture.failedFuture(new RejectedExecutionException("Scheduler is shut down"));
        return CompletableFuture.supplyAsync(task, ioExecutor)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    plugin.getLogger().warning("IO task failed: " + ex.getMessage());
                }
            });
    }

    /**
     * 批量异步执行任务
     * @param tasks 要执行的任务列表
     * @param onAllComplete 所有任务完成后的回调
     */
    public void runAsyncBatch(List<Runnable> tasks, Runnable onAllComplete) {
        if (shutdown || tasks.isEmpty()) {
            if (onAllComplete != null) onAllComplete.run();
            return;
        }
        CompletableFuture<?>[] futures = tasks.stream()
            .map(task -> CompletableFuture.runAsync(task, asyncExecutor)
                .whenComplete((r, ex) -> recordTaskCompletion(System.nanoTime(), ex == null)))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).whenComplete((result, ex) -> {
            if (onAllComplete != null) {
                runSync(onAllComplete);
            }
        });
    }

    /**
     * 异步执行任务，带完成回调
     * @param task 要执行的任务
     * @param onComplete 完成回调
     * @return 任务 ID，可用于取消
     */
    public int runAsync(Runnable task, Runnable onComplete) {
        if (shutdown) return -1;
        int taskId = taskIdGenerator.getAndIncrement();
        long startNanos = System.nanoTime();
        CompletableFuture.runAsync(task, asyncExecutor)
            .whenComplete((result, ex) -> {
                recordTaskCompletion(startNanos, ex == null);
                if (ex != null) {
                    plugin.getLogger().warning("Async task " + taskId + " failed: " + ex.getMessage());
                } else if (onComplete != null) {
                    runSync(onComplete);
                }
            });
        return taskId;
    }

    public CompletableFuture<Void> runAsyncVoid(Runnable task) {
        if (shutdown) return CompletableFuture.failedFuture(new RejectedExecutionException("Scheduler is shut down"));
        long startNanos = System.nanoTime();
        return CompletableFuture.runAsync(task, asyncExecutor)
            .whenComplete((r, ex) -> recordTaskCompletion(startNanos, ex == null))
            .exceptionally(ex -> {
                plugin.getLogger().warning("Async task failed: " + ex.getMessage());
                return null;
            });
    }

    /**
     * 异步执行任务并返回结果
     * @param task 要执行的任务
     * @return CompletableFuture，任务 ID 存储在 exceptionally 中
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        if (shutdown) return CompletableFuture.failedFuture(new RejectedExecutionException("Scheduler is shut down"));
        long startNanos = System.nanoTime();
        return CompletableFuture.supplyAsync(task, asyncExecutor)
            .whenComplete((r, ex) -> recordTaskCompletion(startNanos, ex == null))
            .exceptionally(ex -> {
                plugin.getLogger().warning("Async supply task failed: " + ex.getMessage());
                throw new RuntimeException(ex);
            });
    }

    /**
     * Records task completion for metrics.
     */
    private void recordTaskCompletion(long startNanos, boolean success) {
        if (success) {
            completedTasks.incrementAndGet();
            totalTaskDurationNanos.addAndGet(System.nanoTime() - startNanos);
        } else {
            failedTasks.incrementAndGet();
        }
    }

    /**
     * 同步执行任务（主线程）
     * @param task 要执行的任务
     * @return BukkitTask，用于取消
     */
    public BukkitTask runSync(Runnable task) {
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * 同步执行任务（主线程）
     * @param task 要执行的任务
     * @param delayTicks 延迟 tick 数
     * @return BukkitTask，用于取消
     */
    public BukkitTask runSyncLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * 异步执行任务（主线程）
     * @param task 要执行的任务
     * @param periodTicks 执行间隔 tick 数
     * @return BukkitTask，用于取消
     */
    public BukkitTask runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    /**
     * 在下一个 tick 执行任务（异步）
     * @param task 要执行的任务
     * @return BukkitTask
     */
    public BukkitTask runNextTick(Runnable task) {
        return runSyncLater(task, 1L);
    }

    /**
     * 执行异步任务并在完成时调用回调（跨线程）
     * @param task 要执行的任务
     * @param onComplete 完成回调（异步线程中执行）
     * @return 任务 ID
     */
    public int runAsyncWithCallback(Runnable task, Consumer<Throwable> onComplete) {
        if (shutdown) return -1;
        int taskId = taskIdGenerator.getAndIncrement();
        long startNanos = System.nanoTime();
        CompletableFuture.runAsync(task, asyncExecutor)
            .whenComplete((result, ex) -> {
                recordTaskCompletion(startNanos, ex == null);
                if (onComplete != null) {
                    onComplete.accept(ex);
                }
            });
        return taskId;
    }

    /**
     * 关闭调度器，停止所有异步任务
     * 使用优雅关闭，等待正在执行的任务完成
     */
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;

        // 拒绝新任务
        asyncExecutor.shutdown();
        ioExecutor.shutdown();

        try {
            // 等待现有任务完成，最多等待30秒
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (!ioExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 立即关闭调度器，不等待任务完成
     */
    public void shutdownNow() {
        shutdown = true;
        asyncExecutor.shutdownNow();
        ioExecutor.shutdownNow();
    }

    private static final class StarCoreThreadFactory implements ThreadFactory {
        private final AtomicInteger id = new AtomicInteger();
        private final String prefix;

        StarCoreThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "starcore-" + prefix + "-" + id.incrementAndGet());
            thread.setDaemon(true);
            // Set thread priority for better responsiveness
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        }
    }
}
