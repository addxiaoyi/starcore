package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.map.model.MapLayerSnapshot;
import dev.starcore.starcore.module.map.model.MapLayerType;
import dev.starcore.starcore.module.map.model.MapMarker;
import dev.starcore.starcore.module.map.model.MapSnapshot;
import dev.starcore.starcore.module.map.model.MapTerritoryPolygon;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

final class MapSnapshotJsonWriter {
    // E-078: toJson 在请求处理线程同步构造 StringBuilder(4096) + 拼装大量 JSON,
    // 但这是单次请求的轻量操作(~4KB base + 几百条记录),通常可在 ms 内完成。
    // 该问题已从 SSE broadcast 路径缓解(snapshotJson 按 access 分桶缓存),
    // 唯独直接 HTTP 请求时仍会重建一次 JSON。改为异步预生成需要跨模块事件订阅器,
    // 改动范围过大,这里保留现状但记录后续可优化方向:
    // (1) 在 MapModule 用 lastSnapshotJson 缓存 + 失效标记;
    // (2) 把全量 JSON 拆成可分支缓存的国家/领土/玩家三层子文档。
    String toJson(
        MapSnapshot snapshot,
        Set<String> visibleNationIds,
        MapViewerAccess access,
        Supplier<Set<String>> publicWorlds,
        Function<MapViewerAccess, String> viewerJson,
        Function<Set<String>, String> terrainJson,
        Map<String, Map<String, String>> diplomacyRelations
    ) {
        MapSnapshot safeSnapshot = snapshot == null ? new MapSnapshot(Instant.EPOCH, List.of()) : snapshot;
        Set<String> safeVisibleNationIds = visibleNationIds == null ? Set.of() : visibleNationIds;
        MapViewerAccess safeAccess = access == null ? MapViewerAccess.publicView() : access;
        Set<String> worlds = safeAccess.isPublic() ? safePublicWorlds(publicWorlds) : collectWorlds(safeSnapshot);
        int claimCount = safeSnapshot.layers().stream().mapToInt(layer -> layer.territories().size()).sum();
        int onlinePlayers = markerCount(safeSnapshot, MapLayerType.PLAYER_MARKERS);
        int resourceDistricts = markerCount(safeSnapshot, MapLayerType.RESOURCE_DISTRICTS);

        StringBuilder builder = new StringBuilder(4096);
        builder.append('{');
        appendField(builder, "generatedAt", safeSnapshot.generatedAt().toString());
        builder.append(',');
        builder.append("\"access\":");
        appendAccess(builder, safeAccess);
        builder.append(',');
        builder.append("\"viewer\":");
        builder.append(viewerJson == null ? "null" : viewerJson.apply(safeAccess));
        builder.append(',');
        builder.append("\"summary\":{");
        appendNumberField(builder, "nationCount", safeVisibleNationIds.size());
        builder.append(',');
        appendNumberField(builder, "claimCount", claimCount);
        builder.append(',');
        appendNumberField(builder, "onlinePlayers", onlinePlayers);
        builder.append(',');
        appendNumberField(builder, "resourceDistricts", resourceDistricts);
        builder.append(',');
        appendNumberField(builder, "worldCount", worlds.size());
        builder.append('}');
        builder.append(',');
        appendWorlds(builder, worlds);
        builder.append(',');
        builder.append(safeTerrainJson(terrainJson, worlds));
        builder.append(',');
        appendDiplomacy(builder, safeVisibleNationIds, diplomacyRelations);
        builder.append(',');
        appendLayers(builder, safeSnapshot.layers());
        builder.append('}');
        return builder.toString();
    }

    String accessJson(MapViewerAccess access) {
        StringBuilder builder = new StringBuilder(128);
        appendAccess(builder, access == null ? MapViewerAccess.publicView() : access);
        return builder.toString();
    }

    private Set<String> safePublicWorlds(Supplier<Set<String>> publicWorlds) {
        if (publicWorlds == null) {
            return Set.of();
        }
        Set<String> worlds = publicWorlds.get();
        return worlds == null ? Set.of() : worlds;
    }

    private String safeTerrainJson(Function<Set<String>, String> terrainJson, Set<String> worlds) {
        if (terrainJson == null) {
            return "\"terrain\":{}";
        }
        String json = terrainJson.apply(worlds);
        return json == null || json.isBlank() ? "\"terrain\":{}" : json;
    }

    private int markerCount(MapSnapshot snapshot, MapLayerType type) {
        return snapshot.layers().stream()
            .filter(layer -> layer.type() == type)
            .mapToInt(layer -> layer.markers().size())
            .sum();
    }

    private void appendAccess(StringBuilder builder, MapViewerAccess access) {
        builder.append('{');
        appendField(builder, "mode", access.source());
        builder.append(',');
        appendField(builder, "scope", access.isPublic() ? "public" : (access.fullAccess() ? "full" : "allied"));
        builder.append(',');
        appendBooleanField(builder, "authenticated", !access.isPublic());
        builder.append(',');
        appendField(builder, "expiresAt", access.isPublic() ? "" : Instant.ofEpochSecond(access.expiresAtEpochSecond()).toString());
        builder.append('}');
    }

