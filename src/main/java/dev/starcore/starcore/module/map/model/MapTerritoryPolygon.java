package dev.starcore.starcore.module.map.model;

import java.util.Map;

public record MapTerritoryPolygon(
    String ownerId,
    String ownerName,
    String world,
    int chunkX,
    int chunkZ,
    String fillColor,
    Map<String, String> metadata
) {
    public MapTerritoryPolygon {
        metadata = Map.copyOf(metadata);
    }
}
