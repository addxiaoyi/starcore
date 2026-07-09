package dev.starcore.starcore.territory;

import java.util.UUID;

/**
 * 临时权限记录类
 * 记录临时授予的权限信息
 */
public class TemporaryPermission {

    private final UUID id;
    private final UUID territoryId;
    private final UUID granterId;
    private final UUID granteeId;
    private final TerritoryPermission permission;
    private final long grantedTime;
    private final long expiryTime;
    private final String reason;

    // 是否已过期
    private boolean expired = false;

    // 是否已撤销
    private boolean revoked = false;

    public TemporaryPermission(UUID territoryId, UUID granterId, UUID granteeId,
                              TerritoryPermission permission, long durationMillis, String reason) {
        this.id = UUID.randomUUID();
        this.territoryId = territoryId;
        this.granterId = granterId;
        this.granteeId = granteeId;
        this.permission = permission;
        this.grantedTime = System.currentTimeMillis();
        this.expiryTime = grantedTime + durationMillis;
        this.reason = reason;
    }

    // ==================== 状态检查 ====================

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expired || System.currentTimeMillis() >= expiryTime;
    }

    /**
     * 检查是否有效
     */
    public boolean isValid() {
        return !expired && !revoked && !isExpired();
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (isExpired() || revoked) {
            return 0;
        }
        return expiryTime - System.currentTimeMillis();
    }

    /**
     * 获取剩余时间（格式化）
     */
    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        if (remaining <= 0) {
            return "已过期";
        }

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天" + (hours % 24) + "小时";
        } else if (hours > 0) {
            return hours + "小时" + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 获取持续时间（毫秒）
     */
    public long getDuration() {
        return expiryTime - grantedTime;
    }

    // ==================== 状态操作 ====================

    /**
     * 标记为已过期
     */
    public void markExpired() {
        this.expired = true;
    }

    /**
     * 撤销权限
     */
    public void revoke() {
        this.revoked = true;
    }

    // ==================== Getter ====================

    public UUID getId() {
        return id;
    }

    public UUID getTerritoryId() {
        return territoryId;
    }

    public UUID getGranterId() {
        return granterId;
    }

    public UUID getGranteeId() {
        return granteeId;
    }

    public TerritoryPermission getPermission() {
        return permission;
    }

    public long getGrantedTime() {
        return grantedTime;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRevoked() {
        return revoked;
    }

    @Override
    public String toString() {
        return String.format("TemporaryPermission[territory=%s, grantee=%s, permission=%s, remaining=%s, valid=%b]",
            territoryId, granteeId, permission, getFormattedRemainingTime(), isValid());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemporaryPermission that = (TemporaryPermission) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
