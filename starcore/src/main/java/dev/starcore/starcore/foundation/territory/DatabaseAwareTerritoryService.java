package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class DatabaseAwareTerritoryService implements TerritoryService {
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private volatile TerritoryService delegate;

    public DatabaseAwareTerritoryService(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.delegate = databaseService.isRunning()
            ? new SqlTerritoryService(databaseService, persistenceService, logger)
            : new InMemoryTerritoryService(persistenceService);
    }

    @Override
    public Optional<TerritoryClaim> claimAt(ChunkCoordinate coordinate) {
        return currentDelegate().claimAt(coordinate);
    }

    @Override
    public boolean isClaimed(ChunkCoordinate coordinate) {
        return currentDelegate().isClaimed(coordinate);
    }

    @Override
    public boolean claim(String ownerId, ChunkCoordinate coordinate) {
        return currentDelegate().claim(ownerId, coordinate);
    }

    @Override
    public boolean unclaim(String ownerId, ChunkCoordinate coordinate) {
        return currentDelegate().unclaim(ownerId, coordinate);
    }

    @Override
    public int claimedChunkCount() {
        return currentDelegate().claimedChunkCount();
    }

    @Override
    public Collection<TerritoryClaim> claimsByOwner(String ownerId) {
        return currentDelegate().claimsByOwner(ownerId);
    }

    private TerritoryService currentDelegate() {
        TerritoryService current = delegate;
        if (!(current instanceof SqlTerritoryService) && databaseService.isRunning()) {
            synchronized (this) {
                current = delegate;
                if (!(current instanceof SqlTerritoryService) && databaseService.isRunning()) {
                    if (current instanceof InMemoryTerritoryService inMemoryTerritoryService) {
                        inMemoryTerritoryService.save();
                    }
                    delegate = current = new SqlTerritoryService(databaseService, persistenceService, logger);
                }
            }
        }
        return current;
    }
}
