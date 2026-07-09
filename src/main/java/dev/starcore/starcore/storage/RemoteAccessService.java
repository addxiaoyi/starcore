package dev.starcore.starcore.storage;

import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 远程访问服务
 * 处理仓库的远程访问功能，包括权限检查、距离限制和费用扣除
 */
public class RemoteAccessService {
    private final StorageService storageService;
    // E-008: 改 volatile 非 final,支持 reloadConfig 热更新
    private volatile StorageConfig config;
    private final Logger logger;
    private final InternalEconomyService economyService;

    /**
     * 构造函数
     * @param storageService 仓库服务
     * @param config 配置
     * @param logger 日志记录器
     * @param economyService 经济服务
     */
    public RemoteAccessService(StorageService storageService, StorageConfig config, Logger logger, InternalEconomyService economyService) {
        this.storageService = storageService;
        this.config = config;
        this.logger = logger;
        this.economyService = economyService;
    }

    /** E-008: 热更新配置引用 */
    public void setConfig(StorageConfig newConfig) {
        if (newConfig != null) this.config = newConfig;
    }

    /**
     * 检查玩家是否可以远程访问指定仓库
     * @param player 玩家
     * @param warehouseId 仓库ID
     * @return 检查结果
     */
    public AccessCheckResult canAccess(Player player, UUID warehouseId) {
        return canAccess(player, warehouseId, null);
    }

    /**
     * 检查玩家是否可以远程访问指定仓库（带位置验证）
     * @param player 玩家
     * @param warehouseId 仓库ID
     * @param warehouseLocation 仓库位置（可选）
     * @return 检查结果
     */
    public AccessCheckResult canAccess(Player player, UUID warehouseId, Location warehouseLocation) {
        // 检查功能是否启用
        if (!config.isRemoteAccessEnabled()) {
            return AccessCheckResult.failure("远程访问功能未启用");
        }

        // 检查仓库是否存在
        Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);
        if (warehouseOpt.isEmpty()) {
            return AccessCheckResult.failure("仓库不存在");
        }

        Warehouse warehouse = warehouseOpt.get();

        // 检查仓库是否锁定
        if (warehouse.isLocked()) {
            return AccessCheckResult.failure("仓库已锁定");
        }

        // 检查权限
        RemoteAccessPermission permission = storageService.getPermission(warehouseId, player.getUniqueId());
        if (!permission.canView()) {
            return AccessCheckResult.failure("您没有访问此仓库的权限");
        }

        // 检查仓库类型是否支持远程访问
        if (!warehouse.getType().supportsRemoteAccess() && !permission.isOwner()) {
            return AccessCheckResult.failure("此类型仓库不支持远程访问");
        }

        // 检查距离限制
        if (config.hasDistanceLimit() && warehouseLocation != null) {
            Location playerLocation = player.getLocation();
            if (!isWithinDistance(playerLocation, warehouseLocation)) {
                return AccessCheckResult.failure("距离仓库太远，最大距离: " +
                        (int) config.getMaxRemoteDistance() + " 格");
            }
        }

        // 检查费用（如果不是所有者）
        BigDecimal cost = getAccessCost(player.getUniqueId(), warehouse);
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            // 检查玩家余额是否足够
            if (!economyService.has(player.getUniqueId(), cost)) {
                return AccessCheckResult.failure("余额不足，需要 " + cost + " 金币");
            }
        }

        return AccessCheckResult.success(warehouse, permission, cost);
    }

    /**
     * 计算远程访问费用
     * @param playerId 玩家ID
     * @param warehouse 仓库
     * @return 费用
     */
    public BigDecimal getAccessCost(UUID playerId, Warehouse warehouse) {
        // 所有者和管理员免费
        RemoteAccessPermission permission = storageService.getPermission(warehouse.getWarehouseId(), playerId);
        if (permission.isAtLeast(RemoteAccessPermission.ADMIN)) {
            return BigDecimal.ZERO;
        }

        // 高级仓库免费
        if (warehouse.getType() == WarehouseType.PREMIUM) {
            return BigDecimal.ZERO;
        }

        return config.getRemoteAccessCost();
    }

    /**
     * 执行远程访问（扣除费用并记录日志）
     * @param player 玩家
     * @param warehouseId 仓库ID
     * @return 是否成功
     */
    public boolean executeRemoteAccess(Player player, UUID warehouseId) {
        AccessCheckResult result = canAccess(player, warehouseId);
        if (!result.isSuccess()) {
            player.sendMessage("§c" + result.getFailureReason());
            return false;
        }

        Warehouse warehouse = result.getWarehouse();
        BigDecimal cost = result.getCost();

        // 扣除费用
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            if (!economyService.withdraw(player.getUniqueId(), cost)) {
                player.sendMessage("§c余额不足，需要 " + cost + " 金币");
                return false;
            }
            player.sendMessage("§a扣除远程访问费用: " + cost + " 金币");
        }

        // 记录日志
        if (config.isLogsEnabled()) {
            StorageLog log = StorageLog.createOpenLog(
                    warehouseId,
                    player.getUniqueId(),
                    player.getName(),
                    true
            );
            storageService.getLogService().addLog(log);
        }

        // 更新仓库访问时间
        warehouse.updateAccessTime();

        logger.info(player.getName() + " remotely accessed warehouse " + warehouseId);
        return true;
    }

    /**
     * 检查距离限制
     * @param playerLocation 玩家位置
     * @param warehouseLocation 仓库位置
     * @return true如果在范围内
     */
    public boolean isWithinDistance(Location playerLocation, Location warehouseLocation) {
        if (!config.hasDistanceLimit()) {
            return true;
        }

        // 不同世界不允许访问
        if (!playerLocation.getWorld().equals(warehouseLocation.getWorld())) {
            return false;
        }

        double distance = playerLocation.distance(warehouseLocation);
        return distance <= config.getMaxRemoteDistance();
    }

    /**
     * 访问检查结果
     */
    public static class AccessCheckResult {
        private final boolean success;
        private final String failureReason;
        private final Warehouse warehouse;
        private final RemoteAccessPermission permission;
        private final BigDecimal cost;

        private AccessCheckResult(boolean success, String failureReason,
                                  Warehouse warehouse, RemoteAccessPermission permission,
                                  BigDecimal cost) {
            this.success = success;
            this.failureReason = failureReason;
            this.warehouse = warehouse;
            this.permission = permission;
            this.cost = cost;
        }

        public static AccessCheckResult success(Warehouse warehouse,
                                                RemoteAccessPermission permission,
                                                BigDecimal cost) {
            return new AccessCheckResult(true, null, warehouse, permission, cost);
        }

        public static AccessCheckResult failure(String reason) {
            return new AccessCheckResult(false, reason, null, null, BigDecimal.ZERO);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public Warehouse getWarehouse() {
            return warehouse;
        }

        public RemoteAccessPermission getPermission() {
            return permission;
        }

        public BigDecimal getCost() {
            return cost;
        }
    }
}
