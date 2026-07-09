package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class NationResourceDistrict {
    private final UUID id;
    private final NationId nationId;
    private ChunkCoordinate coordinate;
    private String biomeName;
    private double biomeRichness;
    private int beaconX;
    private int beaconY;
    private int beaconZ;
    private long totalExperience;
    private long lastRefreshAtMillis;
    private long nextRefreshAtMillis;
    private MigrationState migrationState;
    private ChunkCoordinate pendingTarget;
    private long migrationRequestedAtMillis;
    private long forceMigrationAtMillis;
    private final List<ResourceBlockLocation> resourceBlocks;

    NationResourceDistrict(UUID id, NationId nationId, ChunkCoordinate coordinate, String biomeName, double biomeRichness) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.biomeName = biomeName == null || biomeName.isBlank() ? "unknown" : biomeName;
        this.biomeRichness = Math.max(0.01D, biomeRichness);
        this.migrationState = MigrationState.NONE;
        this.resourceBlocks = new ArrayList<>();
    }

    UUID id() { return id; }
    NationId nationId() { return nationId; }
    ChunkCoordinate coordinate() { return coordinate; }
    String biomeName() { return biomeName; }
    double biomeRichness() { return biomeRichness; }
    int beaconX() { return beaconX; }
    int beaconY() { return beaconY; }
    int beaconZ() { return beaconZ; }
    long totalExperience() { return totalExperience; }
    long lastRefreshAtMillis() { return lastRefreshAtMillis; }
    long nextRefreshAtMillis() { return nextRefreshAtMillis; }
    MigrationState migrationState() { return migrationState; }
    ChunkCoordinate pendingTarget() { return pendingTarget; }
    long migrationRequestedAtMillis() { return migrationRequestedAtMillis; }
    long forceMigrationAtMillis() { return forceMigrationAtMillis; }
    List<ResourceBlockLocation> resourceBlocks() { return resourceBlocks; }

    void setCoordinate(ChunkCoordinate coordinate, String biomeName, double biomeRichness) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.biomeName = biomeName == null || biomeName.isBlank() ? "unknown" : biomeName;
        this.biomeRichness = Math.max(0.01D, biomeRichness);
    }

    void setBeacon(int x, int y, int z) {
        this.beaconX = x;
        this.beaconY = y;
        this.beaconZ = z;
    }

    void setTotalExperience(long totalExperience) {
        this.totalExperience = Math.max(0L, totalExperience);
    }

    void addExperience(long amount) {
        if (amount <= 0L) {
            return;
        }
        long updated = totalExperience + amount;
        this.totalExperience = updated < totalExperience ? Long.MAX_VALUE : updated;
    }

    void setRefreshTimes(long lastRefreshAtMillis, long nextRefreshAtMillis) {
        this.lastRefreshAtMillis = Math.max(0L, lastRefreshAtMillis);
        this.nextRefreshAtMillis = Math.max(0L, nextRefreshAtMillis);
    }

    void setMigration(MigrationState migrationState, ChunkCoordinate pendingTarget, long migrationRequestedAtMillis, long forceMigrationAtMillis) {
        this.migrationState = Objects.requireNonNull(migrationState, "migrationState");
        this.pendingTarget = pendingTarget;
        this.migrationRequestedAtMillis = Math.max(0L, migrationRequestedAtMillis);
        this.forceMigrationAtMillis = Math.max(0L, forceMigrationAtMillis);
    }

    NationResourceDistrictSnapshot snapshot() {
        return new NationResourceDistrictSnapshot(
            id,
            nationId,
            coordinate,
            biomeName,
            biomeRichness,
            beaconX,
            beaconY,
            beaconZ,
            resourceBlocks.size(),
            totalExperience,
            nextRefreshAtMillis,
            migrationState.name().toLowerCase(),
            pendingTarget,
            forceMigrationAtMillis
        );
    }

    enum MigrationState {
        NONE,
        AWAITING_TARGET,
        WAITING_DEPLETION
    }
}
