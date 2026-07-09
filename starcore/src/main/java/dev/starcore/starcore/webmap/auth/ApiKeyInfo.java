package dev.starcore.starcore.webmap.auth;

import java.time.Instant;
import java.util.Set;

/**
 * API Key 信息
 */
public record ApiKeyInfo(
    String apiKey,
    String owner,
    Instant createdAt,
    Instant expiresAt,
    Set<String> permissions,
    boolean active
) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission) || permissions.contains("*");
    }

    @Override
    public String toString() {
        return String.format("ApiKey[owner=%s, created=%s, active=%s, permissions=%d]",
            owner, createdAt, active, permissions.size());
    }
}
