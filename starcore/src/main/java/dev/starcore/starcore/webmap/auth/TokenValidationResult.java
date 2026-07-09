package dev.starcore.starcore.webmap.auth;

/**
 * Token 验证结果
 */
public record TokenValidationResult(
    boolean valid,
    String message,
    String userId
) {
    @Override
    public String toString() {
        return String.format("TokenValidation[valid=%s, userId=%s, message=%s]",
            valid, userId, message);
    }
}
