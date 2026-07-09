package dev.starcore.starcore.foundation.player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileService {
    void recordSeen(UUID playerId, String lastKnownName);

    Map<UUID, String> snapshot();

    Optional<String> lastKnownName(UUID playerId);
}
