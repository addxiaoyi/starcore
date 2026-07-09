package dev.starcore.starcore.core.persistence;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class PersistenceService {
    private final JavaPlugin plugin;
    private final StarCoreScheduler scheduler;
    private final ConcurrentMap<String, CompletableFuture<Void>> pendingAsyncSaves = new ConcurrentHashMap<>();

    // E-053: 用固定 stripes 数量的锁避免 fileLocks 无限增长;按 saveKey hash 取模 分配一把锁
    private static final int LOCK_STRIPES = 64;
    private final Object[] fileLockStripes;
    {
        fileLockStripes = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) fileLockStripes[i] = new Object();
    }

    private Path dataDirectory;

    public PersistenceService(JavaPlugin plugin, StarCoreScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void start() {
        this.dataDirectory = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create STARCORE data directory", exception);
        }
    }

    public void stop() {
        drainAsyncSaves();
    }

    public CompletableFuture<Void> ensureNamespace(String namespace) {
        return scheduler.supplyAsync(() -> {
            try {
                Files.createDirectories(namespacePath(namespace));
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to create persistence namespace: " + namespace, exception);
                throw new IllegalStateException(exception);
            }
            return null;
        });
    }

    public Path namespacePath(String namespace) throws IOException {
        Objects.requireNonNull(namespace, "namespace");
        if (dataDirectory == null) {
            throw new IllegalStateException("Persistence service has not started");
        }
        Path path = dataDirectory.resolve(namespace).normalize();
        Files.createDirectories(path);
        return path;
    }

    public Properties loadProperties(String namespace, String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        Properties properties = new Properties();
        try {
            Path file = namespacePath(namespace).resolve(fileName).normalize();
            if (Files.notExists(file)) {
                return properties;
            }
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
            return properties;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to load persistence file: " + namespace + "/" + fileName, exception);
            throw new IllegalStateException(exception);
        }
    }

    /**
     * E-054: saveProperties 同步等待 awaitPendingAsyncSave 然后写文件,在主线程上的同步 IO
     * 会阻塞主线程。建议改用异步接口 savePropertiesAsync;若调用方仍需同步,可在主线程外执行。
     * 这里在方法注释标明,调用方应优先用 savePropertiesAsync。
     */
    public void saveProperties(String namespace, String fileName, Properties properties) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(properties, "properties");
        String key = saveKey(namespace, fileName);
        awaitPendingAsyncSave(key);
        writeProperties(key, namespace, fileName, properties);
    }

    public CompletableFuture<Void> savePropertiesAsync(String namespace, String fileName, Properties properties) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(properties, "properties");
        String key = saveKey(namespace, fileName);
        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        CompletableFuture<Void> queuedSave = pendingAsyncSaves.compute(key, (ignored, previous) -> {
            CompletableFuture<Void> previousSave = previous == null
                ? CompletableFuture.completedFuture(null)
                // E-051: 原 previous.thenCompose(v -> ...) 在 previous 失败时不会调用 function,
                // 导致最新数据丢失。改用 exceptionally 吞掉旧 future 的异常并继续执行新 save,
                // 保证 writeProperties 始终被调用。
                : previous.exceptionally(_err -> null);
            return previousSave.thenCompose(v -> scheduler.runAsyncVoid(() -> writeProperties(key, namespace, fileName, snapshot)));
        });
        queuedSave.whenComplete((ignored, exception) -> pendingAsyncSaves.remove(key, queuedSave));
        return queuedSave;
    }

    private Object fileLock(String key) {
        return fileLockStripes[Math.floorMod(key.hashCode(), LOCK_STRIPES)];
    }

    private void writeProperties(String key, String namespace, String fileName, Properties properties) {
        try {
            synchronized (fileLock(key)) {
                Path file = namespacePath(namespace).resolve(fileName).normalize();
                try (OutputStream output = Files.newOutputStream(file)) {
                    properties.store(output, "STARCORE " + namespace + " state");
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save persistence file: " + namespace + "/" + fileName, exception);
            throw new IllegalStateException(exception);
        }
    }

    private void drainAsyncSaves() {
        // E-052: 给每个批次等待一个超时,避免 IO 卡死时主线程在 stop() 全服务器关闭超时
        // 原代码 CompletableFuture.allOf(...).join() 无超时
        long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (true) {
            List<CompletableFuture<Void>> pending = new ArrayList<>(pendingAsyncSaves.values());
            if (pending.isEmpty()) {
                return;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                plugin.getLogger().log(Level.SEVERE,
                    "Persistence drainAsyncSaves 超时 10s,仍有 " + pending.size() + " 个未完成保存",
                    new java.util.concurrent.TimeoutException());
                return;
            }
            try {
                await(CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                            .orTimeout(remainingNanos, java.util.concurrent.TimeUnit.NANOSECONDS),
                        "Unable to finish queued async persistence saves");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "drainAsyncSaves await 异常: " + ex.getMessage(), ex);
                return;
            }
            pendingAsyncSaves.entrySet().removeIf(entry -> entry.getValue().isDone());
        }
    }

    private void awaitPendingAsyncSave(String key) {
        CompletableFuture<Void> pending = pendingAsyncSaves.get(key);
        if (pending != null) {
            await(pending, "Unable to finish queued async persistence save: " + key);
        }
    }

    private void await(CompletableFuture<Void> future, String message) {
        try {
            future.join();
        } catch (CompletionException exception) {
            plugin.getLogger().log(Level.SEVERE, message, exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, message, exception);
        }
    }

    private static String saveKey(String namespace, String fileName) {
        return namespace + '/' + fileName;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }
}
