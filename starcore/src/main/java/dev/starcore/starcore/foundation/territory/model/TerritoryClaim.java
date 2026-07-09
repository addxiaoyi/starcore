package dev.starcore.starcore.foundation.territory.model;

import java.util.Objects;

public record TerritoryClaim(String ownerId, ChunkCoordinate coordinate) {
    public TerritoryClaim {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(coordinate, "coordinate");
    }
}
