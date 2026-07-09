package dev.starcore.starcore.performance;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 异步任务管理器
 * 高性能异步处理系统
 */
public final class AsyncTaskManager {
    private static final Logger LOGGER = Logger.getLogger(AsyncTaskManager.class.getName());
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;

    // E-090: 使用 AtomicLong 保证多线程并发统计的可见性和原子性
    private final AtomicLong totalTasksExecuted = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);

    public AsyncTaskManager(int threadPoolSize) {
        // 创建线程池
        this.executorService = new ThreadPoolExecutor(
            threadPoolSize / 2,           // 核心线程数
            threadPoolSize,               // 最大线程数
            60L,                          // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 任务队列
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("STARCORE-Async-" + (++count));
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );

        // 创建调度线程池
        this.scheduledExecutor = Executors.newScheduledThreadPool(
            2,
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("STARCORE-Scheduler-" + (++count));
                    thread.setDaemon(true);
                    return thread;
                }
            }
        );
    }

    /**
     * 异步执行任务
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        totalTasksExecuted.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                handleException(e);
            }
        }, executorService);
    }

    /**
     * 异步执行任务（带返回值）
     * E-091: 异常时使用 completeExceptionally 而不是返回 null，让调用方感知失败
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        totalTasksExecuted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                handleException(e);
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * 延迟执行任务
     */
    public ScheduledFuture<?> runLater(Runnable task, long delay, TimeUnit unit) {
        totalTasksExecuted.incrementAndGet();
        return scheduledExecutor.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                handleException(e);
            }
        }, delay, unit);
    }

    /**
     * 定时重复执行任务
     */
    public ScheduledFuture<?> runRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                handleException(e);
            }
        }, initialDelay, period, unit);
    }

    /**
     * 批量执行任务
     */
    public CompletableFuture<Void> runBatch(Runnable... tasks) {
        CompletableFuture<?>[] futures = new CompletableFuture[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = runAsync(tasks[i]);
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * 异常处理
     */
    private void handleException(Exception e) {
        // 记录日志或其他处理
        LOGGER.log(Level.SEVERE, "Async task failed", e);
    }

    /**
     * 获取统计信息
     */
    public TaskStats getStats() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorService;
        return new TaskStats(
            totalTasksExecuted.get(),
            failedTasks.get(),
            executor.getActiveCount(),
            executor.getPoolSize(),
            executor.getQueue().size()
        );
    }

    /**
     * 关闭
     */
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 任务统计
     */
    public record TaskStats(
        long totalExecuted,
        long failed,
        int activeThreads,
        int poolSize,
        int queueSize
    ) {}
}
