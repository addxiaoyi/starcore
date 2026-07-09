package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NationResourceDistrictMenuSupport {
    private static final DateTimeFormatter MENU_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NationResourceDistrictMenuSupport() {
    }

    public static MenuPaneSpec statusPane(
        MessageService messages,
        String nationName,
        NationResourceDistrict district,
        NationResourceDistrictOperationalOverview overview,
        long nowMillis,
        ZoneId zoneId
    ) {
        NationResourceDistrictOperationalOverview safeOverview = safeOverview(overview);
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.status-name"),
            appendOperationalLore(
                List.of(
                    msg(messages, "resource.district.menu.status-nation", nationName),
                    msg(messages, "resource.district.menu.status-chunk", district.coordinate()),
                    msg(messages, "resource.district.menu.status-resources", district.resourceBlocks().size()),
                    msg(messages, "resource.district.menu.status-experience", district.totalExperience()),
                    msg(messages, "resource.district.menu.status-biome", district.biomeName())
                ),
                List.of(
                    msg(messages, "resource.district.menu.status-pending-target", formatChunkCoordinate(messages, district.pendingTarget())),
                    msg(messages, "resource.district.menu.status-next-refresh", formatMoment(messages, district.nextRefreshAtMillis(), nowMillis, zoneId)),
                    msg(messages, "resource.district.menu.status-force-migration", formatMoment(messages, district.forceMigrationAtMillis(), nowMillis, zoneId))
                ),
                messages,
                safeOverview
            )
        );
    }

    public static MenuPaneSpec migrationStatusPane(
        MessageService messages,
        NationResourceDistrict district,
        NationResourceDistrictOperationalOverview overview,
        NationResourceDistrictCommandSupport.CommandPresentation presentation,
        long nowMillis,
        ZoneId zoneId
    ) {
        NationResourceDistrictOperationalOverview safeOverview = safeOverview(overview);
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.migration-status-name"),
            appendOperationalLore(
                List.of(
                    msg(messages, "resource.district.menu.migration-status-state", NationResourceDistrictViewSupport.localizedMigrationLabel(messages, district)),
                    msg(messages, "resource.district.menu.migration-status-target", formatChunkCoordinate(messages, district.pendingTarget())),
                    msg(messages, "resource.district.menu.operation.stage-label", presentation.stage()),
                    msg(messages, "resource.district.menu.operation.next-step-label", presentation.nextStep()),
                    msg(messages, "resource.district.menu.operation.restriction-label", presentation.restrictionDetail())
                ),
                List.of(
                    msg(messages, "resource.district.menu.migration-status-next-refresh", formatMoment(messages, district.nextRefreshAtMillis(), nowMillis, zoneId)),
                    msg(messages, "resource.district.menu.migration-status-force", formatMoment(messages, district.forceMigrationAtMillis(), nowMillis, zoneId))
                ),
                messages,
                safeOverview
            )
        );
    }

    public static MenuPaneSpec confirmationStatusPane(MessageService messages, NationResourceDistrict district) {
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.confirm-status-name"),
            List.of(
                msg(messages, "resource.district.menu.status-chunk", district.coordinate()),
                msg(messages, "resource.district.menu.status-resources", district.resourceBlocks().size()),
                msg(messages, "resource.district.menu.status-experience", district.totalExperience()),
                msg(messages, "resource.district.menu.status-biome", district.biomeName()),
                msg(messages, "resource.district.menu.migration-status-state", NationResourceDistrictViewSupport.localizedMigrationLabel(messages, district))
            )
        );
    }

    public static MenuPaneSpec confirmationStatusPane(
        MessageService messages,
        NationResourceDistrict district,
        long nowMillis,
        ZoneId zoneId
    ) {
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.confirm-status-name"),
            List.of(
                msg(messages, "resource.district.menu.status-chunk", district.coordinate()),
                msg(messages, "resource.district.menu.status-resources", district.resourceBlocks().size()),
                msg(messages, "resource.district.menu.status-experience", district.totalExperience()),
                msg(messages, "resource.district.menu.status-biome", district.biomeName()),
                msg(messages, "resource.district.menu.migration-status-state", NationResourceDistrictViewSupport.localizedMigrationLabel(messages, district)),
                msg(messages, "resource.district.menu.status-pending-target", formatChunkCoordinate(messages, district.pendingTarget())),
                msg(messages, "resource.district.menu.status-force-migration", formatMoment(messages, district.forceMigrationAtMillis(), nowMillis, zoneId))
            )
        );
    }

    public static MenuPaneSpec confirmationOverviewPane(
        MessageService messages,
        NationResourceDistrict district,
        NationResourceDistrictOperationalOverview overview,
        long nowMillis,
        ZoneId zoneId
    ) {
        NationResourceDistrictOperationalOverview safeOverview = safeOverview(overview);
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.confirm-overview-name"),
            appendOperationalLore(
                List.of(),
                List.of(
                    msg(messages, "resource.district.menu.status-next-refresh", formatMoment(messages, district.nextRefreshAtMillis(), nowMillis, zoneId)),
                    msg(messages, "resource.district.menu.status-force-migration", formatMoment(messages, district.forceMigrationAtMillis(), nowMillis, zoneId))
                ),
                messages,
                safeOverview
            )
        );
    }

    public static MenuPaneSpec actionPane(
        MessageService messages,
        NationResourceDistrictCommandSupport.CommandState commandState,
        NationResourceDistrictCommandSupport.CommandPresentation presentation
    ) {
        return actionPane(messages, commandState, presentation, List.of());
    }

    public static MenuPaneSpec actionPane(
        MessageService messages,
        NationResourceDistrictCommandSupport.CommandState commandState,
        NationResourceDistrictCommandSupport.CommandPresentation presentation,
        List<String> prefix
    ) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(presentation, "presentation");
        return new MenuPaneSpec(
            actionName(messages, commandState),
            operationLore(messages, commandState, presentation, prefix)
        );
    }

    public static MenuPaneSpec confirmationActionPane(
        MessageService messages,
        NationResourceDistrictCommandSupport.CommandState commandState,
        NationResourceDistrictCommandSupport.CommandPresentation presentation
    ) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(presentation, "presentation");
        return new MenuPaneSpec(
            msg(messages, "resource.district.menu.confirm-name"),
            operationLore(
                messages,
                commandState,
                presentation,
                List.of(msg(messages, "resource.district.menu.confirm-lore"))
            )
        );
    }

    public static List<String> resultFeedbackLore(
        MessageService messages,
        NationResourceDistrictMigrationResult result,
        long nowMillis,
        ZoneId zoneId
    ) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return List.of();
        }
        List<String> lore = new ArrayList<>();
        lore.add(msg(messages, "resource.district.menu.operation.result-label", result.message()));
        NationResourceDistrictSnapshot snapshot = result.snapshot();
        if (snapshot != null) {
            lore.add(msg(
                messages,
                "resource.district.menu.operation.result-state-label",
                NationResourceDistrictViewSupport.localizedMigrationLabel(messages, snapshot)
            ));
            lore.add(msg(
                messages,
                "resource.district.menu.operation.result-target-label",
                formatChunkCoordinate(messages, snapshot.pendingTarget())
            ));
            lore.add(msg(
                messages,
                "resource.district.menu.operation.result-force-migration-label",
                formatMoment(messages, snapshot.forceMigrationAtMillis(), nowMillis, zoneId)
            ));
        }
        return List.copyOf(lore);
    }

    static String formatChunkCoordinate(MessageService messages, ChunkCoordinate coordinate) {
        return coordinate == null ? msg(messages, "resource.district.menu.none") : coordinate.toString();
    }

    static String formatDurationMinutes(long totalMinutes) {
        return EpochService.humanDuration(Duration.ofMinutes(Math.max(0L, totalMinutes)));
    }

    static String formatMoment(MessageService messages, long epochMillis, long nowMillis, ZoneId zoneId) {
        if (epochMillis <= 0L) {
            return msg(messages, "resource.district.menu.none");
        }
        Instant instant = Instant.ofEpochMilli(epochMillis);
        String formatted = instant.atZone(zoneId == null ? ZoneId.systemDefault() : zoneId).format(MENU_TIME_FORMATTER);
        long remainingMillis = epochMillis - nowMillis;
        if (remainingMillis <= 0L) {
            return msg(messages, "resource.district.menu.time-ready", formatted);
        }
        return msg(messages, "resource.district.menu.time-with-remaining", formatted, EpochService.humanDuration(Duration.ofMillis(remainingMillis)));
    }

    private static List<String> operationLore(
        MessageService messages,
        NationResourceDistrictCommandSupport.CommandState commandState,
        NationResourceDistrictCommandSupport.CommandPresentation presentation,
        List<String> prefix
    ) {
        List<String> lore = new ArrayList<>(prefix);
        lore.add(msg(messages, "resource.district.menu.migrate-cost", moneyText(commandState.migrationCost())));
        lore.add(msg(
            messages,
            "resource.district.menu.operation.charge-label",
            msg(messages, commandState.migrationCostRequired()
                ? "resource.district.menu.operation.charge-yes"
                : "resource.district.menu.operation.charge-no")
        ));
        lore.add(msg(messages, "resource.district.menu.operation.balance-label", moneyText(commandState.viewerBalance())));
        lore.add(msg(messages, "resource.district.menu.operation.afford-label", presentation.affordLabel()));
        lore.add(msg(messages, "resource.district.menu.operation.shortfall-label", moneyText(commandState.migrationBalanceShortfall())));
        lore.add(msg(messages, "resource.district.menu.operation.stage-label", presentation.stage()));
        lore.add(msg(messages, "resource.district.menu.operation.next-step-label", presentation.nextStep()));
        lore.add(msg(messages, "resource.district.menu.operation.restriction-label", presentation.restrictionDetail()));
        return List.copyOf(lore);
    }

    private static List<String> appendOperationalLore(
        List<String> prefix,
        List<String> suffix,
        MessageService messages,
        NationResourceDistrictOperationalOverview overview
    ) {
        List<String> lore = new ArrayList<>(prefix);
        lore.add(msg(messages, "resource.district.menu.status-cycle-resources", overview.expectedResourceYield()));
        lore.add(msg(messages, "resource.district.menu.status-cycle-experience", overview.expectedExperienceYield()));
        lore.add(msg(messages, "resource.district.menu.status-hourly-resources", formatRate(overview.expectedResourceYieldPerHour())));
        lore.add(msg(messages, "resource.district.menu.status-hourly-experience", formatRate(overview.expectedExperienceYieldPerHour())));
        lore.add(msg(messages, "resource.district.menu.status-refresh-cycle", formatDurationMinutes(overview.refreshCooldownMinutes())));
        lore.add(msg(messages, "resource.district.menu.status-next-three-cycles", overview.forecastResourceYieldNext3Cycles(), overview.forecastExperienceYieldNext3Cycles()));
        lore.add(msg(messages, "resource.district.menu.status-forecast-window", formatDurationMinutes(overview.forecastWindowMinutesNext3Cycles())));
        lore.addAll(suffix);
        return List.copyOf(lore);
    }

    private static String actionName(MessageService messages, NationResourceDistrictCommandSupport.CommandState commandState) {
        return switch (String.valueOf(commandState.migrationActionState())) {
            case "ready" -> msg(messages, "resource.district.menu.migrate-name");
            case "awaiting-target" -> msg(messages, "resource.district.menu.operation.awaiting-target-name");
            case "waiting-depletion" -> msg(messages, "resource.district.menu.operation.waiting-depletion-name");
            case "insufficient-balance" -> msg(messages, "resource.district.menu.operation.balance-name");
            default -> msg(messages, "resource.district.menu.operation.blocked-name");
        };
    }

    private static String moneyText(java.math.BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0D, value));
    }

    private static NationResourceDistrictOperationalOverview safeOverview(NationResourceDistrictOperationalOverview overview) {
        return overview == null ? new NationResourceDistrictOperationalOverview(0, 0L, 0L) : overview;
    }

    private static String msg(MessageService messages, String key, Object... args) {
        return Objects.requireNonNull(messages, "messages").format(key, args);
    }

    public record MenuPaneSpec(String name, List<String> lore) {
        public MenuPaneSpec {
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }
}
