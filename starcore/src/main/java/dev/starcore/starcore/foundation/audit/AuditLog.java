package dev.starcore.starcore.foundation.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * 审计日志 - SSS级安全追踪
 * 记录所有重要操作，用于安全审计和问题追踪
 */
public record AuditLog(
    UUID id,
    AuditAction action,
    UUID actorId,
    String actorName,
    UUID targetId,
    String targetName,
    String details,
    Instant timestamp,
    String ipAddress,
    boolean success
) {

    /**
     * 审计操作类型
     */
    public enum AuditAction {
        // 国家操作
        NATION_CREATE("创建国家"),
        NATION_DISBAND("解散国家"),
        NATION_RENAME("重命名国家"),

        // 成员操作
        MEMBER_JOIN("加入国家"),
        MEMBER_LEAVE("离开国家"),
        MEMBER_KICK("踢出成员"),
        MEMBER_PROMOTE("提升职位"),
        MEMBER_DEMOTE("降低职位"),

        // 领地操作
        TERRITORY_CLAIM("圈地"),
        TERRITORY_UNCLAIM("取消圈地"),

        // 财政操作
        TREASURY_DEPOSIT("存入国库"),
        TREASURY_WITHDRAW("取出国库"),

        // 战争操作
        WAR_DECLARE("宣战"),
        WAR_PEACE("和平"),
        ARMY_CREATE("创建军队"),
        ARMY_ATTACK("军队攻击"),

        // 管理操作
        ADMIN_COMMAND("管理员命令"),
        CONFIG_CHANGE("配置更改");

        private final String displayName;

        AuditAction(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 创建审计日志
     */
    public static AuditLog create(
        AuditAction action,
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetName,
        String details,
        boolean success
    ) {
        return new AuditLog(
            UUID.randomUUID(),
            action,
            actorId,
            actorName,
            targetId,
            targetName,
            details,
            Instant.now(),
            null,
            success
        );
    }

    /**
     * 格式化为可读字符串
     */
    public String format() {
        return String.format(
            "[%s] %s(%s) 对 %s(%s) 执行了 %s: %s (%s)",
            timestamp,
            actorName,
            actorId,
            targetName != null ? targetName : "N/A",
            targetId != null ? targetId : "N/A",
            action.getDisplayName(),
            details,
            success ? "成功" : "失败"
        );
    }
}
