package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class TerritoryStateCodec {
    private TerritoryStateCodec() {
    }

    static Properties toProperties(Collection<TerritoryClaim> claims) {
        Properties properties = new Properties();
        var snapshot = claims.stream()
            .sorted(Comparator.comparing(claim -> claim.coordinate().toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            TerritoryClaim claim = snapshot.get(index);
            String prefix = "claim." + index + '.';
            properties.setProperty(prefix + "owner", claim.ownerId());
            properties.setProperty(prefix + "world", claim.coordinate().world());
            properties.setProperty(prefix + "x", String.valueOf(claim.coordinate().x()));
            properties.setProperty(prefix + "z", String.valueOf(claim.coordinate().z()));
        }
        return properties;
    }

    static Map<ChunkCoordinate, TerritoryClaim> fromProperties(Properties properties) {
        Map<ChunkCoordinate, TerritoryClaim> claims = new LinkedHashMap<>();
        int count = parseInt(properties.getProperty("count"), 0);
        for (int index = 0; index < count; index++) {
            String prefix = "claim." + index + '.';
            String owner = properties.getProperty(prefix + "owner");
            String world = properties.getProperty(prefix + "world");
            int x = parseInt(properties.getProperty(prefix + "x"), Integer.MIN_VALUE);
            int z = parseInt(properties.getProperty(prefix + "z"), Integer.MIN_VALUE);
            if (owner == null || owner.isBlank() || world == null || world.isBlank() || x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
                continue;
            }
            ChunkCoordinate coordinate = new ChunkCoordinate(world, x, z);
            claims.put(coordinate, new TerritoryClaim(owner, coordinate));
        }
        return claims;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
