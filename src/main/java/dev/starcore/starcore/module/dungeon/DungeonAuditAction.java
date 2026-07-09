package dev.starcore.starcore.module.dungeon;

/**
 * 审计日志动作类型
 */
public enum DungeonAuditAction {
    // 副本操作
    INSTANCE_CREATED("创建副本"),
    INSTANCE_STARTED("开始副本"),
    INSTANCE_COMPLETED("完成副本"),
    INSTANCE_FAILED("副本失败"),
    INSTANCE_TIMEOUT("副本超时"),
    INSTANCE_CANCELLED("取消副本"),

    // 玩家操作
    PLAYER_ENTER("进入副本"),
    PLAYER_LEAVE("离开副本"),
    PLAYER_KICKED("踢出副本"),
    PLAYER_DEATH("玩家死亡"),
    PLAYER_RESPAWN("玩家复活"),
    PLAYER_DISCONNECT("玩家断开"),

    // 战斗操作
    BOSS_SPAWNED("BOSS生成"),
    BOSS_PHASE_CHANGE("BOSS阶段变化"),
    BOSS_DEFEATED("BOSS击败"),
    MOB_KILLED("怪物击杀"),

    // 房间操作
    ROOM_ENTERED("进入房间"),
    ROOM_CLEARED("房间清除"),
    ROOM_FAILED("房间失败"),
    PUZZLE_SOLVED("谜题解开"),

    // 奖励操作
    REWARD_CLAIMED("领取奖励"),
    REWARD_DISTRIBUTED("奖励分发"),

    // 管理操作
    ADMIN_TELEPORT("管理员传送"),
    ADMIN_FORCE_START("强制开始"),
    ADMIN_FORCE_END("强制结束"),
    ADMIN_BACKUP("创建备份"),
    ADMIN_RESTORE("恢复备份"),

    // 其他
    WORLD_LOADED("世界加载"),
    WORLD_UNLOADED("世界卸载"),
    STATE_SAVED("状态保存"),
    STATE_RESTORED("状态恢复"),

    // 未知/未分类
    UNKNOWN("未知操作");

    private final String displayName;

    DungeonAuditAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
