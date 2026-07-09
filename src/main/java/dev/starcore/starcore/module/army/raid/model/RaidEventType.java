package dev.starcore.starcore.module.army.raid.model;

/**
 * 突袭事件类型
 */
public enum RaidEventType {
    RAID_CREATED("突袭创建"),
    PLAYER_JOINED_ATTACKER("玩家加入攻击方"),
    PLAYER_JOINED_DEFENDER("玩家加入防御方"),
    PLAYER_LEFT("玩家离开"),
    COMBAT_STARTED("战斗开始"),
    PLAYER_KILL("玩家击杀"),
    BUILDING_DESTROYED("建筑被摧毁"),
    LOOT("掠夺资源"),
    VICTORY_REACHED("胜利条件达成"),
    RAID_ENDED("突袭结束"),
    RAID_CANCELLED("突袭取消"),
    RAID_EXPIRED("突袭过期");

    private final String displayName;

    RaidEventType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }
}