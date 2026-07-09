package dev.starcore.starcore.storage;

/**
 * 远程访问权限枚举
 * 定义用户对仓库的远程访问权限等级
 */
public enum RemoteAccessPermission {
    /**
     * 无权限
     * - 无法访问仓库
     */
    NONE("无权限", 0),

    /**
     * 查看权限
     * - 可以查看仓库内容
     * - 不能存取物品
     */
    VIEW("查看", 1),

    /**
     * 取出权限
     * - 可以查看和取出物品
     * - 不能存入物品
     */
    WITHDRAW("取出", 2),

    /**
     * 存入权限
     * - 可以查看和存入物品
     * - 不能取出物品
     */
    DEPOSIT("存入", 3),

    /**
     * 完全访问权限
     * - 可以存取物品
     * - 可以整理仓库
     * - 不能修改权限和升级
     */
    FULL("完全访问", 4),

    /**
     * 管理权限
     * - 拥有所有权限
     * - 可以授权其他玩家
     * - 可以升级仓库
     * - 不能转让所有权
     */
    ADMIN("管理", 5),

    /**
     * 所有者权限
     * - 最高权限
     * - 可以转让所有权
     * - 可以删除仓库
     */
    OWNER("所有者", 6);

    private final String displayName;
    private final int level;

    RemoteAccessPermission(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    /**
     * 获取显示名称
     * @return 权限的中文名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取权限等级
     * @return 权限等级（数值越大权限越高）
     */
    public int getLevel() {
        return level;
    }

    /**
     * 检查是否可以查看仓库
     * @return true如果权限等级足够
     */
    public boolean canView() {
        return level >= VIEW.level;
    }

    /**
     * 检查是否可以取出物品
     * @return true如果权限等级足够
     */
    public boolean canWithdraw() {
        return level >= WITHDRAW.level;
    }

    /**
     * 检查是否可以存入物品
     * @return true如果权限等级足够
     */
    public boolean canDeposit() {
        return level >= DEPOSIT.level;
    }

    /**
     * 检查是否拥有完全访问权限
     * @return true如果权限等级足够
     */
    public boolean hasFullAccess() {
        return level >= FULL.level;
    }

    /**
     * 检查是否拥有管理权限
     * @return true如果权限等级足够
     */
    public boolean canAdmin() {
        return level >= ADMIN.level;
    }

    /**
     * 检查是否为所有者
     * @return true如果是所有者
     */
    public boolean isOwner() {
        return this == OWNER;
    }

    /**
     * 检查权限等级是否高于另一个权限
     * @param other 另一个权限
     * @return true如果当前权限更高
     */
    public boolean isHigherThan(RemoteAccessPermission other) {
        return this.level > other.level;
    }

    /**
     * 检查权限等级是否高于或等于另一个权限
     * @param other 另一个权限
     * @return true如果当前权限更高或相等
     */
    public boolean isAtLeast(RemoteAccessPermission other) {
        return this.level >= other.level;
    }

    /**
     * 根据权限等级获取枚举
     * @param level 权限等级
     * @return 对应的权限枚举
     */
    public static RemoteAccessPermission fromLevel(int level) {
        for (RemoteAccessPermission permission : values()) {
            if (permission.level == level) {
                return permission;
            }
        }
        return NONE;
    }

    /**
     * 根据名称获取枚举（不区分大小写）
     * @param name 权限名称
     * @return 对应的权限枚举，如果找不到则返回NONE
     */
    public static RemoteAccessPermission fromName(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
