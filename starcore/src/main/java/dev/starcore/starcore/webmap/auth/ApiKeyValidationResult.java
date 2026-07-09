package dev.starcore.starcore.webmap.auth;

/**
 * API Key 验证结果
 */
public record ApiKeyValidationResult(
    boolean valid,
    String message,
    ApiKeyInfo keyInfo
) {
    @Override
    public String toString() {
        return String.format("ApiKeyValidation[valid=%s, owner=%s, message=%s]",
            valid, keyInfo != null ? keyInfo.owner() : "null", message);
    }
}
