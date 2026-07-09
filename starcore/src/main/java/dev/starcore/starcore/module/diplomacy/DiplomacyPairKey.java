package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

record DiplomacyPairKey(NationId left, NationId right) {
    DiplomacyPairKey {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        // D-129: 双向关系双写约束已知。若 loading 期间单边写入，长期可能不一致。
        // 建议：AllianceService 中 setAllianceRelation 已双写，可加自动镜像修复 hook。
    }

    static DiplomacyPairKey of(NationId first, NationId second) {
        return first.toString().compareTo(second.toString()) <= 0
            ? new DiplomacyPairKey(first, second)
            : new DiplomacyPairKey(second, first);
    }

    boolean contains(NationId nationId) {
        return left.equals(nationId) || right.equals(nationId);
    }

    NationId other(NationId nationId) {
        if (left.equals(nationId)) {
            return right;
        }
        if (right.equals(nationId)) {
            return left;
        }
        throw new IllegalArgumentException("nation not part of pair");
    }
}
