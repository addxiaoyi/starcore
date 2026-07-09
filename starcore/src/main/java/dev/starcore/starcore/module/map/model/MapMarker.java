package dev.starcore.starcore.module.map.model;

import java.util.Map;

public record MapMarker(
    String id,
    String label,
    String world,
    double x,
    double z,
    String icon,
    Map<String, String> metadata
) {
    public MapMarker {
        metadata = Map.copyOf(metadata);
    }
}
