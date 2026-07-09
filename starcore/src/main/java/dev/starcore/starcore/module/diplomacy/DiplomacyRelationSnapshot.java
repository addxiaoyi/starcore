package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

public record DiplomacyRelationSnapshot(
    NationId source,
    NationId target,
    DiplomacyRelation relation
) {
}
