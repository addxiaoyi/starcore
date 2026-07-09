package dev.starcore.starcore.module.vassal.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

/**
 * 宗藩关系键值对
 * Used as a key for the vassal relations map
 */
public final class VassalRelationKey {
    private final NationId suzerainId;
    private final NationId vassalId;

    public VassalRelationKey(NationId suzerainId, NationId vassalId) {
        this.suzerainId = Objects.requireNonNull(suzerainId, "suzerainId");
        this.vassalId = Objects.requireNonNull(vassalId, "vassalId");
    }

    public NationId suzerainId() {
        return suzerainId;
    }

    public NationId vassalId() {
        return vassalId;
    }

    /**
     * 创建宗藩关系键
     */
    public static VassalRelationKey of(NationId suzerainId, NationId vassalId) {
        return new VassalRelationKey(suzerainId, vassalId);
    }

    /**
     * 检查是否包含指定国家
     */
    public boolean contains(NationId nationId) {
        return suzerainId.equals(nationId) || vassalId.equals(nationId);
    }

    /**
     * 获取关系的另一方
     */
    public NationId other(NationId nationId) {
        if (suzerainId.equals(nationId)) {
            return vassalId;
        } else if (vassalId.equals(nationId)) {
            return suzerainId;
        }
        throw new IllegalArgumentException("NationId not part of this relation");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VassalRelationKey that = (VassalRelationKey) o;
        return suzerainId.equals(that.suzerainId) && vassalId.equals(that.vassalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(suzerainId, vassalId);
    }

    @Override
    public String toString() {
        return "VassalRelationKey{suzerain=" + suzerainId + ", vassal=" + vassalId + "}";
    }
}
