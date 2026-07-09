package dev.starcore.starcore.storage;

/**
 * 仓库类型枚举
 * 定义不同类型的仓库及其特性
 */
public enum WarehouseType {
    /**
     * 个人仓库
     * - 玩家个人专属
     * - 仅所有者可访问（除非授权）
     * - 基础容量27格（1级）
     */
    PERSONAL("个人仓库", true, false),

    /**
     * 国家仓库
     * - 归属于特定国家
     * - 国家成员可访问（根据权限）
     * - 容量可由国家升级
     */
    NATION("国家仓库", false, true),

    /**
     * 共享仓库
     * - 可授权多个玩家访问
     * - 支持细粒度权限控制
     * - 适合团队协作
     */
    SHARED("共享仓库", true, false),

    /**
     * 高级仓库
     * - 付费或特殊权限玩家专属
     * - 更大容量和更多功能
     * - 支持远程访问和自动整理
     */
    PREMIUM("高级仓库", true, true);

    private final String displayName;
    private final boolean personalOwned;
    private final boolean nationOwned;

    WarehouseType(String displayName, boolean personalOwned, boolean nationOwned) {
        this.displayName = displayName;
        this.personalOwned = personalOwned;
        this.nationOwned = nationOwned;
    }

    /**
     * 获取显示名称
     * @return 仓库类型的中文名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 是否为个人所有
     * @return true如果是个人仓库
     */
    public boolean isPersonalOwned() {
        return personalOwned;
    }

    /**
     * 是否为国家所有
     * @return true如果是国家仓库
     */
    public boolean isNationOwned() {
        return nationOwned;
    }

    /**
     * 是否支持远程访问
     * @return true如果支持远程访问
     */
    public boolean supportsRemoteAccess() {
        // 高级仓库和共享仓库默认支持远程访问
        return this == PREMIUM || this == SHARED;
    }

    /**
     * 获取基础容量（格子数）
     * @return 1级时的容量
     */
    public int getBaseCapacity() {
        return switch (this) {
            case PERSONAL -> 27;  // 3行
            case NATION -> 54;    // 6行
            case SHARED -> 36;    // 4行
            case PREMIUM -> 54;   // 6行
        };
    }

    /**
     * 获取最大等级
     * @return 可升级的最大等级
     */
    public int getMaxLevel() {
        return switch (this) {
            case PERSONAL -> 10;
            case NATION -> 15;
            case SHARED -> 10;
            case PREMIUM -> 20;
        };
    }
}
