package dev.starcore.starcore.module.diplomacy.alliance;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

/**
 * 联盟配对键（标准化顺序）
 * 用于唯一标识一对国家之间的联盟关系
 */
public record AlliancePairKey(NationId left, NationId right) {

    /**
     * 工厂方法 - 自动标准化顺序
     */
    public static AlliancePairKey of(NationId a, NationId b) {
        // 标准化：UUID 字符串字典序较小的在前
        String aStr = a.value().toString();
        String bStr = b.value().toString();
        if (aStr.compareTo(bStr) < 0) {
            return new AlliancePairKey(a, b);
        } else {
            return new AlliancePairKey(b, a);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlliancePairKey that = (AlliancePairKey) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "AlliancePairKey[" + left.value() + ":" + right.value() + "]";
    }
}