    private void appendWorlds(StringBuilder builder, Set<String> worlds) {
        builder.append("\"worlds\":[");
        int worldIndex = 0;
        for (String world : worlds) {
            if (worldIndex++ > 0) {
                builder.append(',');
            }
            appendStringValue(builder, world);
        }
        builder.append(']');
    }

    private void appendDiplomacy(
        StringBuilder builder,
        Set<String> visibleNationIds,
        Map<String, Map<String, String>> diplomacyRelations
    ) {
        Map<String, Map<String, String>> relationsBySource = diplomacyRelations == null ? Map.of() : diplomacyRelations;
        builder.append("\"diplomacy\":{");
        builder.append("\"relations\":{");
        List<String> nations = visibleNationIds.stream().sorted().toList();
        int sourceIndex = 0;
        for (String source : nations) {
            if (sourceIndex++ > 0) {
                builder.append(',');
            }
            appendStringValue(builder, source);
            builder.append(':');
            builder.append('{');
            int targetIndex = 0;
            Map<String, String> relations = relationsBySource.getOrDefault(source, Map.of());
            for (String target : nations) {
                if (targetIndex++ > 0) {
                    builder.append(',');
                }
                appendStringValue(builder, target);
                builder.append(':');
                String relation = source.equals(target)
                    ? "member"
                    : relations.getOrDefault(target, DiplomacyRelation.NEUTRAL.name().toLowerCase());
                appendStringValue(builder, relation);
            }
            builder.append('}');
        }
        builder.append('}');
        builder.append('}');
    }

    private Set<String> collectWorlds(MapSnapshot snapshot) {
        Set<String> worlds = new LinkedHashSet<>();
        for (MapLayerSnapshot layer : snapshot.layers()) {
            layer.territories().forEach(territory -> worlds.add(territory.world()));
            layer.markers().forEach(marker -> worlds.add(marker.world()));
        }
        return worlds;
    }

    private void appendLayers(StringBuilder builder, List<MapLayerSnapshot> layers) {
        builder.append("\"layers\":[");
        for (int index = 0; index < layers.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendLayer(builder, layers.get(index));
        }
        builder.append(']');
    }

    private void appendLayer(StringBuilder builder, MapLayerSnapshot layer) {
        builder.append('{');
        appendField(builder, "type", layer.type().name());
        builder.append(',');
        builder.append("\"territories\":[");
        for (int i = 0; i < layer.territories().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendTerritory(builder, layer.territories().get(i));
        }
        builder.append(']');
        builder.append(',');
        builder.append("\"markers\":[");
        for (int i = 0; i < layer.markers().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendMarker(builder, layer.markers().get(i));
        }
        builder.append(']');
        builder.append('}');
    }

    private void appendTerritory(StringBuilder builder, MapTerritoryPolygon territory) {
        builder.append('{');
        appendField(builder, "ownerId", territory.ownerId());
        builder.append(',');
        appendField(builder, "ownerName", territory.ownerName());
        builder.append(',');
        appendField(builder, "world", territory.world());
        builder.append(',');
        appendNumberField(builder, "chunkX", territory.chunkX());
        builder.append(',');
        appendNumberField(builder, "chunkZ", territory.chunkZ());
        builder.append(',');
        appendField(builder, "fillColor", territory.fillColor());
        builder.append(',');
        appendMetadata(builder, territory.metadata());
        builder.append('}');
    }

    private void appendMarker(StringBuilder builder, MapMarker marker) {
        builder.append('{');
        appendField(builder, "id", marker.id());
        builder.append(',');
        appendField(builder, "label", marker.label());
        builder.append(',');
        appendField(builder, "world", marker.world());
        builder.append(',');
        appendDecimalField(builder, "x", marker.x());
        builder.append(',');
        appendDecimalField(builder, "z", marker.z());
        builder.append(',');
        appendField(builder, "icon", marker.icon());
        builder.append(',');
        appendMetadata(builder, marker.metadata());
        builder.append('}');
    }

    private void appendMetadata(StringBuilder builder, Map<String, String> metadata) {
        builder.append("\"metadata\":{");
        int index = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            appendField(builder, entry.getKey(), entry.getValue());
        }
        builder.append('}');
    }

    private void appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private void appendStringValue(StringBuilder builder, String value) {
        builder.append('"').append(escape(value)).append('"');
    }

    private void appendNumberField(StringBuilder builder, String name, int value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendBooleanField(StringBuilder builder, String name, boolean value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendDecimalField(StringBuilder builder, String name, double value) {
        builder.append('"').append(escape(name)).append("\":").append(Double.toString(value));
    }

    private String escape(String input) {
        return (input == null ? "" : input)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
