package dev.starcore.starcore.module.army.siege.model;

import java.util.List;

/**
 * 攻城结果
 */
public final class SiegeResult {
    private final boolean success;
    private final SiegeResultType resultType;
    private final String message;
    private final double damageDealt;
    private final int wallHealthRemaining;
    private final int wallMaxHealth;
    private final List<String> effects;

    private SiegeResult(
        boolean success,
        SiegeResultType resultType,
        String message,
        double damageDealt,
        int wallHealthRemaining,
        int wallMaxHealth,
        List<String> effects
    ) {
        this.success = success;
        this.resultType = resultType;
        this.message = message;
        this.damageDealt = damageDealt;
        this.wallHealthRemaining = wallHealthRemaining;
        this.wallMaxHealth = wallMaxHealth;
        this.effects = effects;
    }

    /**
     * 创建成功结果
     */
    public static SiegeResult hit(double damage, int healthRemaining, int maxHealth, List<String> effects) {
        return new SiegeResult(true, SiegeResultType.HIT, null, damage, healthRemaining, maxHealth, effects);
    }

    /**
     * 创建城墙摧毁结果
     */
    public static SiegeResult wallDestroyed(int maxHealth, List<String> effects) {
        return new SiegeResult(true, SiegeResultType.WALL_DESTROYED,
            "城墙已被摧毁!", 0, 0, maxHealth, effects);
    }

    /**
     * 创建城门打开结果
     */
    public static SiegeResult gateOpened(int healthRemaining, int maxHealth, List<String> effects) {
        return new SiegeResult(true, SiegeResultType.GATE_OPENED,
            "城门已被打开!", 0, healthRemaining, maxHealth, effects);
    }

    /**
     * 创建失败结果
     */
    public static SiegeResult failed(SiegeResultType reason, String message) {
        return new SiegeResult(false, reason, message, 0, 0, 0, List.of());
    }

    /**
     * 创建弹药不足结果
     */
    public static SiegeResult noAmmunition() {
        return failed(SiegeResultType.NO_AMMUNITION, "攻城器械弹药不足!");
    }

    /**
     * 创建超出范围结果
     */
    public static SiegeResult outOfRange() {
        return failed(SiegeResultType.OUT_OF_RANGE, "目标超出攻城器械射程!");
    }

    /**
     * 创建攻城器械损坏结果
     */
    public static SiegeResult siegeDamaged(double damageTaken) {
        return new SiegeResult(false, SiegeResultType.SIEGE_DAMAGED,
            "攻城器械受损! 伤害: " + damageTaken, 0, 0, 0, List.of());
    }

    // ==================== Getters ====================

    public boolean isSuccess() {
        return success;
    }

    public SiegeResultType resultType() {
        return resultType;
    }

    public String message() {
        return message;
    }

    public double damageDealt() {
        return damageDealt;
    }

    public int wallHealthRemaining() {
        return wallHealthRemaining;
    }

    public int wallMaxHealth() {
        return wallMaxHealth;
    }

    public List<String> effects() {
        return effects;
    }

    /**
     * 获取墙生命值百分比
     */
    public double wallHealthPercent() {
        if (wallMaxHealth == 0) return 0;
        return (wallHealthRemaining * 100.0) / wallMaxHealth;
    }

    /**
     * 格式化结果报告
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();

        if (message != null) {
            sb.append(message).append("\n");
        }

        sb.append("结果: ").append(resultType.description()).append("\n");

        if (damageDealt > 0) {
            sb.append("造成伤害: ").append(String.format("%.1f", damageDealt)).append("\n");
        }

        if (wallMaxHealth > 0) {
            sb.append("城墙状态: ").append(wallHealthRemaining).append("/").append(wallMaxHealth)
              .append(" (").append(String.format("%.1f%%", wallHealthPercent())).append(")\n");
        }

        if (!effects.isEmpty()) {
            sb.append("效果:\n");
            for (String effect : effects) {
                sb.append("  - ").append(effect).append("\n");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return String.format("SiegeResult{success=%s, type=%s, damage=%.1f, health=%d/%d}",
            success, resultType, damageDealt, wallHealthRemaining, wallMaxHealth);
    }

    /**
     * 攻城结果类型
     */
    public enum SiegeResultType {
        /** 命中目标 */
        HIT("命中目标"),

        /** 城墙被摧毁 */
        WALL_DESTROYED("城墙被摧毁"),

        /** 城门被打开 */
        GATE_OPENED("城门被打开"),

        /** 弹药不足 */
        NO_AMMUNITION("弹药不足"),

        /** 超出射程 */
        OUT_OF_RANGE("超出射程"),

        /** 攻城器械损坏 */
        SIEGE_DAMAGED("攻城器械损坏"),

        /** 无效目标 */
        INVALID_TARGET("无效目标"),

        /** 攻城器械已销毁 */
        SIEGE_DESTROYED("攻城器械已销毁");

        private final String description;

        SiegeResultType(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}