package dev.starcore.starcore.core.database;

import dev.starcore.starcore.core.StarCoreContext;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库迁移命令
 *
 * 用法:
 * - /dbmigrate info     - 显示迁移状态
 * - /dbmigrate migrate  - 执行迁移
 * - /dbdbmigrate undo   - 回滚最后一次迁移
 * - /dbmigrate redo     - 重做最后一次迁移
 * - /dbmigrate validate - 验证数据库状态
 * - /dbmigrate repair   - 修复数据库状态
 * - /dbmigrate backup  - 创建备份
 * - /dbmigrate history - 显示迁移历史
 * - /dbmigrate pending - 显示待执行迁移
 */
public class MigrationCommand implements CommandExecutor, TabCompleter {

    private final StarCoreContext context;
    private static final String PERMISSION = "starcore.admin.database";

    public MigrationCommand(StarCoreContext context) {
        this.context = context;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info", "status" -> showInfo(sender);
            case "migrate", "run" -> executeMigrate(sender, args);
            case "undo" -> executeUndo(sender, args);
            case "redo" -> executeRedo(sender);
            case "validate", "check" -> executeValidate(sender);
            case "repair", "fix" -> executeRepair(sender, args);
            case "backup" -> executeBackup(sender);
            case "history", "log" -> showHistory(sender);
            case "pending" -> showPending(sender);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage("§c未知命令: " + subCommand);
                sendHelp(sender);
            }
        }

        return true;
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage("§6========== 数据库迁移状态 ==========");

        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                DatabaseMigrationService.MigrationSummary summary = service.getSummary();

                sender.sendMessage("§a当前版本: §f" + summary.currentVersion);
                sender.sendMessage("§a已执行迁移: §f" + summary.appliedCount);
                sender.sendMessage("§a待执行迁移: §f" + summary.pendingCount);
                sender.sendMessage("§a总迁移数: §f" + summary.totalCount);
                sender.sendMessage("§a平均执行时间: §f" + summary.averageExecutionTimeMs + " ms");

                // 分隔
                sender.sendMessage("§6========== 可用命令 ==========");
                sender.sendMessage("§e/dbmigrate migrate [版本] §7- 执行迁移到指定版本");
                sender.sendMessage("§e/dbmigrate undo [数量] §7- 回滚迁移");
                sender.sendMessage("§e/dbmigrate redo §7- 重做迁移");
                sender.sendMessage("§e/dbmigrate validate §7- 验证数据库");
                sender.sendMessage("§e/dbmigrate repair §7- 修复数据库");
                sender.sendMessage("§e/dbmigrate backup §7- 创建备份");
                sender.sendMessage("§e/dbmigrate history §7- 查看历史");
                sender.sendMessage("§e/dbmigrate pending §7- 查看待执行");
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeMigrate(CommandSender sender, String[] args) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                String targetVersion = args.length > 1 ? args[1] : null;

                sender.sendMessage("§6开始执行数据库迁移...");

                DatabaseMigrationService.MigrationResult result;
                if (targetVersion != null) {
                    sender.sendMessage("§6目标版本: " + targetVersion);
                    result = service.migrate(targetVersion);
                } else {
                    result = service.migrate();
                }

                if (result.success) {
                    sender.sendMessage("§a迁移成功！");
                    sender.sendMessage("§a执行迁移数: " + result.migrationsExecuted);
                    sender.sendMessage("§a目标版本: " + result.targetVersion);
                    sender.sendMessage("§a耗时: " + result.executionTimeMs + " ms");
                } else {
                    sender.sendMessage("§c迁移失败: " + result.errorMessage);
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeUndo(CommandSender sender, String[] args) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                int count = args.length > 1 ? Integer.parseInt(args[1]) : 1;

                sender.sendMessage("§6开始回滚迁移 (数量: " + count + ")...");

                DatabaseMigrationService.MigrationResult result = service.undo(count);

                if (result.success) {
                    sender.sendMessage("§a回滚成功！");
                    sender.sendMessage("§a回滚迁移数: " + result.migrationsExecuted);
                } else {
                    sender.sendMessage("§c回滚失败: " + result.errorMessage);
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeRedo(CommandSender sender) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                sender.sendMessage("§6开始重做迁移...");

                DatabaseMigrationService.MigrationResult result = service.redo();

                if (result.success) {
                    sender.sendMessage("§a重做成功！");
                    sender.sendMessage("§a重做迁移数: " + result.migrationsExecuted);
                } else {
                    sender.sendMessage("§c重做失败: " + result.errorMessage);
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeValidate(CommandSender sender) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                sender.sendMessage("§6正在验证数据库状态...");

                boolean valid = service.validate();

                if (valid) {
                    sender.sendMessage("§a数据库状态验证通过！");
                } else {
                    sender.sendMessage("§c数据库状态验证失败！");
                    sender.sendMessage("§e建议运行 /dbmigrate repair 修复");
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeRepair(CommandSender sender, String[] args) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                String baselineVersion = args.length > 1 ? args[1] : "0";

                sender.sendMessage("§6开始修复数据库状态 (baseline: " + baselineVersion + ")...");

                DatabaseMigrationService.MigrationResult result = service.repair(baselineVersion);

                if (result.success) {
                    sender.sendMessage("§a数据库修复成功！");
                } else {
                    sender.sendMessage("§c数据库修复失败: " + result.errorMessage);
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void executeBackup(CommandSender sender) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                sender.sendMessage("§6正在创建数据库备份...");

                boolean success = service.createBackup("manual");

                if (success) {
                    sender.sendMessage("§a备份创建成功！");
                } else {
                    sender.sendMessage("§c备份创建失败");
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void showHistory(CommandSender sender) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                List<DatabaseMigrationService.MigrationRecord> history = service.getMigrationHistory();

                if (history.isEmpty()) {
                    sender.sendMessage("§7暂无迁移历史");
                    return;
                }

                sender.sendMessage("§6========== 迁移历史 ==========");

                for (int i = Math.max(0, history.size() - 10); i < history.size(); i++) {
                    DatabaseMigrationService.MigrationRecord record = history.get(i);
                    String status = record.success ? "§a成功" : "§c失败";
                    String time = java.time.Instant.ofEpochMilli(record.startTime).toString();
                    sender.sendMessage(String.format("§7%s | %s | %s | %dms | %s",
                        record.type.name(),
                        record.targetVersion != null ? record.targetVersion : "N/A",
                        time.substring(0, 19),
                        record.executionTimeMs,
                        status
                    ));
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void showPending(CommandSender sender) {
        context.databaseService().migrationService().ifPresentOrElse(
            service -> {
                List<DatabaseMigrationService.MigrationInfoEntry> migrations = service.getAllMigrations();

                List<DatabaseMigrationService.MigrationInfoEntry> pending = migrations.stream()
                    .filter(m -> "PENDING".equals(m.state))
                    .collect(Collectors.toList());

                if (pending.isEmpty()) {
                    sender.sendMessage("§a没有待执行的迁移");
                    return;
                }

                sender.sendMessage("§6========== 待执行迁移 ==========");

                for (DatabaseMigrationService.MigrationInfoEntry entry : pending) {
                    sender.sendMessage("§e" + entry.version + " §7- " + entry.description);
                }
            },
            () -> sender.sendMessage("§c数据库迁移服务未初始化")
        );
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== 数据库迁移命令 ==========");
        sender.sendMessage("§e/dbmigrate info §7- 显示迁移状态");
        sender.sendMessage("§e/dbmigrate migrate [版本] §7- 执行迁移");
        sender.sendMessage("§e/dbmigrate undo [数量] §7- 回滚迁移");
        sender.sendMessage("§e/dbmigrate redo §7- 重做迁移");
        sender.sendMessage("§e/dbmigrate validate §7- 验证数据库");
        sender.sendMessage("§e/dbmigrate repair [版本] §7- 修复数据库");
        sender.sendMessage("§e/dbmigrate backup §7- 创建备份");
        sender.sendMessage("§e/dbmigrate history §7- 查看历史");
        sender.sendMessage("§e/dbmigrate pending §7- 查看待执行");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList(
                "info", "migrate", "undo", "redo",
                "validate", "repair", "backup",
                "history", "pending", "help"
            ).stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "migrate" -> Arrays.asList("V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9");
                case "undo" -> Arrays.asList("1", "2", "3", "5", "10");
                case "repair" -> Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7");
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }
}
