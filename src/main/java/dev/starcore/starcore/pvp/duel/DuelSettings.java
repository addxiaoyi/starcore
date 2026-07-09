package dev.starcore.starcore.pvp.duel;

import java.math.BigDecimal;

/**
 * 决斗设置
 */
public record DuelSettings(
    String kitName,           // Kit名称
    BigDecimal wager,         // 赌注金额
    boolean allowSpectators,  // 允许观战
    int bestOf              // BO几（如BO3）
) {
    public static DuelSettings defaults() {
        return new DuelSettings(
            "default",
            BigDecimal.ZERO,
            true,
            1
        );
    }

    /**
     * 创建带赌注的决斗
     */
    public static DuelSettings withWager(String kitName, BigDecimal wager) {
        return new DuelSettings(kitName, wager, true, 1);
    }

    /**
     * 创建BO3决斗
     */
    public static DuelSettings bestOf3(String kitName) {
        return new DuelSettings(kitName, BigDecimal.ZERO, true, 3);
    }
}
