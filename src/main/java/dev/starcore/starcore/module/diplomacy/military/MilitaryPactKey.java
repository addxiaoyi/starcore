package dev.starcore.starcore.module.diplomacy.military;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

/**
 * 军事联盟配对键（标准化顺序）
 * 用于唯一标识一对国家之间的军事联盟关系
 */
public final class MilitaryPactKey {

    private final NationId left;
    private final NationId right;

    public MilitaryPactKey(NationId left, NationId right) {
        this.left = left;
        this.right = right;
    }

    /**
     * 工厂方法 - 自动标准化顺序
     */
    public static MilitaryPactKey of(NationId a, NationId b) {
        // 标准化：UUID 字符串字典序较小的在前
        String aStr = a.value().toString();
        String bStr = b.value().toString();
        if (aStr.compareTo(bStr) < 0) {
            return new MilitaryPactKey(a, b);
        } else {
            return new MilitaryPactKey(b, a);
        }
    }

    public NationId left() {
        return left;
    }

    public NationId right() {
        return right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MilitaryPactKey that = (MilitaryPactKey) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "MilitaryPactKey[" + left.value() + ":" + right.value() + "]";
    }
}
