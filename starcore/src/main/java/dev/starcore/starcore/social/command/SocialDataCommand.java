package dev.starcore.starcore.social.command;

import dev.starcore.starcore.social.SocialImportExportService;
import dev.starcore.starcore.social.SocialModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 社交数据导入导出命令
 *
 * 用法：
 * /social export - 导出所有社交数据
 * /social import <文件> - 导入社交数据
 * /social exportplayer [玩家] - 导出玩家数据
 * /social list - 列出所有导出文件
 */
public class SocialDataCommand implements CommandExecutor, TabCompleter {

    private final SocialModule socialModule;

    public SocialDataCommand(SocialModule socialModule) {
        this.socialModule = socialModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("starcore.social.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "export" -> handleExport(sender, args);
            case "import" -> handleImport(sender, args);
            case "exportplayer" -> handleExportPlayer(sender, args);
            case "list" -> handleList(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleExport(CommandSender sender, String[] args) {
        String fileName = args.length > 1 ? args[1] : null;
        SocialImportExportService service = socialModule.importExportService();

        if (service == null) {
            sender.sendMessage("§c导入导出服务未初始化");
            return;
        }

        sender.sendMessage("§a正在导出社交数据...");
        String path = service.exportAll(fileName);

        if (path != null) {
            sender.sendMessage("§a导出成功: §f" + path);
        } else {
            sender.sendMessage("§c导出失败，请查看控制台日志");
        }
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /social import <文件名>");
            return;
        }

        SocialImportExportService service = socialModule.importExportService();
        if (service == null) {
            sender.sendMessage("§c导入导出服务未初始化");
            return;
        }

        String fileName = args[1];
        String exportDir = service.getExportDirectory();
        String fullPath = exportDir + "/" + fileName;

        sender.sendMessage("§a正在导入社交数据...");
        boolean success = service.importAll(fullPath);

        if (success) {
            sender.sendMessage("§a导入成功!");
        } else {
            sender.sendMessage("§c导入失败，请查看控制台日志");
        }
    }

    private void handleExportPlayer(CommandSender sender, String[] args) {
        UUID playerId;

        if (args.length > 1) {
            // 指定玩家名
            Player target = sender.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c玩家不存在或不在线: " + args[1]);
                return;
            }
            playerId = target.getUniqueId();
        } else if (sender instanceof Player) {
            playerId = ((Player) sender).getUniqueId();
        } else {
            sender.sendMessage("§c请指定玩家: /social exportplayer <玩家名>");
            return;
        }

        SocialImportExportService service = socialModule.importExportService();
        if (service == null) {
            sender.sendMessage("§c导入导出服务未初始化");
            return;
        }

        sender.sendMessage("§a正在导出玩家数据...");
        String path = service.exportPlayerData(playerId);

        if (path != null) {
            sender.sendMessage("§a导出成功: §f" + path);
        } else {
            sender.sendMessage("§c导出失败，请查看控制台日志");
        }
    }

    private void handleList(CommandSender sender) {
        SocialImportExportService service = socialModule.importExportService();
        if (service == null) {
            sender.sendMessage("§c导入导出服务未初始化");
            return;
        }

        List<String> files = service.listExports();
        if (files.isEmpty()) {
            sender.sendMessage("§e暂无导出文件");
        } else {
            sender.sendMessage("§e导出文件列表:");
            for (String file : files) {
                sender.sendMessage("§f- " + file);
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== 社交数据管理 ===");
        sender.sendMessage("§e/social export [文件名] §7- 导出所有社交数据");
        sender.sendMessage("§e/social import <文件名> §7- 导入社交数据");
        sender.sendMessage("§e/social exportplayer [玩家] §7- 导出玩家社交数据");
        sender.sendMessage("§e/social list §7- 列出所有导出文件");
        sender.sendMessage("§7导出目录: §f" + (socialModule.importExportService() != null ?
            socialModule.importExportService().getExportDirectory() : "未初始化"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("export", "import", "exportplayer", "list"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("import") || args[0].equalsIgnoreCase("export")) {
                SocialImportExportService service = socialModule.importExportService();
                if (service != null) {
                    completions.addAll(service.listExports());
                }
            } else if (args[0].equalsIgnoreCase("exportplayer")) {
                sender.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        }

        return completions;
    }
}
