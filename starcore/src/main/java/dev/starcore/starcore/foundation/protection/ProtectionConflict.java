package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.util.Objects;

public record ProtectionConflict(
    String providerName,
    String rangeName,
    String rangeId,
    ChunkCoordinate coordinate
) {
    public ProtectionConflict {
        providerName = normalize(providerName);
        rangeName = normalize(rangeName);
        rangeId = normalize(rangeId);
        Objects.requireNonNull(coordinate, "coordinate");
    }

    public String displayLabel() {
        if (!rangeName.isBlank()) {
            return rangeName;
        }
        if (!rangeId.isBlank()) {
            return rangeId;
        }
        return coordinate.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
