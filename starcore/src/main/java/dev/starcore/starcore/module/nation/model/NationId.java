package dev.starcore.starcore.module.nation.model;

import java.util.Objects;
import java.util.UUID;

public record NationId(UUID value) {
    public NationId {
        Objects.requireNonNull(value, "value");
    }

    public static NationId random() {
        return new NationId(UUID.randomUUID());
    }

    public static NationId of(UUID value) {
        return new NationId(value);
    }

    /**
     * 从字符串解析 NationId
     * @param value UUID 字符串
     * @return NationId
     */
    public static NationId fromString(String value) {
        return new NationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }

    /**
     * 获取底层的 UUID 值（兼容旧代码）
     * @return UUID 值
     */
    public UUID uuid() {
        return value;
    }
}
