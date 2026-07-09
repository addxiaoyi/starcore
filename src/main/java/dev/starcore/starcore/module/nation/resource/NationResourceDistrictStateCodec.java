package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

final class NationResourceDistrictStateCodec {
    private NationResourceDistrictStateCodec() {
    }

    static Properties toProperties(Collection<NationResourceDistrict> districts) {
        Properties properties = new Properties();
        List<NationResourceDistrict> snapshot = districts.stream()
            .sorted((left, right) -> left.id().toString().compareTo(right.id().toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            NationResourceDistrict district = snapshot.get(index);
            String prefix = "district." + index + '.';
            properties.setProperty(prefix + "id", district.id().toString());
            properties.setProperty(prefix + "nationId", district.nationId().toString());
            properties.setProperty(prefix + "world", district.coordinate().world());
            properties.setProperty(prefix + "chunkX", String.valueOf(district.coordinate().x()));
            properties.setProperty(prefix + "chunkZ", String.valueOf(district.coordinate().z()));
            properties.setProperty(prefix + "biome", district.biomeName());
            properties.setProperty(prefix + "richness", String.valueOf(district.biomeRichness()));
            properties.setProperty(prefix + "beaconX", String.valueOf(district.beaconX()));
            properties.setProperty(prefix + "beaconY", String.valueOf(district.beaconY()));
            properties.setProperty(prefix + "beaconZ", String.valueOf(district.beaconZ()));
            properties.setProperty(prefix + "totalExperience", String.valueOf(district.totalExperience()));
            properties.setProperty(prefix + "lastRefreshAtMillis", String.valueOf(district.lastRefreshAtMillis()));
            properties.setProperty(prefix + "nextRefreshAtMillis", String.valueOf(district.nextRefreshAtMillis()));
            properties.setProperty(prefix + "migrationState", district.migrationState().name());
            if (district.pendingTarget() != null) {
                properties.setProperty(prefix + "pendingWorld", district.pendingTarget().world());
                properties.setProperty(prefix + "pendingChunkX", String.valueOf(district.pendingTarget().x()));
                properties.setProperty(prefix + "pendingChunkZ", String.valueOf(district.pendingTarget().z()));
            }
            properties.setProperty(prefix + "migrationRequestedAtMillis", String.valueOf(district.migrationRequestedAtMillis()));
            properties.setProperty(prefix + "forceMigrationAtMillis", String.valueOf(district.forceMigrationAtMillis()));
            List<ResourceBlockLocation> resourceBlocks = List.copyOf(district.resourceBlocks());
            properties.setProperty(prefix + "resourceBlockCount", String.valueOf(resourceBlocks.size()));
            for (int blockIndex = 0; blockIndex < resourceBlocks.size(); blockIndex++) {
                properties.setProperty(prefix + "resourceBlock." + blockIndex, resourceBlocks.get(blockIndex).serialize());
            }
        }
        return properties;
    }

    static List<NationResourceDistrict> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        List<NationResourceDistrict> districts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            NationResourceDistrict district = loadDistrict(properties, "district." + index + '.');
            if (district != null) {
                districts.add(district);
            }
        }
        return List.copyOf(districts);
    }

    private static NationResourceDistrict loadDistrict(Properties properties, String prefix) {
        try {
            UUID id = UUID.fromString(properties.getProperty(prefix + "id"));
            NationId nationId = new NationId(UUID.fromString(properties.getProperty(prefix + "nationId")));
            ChunkCoordinate coordinate = new ChunkCoordinate(
                properties.getProperty(prefix + "world"),
                Integer.parseInt(properties.getProperty(prefix + "chunkX")),
                Integer.parseInt(properties.getProperty(prefix + "chunkZ"))
            );
            NationResourceDistrict district = new NationResourceDistrict(
                id,
                nationId,
                coordinate,
                properties.getProperty(prefix + "biome", "unknown"),
                parseDouble(properties.getProperty(prefix + "richness"), 1.0D)
            );
            district.setBeacon(
                parseInt(properties.getProperty(prefix + "beaconX"), 0),
                parseInt(properties.getProperty(prefix + "beaconY"), 0),
                parseInt(properties.getProperty(prefix + "beaconZ"), 0)
            );
            district.setTotalExperience(parseLong(properties.getProperty(prefix + "totalExperience"), 0L));
            district.setRefreshTimes(
                parseLong(properties.getProperty(prefix + "lastRefreshAtMillis"), 0L),
                parseLong(properties.getProperty(prefix + "nextRefreshAtMillis"), 0L)
            );
            district.setMigration(
                parseMigrationState(properties.getProperty(prefix + "migrationState")),
                parsePendingTarget(properties, prefix),
                parseLong(properties.getProperty(prefix + "migrationRequestedAtMillis"), 0L),
                parseLong(properties.getProperty(prefix + "forceMigrationAtMillis"), 0L)
            );
            int blockCount = parseInt(properties.getProperty(prefix + "resourceBlockCount"), 0);
            for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
                ResourceBlockLocation location = ResourceBlockLocation.parse(properties.getProperty(prefix + "resourceBlock." + blockIndex));
                if (location != null) {
                    district.resourceBlocks().add(location);
                }
            }
            return district;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static ChunkCoordinate parsePendingTarget(Properties properties, String prefix) {
        String world = properties.getProperty(prefix + "pendingWorld");
        if (world == null || world.isBlank()) {
            return null;
        }
        return new ChunkCoordinate(
            world,
            parseInt(properties.getProperty(prefix + "pendingChunkX"), 0),
            parseInt(properties.getProperty(prefix + "pendingChunkZ"), 0)
        );
    }

    private static NationResourceDistrict.MigrationState parseMigrationState(String raw) {
        if (raw == null || raw.isBlank()) {
            return NationResourceDistrict.MigrationState.NONE;
        }
        try {
            return NationResourceDistrict.MigrationState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return NationResourceDistrict.MigrationState.NONE;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
