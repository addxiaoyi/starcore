package dev.starcore.starcore.territory;

/**
 * Territory类型枚举
 * 基于Towny的Plot类型系统
 */
public enum TerritoryType {

    /**
     * 城市中心 - Nation首都核心区
     */
    CAPITAL(
        "城市中心",
        "§6",
        true,
        1.5,
        "Nation的政治和行政中心"
    ),

    /**
     * 住宅区 - 玩家居住
     */
    RESIDENTIAL(
        "住宅区",
        "§a",
        false,
        1.0,
        "玩家建造房屋的区域"
    ),

    /**
     * 商业区 - 商店和交易
     */
    COMMERCIAL(
        "商业区",
        "§e",
        false,
        1.2,
        "商店和交易中心"
    ),

    /**
     * 工业区 - 生产和制造
     */
    INDUSTRIAL(
        "工业区",
        "§7",
        false,
        0.8,
        "工厂和生产设施"
    ),

    /**
     * 军事区 - 军事设施
     */
    MILITARY(
        "军事区",
        "§c",
        true,
        2.0,
        "军营、防御工事"
    ),

    /**
     * 农业区 - 农场和养殖
     */
    FARM(
        "农业区",
        "§2",
        false,
        0.5,
        "农田和牧场"
    ),

    /**
     * 港口区 - 码头和航运
     */
    HARBOR(
        "港口区",
        "§9",
        false,
        1.3,
        "码头和船坞"
    ),

    /**
     * 使馆区 - 外交区域
     */
    EMBASSY(
        "使馆区",
        "§b",
        true,
        1.5,
        "外交使馆和领事馆"
    ),

    /**
     * 竞技场 - PvP区域
     */
    ARENA(
        "竞技场",
        "§4",
        true,
        1.0,
        "PvP战斗场地"
    ),

    /**
     * 荒野 - 未开发区域
     */
    WILDERNESS(
        "荒野",
        "§8",
        false,
        0.0,
        "未被任何Nation占领"
    );

    private final String displayName;
    private final String colorCode;
    private final boolean requiresSpecialPermission;
    private final double taxMultiplier;
    private final String description;

    TerritoryType(String displayName, String colorCode,
                  boolean requiresSpecialPermission, double taxMultiplier,
                  String description) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.requiresSpecialPermission = requiresSpecialPermission;
        this.taxMultiplier = taxMultiplier;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }

    public boolean requiresSpecialPermission() {
        return requiresSpecialPermission;
    }

    public double getTaxMultiplier() {
        return taxMultiplier;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 获取基础维护费
     */
    public double getBaseMaintenance() {
        return switch (this) {
            case CAPITAL -> 100.0;
            case MILITARY, EMBASSY -> 50.0;
            case COMMERCIAL -> 30.0;
            case RESIDENTIAL -> 20.0;
            case INDUSTRIAL, HARBOR -> 25.0;
            case FARM -> 10.0;
            case ARENA -> 15.0;
            case WILDERNESS -> 0.0;
        };
    }

    /**
     * 是否允许PvP
     */
    public boolean isPvpAllowed() {
        return switch (this) {
            case ARENA, MILITARY -> true;
            default -> false;
        };
    }

    /**
     * 是否允许爆炸
     */
    public boolean isExplosionAllowed() {
        return switch (this) {
            case MILITARY, WILDERNESS -> true;
            default -> false;
        };
    }

    /**
     * 是否允许怪物生成
     */
    public boolean isMobSpawnAllowed() {
        return switch (this) {
            case FARM, WILDERNESS -> true;
            default -> false;
        };
    }
}
