package dev.starcore.starcore.nation.relation;

/**
 * 联盟操作结果
 */
public sealed interface AllianceOperationResult
        permits AllianceOperationResult.Success,
                AllianceOperationResult.CooldownBlocked,
                AllianceOperationResult.NationNotFound,
                AllianceOperationResult.InvalidState {

    /**
     * 成功结果
     */
    record Success(String message) implements AllianceOperationResult {}

    /**
     * 冷却中阻止
     */
    record CooldownBlocked(long remainingMs) implements AllianceOperationResult {
        public long getRemainingHours() {
            return remainingMs / (60 * 60 * 1000);
        }

        public long getRemainingMinutes() {
            return (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
        }

        public String getRemainingTimeFormatted() {
            return getRemainingHours() + "小时" + getRemainingMinutes() + "分钟";
        }
    }

    /**
     * 国家不存在
     */
    record NationNotFound(String nationId) implements AllianceOperationResult {}

    /**
     * 无效状态
     */
    record InvalidState(String reason) implements AllianceOperationResult {}

    /**
     * 检查是否成功
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * 获取消息（适用于所有类型）
     */
    default String getMessage() {
        return switch (this) {
            case Success s -> s.message();
            case CooldownBlocked c -> "解除联盟被冷却阻止: 还需 " + c.getRemainingTimeFormatted();
            case NationNotFound n -> "国家不存在: " + n.nationId();
            case InvalidState i -> "操作无效: " + i.reason();
        };
    }
}
