package dev.starcore.starcore.module.map.model;

import java.time.Instant;
import java.util.List;

public record MapSnapshot(
    Instant generatedAt,
    List<MapLayerSnapshot> layers
) {
    public MapSnapshot {
        layers = List.copyOf(layers);
    }
}
