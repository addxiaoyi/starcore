package dev.starcore.starcore.module.diplomacy.alliance;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;

/**
 * 联盟信息数据记录
 * 存储联盟的详细信息
 */
public record AllianceInfoData(
    NationId nation1,
    NationId nation2,
    String nation1Name,
    String nation2Name,
    Instant formedAt
) {}
