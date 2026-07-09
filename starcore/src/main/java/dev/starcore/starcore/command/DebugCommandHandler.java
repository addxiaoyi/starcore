package dev.starcore.starcore.command;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.cache.CacheManager;
import dev.starcore.starcore.core.cache.CacheStatistics;
import dev.starcore.starcore.core.database.DatabaseMigrationService;
import dev.starcore.starcore.core.metrics.PerformanceMetricsService;
import dev.starcore.starcore.core.metrics.PerformanceSnapshot;
import dev.starcore.starcore.foundation.message.MessageService;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * Handles debug commands for STARCORE diagnostics and monitoring.
 *
 * <p>Available subcommands:
 * <ul>
 *   <li>/sc debug performance - Show performance metrics snapshot</li>
 *   <li>/sc debug cache [name] - Show cache statistics</li>
 *   <li>/sc debug metrics reset - Reset performance metrics</li>
 *   <li>/sc debug metrics enable/disable - Toggle metrics collection</li>
 * </ul>
 */
public final class DebugCommandHandler {
    private final StarCoreContext context;
    private final MessageService messages;

    public DebugCommandHandler(StarCoreContext context) {
        this.context = context;
        this.messages = context.serviceRegistry().require(MessageService.class);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin.debug")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (subCommand) {
            case "performance", "perf" -> handlePerformance(sender, subArgs);
            case "cache" -> handleCache(sender, subArgs);
            case "metrics" -> handleMetrics(sender, subArgs);
            case "database", "db" -> handleDatabase(sender, subArgs);
            default -> {
                showHelp(sender);
                yield true;
            }
        };
    }

    private boolean handlePerformance(CommandSender sender, String[] args) {
        PerformanceMetricsService metrics = context.serviceRegistry()
            .find(PerformanceMetricsService.class)
            .orElse(null);

        if (metrics == null) {
            sender.sendMessage("§cPerformanceMetricsService is not available.");
            return true;
        }

        PerformanceSnapshot snapshot = metrics.snapshot();
        sender.sendMessage("§6" + "=".repeat(50));
        for (String line : snapshot.summary().split("\n")) {
            sender.sendMessage("§e" + line);
        }
        sender.sendMessage("§6" + "=".repeat(50));
        return true;
    }

    private boolean handleCache(CommandSender sender, String[] args) {
        CacheManager cacheManager = context.serviceRegistry()
            .find(CacheManager.class)
            .orElse(null);

        if (cacheManager == null) {
            sender.sendMessage("§cCacheManager is not available.");
            return true;
        }

        if (args.length == 0) {
            // Show all caches
            var caches = cacheManager.allCaches();
            if (caches.isEmpty()) {
                sender.sendMessage("§eNo caches registered.");
                return true;
            }

            sender.sendMessage("§6=== STARCORE Caches ===");
            for (var info : caches) {
                CacheStatistics stats = info.statistics();
                sender.sendMessage(String.format(
                    "§e%s: §f%d entries, §a%.1f%% §fhit rate",
                    info.name(),
                    info.size(),
                    stats.hitRate() * 100
                ));
            }
            sender.sendMessage("§7Use '/sc debug cache <name>' for details");
        } else {
            // Show specific cache
            String name = args[0];
            CacheStatistics stats = cacheManager.statistics(name);
            sender.sendMessage("§6=== Cache: " + name + " ===");
            sender.sendMessage("§eHits: §f" + stats.hitCount());
            sender.sendMessage("§eMisses: §f" + stats.missCount());
            sender.sendMessage("§eHit Rate: §f" + String.format("%.2f%%", stats.hitRate() * 100));
            sender.sendMessage("§eLoad Success: §f" + stats.loadSuccessCount());
            sender.sendMessage("§eLoad Failure: §f" + stats.loadFailureCount());
            sender.sendMessage("§eAvg Load Time: §f" + formatNanos(stats.averageLoadNanos()));
            sender.sendMessage("§eEvictions: §f" + stats.evictionCount());
        }
        return true;
    }

    private boolean handleMetrics(CommandSender sender, String[] args) {
        PerformanceMetricsService metrics = context.serviceRegistry()
            .find(PerformanceMetricsService.class)
            .orElse(null);

        if (metrics == null) {
            sender.sendMessage("§cPerformanceMetricsService is not available.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eMetrics enabled: §f" + metrics.isEnabled());
            sender.sendMessage("§7Use '/sc debug metrics reset' to reset");
            sender.sendMessage("§7Use '/sc debug metrics enable|disable' to toggle");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "reset" -> {
                metrics.reset();
                sender.sendMessage("§aPerformance metrics reset.");
            }
            case "enable" -> {
                metrics.setEnabled(true);
                sender.sendMessage("§aPerformance metrics enabled.");
            }
            case "disable" -> {
                metrics.setEnabled(false);
                sender.sendMessage("§cPerformance metrics disabled.");
            }
            default -> sender.sendMessage("§cUnknown action: " + action);
        }
        return true;
    }

    private boolean handleDatabase(CommandSender sender, String[] args) {
        var dbOpt = context.databaseService().migrationService();
        if (dbOpt.isEmpty()) {
            sender.sendMessage("§cDatabase migration service not available.");
            return true;
        }
        DatabaseMigrationService service = dbOpt.get();

        if (args.length == 0) {
            // Show database status
            DatabaseMigrationService.MigrationSummary summary = service.getSummary();
            sender.sendMessage("§6=== Database Status ===");
            sender.sendMessage("§eVersion: §f" + summary.currentVersion);
            sender.sendMessage("§eApplied: §f" + summary.appliedCount);
            sender.sendMessage("§ePending: §f" + summary.pendingCount);
            // 分隔
            sender.sendMessage("§6Available subcommands:");
            sender.sendMessage("§e/sc debug database migrate §7- Run pending migrations");
            sender.sendMessage("§e/sc debug database repair §7- Repair database state");
            sender.sendMessage("§e/sc debug database validate §7- Validate database state");
            sender.sendMessage("§e/sc debug database info §7- Show migration info");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "migrate" -> {
                sender.sendMessage("§eRunning database migrations...");
                DatabaseMigrationService.MigrationResult result = service.migrate();
                if (result.success) {
                    sender.sendMessage("§aDatabase migration completed successfully.");
                    if (result.migrationsExecuted > 0) {
                        sender.sendMessage("§aExecuted §f" + result.migrationsExecuted + " §amigration(s)");
                    } else {
                        sender.sendMessage("§7No migrations to execute.");
                    }
                } else {
                    sender.sendMessage("§cDatabase migration failed: " + result.errorMessage);
                }
            }
            case "repair" -> {
                sender.sendMessage("§eRepairing database state...");
                DatabaseMigrationService.MigrationResult result = service.repair("0");
                if (result.success) {
                    sender.sendMessage("§aDatabase repair completed successfully.");
                } else {
                    sender.sendMessage("§cDatabase repair failed: " + result.errorMessage);
                }
            }
            case "validate" -> {
                if (service.validate()) {
                    sender.sendMessage("§aDatabase validation passed.");
                } else {
                    sender.sendMessage("§cDatabase validation failed. Use §e/sc debug database repair §cto fix.");
                }
            }
            case "info" -> {
                DatabaseMigrationService.MigrationSummary summary = service.getSummary();
                sender.sendMessage("§6=== Database Migration Info ===");
                sender.sendMessage("§eCurrent Version: §f" + summary.currentVersion);
                sender.sendMessage("§eTotal Migrations: §f" + summary.totalCount);
                sender.sendMessage("§eApplied: §f" + summary.appliedCount);
                sender.sendMessage("§ePending: §f" + summary.pendingCount);
                sender.sendMessage("§eAvg Execution Time: §f" + summary.averageExecutionTimeMs + " ms");
            }
            default -> sender.sendMessage("§cUnknown database action: " + action);
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== STARCORE Debug Commands ===");
        sender.sendMessage("§e/sc debug performance §7- Performance metrics snapshot");
        sender.sendMessage("§e/sc debug cache [name] §7- Cache statistics");
        sender.sendMessage("§e/sc debug metrics reset §7- Reset metrics");
        sender.sendMessage("§e/sc debug metrics enable|disable §7- Toggle metrics");
        sender.sendMessage("§e/sc debug database [migrate|repair|validate|info] §7- Database operations");
    }

    private String formatNanos(long nanos) {
        if (nanos < 1_000) {
            return nanos + "ns";
        } else if (nanos < 1_000_000) {
            return String.format("%.2fμs", nanos / 1000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2fms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2fs", nanos / 1_000_000_000.0);
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin.debug")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("performance", "cache", "metrics", "database");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("metrics".equals(subCommand)) {
                return List.of("reset", "enable", "disable");
            }
            if ("database".equals(subCommand) || "db".equals(subCommand)) {
                return List.of("migrate", "repair", "validate", "info");
            }
            if ("cache".equals(subCommand)) {
                CacheManager cacheManager = context.serviceRegistry()
                    .find(CacheManager.class)
                    .orElse(null);
                if (cacheManager != null) {
                    return cacheManager.allCaches().stream()
                        .map(CacheManager.CacheInfo::name)
                        .toList();
                }
            }
        }

        return List.of();
    }
}
