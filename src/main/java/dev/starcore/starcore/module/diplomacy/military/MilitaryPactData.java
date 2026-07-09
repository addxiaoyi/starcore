package dev.starcore.starcore.module.diplomacy.military;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;

/**
 * 军事联盟信息数据记录
 * 存储军事联盟的详细信息
 */
public record MilitaryPactData(
    NationId nation1,
    NationId nation2,
    String nation1Name,
    String nation2Name,
    MilitaryAllianceService.PactType pactType,
    Instant formedAt,
    Instant upgradedAt
) {}
