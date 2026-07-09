package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

final class GuardedExternalProtectionService implements ExternalProtectionService {
    private final String providerKey;
    private final String providerName;
    private final ExternalProtectionService delegate;
    private final Logger logger;

    GuardedExternalProtectionService(String providerKey, String providerName, ExternalProtectionService delegate, Logger logger) {
        this.providerKey = Objects.requireNonNull(providerKey, "providerKey");
        this.providerName = Objects.requireNonNull(providerName, "providerName");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Optional<ProtectionConflict> findClaimConflict(ChunkClaimSelection selection) {
        Objects.requireNonNull(selection, "selection");
        try {
            return delegate.findClaimConflict(selection);
        } catch (RuntimeException exception) {
            logger.warning("External protection bridge '" + providerName + "' query failed: " + exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String summary() {
        return safeSummary(true);
    }

    @Override
    public List<ExternalProtectionBridgeStatus> bridgeStatuses() {
        try {
            List<ExternalProtectionBridgeStatus> statuses = delegate.bridgeStatuses();
            if (statuses != null && !statuses.isEmpty()) {
                return statuses.stream()
                    .map(this::normalizeStatus)
                    .toList();
            }
            String summary = safeSummary(false);
            String stateKey = inferStateKey(summary);
            return List.of(new ExternalProtectionBridgeStatus(
                providerKey,
                providerName,
                stateKey,
                "active".equals(stateKey),
                summary
            ));
        } catch (RuntimeException exception) {
            logger.warning("External protection bridge '" + providerName + "' status list failed: " + exception.getMessage());
            return List.of(new ExternalProtectionBridgeStatus(providerKey, providerName, "error", false, providerName + " 状态读取失败"));
        }
    }

    private String safeSummary(boolean logFailure) {
        try {
            String summary = delegate.summary();
            if (summary == null || summary.isBlank()) {
                return providerName + " 状态读取失败";
            }
            return summary;
        } catch (RuntimeException exception) {
            if (logFailure) {
                logger.warning("External protection bridge '" + providerName + "' summary failed: " + exception.getMessage());
            }
            return providerName + " 状态读取失败";
        }
    }

    private ExternalProtectionBridgeStatus normalizeStatus(ExternalProtectionBridgeStatus status) {
        if (status == null) {
            return new ExternalProtectionBridgeStatus(providerKey, providerName, "error", false, providerName + " 状态读取失败");
        }
        return new ExternalProtectionBridgeStatus(
            status.bridgeKey().isBlank() ? providerKey : status.bridgeKey(),
            status.bridgeName().isBlank() ? providerName : status.bridgeName(),
            status.stateKey().isBlank() ? inferStateKey(status.displaySummary()) : status.stateKey(),
            status.active(),
            status.displaySummary()
        );
    }

    private static String inferStateKey(String summary) {
        String normalized = summary == null ? "" : summary.trim();
        if (normalized.isBlank()) {
            return "error";
        }
        if (normalized.contains("未配置")) {
            return "unconfigured";
        }
        if (normalized.contains("已关闭")) {
            return "disabled";
        }
        if (normalized.contains("未安装") || normalized.contains("未启用")) {
            return "missing";
        }
        if (normalized.contains("失败") || normalized.contains("异常")) {
            return "error";
        }
        return "active";
    }
}
