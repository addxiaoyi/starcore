package dev.starcore.starcore.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日志查询过滤器
 * 用于过滤和查询仓库操作日志
 */
public class LogQueryFilter {
    private UUID warehouseId;
    private UUID playerId;
    private String playerName;
    private StorageLog.LogAction action;
    private Instant startTime;
    private Instant endTime;
    private Boolean isRemoteAccess;
    private String itemInfo;
    private int limit;
    private int offset;

    /**
     * 私有构造函数，使用Builder创建
     */
    private LogQueryFilter() {
        this.limit = 50; // 默认返回50条
        this.offset = 0;
    }

    // ==================== Getters ====================

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public StorageLog.LogAction getAction() {
        return action;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Boolean getIsRemoteAccess() {
        return isRemoteAccess;
    }

    public String getItemInfo() {
        return itemInfo;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    // ==================== 匹配方法 ====================

    /**
     * 检查日志是否匹配此过滤器
     * @param log 日志记录
     * @return true如果日志匹配所有条件
     */
    public boolean matches(StorageLog log) {
        if (warehouseId != null && !warehouseId.equals(log.getWarehouseId())) {
            return false;
        }

        if (playerId != null && !playerId.equals(log.getPlayerId())) {
            return false;
        }

        if (playerName != null && !playerName.equalsIgnoreCase(log.getPlayerName())) {
            return false;
        }

        if (action != null && action != log.getAction()) {
            return false;
        }

        if (startTime != null && log.getTimestamp().isBefore(startTime)) {
            return false;
        }

        if (endTime != null && log.getTimestamp().isAfter(endTime)) {
            return false;
        }

        if (isRemoteAccess != null && isRemoteAccess != log.isRemoteAccess()) {
            return false;
        }

        if (itemInfo != null && log.getItemInfo() != null &&
                !log.getItemInfo().toLowerCase().contains(itemInfo.toLowerCase())) {
            return false;
        }

        return true;
    }

    /**
     * 过滤日志列表
     * @param logs 原始日志列表
     * @return 过滤后的日志列表
     */
    public List<StorageLog> filter(List<StorageLog> logs) {
        List<StorageLog> filtered = new ArrayList<>();
        int count = 0;
        int skipped = 0;

        for (StorageLog log : logs) {
            if (matches(log)) {
                if (skipped < offset) {
                    skipped++;
                    continue;
                }
                filtered.add(log);
                count++;
                if (count >= limit) {
                    break;
                }
            }
        }

        return filtered;
    }

    // ==================== Builder ====================

    /**
     * 创建Builder
     * @return 新的Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * LogQueryFilter构造器
     */
    public static class Builder {
        private final LogQueryFilter filter;

        private Builder() {
            this.filter = new LogQueryFilter();
        }

        /**
         * 设置仓库ID过滤
         */
        public Builder warehouseId(UUID warehouseId) {
            filter.warehouseId = warehouseId;
            return this;
        }

        /**
         * 设置玩家ID过滤
         */
        public Builder playerId(UUID playerId) {
            filter.playerId = playerId;
            return this;
        }

        /**
         * 设置玩家名称过滤
         */
        public Builder playerName(String playerName) {
            filter.playerName = playerName;
            return this;
        }

        /**
         * 设置操作类型过滤
         */
        public Builder action(StorageLog.LogAction action) {
            filter.action = action;
            return this;
        }

        /**
         * 设置开始时间
         */
        public Builder startTime(Instant startTime) {
            filter.startTime = startTime;
            return this;
        }

        /**
         * 设置结束时间
         */
        public Builder endTime(Instant endTime) {
            filter.endTime = endTime;
            return this;
        }

        /**
         * 设置时间范围（最近N天）
         */
        public Builder lastDays(int days) {
            filter.startTime = Instant.now().minus(days, ChronoUnit.DAYS);
            filter.endTime = Instant.now();
            return this;
        }

        /**
         * 设置时间范围（最近N小时）
         */
        public Builder lastHours(int hours) {
            filter.startTime = Instant.now().minus(hours, ChronoUnit.HOURS);
            filter.endTime = Instant.now();
            return this;
        }

        /**
         * 设置是否为远程访问
         */
        public Builder remoteAccess(Boolean isRemoteAccess) {
            filter.isRemoteAccess = isRemoteAccess;
            return this;
        }

        /**
         * 仅远程访问
         */
        public Builder onlyRemote() {
            filter.isRemoteAccess = true;
            return this;
        }

        /**
         * 仅本地访问
         */
        public Builder onlyLocal() {
            filter.isRemoteAccess = false;
            return this;
        }

        /**
         * 设置物品信息过滤（模糊匹配）
         */
        public Builder itemInfo(String itemInfo) {
            filter.itemInfo = itemInfo;
            return this;
        }

        /**
         * 设置返回数量限制
         */
        public Builder limit(int limit) {
            filter.limit = Math.max(1, Math.min(limit, 1000)); // 最多1000条
            return this;
        }

        /**
         * 设置偏移量（分页）
         */
        public Builder offset(int offset) {
            filter.offset = Math.max(0, offset);
            return this;
        }

        /**
         * 设置分页
         * @param page 页码（从1开始）
         * @param pageSize 每页大小
         */
        public Builder page(int page, int pageSize) {
            filter.limit = Math.max(1, Math.min(pageSize, 1000));
            filter.offset = Math.max(0, (page - 1) * pageSize);
            return this;
        }

        /**
         * 构建过滤器
         */
        public LogQueryFilter build() {
            return filter;
        }
    }

    @Override
    public String toString() {
        return "LogQueryFilter{" +
                "warehouse=" + warehouseId +
                ", player=" + playerId +
                ", action=" + action +
                ", timeRange=" + (startTime != null || endTime != null) +
                ", remote=" + isRemoteAccess +
                ", limit=" + limit +
                ", offset=" + offset +
                '}';
    }
}
