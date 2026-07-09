package dev.starcore.starcore.module.map.model;

import java.util.List;

public record MapLayerSnapshot(
    MapLayerType type,
    List<MapTerritoryPolygon> territories,
    List<MapMarker> markers
) {
    public MapLayerSnapshot {
        territories = List.copyOf(territories);
        markers = List.copyOf(markers);
    }
}
