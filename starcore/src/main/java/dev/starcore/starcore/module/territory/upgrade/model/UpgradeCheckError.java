package dev.starcore.starcore.module.territory.upgrade.model;

/**
 * Error types for upgrade checks.
 * 升级检查错误类型
 */
public enum UpgradeCheckError {
    // 基础错误
    INVALID_PATH("无效的升级路径"),
    INVALID_LEVEL("无效的升级等级"),
    ALREADY_MAXED("已达到最高等级"),
    NOT_ENOUGH_EXP("经验值不足"),

    // 权限和前提条件错误
    MISSING_PERMISSION("缺少必要权限"),
    MISSING_PREREQUISITE("缺少前置升级"),
    PREREQUISITE_NOT_MET("前置条件未满足"),

    // 资源错误
    NOT_ENOUGH_TREASURY("国库余额不足"),
    NOT_ENOUGH_RESOURCES("资源不足"),
    RESOURCE_NOT_FOUND("资源类型不存在"),

    // 状态错误
    UPGRADE_IN_PROGRESS("升级正在进行中"),
    UPGRADE_NOT_IN_PROGRESS("没有进行中的升级"),
    NATION_NOT_FOUND("国家不存在"),
    NATION_DISBANDED("国家已被解散"),
    PLAYER_NOT_IN_NATION("玩家未加入国家"),

    // 冷却和限制错误
    COOLDOWN_ACTIVE("冷却时间未结束"),
    LEVEL_ALREADY_ACHIEVED("该等级已达成"),
    UPGRADE_DISABLED("升级系统已禁用");

    private final String description;

    UpgradeCheckError(String description) {
        this.description = description;
    }

    /**
     * Get the localized description of this error.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the error key for translation.
     */
    public String getKey() {
        return "upgrade.error." + this.name().toLowerCase();
    }
}
