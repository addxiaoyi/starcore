package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;

public record TreasuryRewardResult(
    NationId nationId,
    BigDecimal amount,
    BigDecimal balance,
    boolean eventRecorded
) {
}
