package dev.starcore.starcore.module.vassal.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 宗藩关系记录
 * Represents a vassal relationship between two nations
 */
public record VassalRelation(
    NationId suzerainId,       // 宗主国ID
    NationId vassalId,         // 藩属国ID
    VassalType type,           // 宗藩关系类型
    Instant formedAt,          // 关系建立时间
    BigDecimal tributeAmount,  // 当前贡金金额
    Instant lastTributeAt,     // 上次缴纳贡金时间
    boolean protectionEnabled  // 是否启用宗主保护
) {
    /**
     * 创建一个新的宗藩关系
     */
    public static VassalRelation create(NationId suzerainId, NationId vassalId, VassalType type) {
        return new VassalRelation(
            suzerainId,
            vassalId,
            type,
            Instant.now(),
            BigDecimal.ZERO,
            null,
            true
        );
    }

    /**
     * 计算持续天数
     */
    public long durationDays() {
        return java.time.Duration.between(formedAt, Instant.now()).toDays();
    }

    /**
     * 计算贡金
     */
    public BigDecimal calculateTribute(BigDecimal vassalIncome) {
        return vassalIncome.multiply(BigDecimal.valueOf(type.tributeRate()));
    }

    /**
     * 更新贡金记录
     */
    public VassalRelation withTribute(BigDecimal amount) {
        return new VassalRelation(
            suzerainId,
            vassalId,
            type,
            formedAt,
            amount,
            Instant.now(),
            protectionEnabled
        );
    }

    /**
     * 更新宗藩类型
     */
    public VassalRelation withType(VassalType newType) {
        return new VassalRelation(
            suzerainId,
            vassalId,
            newType,
            formedAt,
            tributeAmount,
            lastTributeAt,
            protectionEnabled
        );
    }

    /**
     * 启用/禁用宗主保护
     */
    public VassalRelation withProtection(boolean enabled) {
        return new VassalRelation(
            suzerainId,
            vassalId,
            type,
            formedAt,
            tributeAmount,
            lastTributeAt,
            enabled
        );
    }
}
