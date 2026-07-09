package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

final class NationResourceDistrictRules {
    private static final double MIN_CHUNK_WEIGHT = 0.05D;
    private static final int STATUS_MENU_MIGRATE_SLOT = 13;
    private static final int CONFIRM_MENU_CONFIRM_SLOT = 11;
    private static final int CONFIRM_MENU_CANCEL_SLOT = 15;

    private NationResourceDistrictRules() {
    }

    static Optional<ChunkCoordinate> selectWeightedChunk(
        List<ChunkCoordinate> candidates,
        ToDoubleFunction<ChunkCoordinate> richnessLookup,
        DoubleSupplier randomUnit
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        double[] weights = new double[candidates.size()];
        double totalWeight = 0.0D;
        for (int i = 0; i < candidates.size(); i++) {
            double weight = Math.max(MIN_CHUNK_WEIGHT, richnessLookup.applyAsDouble(candidates.get(i)));
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0D || Double.isNaN(totalWeight)) {
            return Optional.of(candidates.getFirst());
        }
        double random = Math.clamp(randomUnit.getAsDouble(), 0.0D, Math.nextDown(1.0D));
        double selected = random * totalWeight;
        double cursor = 0.0D;
        for (int i = 0; i < candidates.size(); i++) {
            cursor += weights[i];
            if (selected < cursor) {
                return Optional.of(candidates.get(i));
            }
        }
        return Optional.of(candidates.getLast());
    }

    static int resourceAmount(int baseAmount, double richness, double richnessMultiplier) {
        double scaled = Math.max(1, baseAmount)
            * Math.max(0.1D, richness)
            * Math.max(0.01D, richnessMultiplier);
        return Math.max(1, (int) Math.round(scaled));
    }

    static long refreshCooldownMinutes(long baseMinutes, long minMinutes, long maxMinutes, double richness) {
        long safeBase = Math.max(1L, baseMinutes);
        long safeMin = Math.max(1L, minMinutes);
        long safeMax = Math.max(safeMin, maxMinutes);
        long scaled = Math.round(safeBase / Math.max(0.25D, richness));
        return Math.clamp(scaled, safeMin, safeMax);
    }

    static long refreshExperience(long baseExperience, double richness) {
        return Math.max(1L, Math.round(Math.max(1L, baseExperience) * Math.max(0.1D, richness)));
    }

    static BigDecimal treasuryIncome(
        BigDecimal baseIncome,
        BigDecimal incomePerGeneratedBlock,
        int generatedBlocks,
        double richness,
        double richnessMultiplier
    ) {
        if (generatedBlocks <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        BigDecimal safeBase = nonNegative(baseIncome);
        BigDecimal safePerBlock = nonNegative(incomePerGeneratedBlock);
        BigDecimal generated = BigDecimal.valueOf(generatedBlocks);
        BigDecimal multiplier = BigDecimal.valueOf(Math.max(0.0D, richness) * Math.max(0.0D, richnessMultiplier));
        return safeBase.add(safePerBlock.multiply(generated))
            .multiply(multiplier)
            .setScale(2, RoundingMode.DOWN);
    }

    static boolean forceMigrationDue(NationResourceDistrict district, long nowMillis) {
        return district != null
            && district.migrationState() == NationResourceDistrict.MigrationState.WAITING_DEPLETION
            && district.forceMigrationAtMillis() > 0L
            && nowMillis >= district.forceMigrationAtMillis();
    }

    static boolean canIssueMigrationCore(NationResourceDistrict district) {
        return district != null && district.migrationState() != NationResourceDistrict.MigrationState.WAITING_DEPLETION;
    }

    static boolean shouldChargeMigrationCost(NationResourceDistrict district) {
        return district != null && district.migrationState() == NationResourceDistrict.MigrationState.NONE;
    }

    static boolean canAcceptMigrationTarget(NationResourceDistrict district) {
        return district != null && district.migrationState() == NationResourceDistrict.MigrationState.AWAITING_TARGET;
    }

    static long forceMigrationAtMillis(long nowMillis, int forceHours) {
        return nowMillis + Duration.ofHours(Math.max(1, forceHours)).toMillis();
    }

    static ResourceDistrictMenuAction menuAction(boolean confirmationMenu, int slot, Material material) {
        if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return ResourceDistrictMenuAction.NONE;
        }
        if (confirmationMenu) {
            if (slot == CONFIRM_MENU_CONFIRM_SLOT && material == Material.LIME_CONCRETE) {
                return ResourceDistrictMenuAction.BEGIN_MIGRATION;
            }
            if (slot == CONFIRM_MENU_CANCEL_SLOT && material == Material.BARRIER) {
                return ResourceDistrictMenuAction.RETURN_TO_STATUS;
            }
            return ResourceDistrictMenuAction.NONE;
        }
        if (slot == STATUS_MENU_MIGRATE_SLOT && material == Material.EMERALD) {
            return ResourceDistrictMenuAction.OPEN_MIGRATION_CONFIRMATION;
        }
        return ResourceDistrictMenuAction.NONE;
    }

    static boolean canCompleteMigration(NationResourceDistrict district, boolean force) {
        if (district == null
            || district.migrationState() != NationResourceDistrict.MigrationState.WAITING_DEPLETION
            || district.pendingTarget() == null) {
            return false;
        }
        return force || district.resourceBlocks().isEmpty();
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    enum ResourceDistrictMenuAction {
        NONE,
        OPEN_MIGRATION_CONFIRMATION,
        BEGIN_MIGRATION,
        RETURN_TO_STATUS
    }
}
