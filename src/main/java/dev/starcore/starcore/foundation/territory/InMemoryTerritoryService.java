package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTerritoryService implements TerritoryService {
    private static final String NAMESPACE = "territory";
    private static final String FILE_NAME = "claims.properties";

    private final Map<ChunkCoordinate, TerritoryClaim> claims = new ConcurrentHashMap<>();
    private final PersistenceService persistenceService;

    public InMemoryTerritoryService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        load();
    }

    @Override
    public Optional<TerritoryClaim> claimAt(ChunkCoordinate coordinate) {
        return Optional.ofNullable(claims.get(coordinate));
    }

    @Override
    public boolean isClaimed(ChunkCoordinate coordinate) {
        return claims.containsKey(coordinate);
    }

    @Override
    public boolean claim(String ownerId, ChunkCoordinate coordinate) {
        boolean claimed = claims.putIfAbsent(coordinate, new TerritoryClaim(ownerId, coordinate)) == null;
        if (claimed) {
            saveAsync();
        }
        return claimed;
    }

    @Override
    public boolean unclaim(String ownerId, ChunkCoordinate coordinate) {
        TerritoryClaim current = claims.get(coordinate);
        if (current == null || !current.ownerId().equals(ownerId)) {
            return false;
        }
        boolean removed = claims.remove(coordinate, current);
        if (removed) {
            saveAsync();
        }
        return removed;
    }

    @Override
    public int claimedChunkCount() {
        return claims.size();
    }

    @Override
    public Collection<TerritoryClaim> claimsByOwner(String ownerId) {
        return List.copyOf(claims.values().stream().filter(claim -> claim.ownerId().equals(ownerId)).toList());
    }

    public void save() {
        persistenceService.saveProperties(NAMESPACE, FILE_NAME, stateProperties());
    }

    private void saveAsync() {
        persistenceService.savePropertiesAsync(NAMESPACE, FILE_NAME, stateProperties());
    }

    private java.util.Properties stateProperties() {
        return TerritoryStateCodec.toProperties(claims.values());
    }

    private void load() {
        claims.clear();
        claims.putAll(TerritoryStateCodec.fromProperties(persistenceService.loadProperties(NAMESPACE, FILE_NAME)));
    }
}
