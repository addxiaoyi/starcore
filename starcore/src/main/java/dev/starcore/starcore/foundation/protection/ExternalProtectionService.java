package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;

public interface ExternalProtectionService {
    Optional<ProtectionConflict> findClaimConflict(ChunkClaimSelection selection);

    /**
     * 检查指定位置是否受外部保护插件保护
     *
     * @param location 要检查的位置
     * @return 如果位置受保护则返回 true
     */
    default boolean isProtectedAt(Location location) {
        if (location == null) {
            return false;
        }
        // 默认实现：创建单个区块选择进行检查
        String world = location.getWorld().getName();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        ChunkClaimSelection singleChunk = new ChunkClaimSelection(world, chunkX, chunkX, chunkZ, chunkZ);
        return findClaimConflict(singleChunk).isPresent();
    }

    String summary();

    default List<ExternalProtectionBridgeStatus> bridgeStatuses() {
        return List.of();
    }
}
