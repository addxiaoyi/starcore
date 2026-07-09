package dev.starcore.starcore.module.army.tunnel.model;

import dev.starcore.starcore.util.ColorCodes;

/**
 * Tunnel State Enum
 * Represents the current state of a tunnel
 */
public enum TunnelState {
    /**
     * Tunnel is under construction
     */
    UNDER_CONSTRUCTION("建设中", ColorCodes.YELLOW),

    /**
     * Tunnel is active and accessible
     */
    ACTIVE("可用", ColorCodes.GREEN),

    /**
     * Tunnel is fortified with defenses
     */
    FORTIFIED("加固中", ColorCodes.AQUA),

    /**
     * Tunnel is collapsed and inaccessible
     */
    COLLAPSED("已坍塌", ColorCodes.RED),

    /**
     * Tunnel is sealed (maintenance/emergency)
     */
    SEALED("已封闭", ColorCodes.GRAY),

    /**
     * Tunnel is destroyed and cannot be restored
     */
    DESTROYED("已摧毁", ColorCodes.DARK_RED);

    private final String displayName;
    private final String colorCode;

    TunnelState(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String displayName() {
        return displayName;
    }

    public String colorCode() {
        return colorCode;
    }

    /**
     * Check if tunnel is operational
     */
    public boolean isOperational() {
        return this == ACTIVE || this == FORTIFIED;
    }

    /**
     * Check if tunnel can be modified
     */
    public boolean canBeModified() {
        return this == ACTIVE || this == UNDER_CONSTRUCTION || this == FORTIFIED;
    }

    /**
     * Check if tunnel can be entered
     */
    public boolean canBeEntered() {
        return this == ACTIVE || this == FORTIFIED;
    }

    /**
     * Check if tunnel can be restored
     */
    public boolean canBeRestored() {
        return this == COLLAPSED;
    }

    /**
     * Get formatted display string
     */
    public String formatted() {
        return colorCode + displayName;
    }
}
