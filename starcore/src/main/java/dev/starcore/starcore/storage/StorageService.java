package dev.starcore.starcore.storage;
import java.util.Optional;

import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * 仓库服务核心
 * 提供仓库的创建、管理和访问功能
 */
public class StorageService {
    private final Logger logger;
    private final Map<UUID, Warehouse> warehouses;
    private final Map<UUID, UUID> playerDefaultWarehouse; // 玩家ID -> 默认仓库ID
    private final Map<UUID, Map<UUID, RemoteAccessPermission>> permissions; // 仓库ID -> (玩家ID -> 权限)
    private final RemoteAccessService remoteAccessService;
    private final WarehouseUpgradeService upgradeService;
    private final StorageLogService logService;
    // E-008: config 改为 volatile 非 final,允许 reloadConfig 热更新引用
    private volatile StorageConfig config;
    private final InternalEconomyService economyService;
    private final StoragePersistenceService persistenceService;
    private final JavaPlugin plugin;

    // E-006: loadData 期间持有的写锁,与 saveDataAsync 异步任务互斥,避免 clear+putAll 之间读到不一致视图
    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();
    // E-005: 周期保存任务句柄
    private org.bukkit.scheduler.BukkitTask periodicSaveTask;

    /**
     * 构造函数
     * @param logger 日志记录器
     * @param config 配置
     * @param economyService 经济服务
     * @param plugin 插件实例
     */
    public StorageService(Logger logger, StorageConfig config, InternalEconomyService economyService, JavaPlugin plugin) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.warehouses = new ConcurrentHashMap<>();
        this.playerDefaultWarehouse = new ConcurrentHashMap<>();
        this.permissions = new ConcurrentHashMap<>();
        this.persistenceService = new StoragePersistenceService(plugin);
        this.remoteAccessService = new RemoteAccessService(this, config, logger, economyService);
        this.upgradeService = new WarehouseUpgradeService(this, config, logger, economyService);
        this.logService = new StorageLogService(config.getLogRetentionDays(), logger);
        // E-011: 把日志结构变化时触发异步持久化,避免清理结果丢失
        this.logService.setStructureChangedCallback(this::saveDataAsync);
    }

    // ==================== 仓库管理 ====================

    /**
     * 创建新仓库
     * @param type 仓库类型
     * @param ownerId 所有者ID
     * @param name 仓库名称
     * @return 新创建的仓库
     */
    public Warehouse createWarehouse(WarehouseType type, UUID ownerId, String name) {
        Warehouse warehouse = new Warehouse(type, ownerId, name);
        warehouses.put(warehouse.getWarehouseId(), warehouse);

        // 如果是个人仓库且玩家还没有默认仓库，设置为默认
        if (type.isPersonalOwned() && !playerDefaultWarehouse.containsKey(ownerId)) {
            playerDefaultWarehouse.put(ownerId, warehouse.getWarehouseId());
        }

        // 设置所有者权限
        setPermission(warehouse.getWarehouseId(), ownerId, RemoteAccessPermission.OWNER);

        logger.info("Created warehouse: " + warehouse);
        return warehouse;
    }

    /**
     * 获取仓库
     * @param warehouseId 仓库ID
     * @return 仓库，如果不存在则返回Optional.empty()
     */
    public Optional<Warehouse> getWarehouse(UUID warehouseId) {
        return Optional.ofNullable(warehouses.get(warehouseId));
    }

    /**
     * 删除仓库
     * @param warehouseId 仓库ID
     * @return true如果删除成功
     */
    public boolean deleteWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouses.remove(warehouseId);
        if (warehouse == null) {
            return false;
        }

        // 清理权限
        permissions.remove(warehouseId);

        // 清理默认仓库引用
        playerDefaultWarehouse.values().removeIf(id -> id.equals(warehouseId));

        logger.info("Deleted warehouse: " + warehouseId);
        return true;
    }

    /**
     * 获取玩家的默认仓库
     * @param playerId 玩家ID
     * @return 默认仓库，如果不存在则创建一个
     */
    public Warehouse getOrCreatePlayerWarehouse(UUID playerId) {
        UUID warehouseId = playerDefaultWarehouse.get(playerId);
        if (warehouseId != null) {
            Warehouse warehouse = warehouses.get(warehouseId);
            if (warehouse != null) {
                return warehouse;
            }
        }

        // 创建新的个人仓库
        return createWarehouse(WarehouseType.PERSONAL, playerId, "个人仓库");
    }

    /**
     * 获取玩家拥有的所有仓库
     * @param playerId 玩家ID
     * @return 仓库列表
     */
    public List<Warehouse> getPlayerWarehouses(UUID playerId) {
        List<Warehouse> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses.values()) {
            if (playerId.equals(warehouse.getOwnerId())) {
                result.add(warehouse);
            }
        }
        return result;
    }

    /**
     * 获取玩家可访问的所有仓库
     * @param playerId 玩家ID
     * @return 仓库列表
     */
    public List<Warehouse> getAccessibleWarehouses(UUID playerId) {
        List<Warehouse> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses.values()) {
            RemoteAccessPermission permission = getPermission(warehouse.getWarehouseId(), playerId);
            if (permission.canView()) {
                result.add(warehouse);
            }
        }
        return result;
    }

    // ==================== 权限管理 ====================

    /**
     * 设置权限
     * @param warehouseId 仓库ID
     * @param playerId 玩家ID
     * @param permission 权限
     */
    public void setPermission(UUID warehouseId, UUID playerId, RemoteAccessPermission permission) {
        permissions.computeIfAbsent(warehouseId, k -> new ConcurrentHashMap<>())
                .put(playerId, permission);
    }

    /**
     * 获取权限
     * @param warehouseId 仓库ID
     * @param playerId 玩家ID
     * @return 权限级别
     */
    public RemoteAccessPermission getPermission(UUID warehouseId, UUID playerId) {
        Map<UUID, RemoteAccessPermission> warehousePerms = permissions.get(warehouseId);
        if (warehousePerms == null) {
            return RemoteAccessPermission.NONE;
        }
        return warehousePerms.getOrDefault(playerId, RemoteAccessPermission.NONE);
    }

    /**
     * 移除权限
     * @param warehouseId 仓库ID
     * @param playerId 玩家ID
     */
    public void removePermission(UUID warehouseId, UUID playerId) {
        Map<UUID, RemoteAccessPermission> warehousePerms = permissions.get(warehouseId);
        if (warehousePerms != null) {
            warehousePerms.remove(playerId);
        }
    }

    /**
     * 检查玩家是否可以访问仓库
     * @param warehouseId 仓库ID
     * @param playerId 玩家ID
     * @param requiredPermission 所需权限级别
     * @return true如果有权限
     */
    public boolean hasPermission(UUID warehouseId, UUID playerId, RemoteAccessPermission requiredPermission) {
        RemoteAccessPermission playerPermission = getPermission(warehouseId, playerId);
        return playerPermission.isAtLeast(requiredPermission);
    }

    /**
     * 获取仓库的所有权限列表
     * @param warehouseId 仓库ID
     * @return 权限映射（玩家ID -> 权限）
     */
    public Map<UUID, RemoteAccessPermission> getWarehousePermissions(UUID warehouseId) {
        Map<UUID, RemoteAccessPermission> warehousePerms = permissions.get(warehouseId);
        if (warehousePerms == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(warehousePerms);
    }

    // ==================== 服务访问 ====================

    /**
     * 获取远程访问服务
     * @return 远程访问服务
     */
    public RemoteAccessService getRemoteAccessService() {
        return remoteAccessService;
    }

    /**
     * 获取升级服务
     * @return 升级服务
     */
    public WarehouseUpgradeService getUpgradeService() {
        return upgradeService;
    }

    /**
     * 获取日志服务
     * @return 日志服务
     */
    public StorageLogService getLogService() {
        return logService;
    }

    /**
     * 获取配置
     * @return 配置对象
     */
    public StorageConfig getConfig() {
        return config;
    }

    /**
     * E-008: 热更新传入新 StorageConfig，并向下级服务（RemoteAccess/Upgrade）下发，避免 reloadConfig
     * 仅替换 loader 中对象而服务仍持有旧引用导致配置不生效。
     */
    public void setConfig(StorageConfig newConfig) {
        if (newConfig == null) return;
        this.config = newConfig;
        this.remoteAccessService.setConfig(newConfig);
        this.upgradeService.setConfig(newConfig);
    }

    /**
     * 获取持久化服务
     * @return 持久化服务
     */
    public StoragePersistenceService getPersistenceService() {
        return persistenceService;
    }

    /**
     * E-018: 暴露 plugin 引用，供子服务（如 WarehouseUpgradeService）调用主线程调度器使用
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    // ==================== 异步操作 ====================

    /**
     * 异步创建仓库
     * @param type 仓库类型
     * @param ownerId 所有者ID
     * @param name 名称
     * @return CompletableFuture包装的仓库
     */
    public CompletableFuture<Warehouse> createWarehouseAsync(WarehouseType type, UUID ownerId, String name) {
        // E-007: 显式传入 persistenceService 的 executor,与其它异步保存任务串行化,避免默认
        // ForkJoinPool.commonPool 与主线程读写共享 Map 并发
        return CompletableFuture.supplyAsync(() -> createWarehouse(type, ownerId, name), persistenceServiceExecutor());
    }

    /**
     * 异步获取仓库
     * @param warehouseId 仓库ID
     * @return CompletableFuture包装的Optional
     */
    public CompletableFuture<Optional<Warehouse>> getWarehouseAsync(UUID warehouseId) {
        return CompletableFuture.supplyAsync(() -> getWarehouse(warehouseId), persistenceServiceExecutor());
    }

    /** 取得 persistenceService 持有的单线程 executor,让异步操作与其保存任务串行化;
     *  若 executor 不可见则回退到 ForkJoinPool.commonPool。 */
    private java.util.concurrent.Executor persistenceServiceExecutor() {
        java.util.concurrent.ExecutorService exec = persistenceService.getExecutor();
        if (exec == null || exec.isShutdown()) {
            return java.util.concurrent.ForkJoinPool.commonPool();
        }
        return exec;
    }

    // ==================== 统计信息 ====================

    /**
     * 获取仓库总数
     * @return 仓库数量
     */
    public int getTotalWarehouses() {
        return warehouses.size();
    }

    /**
     * 获取指定类型的仓库数量
     * @param type 仓库类型
     * @return 数量
     */
    public int getWarehouseCount(WarehouseType type) {
        return (int) warehouses.values().stream()
                .filter(w -> w.getType() == type)
                .count();
    }

    /**
     * 获取玩家的仓库数量
     * @param playerId 玩家ID
     * @return 数量
     */
    public int getPlayerWarehouseCount(UUID playerId) {
        return (int) warehouses.values().stream()
                .filter(w -> playerId.equals(w.getOwnerId()))
                .count();
    }

    // ==================== 生命周期 ====================

    /**
     * 启动服务并加载数据
     */
    public void start() {
        // 加载持久化数据
        loadData();

        logger.info("StorageService started with " + warehouses.size() + " warehouses");
        logService.startCleanupTask();

        // E-005: 注册周期保存任务（默认每 5 分钟保存一次），避免崩溃/kill -9 时自上次保存以来的全部仓库操作丢失
        long periodTicks = 5L * 60L * 20L; // 5 分钟
        periodicSaveTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDataAsync,
                periodTicks, periodTicks);

        // E-019: 启动时恢复持久化的 activeUpgrades
        upgradeService.start();
    }

    /**
     * 停止服务并保存数据
     */
    public void stop() {
        // E-005/E-009: 取消周期保存任务,避免关服期间再排队
        if (periodicSaveTask != null) {
            try {
                periodicSaveTask.cancel();
            } catch (IllegalStateException ignored) {
                // already scheduled/cancelled — ignore
            }
            periodicSaveTask = null;
        }

        // 保存数据
        saveData();

        logService.stopCleanupTask();

        // E-019: 关服时保存 activeUpgrades,让下次启动恢复
        upgradeService.stop();

        // E-009: 关服时等待持久化 executor 的异步 flush 完成
        persistenceService.shutdown();

        logger.info("StorageService stopped");
    }

    // ==================== 数据持久化 ====================

    /**
     * 加载数据
     */
    public void loadData() {
        StoragePersistenceService.StorageData data = persistenceService.loadData();
        if (data == null) {
            logger.info("No persisted data to load, starting fresh");
            return;
        }

        // E-006: 加载期间持有写锁,与 saveDataAsync 异步任务互斥,避免异步保存线程在 clear 之后
        // putAll 之前读到不一致视图造成部分写入丢失
        dataLock.writeLock().lock();
        try {
            // 恢复仓库数据
            warehouses.clear();
            warehouses.putAll(data.warehouses());

            // 恢复默认仓库映射
            playerDefaultWarehouse.clear();
            playerDefaultWarehouse.putAll(data.playerDefaultWarehouse());

            // 恢复权限数据
            permissions.clear();
            permissions.putAll(data.permissions());
        } finally {
            dataLock.writeLock().unlock();
        }

        // 恢复日志
        logService.clearAllLogs();
        for (StorageLog log : data.logs()) {
            logService.addLog(log);
        }

        logger.info("Loaded " + warehouses.size() + " warehouses and " + data.logs().size() + " logs from storage");
    }

    /**
     * 保存数据
     */
    public void saveData() {
        // E-006: 与 loadData 互斥,确保保存时看到的是一致视图
        dataLock.readLock().lock();
        try {
            boolean ok = persistenceService.saveData(
                    warehouses,
                    playerDefaultWarehouse,
                    permissions,
                    logService.getAllLogs()
            );
            if (!ok) {
                logger.severe("StorageService.saveData: persistence save reported failure; data may not have been persisted");
            }
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * 异步保存数据
     */
    public CompletableFuture<Void> saveDataAsync() {
        // E-006: 持读锁快照引用,持久化服务内部再做快照拷贝
        dataLock.readLock().lock();
        try {
            return persistenceService.saveDataAsync(
                    warehouses,
                    playerDefaultWarehouse,
                    permissions,
                    logService.getAllLogs()
            );
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * 清理所有数据（用于测试）
     */
    public void clear() {
        warehouses.clear();
        playerDefaultWarehouse.clear();
        permissions.clear();
        logService.clearAllLogs();
    }
}
