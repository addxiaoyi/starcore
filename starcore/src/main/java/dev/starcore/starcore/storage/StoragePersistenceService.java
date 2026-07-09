package dev.starcore.starcore.storage;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 存储系统数据持久化服务
 * 负责仓库数据的保存和加载
 */
public class StoragePersistenceService {
    private static final String DATA_FILE_NAME = "warehouse_data.json";
    private static final String BACKUP_FILE_NAME = "warehouse_data.backup.json";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path dataDirectory;
    private final Path dataFile;
    private final Path backupFile;

    private final Gson gson;
    private final ExecutorService executor;
    private volatile boolean shutdownRequested = false;

    // 序列化适配器
    private final WarehouseAdapter warehouseAdapter = new WarehouseAdapter();
    private final StorageItemAdapter itemAdapter = new StorageItemAdapter();

    public StoragePersistenceService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.dataFile = dataDirectory.resolve(DATA_FILE_NAME);
        this.backupFile = dataDirectory.resolve(BACKUP_FILE_NAME);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "StoragePersistence");
            t.setDaemon(true);
            return t;
        });

        // 配置Gson，带TypeAdapter
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .registerTypeAdapter(StorageItem.class, new StorageItemAdapter())
                .registerTypeAdapter(Warehouse.class, new WarehouseAdapter())
                .create();

        // 确保数据目录存在
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.severe("Failed to create data directory: " + e.getMessage());
        }
    }

    /**
     * 异步保存所有数据
     */
    public CompletableFuture<Void> saveDataAsync(Map<UUID, Warehouse> warehouses,
                                                   Map<UUID, UUID> playerDefaultWarehouse,
                                                   Map<UUID, Map<UUID, RemoteAccessPermission>> permissions,
                                                   List<StorageLog> logs) {
        if (shutdownRequested || executor.isShutdown()) {
            // E-010: shutdown 后直接返回失败 future，不抛 RejectedExecutionException
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RejectedExecutionException("StoragePersistenceService executor is shut down"));
            return failed;
        }
        return CompletableFuture.runAsync(() -> {
            try {
                boolean ok = saveData(warehouses, playerDefaultWarehouse, permissions, logs);
                if (!ok) {
                    logger.severe("Storage persistence saveData returned false — data may not have been written");
                }
            } catch (Exception e) {
                logger.severe("Failed to save storage data: " + e.getMessage());
                logger.warning("Stack trace: " + e);
            }
        }, executor);
    }

    /**
     * 同步保存所有数据
     * @return true 保存成功；false 保存失败（IO 异常或 JSON 序列化失败），调用方应感知并记录
     */
    public boolean saveData(Map<UUID, Warehouse> warehouses,
                          Map<UUID, UUID> playerDefaultWarehouse,
                          Map<UUID, Map<UUID, RemoteAccessPermission>> permissions,
                          List<StorageLog> logs) {
        // E-001: 在异步线程构造 StorageData 前，先对传入的 ConcurrentHashMap 做防御性快照，
        // 避免 gson.toJson 遍历时其它线程并发修改触发 ConcurrentModificationException。
        // StorageData 的 record compact constructor 也会 new HashMap，但在异步线程上双重保险更稳。
        Map<UUID, Warehouse> warehousesSnapshot = snapshotMap(warehouses);
        Map<UUID, UUID> defaultSnapshot = snapshotMap(playerDefaultWarehouse);
        Map<UUID, Map<UUID, RemoteAccessPermission>> permsSnapshot = snapshotPermissions(permissions);
        List<StorageLog> logsSnapshot = logs == null ? new ArrayList<>() : new ArrayList<>(logs);

        StorageData data = new StorageData(
                warehousesSnapshot,
                defaultSnapshot,
                permsSnapshot,
                logsSnapshot,
                Instant.now()
        );

        String json;
        try {
            json = gson.toJson(data);
        } catch (JsonParseException e) {
            // E-002: gson.toJson 自身可能抛 JsonParseException，原本未捕获
            logger.severe("Failed to serialize storage data to JSON: " + e.getMessage());
            logger.warning("Stack trace: " + e);
            return false;
        }

        try {
            // 创建备份
            if (Files.exists(dataFile)) {
                Files.copy(dataFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 原子写入：先写临时文件再原子 move 替换，避免写入中途崩溃留下半写文件
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Storage data saved successfully (" + warehousesSnapshot.size() + " warehouses, " + logsSnapshot.size() + " logs)");
            return true;
        } catch (IOException e) {
            // E-002: 原 catch 仅 logger.severe/return,调用方无感知。改为返回 false 以便上层感知
            logger.severe("Failed to save storage data: " + e.getMessage());
            logger.warning("Stack trace: " + e);
            return false;
        } catch (UnsupportedOperationException e) {
            // ATOMIC_MOVE 在某些文件系统不支持,回退到非原子 move
            try {
                Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
                Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Storage data saved (non-atomic) (" + warehousesSnapshot.size() + " warehouses, " + logsSnapshot.size() + " logs)");
                return true;
            } catch (IOException ioe) {
                logger.severe("Failed to save storage data (fallback move): " + ioe.getMessage());
                return false;
            }
        }
    }

    /** 对外层 Map 做防御性快照，弱一致迭代;若被并发修改降低 CME 概率 */
    private <K, V> Map<K, V> snapshotMap(Map<K, V> source) {
        if (source == null) return new HashMap<>();
        // 先 new HashMap,迭代期间被并发修改最坏抛 CME,我们把单个 entry 逐个 put 以减少风险;
        // 为彻底消除 CME,改用途FU loop over new ArrayList<>(source.entrySet())
        Map<K, V> out = new HashMap<>(source.size());
        for (Map.Entry<K, V> e : new ArrayList<>(source.entrySet())) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private <K1, K2, V> Map<K1, Map<K2, V>> snapshotPermissions(Map<K1, Map<K2, V>> source) {
        if (source == null) return new HashMap<>();
        Map<K1, Map<K2, V>> out = new HashMap<>(source.size());
        for (Map.Entry<K1, Map<K2, V>> e : new ArrayList<>(source.entrySet())) {
            Map<K2, V> inner = e.getValue();
            out.put(e.getKey(), inner == null ? new HashMap<>() : snapshotMap(inner));
        }
        return out;
    }

    /**
     * 异步加载数据
     */
    public CompletableFuture<StorageData> loadDataAsync() {
        if (shutdownRequested || executor.isShutdown()) {
            CompletableFuture<StorageData> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RejectedExecutionException("StoragePersistenceService executor is shut down"));
            return failed;
        }
        return CompletableFuture.supplyAsync(this::loadData, executor);
    }

    /**
     * 同步加载数据
     */
    public StorageData loadData() {
        if (!Files.exists(dataFile)) {
            logger.info("No storage data file found, starting fresh");
            return null;
        }

        String json;
        try {
            json = Files.readString(dataFile);
        } catch (IOException e) {
            logger.severe("Failed to read storage data file: " + e.getMessage());
            logger.warning("Stack trace: " + e);
            return tryLoadBackup();
        }

        StorageData data;
        try {
            data = gson.fromJson(json, StorageData.class);
        } catch (JsonParseException e) {
            // E-003: 原本只 catch IOException 再尝试 backup,fromJson 抛 JsonSyntaxException 时
            // 直接未捕获导致服务启动失败,且 backup 未尝试。这里一并尝试 backup 路径
            logger.severe("Failed to parse storage data JSON: " + e.getMessage());
            logger.warning("Stack trace: " + e);
            return tryLoadBackup();
        }

        // E-004: gson.fromJson 返回 null 时（空文件或可解析但为 null）后续 data.warehouses().size() 直接 NPE
        if (data == null) {
            logger.warning("Storage data file parsed to null; treating as empty state");
            return null;
        }

        logger.info("Storage data loaded successfully (" +
                data.warehouses().size() + " warehouses, " +
                data.logs().size() + " logs)");

        return data;
    }

    /** 尝试加载备份文件，若不存在或解析失败返回 null */
    private StorageData tryLoadBackup() {
        if (!Files.exists(backupFile)) {
            return null;
        }
        logger.info("Attempting to load backup data...");
        try {
            String json = Files.readString(backupFile);
            StorageData data = gson.fromJson(json, StorageData.class);
            if (data == null) {
                logger.warning("Backup data parsed to null");
                return null;
            }
            logger.info("Backup data loaded successfully");
            return data;
        } catch (IOException | JsonParseException ex) {
            // E-003: 同时捕获 JsonParseException
            logger.severe("Failed to load backup data: " + ex.getMessage());
            logger.warning("Stack trace: " + ex);
            return null;
        }
    }

    /**
     * 取得持久化服务持有的单线程 executor，用于让异步仓库操作与保存任务串行化（E-007）。
     * 返回值仅供提交任务使用,不应直接 shutdown。
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * 关闭持久化服务
     */
    public void shutdown() {
        shutdownRequested = true;
        executor.shutdown();
        try {
            // E-009/E-010: 关服时等待异步 flush 完成
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("StoragePersistence executor did not terminate in 5s, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 存储数据结构
     */
    public record StorageData(
            Map<UUID, Warehouse> warehouses,
            Map<UUID, UUID> playerDefaultWarehouse,
            Map<UUID, Map<UUID, RemoteAccessPermission>> permissions,
            List<StorageLog> logs,
            Instant savedAt
    ) {
        public StorageData {
            // 确保非空
            warehouses = warehouses != null ? new HashMap<>(warehouses) : new HashMap<>();
            playerDefaultWarehouse = playerDefaultWarehouse != null ? new HashMap<>(playerDefaultWarehouse) : new HashMap<>();
            permissions = permissions != null ? new HashMap<>(permissions) : new HashMap<>();
            logs = logs != null ? new ArrayList<>(logs) : new ArrayList<>();
        }
    }
}
