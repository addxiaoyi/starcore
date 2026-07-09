package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.UUID;

public record NationResourceDistrictSnapshot(
    UUID id,
    NationId nationId,
    ChunkCoordinate coordinate,
    String biomeName,
    double biomeRichness,
    int beaconX,
    int beaconY,
    int beaconZ,
    int remainingResources,
    long totalExperience,
    long nextRefreshAtMillis,
    String migrationState,
    ChunkCoordinate pendingTarget,
    long forceMigrationAtMillis
) {
}
