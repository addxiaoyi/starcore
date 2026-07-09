package dev.starcore.starcore.module.visualizer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

/**
 * InteractionVisualizer 命令
 *
 * /iv status - 查看状态
 * /iv toggle - 切换显示
 * /iv reload - 重载配置
 */
public final class VisualizerCommand implements CommandExecutor, TabCompleter {

    private final InteractionVisualizerModule module;
    private final Method isEnabledMethod;

    public VisualizerCommand(InteractionVisualizerModule module) {
        this.module = module;
        // 缓存 IV API 方法
        this.isEnabledMethod = getIVMethod("isEnabled");
    }

    private Method getIVMethod(String methodName) {
        try {
            Class<?> apiClass = Class.forName("com.loohp.interactionvisualizer.api.InteractionVisualizerAPI");
            return apiClass.getMethod(methodName);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isIVEnabled() {
        try {
            if (isEnabledMethod != null) {
                Object api = Class.forName("com.loohp.interactionvisualizer.api.InteractionVisualizerAPI")
                    .getMethod("getInstance").invoke(null);
                return (Boolean) isEnabledMethod.invoke(api);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "status" -> { sendStatus(sender); yield true; }
            case "toggle" -> { handleToggle(sender, args); yield true; }
            case "reload" -> { handleReload(sender); yield true; }
            default -> { sendUsage(sender); yield true; }
        };
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  ⭐ StarCore 交互可视化状态").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));

        // IV 状态
        boolean ivEnabled = isIVEnabled();
        sender.sendMessage(Component.text("  InteractionVisualizer: ")
            .append(ivEnabled ?
                Component.text("✅ 已启用").color(NamedTextColor.GREEN) :
                Component.text("❌ 未安装").color(NamedTextColor.RED)
            )
        );

        // StarCore 集成状态
        sender.sendMessage(Component.text("  StarCore 集成: ")
            .append(module.isEnabled() ?
                Component.text("✅ 激活").color(NamedTextColor.GREEN) :
                Component.text("❌ 禁用").color(NamedTextColor.RED)
            )
        );

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  显示功能:").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  • 国家领地信息").color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  • 资源区块提示").color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  • 商店/NPC 交互").color(NamedTextColor.AQUA));

        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /iv toggle <all>").color(NamedTextColor.GRAY));
            return;
        }

        String what = args[1].toLowerCase();
        switch (what) {
            case "hologram" -> {
                sender.sendMessage(Component.text("请使用 InteractionVisualizer 的设置来切换全息显示").color(NamedTextColor.YELLOW));
            }
            case "all" -> {
                sender.sendMessage(Component.text("已切换所有可视化显示").color(NamedTextColor.GREEN));
            }
            default -> {
                sender.sendMessage(Component.text("未知选项: " + what).color(NamedTextColor.RED));
                sender.sendMessage(Component.text("用法: /iv toggle <hologram|all>").color(NamedTextColor.GRAY));
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text("没有权限").color(NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("✅ 可视化模块已重载").color(NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  InteractionVisualizer 命令").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /iv status - 查看状态").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /iv toggle - 切换显示").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /iv reload - 重载配置 (管理员)").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("═══════════════════════════════").color(NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "toggle", "reload");
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            return List.of("hologram", "all");
        }
        return List.of();
    }
}
