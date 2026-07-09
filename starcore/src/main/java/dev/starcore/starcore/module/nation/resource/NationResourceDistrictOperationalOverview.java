package dev.starcore.starcore.module.nation.resource;

import java.math.BigDecimal;

public record NationResourceDistrictOperationalOverview(
    int expectedResourceYield,
    long expectedExperienceYield,
    long refreshCooldownMinutes,
    BigDecimal expectedTreasuryIncomeYield,
    double expectedResourceYieldPerHour,
    double expectedExperienceYieldPerHour,
    double expectedTreasuryIncomeYieldPerHour,
    long forecastResourceYieldNext3Cycles,
    long forecastExperienceYieldNext3Cycles,
    BigDecimal forecastTreasuryIncomeNext3Cycles,
    long forecastWindowMinutesNext3Cycles
) {
    private static final long DEFAULT_FUTURE_CYCLES = 3L;

    public NationResourceDistrictOperationalOverview(int expectedResourceYield, long expectedExperienceYield, long refreshCooldownMinutes) {
        this(expectedResourceYield, expectedExperienceYield, refreshCooldownMinutes, BigDecimal.ZERO);
    }

    public NationResourceDistrictOperationalOverview(
        int expectedResourceYield,
        long expectedExperienceYield,
        long refreshCooldownMinutes,
        BigDecimal expectedTreasuryIncomeYield
    ) {
        this(
            expectedResourceYield,
            expectedExperienceYield,
            refreshCooldownMinutes,
            nonNegativeMoney(expectedTreasuryIncomeYield),
            perHour(expectedResourceYield, refreshCooldownMinutes),
            perHour(expectedExperienceYield, refreshCooldownMinutes),
            perHour(nonNegativeMoney(expectedTreasuryIncomeYield), refreshCooldownMinutes),
            Math.max(0L, expectedResourceYield) * DEFAULT_FUTURE_CYCLES,
            Math.max(0L, expectedExperienceYield) * DEFAULT_FUTURE_CYCLES,
            nonNegativeMoney(expectedTreasuryIncomeYield).multiply(BigDecimal.valueOf(DEFAULT_FUTURE_CYCLES)),
            Math.max(0L, refreshCooldownMinutes) * DEFAULT_FUTURE_CYCLES
        );
    }

    private static double perHour(long yield, long cooldownMinutes) {
        if (yield <= 0L || cooldownMinutes <= 0L) {
            return 0.0D;
        }
        return (yield * 60.0D) / cooldownMinutes;
    }

    private static double perHour(BigDecimal yield, long cooldownMinutes) {
        if (yield == null || yield.signum() <= 0 || cooldownMinutes <= 0L) {
            return 0.0D;
        }
        return yield.doubleValue() * 60.0D / cooldownMinutes;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
