package dev.starcore.starcore.foundation.player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryPlayerProfileService implements PlayerProfileService {
    private final ConcurrentMap<UUID, String> profiles = new ConcurrentHashMap<>();

    @Override
    public void recordSeen(UUID playerId, String lastKnownName) {
        profiles.put(playerId, lastKnownName);
    }

    @Override
    public Map<UUID, String> snapshot() {
        return Map.copyOf(profiles);
    }

    @Override
    public Optional<String> lastKnownName(UUID playerId) {
        return Optional.ofNullable(profiles.get(playerId));
    }
}
