package dev.starcore.starcore.monitoring.command;

import dev.starcore.starcore.monitoring.PerformanceMonitor;
import dev.starcore.starcore.monitoring.PerformanceMonitor.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 性能监控命令 - Bukkit原生实现
 * 命令：/perf <子命令>
 */
public class PerformanceCommand implements CommandExecutor, TabCompleter {

    private final PerformanceMonitor monitor;

    public PerformanceCommand(PerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (!player.hasPermission("starcore.admin")) {
            player.sendMessage("§c你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handlePerf(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "cache" -> {
                return handleCache(player);
            }
            case "tps" -> {
                return handleTPS(player);
            }
            case "memory", "mem" -> {
                return handleMemory(player);
            }
            case "gc" -> {
                return handleGC(player);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                player.sendMessage("§7可用命令: cache, tps, memory, gc");
                return true;
            }
        }
    }

    private boolean handlePerf(Player player) {
        PerformanceReport report = monitor.generateReport();

        player.sendMessage("§6§l==== 服务器性能监控 ====");
        // 分隔

        // TPS
        player.sendMessage("§e§lTPS:");
        player.sendMessage(String.format(
            "  当前: §f%.2f §7| 状态: %s",
            report.tps(),
            report.tpsHealth().getDisplayName()
        ));
        // 分隔

        // 内存
        MemoryMetrics memory = report.memory();
        player.sendMessage("§e§l内存:");
        player.sendMessage(String.format(
            "  使用: §f%dMB §7/ §f%dMB §7(%.1f%%)",
            memory.usedMB(),
            memory.maxMB(),
            memory.usagePercent()
        ));
        player.sendMessage("  状态: " + memory.getHealthStatus());
        // 分隔

        // 世界
        WorldMetrics world = report.world();
        player.sendMessage("§e§l世界:");
        player.sendMessage(String.format(
            "  世界数: §f%d §7| 玩家: §f%d",
            world.worldCount(),
            world.playerCount()
        ));
        player.sendMessage(String.format(
            "  实体: §f%d §7| 区块: §f%d",
            world.totalEntities(),
            world.totalChunks()
        ));
        // 分隔

        // 综合评分
        player.sendMessage("§e§l综合评分:");
        player.sendMessage(String.format(
            "  得分: §f%.1f/100 §7| 状态: %s",
            report.performanceScore(),
            report.getOverallHealth()
        ));
        return true;
    }

    private boolean handleCache(Player player) {
        Map<String, CacheMetrics> caches = monitor.getCacheMetrics();

        if (caches.isEmpty()) {
            player.sendMessage("§7暂无缓存统计数据");
            return true;
        }

        player.sendMessage("§6§l==== 缓存统计 ====");

        for (Map.Entry<String, CacheMetrics> entry : caches.entrySet()) {
            String name = entry.getKey();
            CacheMetrics metrics = entry.getValue();

            // 分隔
            player.sendMessage("§e" + name + ":");
            player.sendMessage(String.format(
                "  大小: §f%d §7| 命中: §f%d §7| 未命中: §f%d",
                metrics.size(),
                metrics.hits(),
                metrics.misses()
            ));
            player.sendMessage(String.format(
                "  命中率: §f%.2f%% §7| 状态: %s",
                metrics.hitRate() * 100,
                metrics.getHealthStatus()
            ));
        }
        return true;
    }

    private boolean handleTPS(Player player) {
        double tps = monitor.getCurrentTPS();
        TPSHealth health = monitor.getTPSHealth();

        player.sendMessage("§6§l==== TPS监控 ====");
        // 分隔
        player.sendMessage("§e当前TPS:");
        player.sendMessage(String.format("  §f%.2f §7/ §f20.00", tps));
        // 分隔
        player.sendMessage("§e健康状态:");
        player.sendMessage("  " + health.getDisplayName());
        player.sendMessage("  §7" + health.getDescription());
        // 分隔

        if (tps < 18.0) {
            player.sendMessage("§c⚠ TPS较低，服务器可能出现卡顿");
            player.sendMessage("§7建议检查：");
            player.sendMessage("§7- 过多的加载区块");
            player.sendMessage("§7- 过多的实体");
            player.sendMessage("§7- 插件性能问题");
        }
        return true;
    }

    private boolean handleMemory(Player player) {
        MemoryMetrics memory = monitor.getMemoryMetrics();

        player.sendMessage("§6§l==== 内存监控 ====");
        // 分隔
        player.sendMessage("§e堆内存:");
        player.sendMessage(String.format(
            "  已使用: §f%dMB",
            memory.usedMB()
        ));
        player.sendMessage(String.format(
            "  已分配: §f%dMB",
            memory.committedMB()
        ));
        player.sendMessage(String.format(
            "  最大值: §f%dMB",
            memory.maxMB()
        ));
        // 分隔
        player.sendMessage(String.format(
            "§e使用率: §f%.1f%%",
            memory.usagePercent()
        ));
        player.sendMessage("§e状态: " + memory.getHealthStatus());
        // 分隔

        if (memory.usagePercent() > 80) {
            player.sendMessage("§c⚠ 内存使用率过高");
            player.sendMessage("§7建议：");
            player.sendMessage("§7- 使用 /perf gc 手动触发GC");
            player.sendMessage("§7- 检查内存泄漏");
            player.sendMessage("§7- 考虑增加堆内存");
        }
        return true;
    }

    private boolean handleGC(Player player) {
        MemoryMetrics before = monitor.getMemoryMetrics();

        player.sendMessage("§a正在触发垃圾回收...");

        System.gc();

        // 等待1秒让GC完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }

        MemoryMetrics after = monitor.getMemoryMetrics();

        long freed = before.usedMB() - after.usedMB();

        player.sendMessage("§a垃圾回收完成！");
        player.sendMessage(String.format(
            "§7释放内存: §f%dMB",
            freed
        ));
        player.sendMessage(String.format(
            "§7使用率: §f%.1f%% §7→ §f%.1f%%",
            before.usagePercent(),
            after.usagePercent()
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("cache", "tps", "memory", "mem", "gc")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
