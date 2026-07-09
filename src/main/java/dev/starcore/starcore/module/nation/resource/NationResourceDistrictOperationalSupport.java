package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.core.config.ConfigurationService;

import java.math.BigDecimal;

public final class NationResourceDistrictOperationalSupport {
    private static final int DEFAULT_BASE_COOLDOWN_MINUTES = 60;
    private static final int DEFAULT_MIN_COOLDOWN_MINUTES = 20;
    private static final int DEFAULT_MAX_COOLDOWN_MINUTES = 180;
    private static final int DEFAULT_BASE_RESOURCE_AMOUNT = 32;
    private static final double DEFAULT_RICHNESS_AMOUNT_MULTIPLIER = 1.0D;
    private static final long DEFAULT_BASE_EXPERIENCE = 120L;
    private static final BigDecimal DEFAULT_TREASURY_BASE_INCOME = new BigDecimal("250.00");
    private static final BigDecimal DEFAULT_TREASURY_INCOME_PER_BLOCK = new BigDecimal("15.00");
    private static final double DEFAULT_TREASURY_RICHNESS_MULTIPLIER = 1.0D;

    private NationResourceDistrictOperationalSupport() {
    }

    public static NationResourceDistrictOperationalOverview overview(ConfigurationService configuration, NationResourceDistrictSnapshot district) {
        if (district == null) {
            return new NationResourceDistrictOperationalOverview(0, 0L, 0L);
        }
        return overview(configuration, district.biomeRichness());
    }

    static NationResourceDistrictOperationalOverview overview(ConfigurationService configuration, NationResourceDistrict district) {
        if (district == null) {
            return new NationResourceDistrictOperationalOverview(0, 0L, 0L);
        }
        return overview(configuration, district.biomeRichness());
    }

    public static NationResourceDistrictOperationalOverview overview(ConfigurationService configuration, double biomeRichness) {
        int expectedResources = NationResourceDistrictRules.resourceAmount(
            configuration == null ? DEFAULT_BASE_RESOURCE_AMOUNT : configuration.nationResourceRefreshBaseAmount(),
            biomeRichness,
            configuration == null ? DEFAULT_RICHNESS_AMOUNT_MULTIPLIER : configuration.nationResourceRefreshRichnessAmountMultiplier()
        );
        long cooldownMinutes = NationResourceDistrictRules.refreshCooldownMinutes(
            configuration == null ? DEFAULT_BASE_COOLDOWN_MINUTES : configuration.nationResourceRefreshBaseCooldownMinutes(),
            configuration == null ? DEFAULT_MIN_COOLDOWN_MINUTES : configuration.nationResourceRefreshMinCooldownMinutes(),
            configuration == null ? DEFAULT_MAX_COOLDOWN_MINUTES : configuration.nationResourceRefreshMaxCooldownMinutes(),
            biomeRichness
        );
        long expectedExperience = NationResourceDistrictRules.refreshExperience(
            configuration == null ? DEFAULT_BASE_EXPERIENCE : configuration.nationResourceRefreshBaseExperience(),
            biomeRichness
        );
        BigDecimal expectedTreasuryIncome = expectedTreasuryIncome(configuration, expectedResources, biomeRichness);
        return new NationResourceDistrictOperationalOverview(expectedResources, expectedExperience, cooldownMinutes, expectedTreasuryIncome);
    }

    private static BigDecimal expectedTreasuryIncome(ConfigurationService configuration, int expectedResources, double biomeRichness) {
        if (configuration != null && !configuration.nationResourceRefreshTreasuryIncomeEnabled()) {
            return BigDecimal.ZERO;
        }
        return NationResourceDistrictRules.treasuryIncome(
            configuration == null ? DEFAULT_TREASURY_BASE_INCOME : configuration.nationResourceRefreshTreasuryBaseIncome(),
            configuration == null ? DEFAULT_TREASURY_INCOME_PER_BLOCK : configuration.nationResourceRefreshTreasuryIncomePerBlock(),
            expectedResources,
            biomeRichness,
            configuration == null ? DEFAULT_TREASURY_RICHNESS_MULTIPLIER : configuration.nationResourceRefreshTreasuryRichnessMultiplier()
        );
    }
}
