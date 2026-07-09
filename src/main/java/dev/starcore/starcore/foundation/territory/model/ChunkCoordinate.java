package dev.starcore.starcore.foundation.territory.model;

import java.util.Objects;

public record ChunkCoordinate(String world, int x, int z) {
    public ChunkCoordinate {
        Objects.requireNonNull(world, "world");
    }

    @Override
    public String toString() {
        return world + ':' + x + ':' + z;
    }
}
