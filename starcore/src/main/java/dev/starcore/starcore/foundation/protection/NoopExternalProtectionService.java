package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class NoopExternalProtectionService implements ExternalProtectionService {
    private final ExternalProtectionBridgeStatus status;

    public NoopExternalProtectionService(String summary) {
        this("", "", "disabled", false, summary);
    }

    public NoopExternalProtectionService(String bridgeName, String stateKey, String summary) {
        this("", bridgeName, stateKey, false, summary);
    }

    public NoopExternalProtectionService(String bridgeKey, String bridgeName, String stateKey, boolean active, String summary) {
        this.status = new ExternalProtectionBridgeStatus(
            Objects.requireNonNull(bridgeKey, "bridgeKey"),
            Objects.requireNonNull(bridgeName, "bridgeName"),
            Objects.requireNonNull(stateKey, "stateKey"),
            active,
            Objects.requireNonNull(summary, "summary")
        );
    }

    @Override
    public Optional<ProtectionConflict> findClaimConflict(ChunkClaimSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return Optional.empty();
    }

    @Override
    public String summary() {
        return status.displaySummary();
    }

    @Override
    public List<ExternalProtectionBridgeStatus> bridgeStatuses() {
        return List.of(status);
    }
}
