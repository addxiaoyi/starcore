package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;

public interface TreasuryRewardService {
    TreasuryRewardResult reward(NationId nationId, BigDecimal amount, String actor, String reason);
}
