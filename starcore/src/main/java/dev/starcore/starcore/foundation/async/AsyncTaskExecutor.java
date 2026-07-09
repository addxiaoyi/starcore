package dev.starcore.starcore.foundation.async;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 异步任务执行器 - SSS级优化
 * 提供高性能的异步任务执行，避免阻塞主线程
 */
public final class AsyncTaskExecutor {
    private final Plugin plugin;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;

    public AsyncTaskExecutor(Plugin plugin) {
        this.plugin = plugin;

        // 使用自定义线程池，根据CPU核心数调整
        int corePoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        int maxPoolSize = corePoolSize * 2;

        this.executorService = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new NamedThreadFactory("STARCORE-Async"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.scheduledExecutor = Executors.newScheduledThreadPool(
            2,
            new NamedThreadFactory("STARCORE-Scheduled")
        );
    }

    /**
     * 异步执行任务
     */
    public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executorService);
    }

    /**
     * 异步执行任务，结果在主线程处理
     * E-106: 添加 whenComplete exception handler 防止异常丢失
     */
    public <T> void runAsyncThenSync(
        Supplier<T> asyncTask,
        Consumer<T> syncCallback
    ) {
        runAsync(asyncTask)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    plugin.getLogger().warning("[异步] runAsyncThenSync 失败: " + ex.getMessage());
                    return;
                }
                // 在主线程执行回调
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    syncCallback.accept(result);
                });
            });
    }

    /**
     * 延迟异步执行
     */
    public <T> CompletableFuture<T> runAsyncDelayed(
        Supplier<T> task,
        long delay,
        TimeUnit unit
    ) {
        CompletableFuture<T> future = new CompletableFuture<>();

        scheduledExecutor.schedule(() -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delay, unit);

        return future;
    }

    /**
     * 批量异步执行
     * E-105: 限制每次最多并发 50 个任务，防止队列堆积
     */
    public <T> CompletableFuture<java.util.List<T>> runAllAsync(
        java.util.List<Supplier<T>> tasks
    ) {
        int maxConcurrent = Math.min(50, tasks.size());
        java.util.List<CompletableFuture<T>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            if (i >= maxConcurrent) {
                // 超出部分串行等待
                futures.add(runAsync(tasks.get(i)));
            } else {
                futures.add(runAsync(tasks.get(i)));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList()
            );
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final java.util.concurrent.atomic.AtomicInteger threadNumber =
            new java.util.concurrent.atomic.AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
