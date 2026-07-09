package dev.starcore.starcore.module.emergency.model;

/**
 * 紧急状态快照
 * 用于跨模块传递紧急状态信息
 */
public record EmergencySnapshot(
    java.util.UUID id,
    dev.starcore.starcore.module.nation.model.NationId nationId,
    EmergencyState.EmergencyType type,
    String reason,
    java.time.Instant declaredAt,
    java.time.Instant expiresAt,
    String declaredBy,
    long remainingMinutes,
    double remainingPercentage
) {
    /**
     * 从 EmergencyState 创建快照
     */
    public static EmergencySnapshot from(EmergencyState state) {
        return new EmergencySnapshot(
            state.id(),
            state.nationId(),
            state.type(),
            state.reason(),
            state.declaredAt(),
            state.expiresAt(),
            state.declaredBy(),
            state.remainingMinutes(),
            state.remainingPercentage()
        );
    }
}