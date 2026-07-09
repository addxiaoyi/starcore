package dev.starcore.starcore.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 存储日志记录
 * 记录所有仓库操作的详细信息
 */
public class StorageLog {
    private final UUID logId;
    private final UUID warehouseId;
    private final UUID playerId;
    private final String playerName;
    private final LogAction action;
    private final Instant timestamp;
    private final String itemInfo;
    private final int amount;
    private final String ipAddress;
    private final boolean isRemoteAccess;
    private final String additionalInfo;

    /**
     * 操作类型枚举
     */
    public enum LogAction {
        OPEN("打开仓库"),
        DEPOSIT("存入物品"),
        WITHDRAW("取出物品"),
        REMOTE_ACCESS("远程访问"),
        UPGRADE("升级仓库"),
        GRANT_PERMISSION("授权"),
        REVOKE_PERMISSION("撤销授权"),
        RENAME("重命名"),
        LOCK("锁定"),
        UNLOCK("解锁"),
        DELETE("删除");

        private final String displayName;

        LogAction(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 完整构造函数
     */
    public StorageLog(UUID logId, UUID warehouseId, UUID playerId, String playerName,
                      LogAction action, Instant timestamp, String itemInfo, int amount,
                      String ipAddress, boolean isRemoteAccess, String additionalInfo) {
        this.logId = Objects.requireNonNull(logId, "logId cannot be null");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId cannot be null");
        this.playerId = playerId;
        this.playerName = playerName;
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.itemInfo = itemInfo;
        this.amount = amount;
        this.ipAddress = ipAddress;
        this.isRemoteAccess = isRemoteAccess;
        this.additionalInfo = additionalInfo;
    }

    /**
     * 简化构造函数
     */
    public StorageLog(UUID warehouseId, UUID playerId, String playerName,
                      LogAction action, String itemInfo, int amount) {
        this(UUID.randomUUID(), warehouseId, playerId, playerName, action,
                Instant.now(), itemInfo, amount, null, false, null);
    }

    /**
     * 创建打开仓库日志
     */
    public static StorageLog createOpenLog(UUID warehouseId, UUID playerId, String playerName, boolean remote) {
        return new StorageLog(UUID.randomUUID(), warehouseId, playerId, playerName,
                remote ? LogAction.REMOTE_ACCESS : LogAction.OPEN,
                Instant.now(), null, 0, null, remote, null);
    }

    /**
     * 创建存入物品日志
     */
    public static StorageLog createDepositLog(UUID warehouseId, UUID playerId, String playerName,
                                               String itemInfo, int amount, boolean remote) {
        return new StorageLog(UUID.randomUUID(), warehouseId, playerId, playerName,
                LogAction.DEPOSIT, Instant.now(), itemInfo, amount, null, remote, null);
    }

    /**
     * 创建取出物品日志
     */
    public static StorageLog createWithdrawLog(UUID warehouseId, UUID playerId, String playerName,
                                                String itemInfo, int amount, boolean remote) {
        return new StorageLog(UUID.randomUUID(), warehouseId, playerId, playerName,
                LogAction.WITHDRAW, Instant.now(), itemInfo, amount, null, remote, null);
    }

    /**
     * 创建升级日志
     */
    public static StorageLog createUpgradeLog(UUID warehouseId, UUID playerId, String playerName,
                                               int fromLevel, int toLevel) {
        String info = "等级 " + fromLevel + " -> " + toLevel;
        return new StorageLog(UUID.randomUUID(), warehouseId, playerId, playerName,
                LogAction.UPGRADE, Instant.now(), null, 0, null, false, info);
    }

    /**
     * 创建权限变更日志
     */
    public static StorageLog createPermissionLog(UUID warehouseId, UUID playerId, String playerName,
                                                  boolean isGrant, String targetPlayer, String permission) {
        String info = targetPlayer + " - " + permission;
        return new StorageLog(UUID.randomUUID(), warehouseId, playerId, playerName,
                isGrant ? LogAction.GRANT_PERMISSION : LogAction.REVOKE_PERMISSION,
                Instant.now(), null, 0, null, false, info);
    }

    // ==================== Getters ====================

    public UUID getLogId() {
        return logId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public LogAction getAction() {
        return action;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getItemInfo() {
        return itemInfo;
    }

    public int getAmount() {
        return amount;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isRemoteAccess() {
        return isRemoteAccess;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取格式化的日志描述
     * @return 易读的日志描述
     */
    public String getFormattedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(action.getDisplayName()).append("] ");
        sb.append(playerName != null ? playerName : "未知玩家");

        if (itemInfo != null) {
            sb.append(" - ").append(itemInfo);
            if (amount > 0) {
                sb.append(" x").append(amount);
            }
        }

        if (additionalInfo != null) {
            sb.append(" (").append(additionalInfo).append(")");
        }

        if (isRemoteAccess) {
            sb.append(" [远程]");
        }

        return sb.toString();
    }

    /**
     * 创建带IP地址的副本
     */
    public StorageLog withIpAddress(String ipAddress) {
        return new StorageLog(logId, warehouseId, playerId, playerName, action,
                timestamp, itemInfo, amount, ipAddress, isRemoteAccess, additionalInfo);
    }

    /**
     * 创建带额外信息的副本
     */
    public StorageLog withAdditionalInfo(String info) {
        return new StorageLog(logId, warehouseId, playerId, playerName, action,
                timestamp, itemInfo, amount, ipAddress, isRemoteAccess, info);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageLog that = (StorageLog) o;
        return Objects.equals(logId, that.logId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId);
    }

    @Override
    public String toString() {
        return "StorageLog{" +
                "id=" + logId +
                ", warehouse=" + warehouseId +
                ", player=" + playerName +
                ", action=" + action +
                ", time=" + timestamp +
                ", remote=" + isRemoteAccess +
                '}';
    }
}
