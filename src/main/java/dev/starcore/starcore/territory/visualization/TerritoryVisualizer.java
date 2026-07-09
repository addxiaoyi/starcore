package dev.starcore.starcore.territory.visualization;

import dev.starcore.starcore.foundation.territory.model.Territory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory边界可视化系统
 * 基于SimpleClaimSystem的智能边界算法
 */
public class TerritoryVisualizer {

    private final Plugin plugin;
    private final Map<UUID, VisualizationTask> activeTasks = new ConcurrentHashMap<>();

    // 边界颜色配置
    private static final Particle.DustOptions FRIENDLY_COLOR =
        new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f);

    private static final Particle.DustOptions NEUTRAL_COLOR =
        new Particle.DustOptions(Color.fromRGB(255, 255, 0), 1.0f);

    private static final Particle.DustOptions ENEMY_COLOR =
        new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.0f);

    private static final Particle.DustOptions OWN_COLOR =
        new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.0f);

    public TerritoryVisualizer(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示Territory边界
     */
    public void showBorder(Player player, Chunk chunk, BorderType type, int duration) {
        // 取消已存在的可视化
        stopVisualization(player);

        // 计算边界点
        Set<Location> borderLocations = calculateBorder(chunk);

        // 选择颜色
        Particle.DustOptions color = getColorForType(type);

        // 创建可视化任务
        VisualizationTask task = new VisualizationTask(
            player, borderLocations, color, duration
        );

        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L); // 每5tick更新一次
    }

    /**
     * 显示玩家所在Territory信息
     */
    public void showTerritoryInfo(Player player, Territory territory) {
        if (territory == null) {
            player.sendActionBar(Component.text("§7荒野", NamedTextColor.GRAY));
            return;
        }

        String nationName = territory.nationName();
        String ownerId = territory.ownerId();

        player.sendActionBar(Component.text(
            "§6" + nationName + " §7的领地 §8| §7所有者: §e" + ownerId,
            NamedTextColor.GOLD
        ));
    }

    /**
     * 停止可视化
     */
    public void stopVisualization(Player player) {
        VisualizationTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 计算边界点（简化版）
     */
    private Set<Location> calculateBorder(Chunk chunk) {
        Set<Location> borders = new HashSet<>();
        World world = chunk.getWorld();

        int xStart = chunk.getX() << 4;
        int zStart = chunk.getZ() << 4;
        int xEnd = xStart + 15;
        int zEnd = zStart + 15;

        int minY = 60;
        int maxY = 100;
        int spacing = 2;

        // 西侧边界
        for (int y = minY; y <= maxY; y += spacing) {
            for (int z = zStart; z <= zEnd; z += spacing) {
                borders.add(new Location(world, xStart, y, z));
            }
        }

        // 东侧边界
        for (int y = minY; y <= maxY; y += spacing) {
            for (int z = zStart; z <= zEnd; z += spacing) {
                borders.add(new Location(world, xEnd + 1, y, z));
            }
        }

        // 北侧边界
        for (int y = minY; y <= maxY; y += spacing) {
            for (int x = xStart; x <= xEnd; x += spacing) {
                borders.add(new Location(world, x, y, zStart));
            }
        }

        // 南侧边界
        for (int y = minY; y <= maxY; y += spacing) {
            for (int x = xStart; x <= xEnd; x += spacing) {
                borders.add(new Location(world, x, y, zEnd + 1));
            }
        }

        return borders;
    }

    /**
     * 根据类型获取颜色
     */
    private Particle.DustOptions getColorForType(BorderType type) {
        return switch (type) {
            case OWN -> OWN_COLOR;
            case FRIENDLY -> FRIENDLY_COLOR;
            case NEUTRAL -> NEUTRAL_COLOR;
            case ENEMY -> ENEMY_COLOR;
        };
    }

    /**
     * 边界类型
     */
    public enum BorderType {
        OWN,        // 自己的领地（蓝色）
        FRIENDLY,   // 友好领地（绿色）
        NEUTRAL,    // 中立领地（黄色）
        ENEMY       // 敌对领地（红色）
    }

    /**
     * 可视化任务
     */
    private class VisualizationTask extends BukkitRunnable {
        private final Player player;
        private final Set<Location> locations;
        private final Particle.DustOptions color;
        private int remainingTicks;

        public VisualizationTask(Player player, Set<Location> locations,
                                Particle.DustOptions color, int durationSeconds) {
            this.player = player;
            this.locations = locations;
            this.color = color;
            this.remainingTicks = durationSeconds * 20; // 转换为ticks
        }

        @Override
        public void run() {
            if (!player.isOnline() || remainingTicks <= 0) {
                cancel();
                activeTasks.remove(player.getUniqueId());
                return;
            }

            // 显示粒子
            for (Location loc : locations) {
                // 只显示玩家附近的粒子（优化性能）
                if (loc.distance(player.getLocation()) <= 64) {
                    player.spawnParticle(
                        Particle.DUST,
                        loc,
                        1,
                        0, 0, 0,
                        0,
                        color
                    );
                }
            }

            remainingTicks -= 5; // 每次减少5ticks
        }
    }
}
