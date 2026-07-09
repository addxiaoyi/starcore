package dev.starcore.starcore.module.vassal.model;

import dev.starcore.starcore.module.nation.model.NationId;

/**
 * 宗藩关系快照
 * Represents a snapshot of a vassal relationship for display purposes
 */
public record VassalRelationSnapshot(
    NationId suzerainId,
    NationId vassalId,
    VassalType type,
    String suzerainName,
    String vassalName,
    long durationDays,
    double tributeRate,
    boolean protectionEnabled
) {
    /**
     * 创建一个快照
     */
    public static VassalRelationSnapshot from(
            VassalRelation relation,
            String suzerainName,
            String vassalName) {
        return new VassalRelationSnapshot(
            relation.suzerainId(),
            relation.vassalId(),
            relation.type(),
            suzerainName,
            vassalName,
            relation.durationDays(),
            relation.type().tributeRate(),
            relation.protectionEnabled()
        );
    }
}
