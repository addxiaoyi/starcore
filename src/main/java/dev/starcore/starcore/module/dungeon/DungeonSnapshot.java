package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 副本快照
 * 用于副本世界的保存和恢复
 */
public record DungeonSnapshot(
    UUID instanceId,
    String dungeonId,
    String worldName,
    long createdAt,
    Map<String, BlockChange> blockChanges,
    Map<String, EntityState> entityStates,
    List<String> removedBlocks,
    List<String> addedBlocks
) {
    /**
     * 方块变更记录
     */
    public record BlockChange(
        String location,
        String originalBlock,
        String newBlock,
        long timestamp
    ) {}

    /**
     * 实体状态记录
     */
    public record EntityState(
        String entityId,
        String entityType,
        String location,
        Map<String, Object> metadata,
        boolean isAlive
    ) {}

    /**
     * 创建空快照
     */
    public static DungeonSnapshot empty(UUID instanceId, String dungeonId, String worldName) {
        return new DungeonSnapshot(
            instanceId,
            dungeonId,
            worldName,
            System.currentTimeMillis(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of()
        );
    }
}
