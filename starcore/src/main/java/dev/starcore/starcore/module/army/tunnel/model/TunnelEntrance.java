package dev.starcore.starcore.module.army.tunnel.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tunnel Entrance Model
 * Represents an entrance point to a tunnel network
 */
public final class TunnelEntrance {

    private final UUID id;
    private final Location location;
    private final boolean hidden; // Hidden entrances are harder to discover
    private final UUID tunnelId;
    private final Instant createdAt;

    public TunnelEntrance(
        UUID id,
        Location location,
        boolean hidden,
        UUID tunnelId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.location = Objects.requireNonNull(location, "location");
        this.hidden = hidden;
        this.tunnelId = Objects.requireNonNull(tunnelId, "tunnelId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ==================== Factory Methods ====================

    /**
     * Create a new visible entrance
     */
    public static TunnelEntrance visible(UUID tunnelId, Location location) {
        return new TunnelEntrance(UUID.randomUUID(), location, false, tunnelId, Instant.now());
    }

    /**
     * Create a new hidden entrance
     */
    public static TunnelEntrance hidden(UUID tunnelId, Location location) {
        return new TunnelEntrance(UUID.randomUUID(), location, true, tunnelId, Instant.now());
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public Location location() {
        return location;
    }

    public boolean isHidden() {
        return hidden;
    }

    public UUID tunnelId() {
        return tunnelId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    // ==================== Computed Properties ====================

    /**
     * Get the world name
     */
    public String worldName() {
        return location.getWorld().getName();
    }

    /**
     * Get coordinates as string
     */
    public String coordinates() {
        return String.format("%d, %d, %d",
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    /**
     * Get formatted location string
     */
    public String formattedLocation() {
        return String.format("%s (%s)", worldName(), coordinates());
    }

    /**
     * Check if entrance is in same world as another location
     */
    public boolean isInWorld(Location other) {
        return location.getWorld().equals(other.getWorld());
    }

    /**
     * Get distance to another location
     */
    public double distanceTo(Location other) {
        if (!isInWorld(other)) {
            return Double.MAX_VALUE;
        }
        return location.distance(other);
    }

    // ==================== Utility ====================

    /**
     * Check if entrance is discoverable by nearby players
     */
    public boolean isDiscoverable(double discoveryRange) {
        // Hidden entrances require being closer to discover
        return hidden && discoveryRange <= 2.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TunnelEntrance other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("TunnelEntrance{id=%s, hidden=%s, location=%s}",
            id.toString().substring(0, 8), hidden, coordinates());
    }
}