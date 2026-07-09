package dev.starcore.starcore.foundation.permission;

import dev.starcore.starcore.core.persistence.PersistenceService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 内部权限服务 - 玩家权限管理
 */
public final class InternalPermissionService {
    private static final String NAMESPACE = "permissions";
    private static final String FILE_NAME = "player-permissions.dat";
    private static final String LEGACY_FILE_NAME = "playerdata/permissions.dat";

    private final ConcurrentMap<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final PersistenceService persistenceService;
    private final Logger logger;
    private volatile boolean loaded = false;

    public InternalPermissionService() {
        this(null, null);
    }

    public InternalPermissionService(PersistenceService persistenceService) {
        this(persistenceService, null);
    }

    public InternalPermissionService(PersistenceService persistenceService, Logger logger) {
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    /**
     * 启动服务并加载权限数据
     */
    public void start() {
        if (loaded) return;
        load();
        loaded = true;
    }

    /**
     * 停止服务并保存权限数据
     */
    public void stop() {
        save();
    }

    /**
     * 检查玩家是否有指定权限
     */
    public boolean has(UUID playerId, String permission) {
        return playerPermissions.getOrDefault(playerId, Set.of()).contains(permission);
    }

    /**
     * 授予玩家权限
     */
    public void grant(UUID playerId, String permission) {
        Set<String> perms = playerPermissions.compute(playerId, (ignored, existing) -> {
            Set<String> permissions = ConcurrentHashMap.newKeySet();
            if (existing != null) {
                permissions.addAll(existing);
            }
            permissions.add(permission);
            return permissions;
        });
        // 异步保存
        scheduleSave();
    }

    /**
     * 撤销玩家权限
     */
    public void revoke(UUID playerId, String permission) {
        playerPermissions.computeIfPresent(playerId, (ignored, existing) -> {
            Set<String> newSet = ConcurrentHashMap.newKeySet();
            newSet.addAll(existing);
            newSet.remove(permission);
            return newSet.isEmpty() ? null : newSet;
        });
        scheduleSave();
    }

    /**
     * 撤销玩家所有权限
     */
    public void revokeAll(UUID playerId) {
        playerPermissions.remove(playerId);
        scheduleSave();
    }

    /**
     * 获取玩家的所有权限
     */
    public Set<String> getPermissions(UUID playerId) {
        return Set.copyOf(playerPermissions.getOrDefault(playerId, Set.of()));
    }

    /**
     * 设置玩家的所有权限（覆盖）
     */
    public void setPermissions(UUID playerId, Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            playerPermissions.remove(playerId);
        } else {
            Set<String> newSet = ConcurrentHashMap.newKeySet();
            newSet.addAll(permissions);
            playerPermissions.put(playerId, newSet);
        }
        scheduleSave();
    }

    /**
     * 添加多个权限
     */
    public void grantAll(UUID playerId, Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return;
        playerPermissions.compute(playerId, (ignored, existing) -> {
            Set<String> newPerms = ConcurrentHashMap.newKeySet();
            if (existing != null) {
                newPerms.addAll(existing);
            }
            newPerms.addAll(permissions);
            return newPerms;
        });
        scheduleSave();
    }

    /**
     * 检查玩家是否有任意一个权限
     */
    public boolean hasAny(UUID playerId, Collection<String> permissions) {
        Set<String> playerPerms = playerPermissions.getOrDefault(playerId, Set.of());
        return permissions.stream().anyMatch(playerPerms::contains);
    }

    /**
     * 检查玩家是否有所有指定权限
     */
    public boolean hasAll(UUID playerId, Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return true;
        Set<String> playerPerms = playerPermissions.getOrDefault(playerId, Set.of());
        return playerPerms.containsAll(permissions);
    }

