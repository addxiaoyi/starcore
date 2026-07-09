package dev.starcore.starcore.module.vassal.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;
import java.util.UUID;

/**
 * 宗藩关系ID
 * 用于唯一标识一个宗藩关系
 */
public record VassalId(UUID value) {
    public VassalId {
        Objects.requireNonNull(value, "value");
    }

    public static VassalId random() {
        return new VassalId(UUID.randomUUID());
    }

    public static VassalId of(UUID value) {
        return new VassalId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
