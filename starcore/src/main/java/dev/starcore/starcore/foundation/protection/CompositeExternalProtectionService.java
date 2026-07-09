package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CompositeExternalProtectionService implements ExternalProtectionService {
    private final List<ExternalProtectionService> activeServices;
    private final List<ExternalProtectionService> inactiveServices;

    CompositeExternalProtectionService(List<ExternalProtectionService> activeServices, List<ExternalProtectionService> inactiveServices) {
        this.activeServices = List.copyOf(Objects.requireNonNull(activeServices, "activeServices"));
        this.inactiveServices = List.copyOf(Objects.requireNonNull(inactiveServices, "inactiveServices"));
    }

    @Override
    public Optional<ProtectionConflict> findClaimConflict(ChunkClaimSelection selection) {
        Objects.requireNonNull(selection, "selection");
        for (ExternalProtectionService service : activeServices) {
            Optional<ProtectionConflict> conflict = service.findClaimConflict(selection);
            if (conflict.isPresent()) {
                return conflict;
            }
        }
        return Optional.empty();
    }

    @Override
    public String summary() {
        if (activeServices.size() == 1 && inactiveServices.isEmpty()) {
            return activeServices.get(0).summary();
        }
        if (activeServices.isEmpty() && inactiveServices.size() == 1) {
            return inactiveServices.get(0).summary();
        }
        List<String> summaries = new ArrayList<>(activeServices.size() + inactiveServices.size());
        for (ExternalProtectionService service : activeServices) {
            String summary = service.summary();
            if (summary != null && !summary.isBlank()) {
                summaries.add(summary);
            }
        }
        for (ExternalProtectionService service : inactiveServices) {
            String summary = service.summary();
            if (summary != null && !summary.isBlank()) {
                summaries.add(summary);
            }
        }
        if (summaries.isEmpty()) {
            return "未配置外部保护桥";
        }
        return String.join(" | ", summaries);
    }

    @Override
    public List<ExternalProtectionBridgeStatus> bridgeStatuses() {
        List<ExternalProtectionBridgeStatus> statuses = new ArrayList<>(activeServices.size() + inactiveServices.size());
        for (ExternalProtectionService service : activeServices) {
            statuses.addAll(service.bridgeStatuses());
        }
        for (ExternalProtectionService service : inactiveServices) {
            statuses.addAll(service.bridgeStatuses());
        }
        if (statuses.isEmpty()) {
            return List.of(new ExternalProtectionBridgeStatus("external.unconfigured", "外部保护桥", "unconfigured", false, "未配置外部保护桥"));
        }
        return List.copyOf(statuses);
    }
}
