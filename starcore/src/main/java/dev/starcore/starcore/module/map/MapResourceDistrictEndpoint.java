package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictCommandSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictMigrationResult;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalOverview;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictSnapshot;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class MapResourceDistrictEndpoint {
    Response migrationResponse(UUID viewerId, UUID districtId, Settings settings) {
        if (!settings.enabled()) {
            return new Response(404, errorJson("disabled", settings.messages().message("command.map.web-resource-district-disabled")));
        }
        NationResourceDistrictSnapshot requestedSnapshot = settings.districtLookup().find(districtId).orElse(null);
        Player player = settings.playerLookup().find(viewerId).orElse(null);
        NationResourceDistrictMigrationResult result;
        if (player == null || !player.isOnline()) {
            result = NationResourceDistrictMigrationResult.failure(
                "player-offline",
                settings.messages().message("resource.district.migration.player-offline"),
                requestedSnapshot
            );
        } else {
            result = settings.migrator().begin(player, districtId);
            if (result.success()) {
                settings.successCallback().run();
            }
        }
        return new Response(status(result), migrationJson(result, viewerId, settings));
    }

    private String migrationJson(NationResourceDistrictMigrationResult result, UUID viewerId, Settings settings) {
        StringBuilder builder = new StringBuilder(320);
        builder.append('{');
        appendBooleanField(builder, "ok", result != null && result.success());
        builder.append(',');
        appendField(builder, "code", result == null || result.code() == null ? "error" : result.code());
        builder.append(',');
        appendField(builder, "message", result == null || result.message() == null ? settings.messages().message("command.map.request-failed") : result.message());
        NationResourceDistrictSnapshot snapshot = result == null ? null : result.snapshot();
        Nation nation = snapshot == null ? null : settings.nationLookup().find(snapshot.nationId()).orElse(null);
        NationOperationalOverview nationOverview = nation == null ? null : settings.nationOverviewProvider().overview(nation);
        NationResourceDistrictCommandSupport.CommandState commandState = settings.commandStateProvider().state(
            viewerId,
            snapshot,
            NationResourceDistrictCommandSupport.actionStateForResultCode(result == null ? null : result.code())
        );
        NationResourceDistrictCommandSupport.CommandPresentation commandPresentation = settings.commandPresentationProvider().presentation(commandState);
        ClaimSelectionExplanation explanation = NationResourceDistrictCommandSupport.explanation(commandState, commandPresentation);
        builder.append(',');
        appendField(builder, "districtId", snapshot == null ? "" : snapshot.id().toString());
        builder.append(',');
        appendField(builder, "nationId", snapshot == null ? "" : snapshot.nationId().toString());
        builder.append(',');
        appendField(builder, "nation", nation == null ? "" : nation.name());
        builder.append(',');
        appendField(builder, "nationKind", nation == null ? "independent" : nation.kind().name().toLowerCase(Locale.ROOT));
        builder.append(',');
        appendField(builder, "founderName", nationOverview == null ? "" : nationOverview.founderName());
        builder.append(',');
        appendField(builder, "government", nation == null ? "" : nation.governmentType().name());
        builder.append(',');
        appendNumberField(builder, "memberCount", nationOverview == null ? 0 : nationOverview.memberCount());
        builder.append(',');
        appendNumberField(builder, "nationLevel", nationOverview == null ? 1 : nationOverview.level());
        builder.append(',');
        appendNumberField(builder, "nationExperience", nationOverview == null ? 0L : nationOverview.experience());
        builder.append(',');
        appendNumberField(builder, "nationExperienceProgress", nationOverview == null ? 0L : nationOverview.currentLevelProgress());
        builder.append(',');
        appendNumberField(builder, "nationNextLevelExperience", nationOverview == null ? 0L : nationOverview.nextLevelExperienceRequired());
        builder.append(',');
        appendNumberField(builder, "nationExperienceRemaining", nationOverview == null ? 0L : nationOverview.remainingExperienceToNextLevel());
        builder.append(',');
        appendBooleanField(builder, "nationMaxLevelReached", nationOverview != null && nationOverview.maxLevelReached());
        builder.append(',');
        appendNumberField(builder, "claimCount", nationOverview == null ? 0 : nationOverview.claimCount());
        builder.append(',');
        appendNumberField(builder, "claimLimit", nationOverview == null ? 0 : nationOverview.claimLimit());
        builder.append(',');
        appendNumberField(builder, "cityStateCount", nationOverview == null ? 0 : nationOverview.cityStateCount());
        builder.append(',');
        appendNumberField(builder, "cityStateLimit", nationOverview == null ? 0 : nationOverview.cityStateLimit());
        builder.append(',');
        appendNumberField(builder, "resourceDistrictCount", nationOverview == null ? 0 : nationOverview.resourceDistrictCount());
        builder.append(',');
        appendNumberField(builder, "resourceDistrictLimit", nationOverview == null ? 0 : nationOverview.resourceDistrictLimit());
        builder.append(',');
        appendField(builder, "biome", snapshot == null ? "" : snapshot.biomeName());
        builder.append(',');
        appendField(builder, "richness", snapshot == null ? "0.00" : String.format(Locale.ROOT, "%.2f", snapshot.biomeRichness()));
        builder.append(',');
        appendField(builder, "migrationState", snapshot == null ? "" : snapshot.migrationState());
        builder.append(',');
        appendField(builder, "migrationLabel", settings.migrationLabelProvider().label(snapshot));
        builder.append(',');
        NationResourceDistrictOperationalOverview overview = settings.districtOverviewProvider().overview(snapshot);
        appendNumberField(builder, "expectedResourceYield", overview.expectedResourceYield());
        builder.append(',');
        appendNumberField(builder, "expectedExperienceYield", overview.expectedExperienceYield());
        builder.append(',');
        appendNumberField(builder, "refreshCooldownMinutes", overview.refreshCooldownMinutes());
        builder.append(',');
        appendMoneyField(builder, "expectedTreasuryIncomeYield", overview.expectedTreasuryIncomeYield());
        builder.append(',');
        appendDecimalField(builder, "expectedResourceYieldPerHour", overview.expectedResourceYieldPerHour());
        builder.append(',');
        appendDecimalField(builder, "expectedExperienceYieldPerHour", overview.expectedExperienceYieldPerHour());
        builder.append(',');
        appendDecimalField(builder, "expectedTreasuryIncomeYieldPerHour", overview.expectedTreasuryIncomeYieldPerHour());
        builder.append(',');
        appendNumberField(builder, "forecastResourceYieldNext3Cycles", overview.forecastResourceYieldNext3Cycles());
        builder.append(',');
        appendNumberField(builder, "forecastExperienceYieldNext3Cycles", overview.forecastExperienceYieldNext3Cycles());
        builder.append(',');
        appendMoneyField(builder, "forecastTreasuryIncomeNext3Cycles", overview.forecastTreasuryIncomeNext3Cycles());
        builder.append(',');
        appendNumberField(builder, "forecastWindowMinutesNext3Cycles", overview.forecastWindowMinutesNext3Cycles());
        builder.append(',');
        appendField(builder, "pendingTarget", snapshot == null || snapshot.pendingTarget() == null ? "" : snapshot.pendingTarget().toString());
        builder.append(',');
        appendField(builder, "beaconPosition", snapshot == null ? "" : settings.beaconPositionProvider().position(snapshot));
        builder.append(',');
        appendField(builder, "nextRefreshAt",
            snapshot == null || snapshot.nextRefreshAtMillis() <= 0L ? "" : settings.timestampFormatter().format(snapshot.nextRefreshAtMillis()));
        builder.append(',');
        appendField(builder, "forceMigrationAt",
            snapshot == null || snapshot.forceMigrationAtMillis() <= 0L ? "" : settings.timestampFormatter().format(snapshot.forceMigrationAtMillis()));
        builder.append(',');
        appendNumberField(builder, "remainingResources", snapshot == null ? 0 : snapshot.remainingResources());
        builder.append(',');
        appendNumberField(builder, "totalExperience", snapshot == null ? 0L : snapshot.totalExperience());
        builder.append(',');
        appendMoneyField(builder, "viewerBalance", commandState.viewerBalance());
        builder.append(',');
        appendMoneyField(builder, "migrationCost", commandState.migrationCost());
        builder.append(',');
        appendBooleanField(builder, "migrationCostRequired", commandState.migrationCostRequired());
        builder.append(',');
        appendMoneyField(builder, "migrationBalanceShortfall", commandState.migrationBalanceShortfall());
        builder.append(',');
        appendBooleanField(builder, "viewerOwnsDistrictNation", commandState.viewerOwnsDistrictNation());
        builder.append(',');
        appendBooleanField(builder, "viewerIsNationLeader", commandState.viewerIsNationLeader());
        builder.append(',');
        appendBooleanField(builder, "viewerCanManageMigration", commandState.viewerCanManageMigration());
        builder.append(',');
        appendField(builder, "viewerAuthorizedOfficerRole", commandState.viewerAuthorizedOfficerRole());
        builder.append(',');
        appendBooleanField(builder, "viewerOnline", commandState.viewerOnline());
        builder.append(',');
        appendBooleanField(builder, "viewerCanAffordMigration", commandState.viewerCanAffordMigration());
        builder.append(',');
        appendBooleanField(builder, "canStartMigration", commandState.canStartMigration());
        builder.append(',');
        appendField(builder, "migrationActionState", commandState.migrationActionState());
        builder.append(',');
        appendField(builder, "migrationStage", commandPresentation.stage());
        builder.append(',');
        appendField(builder, "migrationNextStep", commandPresentation.nextStep());
        builder.append(',');
        appendField(builder, "migrationRestriction", commandPresentation.restriction());
        builder.append(',');
        appendField(builder, "migrationRestrictionDetail", commandPresentation.restrictionDetail());
        builder.append(',');
        appendField(builder, "migrationAffordLabel", commandPresentation.affordLabel());
        builder.append(',');
        appendExplanationField(builder, explanation);
        builder.append('}');
        return builder.toString();
    }

    private int status(NationResourceDistrictMigrationResult result) {
        if (result == null) {
            return 500;
        }
        if (result.success()) {
            return 200;
        }
        return switch (String.valueOf(result.code())) {
            case "district-not-found" -> 404;
            case "leader-only" -> 403;
            case "already-waiting", "insufficient-balance", "player-offline" -> 409;
            default -> 400;
        };
    }

    private String errorJson(String code, String message) {
        StringBuilder builder = new StringBuilder(128);
        builder.append('{');
        appendBooleanField(builder, "ok", false);
        builder.append(',');
        appendField(builder, "code", code == null ? "error" : code);
        builder.append(',');
        appendField(builder, "message", message == null || message.isBlank() ? "request failed" : message);
        builder.append(',');
        appendBooleanField(builder, "canSubmit", false);
        builder.append(',');
        appendExplanationField(builder, endpointExplanation(code, "error", message));
        builder.append('}');
        return builder.toString();
    }

    private void appendExplanationField(StringBuilder builder, ClaimSelectionExplanation explanation) {
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
            appendReason(builder, reasons.get(index));
        }
        builder.append("]}");
    }

    private void appendReason(StringBuilder builder, ClaimSelectionReason reason) {
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

    private ClaimSelectionExplanation endpointExplanation(String state, String severity, String summary) {
        return ClaimSelectionExplanation.of(
            state,
            severity,
            summary,
            summary == null || summary.isBlank() ? List.of() : List.of(ClaimSelectionReason.of(state, summary))
        );
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
    interface DistrictLookup {
        Optional<NationResourceDistrictSnapshot> find(UUID districtId);
    }

    @FunctionalInterface
    interface PlayerLookup {
        Optional<Player> find(UUID viewerId);
    }

    @FunctionalInterface
    interface Migrator {
        NationResourceDistrictMigrationResult begin(Player player, UUID districtId);
    }

    @FunctionalInterface
    interface NationLookup {
        Optional<Nation> find(NationId nationId);
    }

    @FunctionalInterface
    interface NationOverviewProvider {
        NationOperationalOverview overview(Nation nation);
    }

    @FunctionalInterface
    interface DistrictOverviewProvider {
        NationResourceDistrictOperationalOverview overview(NationResourceDistrictSnapshot district);
    }

    @FunctionalInterface
    interface CommandStateProvider {
        NationResourceDistrictCommandSupport.CommandState state(UUID viewerId, NationResourceDistrictSnapshot district, String fallbackActionState);
    }

    @FunctionalInterface
    interface CommandPresentationProvider {
        NationResourceDistrictCommandSupport.CommandPresentation presentation(NationResourceDistrictCommandSupport.CommandState commandState);
    }

    @FunctionalInterface
    interface MigrationLabelProvider {
        String label(NationResourceDistrictSnapshot district);
    }

    @FunctionalInterface
    interface BeaconPositionProvider {
        String position(NationResourceDistrictSnapshot district);
    }

    @FunctionalInterface
    interface TimestampFormatter {
        String format(long epochMillis);
    }

    @FunctionalInterface
    interface MessageResolver {
        String message(String key, Object... args);
    }

    record Settings(
        boolean enabled,
        DistrictLookup districtLookup,
        PlayerLookup playerLookup,
        Migrator migrator,
        NationLookup nationLookup,
        NationOverviewProvider nationOverviewProvider,
        DistrictOverviewProvider districtOverviewProvider,
        CommandStateProvider commandStateProvider,
        CommandPresentationProvider commandPresentationProvider,
        MigrationLabelProvider migrationLabelProvider,
        BeaconPositionProvider beaconPositionProvider,
        TimestampFormatter timestampFormatter,
        MessageResolver messages,
        Runnable successCallback
    ) {
        Settings {
            if (districtLookup == null) {
                districtLookup = ignored -> Optional.empty();
            }
            if (playerLookup == null) {
                playerLookup = ignored -> Optional.empty();
            }
            if (migrator == null) {
                migrator = (player, districtId) -> NationResourceDistrictMigrationResult.failure("disabled", "disabled", null);
            }
            if (nationLookup == null) {
                nationLookup = ignored -> Optional.empty();
            }
            if (nationOverviewProvider == null) {
                nationOverviewProvider = ignored -> null;
            }
            if (districtOverviewProvider == null) {
                districtOverviewProvider = ignored -> new NationResourceDistrictOperationalOverview(0, 0L, 0L);
            }
            if (commandStateProvider == null) {
                commandStateProvider = (viewerId, district, fallback) -> NationResourceDistrictCommandSupport.evaluate(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "",
                    false,
                    false,
                    district == null || district.nationId() == null ? "" : district.nationId().toString(),
                    district == null ? "" : district.migrationState(),
                    fallback
                );
            }
            if (commandPresentationProvider == null) {
                commandPresentationProvider = ignored -> new NationResourceDistrictCommandSupport.CommandPresentation("", "", "", "", "");
            }
            if (migrationLabelProvider == null) {
                migrationLabelProvider = ignored -> "";
            }
            if (beaconPositionProvider == null) {
                beaconPositionProvider = ignored -> "";
            }
            if (timestampFormatter == null) {
                timestampFormatter = ignored -> "";
            }
            if (messages == null) {
                messages = (key, args) -> key;
            }
            if (successCallback == null) {
                successCallback = () -> {
                };
            }
        }
    }

    record Response(int status, String json) {
    }
}