    /**
     * 获取快照
     */
    public Map<UUID, Set<String>> snapshot() {
        Map<UUID, Set<String>> result = new HashMap<>();
        playerPermissions.forEach((playerId, perms) ->
            result.put(playerId, Set.copyOf(perms)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取所有有权限数据的玩家数量
     */
    public int getPlayerCount() {
        return playerPermissions.size();
    }

    /**
     * 获取总权限数量
     */
    public long getTotalPermissionCount() {
        return playerPermissions.values().stream()
            .mapToLong(Set::size)
            .sum();
    }

    // ==================== 持久化 ====================

    private void load() {
        if (persistenceService == null) {
            loadLegacy();
            return;
        }

        try {
            Path file = persistenceService.namespacePath(NAMESPACE).resolve(FILE_NAME);
            if (!Files.exists(file)) {
                // 如果新格式不存在，尝试从旧格式迁移
                loadLegacy();
                return;
            }
            try (InputStream input = Files.newInputStream(file)) {
                Properties properties = new Properties();
                properties.load(input);

                playerPermissions.clear();
                for (String key : properties.stringPropertyNames()) {
                    try {
                        UUID playerId = UUID.fromString(key);
                        String[] perms = properties.getProperty(key, "").split(",");
                        Set<String> permSet = ConcurrentHashMap.newKeySet();
                        for (String perm : perms) {
                            String trimmed = perm.trim();
                            if (!trimmed.isEmpty()) {
                                permSet.add(trimmed);
                            }
                        }
                        if (!permSet.isEmpty()) {
                            playerPermissions.put(playerId, permSet);
                        }
                    } catch (Exception e) {
                        logWarning("Failed to parse permission entry for key: " + key + " - " + e.getMessage());
                    }
                }
                logInfo("Loaded permissions for " + playerPermissions.size() + " players from new format");
            }
        } catch (IOException e) {
            logWarning("Failed to load permissions from new format, attempting legacy: " + e.getMessage());
            loadLegacy();
        }
    }

    private void loadLegacy() {
        // 尝试从旧格式加载（兼容早期版本）
        // 旧格式可能是不同的文件名或位置
        if (persistenceService == null) {
            logInfo("No persistence service available, skipping legacy permission load");
            return;
        }

        Path legacyPath;
        try {
            legacyPath = persistenceService.namespacePath(NAMESPACE).resolve(LEGACY_FILE_NAME);
        } catch (IOException e) {
            logWarning("Failed to get legacy permission path: " + e.getMessage());
            return;
        }
        if (!Files.exists(legacyPath)) {
            logInfo("No legacy permission file found at: " + legacyPath);
            return;
        }

        try (InputStream input = Files.newInputStream(legacyPath)) {
            Properties properties = new Properties();
            properties.load(input);

            int loadedCount = 0;
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    String[] perms = properties.getProperty(key, "").split(",");
                    Set<String> permSet = ConcurrentHashMap.newKeySet();
                    for (String perm : perms) {
                        String trimmed = perm.trim();
                        if (!trimmed.isEmpty()) {
                            permSet.add(trimmed);
                        }
                    }
                    if (!permSet.isEmpty()) {
                        // 如果玩家已有权限，合并；否则直接添加
                        playerPermissions.compute(playerId, (ignored, existing) -> {
                            if (existing != null) {
                                Set<String> merged = ConcurrentHashMap.newKeySet();
                                merged.addAll(existing);
                                merged.addAll(permSet);
                                return merged;
                            }
                            return permSet;
                        });
                        loadedCount++;
                    }
                } catch (Exception e) {
                    logWarning("Failed to parse legacy permission entry for key: " + key + " - " + e.getMessage());
                }
            }
            logInfo("Migrated permissions for " + loadedCount + " players from legacy format");

            // 迁移成功后，删除旧文件（可选）
            // Files.deleteIfExists(legacyPath);
        } catch (IOException e) {
            logWarning("Failed to load legacy permissions: " + e.getMessage());
        }
    }

    private void scheduleSave() {
        if (persistenceService == null) {
            return;
        }
        persistenceService.ensureNamespace(NAMESPACE)
            .thenRun(this::saveSync);
    }

    private void save() {
        if (persistenceService == null) {
            return;
        }
        persistenceService.ensureNamespace(NAMESPACE).join();
        saveSync();
    }

    private void saveSync() {
        if (persistenceService == null) {
            return;
        }
        try {
            Path file = persistenceService.namespacePath(NAMESPACE).resolve(FILE_NAME);
            Properties properties = new Properties();

            playerPermissions.forEach((playerId, perms) -> {
                if (!perms.isEmpty()) {
                    properties.setProperty(playerId.toString(), String.join(",", perms));
                }
            });

            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "STARCORE Player Permissions");
            }
        } catch (IOException e) {
            logWarning("Failed to save permissions: " + e.getMessage());
        }
    }

    // ==================== 日志辅助 ====================

    private void logInfo(String message) {
        if (logger != null) {
            logger.info("[PermissionService] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning("[PermissionService] " + message);
        }
    }
}
