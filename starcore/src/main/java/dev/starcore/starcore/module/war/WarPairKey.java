package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

record WarPairKey(NationId left, NationId right) {
    WarPairKey {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    static WarPairKey of(NationId first, NationId second) {
        return first.toString().compareTo(second.toString()) <= 0
            ? new WarPairKey(first, second)
            : new WarPairKey(second, first);
    }

    boolean contains(NationId nationId) {
        return left.equals(nationId) || right.equals(nationId);
    }
}
