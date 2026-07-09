package dev.starcore.starcore.module.territory.upgrade.model;

/**
 * Represents the result of an upgrade check.
 * 升级检查结果
 */
public record UpgradeCheckResult(
    boolean canUpgrade,
    String errorMessage,
    UpgradeCheckError error
) {
    /**
     * Create a successful check result.
     */
    public static UpgradeCheckResult success() {
        return new UpgradeCheckResult(true, null, null);
    }

    /**
     * Create a failed check result with error message.
     */
    public static UpgradeCheckResult failure(String message, UpgradeCheckError error) {
        return new UpgradeCheckResult(false, message, error);
    }

    /**
     * Check if this result indicates success.
     */
    public boolean isSuccess() {
        return canUpgrade;
    }

    /**
     * Check if this result indicates failure.
     */
    public boolean isFailure() {
        return !canUpgrade;
    }

    /**
     * Get error message or empty string if success.
     */
    public String getErrorOrEmpty() {
        return errorMessage != null ? errorMessage : "";
    }
}
