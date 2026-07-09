package dev.starcore.starcore.module.map;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.module.map.model.WebClaimConfirmationResult;
import dev.starcore.starcore.module.nation.model.ClaimChunkPrice;
import dev.starcore.starcore.module.nation.model.ClaimPriceBreakdown;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MapClaimEndpoint {
    private static final int PENDING_ID_LENGTH = 8;

    private final Map<String, PendingClaim> pendingClaims = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> cooldowns = new ConcurrentHashMap<>();

    Response response(UUID viewerId, Map<String, String> params, boolean submitRequest, Settings settings) {
        ChunkClaimSelection selection = selectionFromParams(params, settings.messages());
        return buildResponse(viewerId, selection, submitRequest, settings);
    }

    WebClaimConfirmationResult confirm(UUID playerId, String pendingId, Settings settings, ConfirmationHandler handler) {
        MessageResolver messages = settings.messages();
        if (playerId == null || pendingId == null || pendingId.isBlank()) {
            String message = messages.message("command.map.confirm-id-invalid");
            return failedConfirmation("", message, endpointExplanation(
                "pending-id-invalid",
                "error",
                message,
                ClaimSelectionReason.of("pending-id-invalid", message)
            ));
        }
        PendingClaim pending = pendingClaims.get(pendingId.trim());
        if (pending == null) {
            String message = messages.message("command.map.confirm-not-found");
            return failedConfirmation(pendingId, message, endpointExplanation(
                "pending-not-found",
                "error",
                message,
                ClaimSelectionReason.of("pending-not-found", message, Map.of("pendingId", pendingId.trim()))
            ));
        }
        if (!pending.playerId().equals(playerId)) {
            String message = messages.message("command.map.confirm-not-owner");
            return failedConfirmation(pending.id(), message, endpointExplanation(
                "pending-not-owner",
                "error",
                message,
                ClaimSelectionReason.of("pending-not-owner", message, Map.of("pendingId", pending.id()))
            ));
        }
        if (Instant.now().isAfter(pending.expiresAt())) {
            pendingClaims.remove(pending.id());
            String message = messages.message("command.map.confirm-expired");
            return failedConfirmation(pending.id(), message, endpointExplanation(
                "pending-expired",
                "warning",
                message,
                ClaimSelectionReason.of(
                    "pending-expired",
                    message,
                    Map.of(
                        "pendingId", pending.id(),
                        "expiresAt", pending.expiresAt().toString()
                    )
                )
            ));
        }
        pendingClaims.remove(pending.id());
        try {
            WebClaimConfirmationResult result = handler.confirm(pending);
            return result == null
                ? failedConfirmation(pending.id(), messages.message("command.map.confirm-state-changed"), endpointExplanation(
                    "confirm-failed",
                    "error",
                    messages.message("command.map.confirm-state-changed"),
                    ClaimSelectionReason.of("confirm-failed", messages.message("command.map.confirm-state-changed"), Map.of("pendingId", pending.id()))
                ))
                : result;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? messages.message("command.map.confirm-state-changed")
                : exception.getMessage();
            return failedConfirmation(pending.id(), message, endpointExplanation(
                "confirm-failed",
                "error",
                message,
                ClaimSelectionReason.of("confirm-failed", message, Map.of("pendingId", pending.id()))
            ));
        }
    }

    WebClaimConfirmationResult cancel(UUID playerId, String pendingId, Settings settings) {
        MessageResolver messages = settings.messages();
        if (playerId == null || pendingId == null || pendingId.isBlank()) {
            String message = messages.message("command.map.confirm-id-invalid");
            return failedConfirmation("", message, endpointExplanation(
                "pending-id-invalid",
                "error",
                message,
                ClaimSelectionReason.of("pending-id-invalid", message)
            ));
        }
        PendingClaim pending = pendingClaims.get(pendingId.trim());
        if (pending == null) {
            String message = messages.message("command.map.confirm-not-found");
            return failedConfirmation(pendingId, message, endpointExplanation(
                "pending-not-found",
                "error",
                message,
                ClaimSelectionReason.of("pending-not-found", message, Map.of("pendingId", pendingId.trim()))
            ));
        }
        if (!pending.playerId().equals(playerId)) {
            String message = messages.message("command.map.confirm-not-owner");
            return failedConfirmation(pending.id(), message, endpointExplanation(
                "pending-not-owner",
                "error",
                message,
                ClaimSelectionReason.of("pending-not-owner", message, Map.of("pendingId", pending.id()))
            ));
        }
        pendingClaims.remove(pending.id());
        if (Instant.now().isAfter(pending.expiresAt())) {
            String message = messages.message("command.map.confirm-expired");
            return failedConfirmation(pending.id(), message, endpointExplanation(
                "pending-expired",
                "warning",
                message,
                ClaimSelectionReason.of(
                    "pending-expired",
                    message,
                    Map.of(
                        "pendingId", pending.id(),
                        "expiresAt", pending.expiresAt().toString()
                    )
                )
            ));
        }
        String message = messages.message("command.map.cancelled");
        return WebClaimConfirmationResult.cancelled(
            pending.id(),
            pending.nationName(),
            pending.selection(),
            pending.chunkCount(),
            pending.price(),
            message,
            endpointExplanation(
                "pending-cancelled",
                "info",
                message,
                ClaimSelectionReason.of(
                    "pending-cancelled",
                    message,
                    Map.of("pendingId", pending.id())
                )
            )
        );
    }

    void clear() {
        pendingClaims.clear();
        cooldowns.clear();
    }

    int pendingCount() {
        cleanupExpired();
        return pendingClaims.size();
    }

    String previewJson(ClaimSelectionPreview preview, int maxSelectionChunks, boolean requestSubmitted, String pendingId, Instant expiresAt, int pricingDetailLimit) {
        ChunkClaimSelection selection = preview.selection();
        StringBuilder builder = new StringBuilder(768);
        builder.append('{');
        appendBooleanField(builder, "ok", preview.canSubmit());
        builder.append(',');
        appendField(builder, "message", preview.message());
        builder.append(',');
        appendClaimExplanationField(builder, preview.explanation());
        builder.append(',');
        appendField(builder, "world", selection.world());
        builder.append(',');
        appendField(builder, "nationId", preview.nationId() == null ? "" : preview.nationId().toString());
        builder.append(',');
        appendField(builder, "nationName", preview.nationName() == null ? "" : preview.nationName());
        builder.append(',');
        appendNumberField(builder, "minChunkX", selection.minChunkX());
        builder.append(',');
        appendNumberField(builder, "maxChunkX", selection.maxChunkX());
        builder.append(',');
        appendNumberField(builder, "minChunkZ", selection.minChunkZ());
        builder.append(',');
        appendNumberField(builder, "maxChunkZ", selection.maxChunkZ());
        builder.append(',');
        appendNumberField(builder, "minX", selection.minBlockX());
        builder.append(',');
        appendNumberField(builder, "maxX", selection.maxBlockX());
        builder.append(',');
        appendNumberField(builder, "minZ", selection.minBlockZ());
        builder.append(',');
        appendNumberField(builder, "maxZ", selection.maxBlockZ());
        builder.append(',');
        appendNumberField(builder, "chunkCount", preview.chunkCount());
        builder.append(',');
        appendNumberField(builder, "overlapCount", preview.overlapCount());
        builder.append(',');
        appendNumberField(builder, "currentClaimCount", preview.currentClaimCount());
        builder.append(',');
        appendNumberField(builder, "maxClaims", preview.maxClaims());
        builder.append(',');
        appendNumberField(builder, "maxSelectionChunks", maxSelectionChunks);
        builder.append(',');
        appendMoneyField(builder, "price", preview.price());
        builder.append(',');
        appendMoneyField(builder, "balance", preview.balance());
        builder.append(',');
        appendClaimPricingField(builder, preview.pricing(), pricingDetailLimit);
        builder.append(',');
        appendBooleanField(builder, "canSubmit", preview.canSubmit());
        builder.append(',');
        appendBooleanField(builder, "requestSubmitted", requestSubmitted && pendingId != null && !pendingId.isBlank());
        builder.append(',');
        appendField(builder, "pendingId", pendingId == null ? "" : pendingId);
        builder.append(',');
        appendField(builder, "expiresAt", expiresAt == null ? "" : expiresAt.toString());
        builder.append('}');
        return builder.toString();
    }

    private Response buildResponse(UUID viewerId, ChunkClaimSelection selection, boolean submitRequest, Settings settings) {
        if (selection.chunkCount() > settings.maxSelectionChunks()) {
            String message = settings.messages().message("command.map.web-claim-too-large", settings.maxSelectionChunks());
            ClaimSelectionPreview limited = new ClaimSelectionPreview(
                null,
                "",
                selection,
                selection.chunkCount(),
                0,
                0,
                settings.maxClaimsPerNation(),
                BigDecimal.ZERO,
                settings.balanceProvider().balance(viewerId),
                false,
                message,
                endpointExplanation(
                    "selection-too-large",
                    "error",
                    message,
                    ClaimSelectionReason.of(
                        "selection-too-large",
                        message,
                        Map.of(
                            "selectionChunks", Integer.toString(selection.chunkCount()),
                            "maxSelectionChunks", Integer.toString(settings.maxSelectionChunks())
                        )
                    )
                )
            );
            return json(200, previewJson(limited, settings.maxSelectionChunks(), submitRequest, null, null, settings.pricingDetailLimit()));
        }
        ClaimSelectionPreview preview = settings.previewProvider().preview(viewerId, selection);
        if (!submitRequest) {
            return json(200, previewJson(preview, settings.maxSelectionChunks(), false, null, null, settings.pricingDetailLimit()));
        }
        if (!preview.canSubmit()) {
            return json(200, previewJson(preview, settings.maxSelectionChunks(), true, null, null, settings.pricingDetailLimit()));
        }
        Instant now = Instant.now();
        Instant cooldownUntil = cooldowns.get(viewerId);
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            String message = settings.messages().message("command.map.web-claim-cooldown");
            ClaimSelectionPreview cooledDown = new ClaimSelectionPreview(
                preview.nationId(),
                preview.nationName(),
                selection,
                preview.chunkCount(),
                preview.overlapCount(),
                preview.currentClaimCount(),
                preview.maxClaims(),
                preview.price(),
                preview.balance(),
                preview.pricing(),
                false,
                message,
                endpointExplanation(
                    "claim-cooldown",
                    "warning",
                    message,
                    ClaimSelectionReason.of(
                        "claim-cooldown",
                        message,
                        Map.of("cooldownUntil", cooldownUntil.toString())
                    )
                )
            );
            return json(200, previewJson(cooledDown, settings.maxSelectionChunks(), true, null, cooldownUntil, settings.pricingDetailLimit()));
        }
        PendingClaim pending = createPendingClaim(viewerId, preview, now, settings);
        pendingClaims.put(pending.id(), pending);
        if (settings.cooldownSeconds() > 0) {
            cooldowns.put(viewerId, now.plusSeconds(settings.cooldownSeconds()));
        }
        settings.pendingNotifier().notify(pending);
        return json(200, previewJson(preview, settings.maxSelectionChunks(), true, pending.id(), pending.expiresAt(), settings.pricingDetailLimit()));
    }

    private ChunkClaimSelection selectionFromParams(Map<String, String> params, MessageResolver messages) {
        String world = params.get("world");
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException(messages.message("command.map.missing-world"));
        }
        return ChunkClaimSelection.fromBlockBounds(
            world.trim(),
            intParam(params, "minX"),
            intParam(params, "maxX"),
            intParam(params, "minZ"),
            intParam(params, "maxZ")
        );
    }

    private int intParam(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private PendingClaim createPendingClaim(UUID viewerId, ClaimSelectionPreview preview, Instant now, Settings settings) {
        cleanupExpired();
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, PENDING_ID_LENGTH);
        } while (pendingClaims.containsKey(id));
        return new PendingClaim(
            id,
            viewerId,
            preview.nationId(),
            preview.nationName(),
            preview.selection(),
            preview.chunkCount(),
            preview.price(),
            now.plus(Duration.ofMinutes(settings.pendingMinutes()))
        );
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingClaims.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        cooldowns.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    private void appendClaimPricingField(StringBuilder builder, ClaimPriceBreakdown pricing, int pricingDetailLimit) {
        ClaimPriceBreakdown safePricing = pricing == null
            ? ClaimPriceBreakdown.empty(BigDecimal.ZERO, BigDecimal.ZERO, 0)
            : pricing;
        List<ClaimChunkPrice> sortedChunks = safePricing.chunks().stream()
            .sorted(Comparator
                .comparing(ClaimChunkPrice::price, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClaimChunkPrice::distanceBlocks, Comparator.reverseOrder())
                .thenComparing(ClaimChunkPrice::chunkX)
                .thenComparing(ClaimChunkPrice::chunkZ))
            .toList();
        builder.append("\"pricing\":{");
        appendMoneyField(builder, "baseChunkPrice", safePricing.baseChunkPrice());
        builder.append(',');
        appendMoneyField(builder, "totalPrice", safePricing.totalPrice());
        builder.append(',');
        appendNumberField(builder, "chunkCount", safePricing.chunkCount());
        builder.append(',');
        int detailLimit = Math.max(0, pricingDetailLimit);
        appendNumberField(builder, "detailLimit", detailLimit);
        builder.append(',');
        builder.append("\"chunks\":[");
        int limit = Math.min(detailLimit, sortedChunks.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendClaimChunkPrice(builder, sortedChunks.get(index));
        }
        builder.append("]}");
    }

    private void appendClaimChunkPrice(StringBuilder builder, ClaimChunkPrice chunk) {
        builder.append('{');
        appendField(builder, "world", chunk.world());
        builder.append(',');
        appendNumberField(builder, "chunkX", chunk.chunkX());
        builder.append(',');
        appendNumberField(builder, "chunkZ", chunk.chunkZ());
        builder.append(',');
        appendField(builder, "biome", chunk.biome());
        builder.append(',');
        appendDecimalField(builder, "biomeRichness", chunk.biomeRichness());
        builder.append(',');
        appendNumberField(builder, "distanceBlocks", chunk.distanceBlocks());
        builder.append(',');
        appendDecimalField(builder, "distanceMultiplier", chunk.distanceMultiplier());
        builder.append(',');
        appendDecimalField(builder, "biomeMultiplier", chunk.biomeMultiplier());
        builder.append(',');
        appendMoneyField(builder, "price", chunk.price());
        builder.append('}');
    }

    private void appendClaimExplanationField(StringBuilder builder, ClaimSelectionExplanation explanation) {
        ClaimSelectionExplanation safe = explanation == null
            ? ClaimSelectionExplanation.basic(false, "")
            : explanation;
        builder.append("\"explanation\":{");
        appendField(builder, "state", safe.state());
        builder.append(',');
        appendField(builder, "severity", safe.severity());
        builder.append(',');
        appendField(builder, "summary", safe.summary());
        builder.append(',');
        builder.append("\"reasons\":[");
        List<ClaimSelectionReason> reasons = safe.reasons();
        for (int index = 0; index < reasons.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendClaimReason(builder, reasons.get(index));
        }
        builder.append("]}");
    }

    private void appendClaimReason(StringBuilder builder, ClaimSelectionReason reason) {
        builder.append('{');
        appendField(builder, "code", reason.code());
        builder.append(',');
        appendField(builder, "message", reason.message());
        builder.append(',');
        builder.append("\"details\":{");
        int index = 0;
        for (Map.Entry<String, String> entry : reason.details().entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            appendField(builder, entry.getKey(), entry.getValue());
        }
        builder.append("}}");
    }

    private ClaimSelectionExplanation endpointExplanation(String state, String severity, String summary, ClaimSelectionReason reason) {
        return ClaimSelectionExplanation.of(state, severity, summary, List.of(reason));
    }

    private WebClaimConfirmationResult failedConfirmation(String pendingId, String message, ClaimSelectionExplanation explanation) {
        return WebClaimConfirmationResult.failed(pendingId, message, explanation);
    }

    private static Response json(int status, String json) {
        return new Response(status, json);
    }

    private static void appendField(StringBuilder builder, String name, String value) {
        appendStringValue(builder, name);
        builder.append(':');
        appendStringValue(builder, value == null ? "" : value);
    }

    private static void appendNumberField(StringBuilder builder, String name, int value) {
        appendStringValue(builder, name);
        builder.append(':').append(value);
    }

    private static void appendNumberField(StringBuilder builder, String name, long value) {
        appendStringValue(builder, name);
        builder.append(':').append(value);
    }

    private static void appendBooleanField(StringBuilder builder, String name, boolean value) {
        appendStringValue(builder, name);
        builder.append(':').append(value);
    }

    private static void appendDecimalField(StringBuilder builder, String name, double value) {
        appendStringValue(builder, name);
        builder.append(':').append(Double.toString(value));
    }

    private static void appendMoneyField(StringBuilder builder, String name, BigDecimal value) {
        appendStringValue(builder, name);
        builder.append(':');
        appendStringValue(builder, value == null ? "0" : value.toPlainString());
    }

    private static void appendStringValue(StringBuilder builder, String value) {
        builder.append('"');
        String safe = value == null ? "" : value;
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        builder.append('"');
    }

    @FunctionalInterface
    interface BalanceProvider {
        BigDecimal balance(UUID playerId);
    }

    @FunctionalInterface
    interface PreviewProvider {
        ClaimSelectionPreview preview(UUID playerId, ChunkClaimSelection selection);
    }

    @FunctionalInterface
    interface PendingNotifier {
        void notify(PendingClaim pending);
    }

    @FunctionalInterface
    interface MessageResolver {
        String message(String key, Object... args);
    }

    @FunctionalInterface
    interface ConfirmationHandler {
        WebClaimConfirmationResult confirm(PendingClaim pending);
    }

    record PendingClaim(
        String id,
        UUID playerId,
        NationId nationId,
        String nationName,
        ChunkClaimSelection selection,
        int chunkCount,
        BigDecimal price,
        Instant expiresAt
    ) {
    }

    record Settings(
        int maxSelectionChunks,
        int maxClaimsPerNation,
        int cooldownSeconds,
        int pendingMinutes,
        int pricingDetailLimit,
        BalanceProvider balanceProvider,
        PreviewProvider previewProvider,
        PendingNotifier pendingNotifier,
        MessageResolver messages
    ) {
        Settings {
            maxSelectionChunks = Math.max(1, maxSelectionChunks);
            maxClaimsPerNation = Math.max(0, maxClaimsPerNation);
            cooldownSeconds = Math.max(0, cooldownSeconds);
            pendingMinutes = Math.max(1, pendingMinutes);
            pricingDetailLimit = Math.max(0, pricingDetailLimit);
            if (balanceProvider == null) {
                balanceProvider = ignored -> BigDecimal.ZERO;
            }
            if (pendingNotifier == null) {
                pendingNotifier = ignored -> {
                };
            }
            if (messages == null) {
                messages = (key, args) -> key;
            }
        }
    }

    record Response(int status, String json) {
    }
}
