package dev.starcore.starcore.module.army.siege.model;

/**
 * 攻城器械状态
 */
public enum SiegeState {
    /**
     * 闲置 - 可移动和部署
     */
    IDLE(true, true, 1.0, 0.8),

    /**
     * 行军 - 正在移动
     */
    MARCHING(true, true, 1.0, 0.7),

    /**
     * 部署中 - 正在部署
     */
    DEPLOYING(false, false, 1.0, 0.9),

    /**
     * 就绪 - 已部署，等待攻击
     */
    READY(false, true, 1.2, 1.0),

    /**
     * 攻城中 - 正在攻击城墙
     */
    BESIEGING(false, true, 1.5, 1.0),

    /**
     * 撤退中 - 正在撤退
     */
    RETREATING(true, true, 0.8, 0.6),

    /**
     * 损坏 - 需要修复
     */
    DAMAGED(false, false, 0.5, 0.5),

    /**
     * 销毁 - 已完全损坏
     */
    DESTROYED(false, false, 0.0, 0.0);

    private final boolean canMove;
    private final boolean canFire;
    private final double combatModifier;
    private final double moraleModifier;

    SiegeState(boolean canMove, boolean canFire, double combatModifier, double moraleModifier) {
        this.canMove = canMove;
        this.canFire = canFire;
        this.combatModifier = combatModifier;
        this.moraleModifier = moraleModifier;
    }

    public boolean canMove() {
        return canMove;
    }

    public boolean canFire() {
        return canFire;
    }

    public double combatModifier() {
        return combatModifier;
    }

    public double moraleModifier() {
        return moraleModifier;
    }

    /**
     * 检查是否可以参与战斗
     */
    public boolean canEngage() {
        return canFire && combatModifier > 0;
    }
}