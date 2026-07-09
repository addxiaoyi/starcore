package dev.starcore.starcore.api.v1.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * API 认证上下文
 * 存储当前请求的认证信息
 */
public record ApiAuthContext(
    UUID playerId,
    String playerName,
    boolean authenticated,
    java.util.List<ApiAuthService.ApiKeyPermission> permissions
) {
    /**
     * 创建认证上下文
     */
    public static ApiAuthContext of(UUID playerId, String playerName, java.util.List<ApiAuthService.ApiKeyPermission> permissions) {
        return new ApiAuthContext(playerId, playerName, true, permissions);
    }

    /**
     * 创建匿名上下文
     */
    public static ApiAuthContext anonymous() {
        return new ApiAuthContext(null, null, false, java.util.List.of());
    }

    /**
     * 检查是否有特定权限
     */
    public boolean hasPermission(ApiAuthService.ApiKeyPermission permission) {
        if (!authenticated || permissions == null) {
            return false;
        }
        // ADMIN 权限拥有所有权限
        if (permissions.contains(ApiAuthService.ApiKeyPermission.ADMIN)) {
            return true;
        }
        return permissions.contains(permission);
    }

    /**
     * 检查是否有读取权限
     */
    public boolean canRead() {
        return authenticated && !permissions.isEmpty();
    }

    /**
     * 检查是否有写入权限
     */
    public boolean canWrite() {
        return hasPermission(ApiAuthService.ApiKeyPermission.ADMIN) ||
               hasPermission(ApiAuthService.ApiKeyPermission.WRITE_NATIONS) ||
               hasPermission(ApiAuthService.ApiKeyPermission.WRITE_FINANCE);
    }

    /**
     * 获取玩家 ID 或默认值
     */
    public UUID getPlayerIdOr(UUID defaultValue) {
        return playerId != null ? playerId : defaultValue;
    }

    /**
     * 获取玩家名称或默认值
     */
    public String getPlayerNameOr(String defaultValue) {
        return playerName != null ? playerName : defaultValue;
    }
}
