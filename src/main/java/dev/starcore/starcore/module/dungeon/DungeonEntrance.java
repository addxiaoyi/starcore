package dev.starcore.starcore.module.dungeon;

/**
 * 副本入口点
 */
public record DungeonEntrance(
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    /**
     * 检查入口是否有效
     */
    public boolean isValid() {
        return world != null && !world.isEmpty();
    }
}
