package dev.starcore.starcore.region;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 区域命令处理器
 * /region 命令的主要处理类
 */
public class RegionCommand implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = Logger.getLogger(RegionCommand.class.getName());
    private final RegionModule regionModule;

    public RegionCommand(RegionModule regionModule) {
        this.regionModule = regionModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);

            case "test":
                return handleTest(sender, args);

            case "info":
                return handleInfo(sender);

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage("§c未知的子命令。使用 /region help 查看帮助");
                return true;
        }
    }

    /**
     * 处理重载命令
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.region.reload")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        try {
            regionModule.getTitleService().reloadConfig();
            sender.sendMessage("§a区域配置已重新加载");
        } catch (Exception e) {
            sender.sendMessage("§c重载配置时出错: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Failed to reload region config", e);
        }

        return true;
    }

    /**
     * 处理测试命令
     */
    private boolean handleTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (!sender.hasPermission("starcore.region.test")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        Player player = (Player) sender;

        // 强制检查玩家当前区域
        regionModule.getDetectionListener().forceCheckPlayer(player);
        sender.sendMessage("§a已触发区域检测");

        return true;
    }

    /**
     * 处理信息命令
     */
    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (!sender.hasPermission("starcore.region.info")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        Player player = (Player) sender;

        // 检测当前位置的区域
        var regionInfo = regionModule.getIntegrationService().detectRegion(player, player.getLocation());

        if (regionInfo.isEmpty()) {
            sender.sendMessage("§e你当前不在任何特殊区域中");
            return true;
        }

        var info = regionInfo.get();
        sender.sendMessage("§6=== 当前区域信息 ===");
        sender.sendMessage("§e类型: §f" + info.getType().name());
        sender.sendMessage("§e标识: §f" + info.getRegionKey());
        sender.sendMessage("§e名称: §f" + info.getDisplayName());
        if (info.getSubtitle() != null) {
            sender.sendMessage("§e副标题: §f" + info.getSubtitle());
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== 区域系统命令帮助 ===");
        sender.sendMessage("§e/region help §f- 显示此帮助信息");
        sender.sendMessage("§e/region info §f- 查看当前位置的区域信息");
        sender.sendMessage("§e/region test §f- 测试区域检测（触发标题显示）");
        sender.sendMessage("§e/region reload §f- 重新加载区域配置");
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> subCommands = Arrays.asList("help", "info", "test", "reload");

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }

            return completions;
        }

        return new ArrayList<>();
    }
}
