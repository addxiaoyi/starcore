package dev.starcore.starcore.performance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化的移动事件监听器
 * 减少不必要的计算和处理
 */
public final class OptimizedMoveListener implements Listener {
    // 玩家上次处理的位置（方块坐标）
    private final Map<UUID, BlockPosition> lastProcessedPosition = new ConcurrentHashMap<>();

    // 玩家上次处理的时间（毫秒）
    private final Map<UUID, Long> lastProcessedTime = new ConcurrentHashMap<>();

    // 配置
    private final long minProcessInterval = 50; // 最小处理间隔（毫秒）
    private final boolean ignoreHeadMovement = true; // 忽略头部转动

    /**
     * 优化的移动事件处理
     * 只在玩家实际移动方块时处理
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 1. 忽略头部转动（只改变视角，不改变位置）
        if (ignoreHeadMovement) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to == null) return;

            // 如果坐标完全相同，只是视角变化
            if (from.getX() == to.getX() &&
                from.getY() == to.getY() &&
                from.getZ() == to.getZ()) {
                return;
            }
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 2. 检查是否跨方块移动
        BlockPosition currentBlock = new BlockPosition(event.getTo());
        BlockPosition lastBlock = lastProcessedPosition.get(playerId);

        if (lastBlock != null && lastBlock.equals(currentBlock)) {
            // 还在同一个方块内，不处理
            return;
        }

        // 3. 检查处理间隔（防止频繁触发）
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastProcessedTime.get(playerId);

        if (lastTime != null && (currentTime - lastTime) < minProcessInterval) {
            // 距离上次处理时间太短，跳过
            return;
        }

        // 4. 更新记录
        lastProcessedPosition.put(playerId, currentBlock);
        lastProcessedTime.put(playerId, currentTime);

        // 5. 执行实际的业务逻辑
        processPlayerMove(player, event.getTo());
    }

    /**
     * 实际的移动处理逻辑
     * 只在必要时调用
     */
    private void processPlayerMove(Player player, Location location) {
        // 在这里添加实际需要的移动处理逻辑
        // 例如：区域检测、战斗标记检查等

        // 示例：检查是否进入特殊区域
        // checkRegion(player, location);

        // 示例：更新战斗状态
        // updateCombatStatus(player);
    }

    /**
     * 玩家退出时清理
     */
    public void cleanup(UUID playerId) {
        lastProcessedPosition.remove(playerId);
        lastProcessedTime.remove(playerId);
    }

    /**
     * 方块位置（用于比较）
     */
    private static class BlockPosition {
        private final int x;
        private final int y;
        private final int z;
        private final String world;

        public BlockPosition(Location location) {
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
            this.world = location.getWorld().getName();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BlockPosition other)) return false;
            return x == other.x && y == other.y && z == other.z && world.equals(other.world);
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + world.hashCode();
            return result;
        }
    }
}
