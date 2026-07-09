package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class NationResourceDistrictViewSupport {
    private NationResourceDistrictViewSupport() {
    }

    public static String localizedMigrationLabel(MessageService messages, NationResourceDistrictSnapshot district) {
        if (district == null) {
            return Objects.requireNonNull(messages, "messages").format("resource.district.migration.none");
        }
        return localizedMigrationLabel(messages, district.migrationState(), district.pendingTarget());
    }

    static String localizedMigrationLabel(MessageService messages, NationResourceDistrict district) {
        if (district == null) {
            return Objects.requireNonNull(messages, "messages").format("resource.district.migration.none");
        }
        return localizedMigrationLabel(messages, district.migrationState().name(), district.pendingTarget());
    }

    public static String localizedMigrationLabel(MessageService messages, String migrationState, ChunkCoordinate pendingTarget) {
        MessageService messageService = Objects.requireNonNull(messages, "messages");
        return switch (normalizeState(migrationState)) {
            case "awaiting_target" -> messageService.format("resource.district.migration.awaiting-target");
            case "waiting_depletion" -> messageService.format(
                "resource.district.migration.waiting-depletion",
                chunkCoordinateText(messageService, pendingTarget)
            );
            default -> messageService.format("resource.district.migration.none");
        };
    }

    public static String chunkCoordinateText(MessageService messages, ChunkCoordinate coordinate) {
        MessageService messageService = Objects.requireNonNull(messages, "messages");
        return coordinate == null ? messageService.format("resource.district.none") : coordinate.toString();
    }

    public static String beaconPosition(NationResourceDistrictSnapshot district) {
        if (district == null) {
            return "";
        }
        return district.beaconX() + ", " + district.beaconY() + ", " + district.beaconZ();
    }

    public static String isoTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    static String displayText(
        MessageService messages,
        String nationName,
        NationResourceDistrict district,
        NationResourceDistrictOperationalOverview overview
    ) {
        MessageService messageService = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(district, "district");
        NationResourceDistrictOperationalOverview safeOverview = overview == null
            ? new NationResourceDistrictOperationalOverview(0, 0L, 0L)
            : overview;
        return messageService.format(
            "resource.district.display",
            nationName,
            district.resourceBlocks().size(),
            district.totalExperience(),
            district.biomeName(),
            localizedMigrationLabel(messageService, district),
            safeOverview.expectedResourceYield(),
            safeOverview.expectedExperienceYield(),
            EpochService.humanDuration(Duration.ofMinutes(Math.max(0L, safeOverview.refreshCooldownMinutes()))),
            formatRate(safeOverview.expectedResourceYieldPerHour()),
            formatRate(safeOverview.expectedExperienceYieldPerHour()),
            safeOverview.forecastResourceYieldNext3Cycles(),
            safeOverview.forecastExperienceYieldNext3Cycles(),
            EpochService.humanDuration(Duration.ofMinutes(Math.max(0L, safeOverview.forecastWindowMinutesNext3Cycles())))
        );
    }

    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0D, value));
    }

    private static String normalizeState(String migrationState) {
        return migrationState == null ? "" : migrationState.trim().toLowerCase(Locale.ROOT);
    }
}
