package dev.starcore.starcore.module.army.tunnel.model;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.time.Instant;
import java.util.*;

/**
 * Tunnel Model
 * Represents an underground tunnel network belonging to a nation
 */
public final class Tunnel {

    private final UUID id;
    private final NationId nationId;
    private String name;
    private TunnelType type;
    private TunnelState state;
    private Location entrance; // Primary entrance location
    private List<TunnelEntrance> entrances;
    private List<UUID> connectedTunnels; // Connected tunnel IDs
    private final Instant createdAt;
    private Instant lastUpdated;
    private int buildTime; // Build time in seconds
    private int progress; // Construction progress (0-100)

    public Tunnel(
        UUID id,
        NationId nationId,
        String name,
        TunnelType type,
        TunnelState state,
        Location entrance,
        List<TunnelEntrance> entrances,
        List<UUID> connectedTunnels,
        Instant createdAt,
        Instant lastUpdated,
        int buildTime,
        int progress
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        this.entrance = entrance;
        this.entrances = new ArrayList<>(Objects.requireNonNull(entrances, "entrances"));
        this.connectedTunnels = new ArrayList<>(Objects.requireNonNull(connectedTunnels, "connectedTunnels"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated");
        this.buildTime = buildTime;
        this.progress = clamp(progress, 0, 100);
    }

    // ==================== Factory Methods ====================

    /**
     * Create a new tunnel
     */
    public static Tunnel create(NationId nationId, String name, TunnelType type, Location entrance, int buildTime) {
        return new Tunnel(
            UUID.randomUUID(),
            nationId,
            name,
            type,
            TunnelState.UNDER_CONSTRUCTION,
            entrance,
            new ArrayList<>(),
            new ArrayList<>(),
            Instant.now(),
            Instant.now(),
            buildTime,
            0
        );
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public NationId nationId() {
        return nationId;
    }

    public String name() {
        return name;
    }

    public TunnelType type() {
        return type;
    }

    public TunnelState state() {
        return state;
    }

    public Location entrance() {
        return entrance;
    }

    public List<TunnelEntrance> entrances() {
        return entrances;
    }

    public List<UUID> connectedTunnels() {
        return connectedTunnels;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public int buildTime() {
        return buildTime;
    }

    public int progress() {
        return progress;
    }

    // ==================== Setters ====================

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.lastUpdated = Instant.now();
    }

    public void setType(TunnelType type) {
        this.type = Objects.requireNonNull(type, "type");
        this.lastUpdated = Instant.now();
    }

    public void setState(TunnelState state) {
        this.state = Objects.requireNonNull(state, "state");
        this.lastUpdated = Instant.now();
    }

    public void setEntrance(Location entrance) {
        this.entrance = entrance;
        this.lastUpdated = Instant.now();
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated");
    }

    public void setBuildTime(int buildTime) {
        this.buildTime = buildTime;
        this.lastUpdated = Instant.now();
    }

    public void setProgress(int progress) {
        this.progress = clamp(progress, 0, 100);
        this.lastUpdated = Instant.now();
    }

    // ==================== Computed Properties ====================

    /**
     * Check if tunnel is accessible
     */
    public boolean isAccessible() {
        return state == TunnelState.ACTIVE || state == TunnelState.FORTIFIED;
    }

    /**
     * Check if tunnel is under construction
     */
    public boolean isUnderConstruction() {
        return state == TunnelState.UNDER_CONSTRUCTION;
    }

    /**
     * Check if tunnel is collapsed or destroyed
     */
    public boolean isDestroyed() {
        return state == TunnelState.COLLAPSED || state == TunnelState.DESTROYED;
    }

    /**
     * Get entrance count
     */
    public int entranceCount() {
        return entrances.size();
    }

    /**
     * Get visible entrance count (non-hidden)
     */
    public long visibleEntranceCount() {
        return entrances.stream().filter(e -> !e.isHidden()).count();
    }

    /**
     * Check if tunnel is secret type
     */
    public boolean isSecret() {
        return type == TunnelType.SECRET;
    }

    /**
     * Get tunnel tier based on type
     */
    public int tier() {
        return switch (type) {
            case SUPPLY -> 1;
            case ESCAPE -> 2;
            case MILITARY -> 3;
            case SECRET -> 4;
        };
    }

    // ==================== Utility ====================

    /**
     * Add connected tunnel
     */
    public void addConnectedTunnel(UUID tunnelId) {
        if (!connectedTunnels.contains(tunnelId)) {
            connectedTunnels.add(tunnelId);
            this.lastUpdated = Instant.now();
        }
    }

    /**
     * Remove connected tunnel
     */
    public void removeConnectedTunnel(UUID tunnelId) {
        connectedTunnels.remove(tunnelId);
        this.lastUpdated = Instant.now();
    }

    /**
     * Check if tunnel has connection to another
     */
    public boolean isConnectedTo(UUID tunnelId) {
        return connectedTunnels.contains(tunnelId);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tunnel other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Tunnel{id=%s, name='%s', type=%s, state=%s, entrances=%d}",
            id, name, type, state, entrances.size());
    }
}