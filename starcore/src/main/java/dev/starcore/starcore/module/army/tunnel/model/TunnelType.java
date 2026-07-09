package dev.starcore.starcore.module.army.tunnel.model;

/**
 * Tunnel Type Enum
 * Different types of tunnels with different purposes
 */
public enum TunnelType {
    /**
     * Supply tunnel - Used for resource transport
     * Default visibility, no special combat bonuses
     */
    SUPPLY("补给地道", "用于运输资源和补给", 1),

    /**
     * Escape tunnel - Used for emergency evacuation
     * Speed boost for users, faster building
     */
    ESCAPE("逃生地道", "紧急撤离通道,使用者获得速度提升", 2),

    /**
     * Military tunnel - Used for troop movement and combat
     * Combat advantages, trap capabilities
     */
    MILITARY("军事地道", "军队调动的秘密通道,支持伏击和陷阱", 3),

    /**
     * Secret tunnel - Highest tier, maximum stealth
     * Complete invisibility, advanced traps, ambush capability
     */
    SECRET("绝密地道", "最高级别保密通道,完全隐蔽,支持高级陷阱和伏击", 4);

    private final String displayName;
    private final String description;
    private final int tier;

    TunnelType(String displayName, String description, int tier) {
        this.displayName = displayName;
        this.description = description;
        this.tier = tier;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int tier() {
        return tier;
    }

    /**
     * Get tier name
     */
    public String tierName() {
        return switch (tier) {
            case 1 -> "Tier I";
            case 2 -> "Tier II";
            case 3 -> "Tier III";
            case 4 -> "Tier IV";
            default -> "Unknown";
        };
    }

    /**
     * Check if this type supports traps
     */
    public boolean supportsTraps() {
        return this == MILITARY || this == SECRET;
    }

    /**
     * Check if this type supports ambush
     */
    public boolean supportsAmbush() {
        return this == SECRET || this == MILITARY;
    }

    /**
     * Get display color
     */
    public String colorCode() {
        return switch (this) {
            case SUPPLY -> "§a";
            case ESCAPE -> "§e";
            case MILITARY -> "§c";
            case SECRET -> "§5";
        };
    }
}
