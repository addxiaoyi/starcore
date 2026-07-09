package dev.starcore.starcore.audit;

/**
 * 审计操作类型
 */
public enum AuditActionType {
    // 命令相关
    COMMAND,

    // 经济相关
    ECONOMY_DEPOSIT,
    ECONOMY_WITHDRAW,
    ECONOMY_TRANSFER,

    // 权限相关
    PERMISSION_CHANGE,

    // 配置相关
    CONFIG_CHANGE,

    // 决斗相关
    DUEL_CREATE,
    DUEL_ACCEPT,
    DUEL_END,

    // NPC相关
    NPC_CREATE,
    NPC_DELETE,
    NPC_COMMAND,

    // 成就相关
    ACHIEVEMENT_UNLOCK,
    ACHIEVEMENT_REWARD,

    // 国家相关
    NATION_CREATE,
    NATION_DELETE,
    NATION_CLAIM,

    // 其他
    LOGIN,
    LOGOUT,
    KICK,
    BAN
}
