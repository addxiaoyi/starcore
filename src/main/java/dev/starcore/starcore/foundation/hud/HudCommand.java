package dev.starcore.starcore.foundation.hud;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HUD 菜单命令
 * 提供现代化的菜单体验
 */
public class HudCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final NationModule nationModule;
    private final MessageService messages;

    public HudCommand(Plugin plugin, NationModule nationModule, MessageService messages) {
        this.plugin = plugin;
        this.nationModule = nationModule;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // 打开主菜单
            openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "main", "menu" -> openMainMenu(player);
            case "style" -> {
                if (args.length > 1) {
                    changeStyle(player, args[1]);
                } else {
                    showStyleHelp(player);
                }
            }
            case "particle" -> {
                if (args.length > 1) {
                    changeParticle(player, args[1]);
                } else {
                    showParticleHelp(player);
                }
            }
            case "particles" -> toggleParticles(player);
            case "breathing" -> toggleBreathing(player);
            case "reload" -> reloadHudConfig(player);
            case "list" -> listStyles(player);
            case "preview" -> {
                if (args.length > 1) {
                    previewStyle(player, args[1]);
                } else {
                    previewStyle(player, "nightmare");
                }
            }
            default -> showHelp(player);
        }

        return true;
    }

    /**
     * 打开主菜单
     */
    private void openMainMenu(Player player) {
        // 获取国家信息
        MainMenuHud.PlayerNationInfo nationInfo = nationModule.nationOf(player.getUniqueId())
            .map(nation -> new MainMenuHud.PlayerNationInfo(
                nation.name(),
                nation.memberCount(),
                nation.territoryCount(),
                nation.getTreasuryBalance().doubleValue(),
                nation.getGovernmentType()
            ))
            .orElse(new MainMenuHud.PlayerNationInfo());

        // 创建并打开主菜单
        MainMenuHud hud = MainMenuHud.create(player, plugin);
        hud.setNationInfo(nationInfo);
        hud.setParticlesEnabled(ModernHudListener.getInstance().isGlobalParticlesEnabled());
        hud.open();

        // 启动粒子效果
        ModernHudListener.getInstance().startParticleEffect(player);
    }

    /**
     * 更改菜单样式
     */
    private void changeStyle(Player player, String styleName) {
        try {
            GlassPaneStyle style = GlassPaneStyle.valueOf(styleName.toUpperCase());
            player.sendMessage(Component.text()
                .content("✓ 样式已更改: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(style.name()).color(NamedTextColor.GOLD))
            );
            // 重新打开菜单以应用新样式
            openMainMenu(player);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text()
                .content("✗ 未知样式: ")
                .color(NamedTextColor.RED)
                .append(Component.text(styleName).color(NamedTextColor.GRAY))
            );
            listStyles(player);
        }
    }

    /**
     * 更改粒子效果
     */
    private void changeParticle(Player player, String particleName) {
        try {
            ModernHudListener.ParticleType type = ModernHudListener.ParticleType.valueOf(particleName.toUpperCase());
            ModernHudListener.getInstance().setParticleType(type);
            player.sendMessage(Component.text()
                .content("✓ 粒子效果已更改: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(type.getDisplayName()).color(NamedTextColor.AQUA))
            );
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text()
                .content("✗ 未知粒子类型: ")
                .color(NamedTextColor.RED)
                .append(Component.text(particleName).color(NamedTextColor.GRAY))
            );
        }
    }

    /**
     * 切换粒子效果
     */
    private void toggleParticles(Player player) {
        ModernHudListener listener = ModernHudListener.getInstance();
        boolean current = listener.isGlobalParticlesEnabled();
        listener.setGlobalParticlesEnabled(!current);

        player.sendMessage(Component.text()
            .content("粒子效果: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(!current ? "启用" : "禁用")
                .color(!current ? NamedTextColor.GREEN : NamedTextColor.RED))
        );
    }

    /**
     * 切换呼吸灯效果
     */
    private void toggleBreathing(Player player) {
        player.sendMessage(Component.text("呼吸灯效果切换", NamedTextColor.YELLOW));
        // 重新打开菜单以应用新设置
        openMainMenu(player);
    }

    /**
     * 重新加载HUD配置
     */
    private void reloadHudConfig(Player player) {
        player.sendMessage(Component.text("✓ HUD 配置已重新加载", NamedTextColor.GREEN));
        openMainMenu(player);
    }

    /**
     * 列出所有样式
     */
    private void listStyles(Player player) {
        player.sendMessage(Component.text("═══ 可用样式 ═══", NamedTextColor.GOLD)
            .decoration(TextDecoration.OBFUSCATED, false));

        for (GlassPaneStyle style : GlassPaneStyle.values()) {
            Component styleName = Component.text(style.name())
                .color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/hud style " + style.name().toLowerCase()))
                .hoverEvent(HoverEvent.showText(Component.text("点击切换到 " + style.name())));

            player.sendMessage(Component.text()
                .append(Component.text(" • ").color(NamedTextColor.GRAY))
                .append(styleName));
        }
    }

    /**
     * 预览样式
     */
    private void previewStyle(Player player, String styleName) {
        try {
            GlassPaneStyle style = GlassPaneStyle.valueOf(styleName.toUpperCase());

            MainMenuHud hud = MainMenuHud.create(player, plugin, style);
            hud.setParticlesEnabled(false); // 预览时不显示粒子
            hud.setBreathingEnabled(false);
            hud.open();

            player.sendMessage(Component.text()
                .content("预览样式: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(style.name()).color(NamedTextColor.GOLD)));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("✗ 未知样式: " + styleName, NamedTextColor.RED));
        }
    }

    /**
     * 显示样式帮助
     */
    private void showStyleHelp(Player player) {
        player.sendMessage(Component.text("═══ 样式帮助 ═══", NamedTextColor.GOLD)
            .decoration(TextDecoration.OBFUSCATED, false));
        player.sendMessage(Component.text("/hud style <名称> - 更改菜单样式", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/hud list - 列出所有可用样式", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/hud preview <样式> - 预览样式", NamedTextColor.GRAY));
    }

    /**
     * 显示粒子帮助
     */
    private void showParticleHelp(Player player) {
        player.sendMessage(Component.text("═══ 粒子帮助 ═══", NamedTextColor.GOLD)
            .decoration(TextDecoration.OBFUSCATED, false));

        for (ModernHudListener.ParticleType type : ModernHudListener.ParticleType.values()) {
            player.sendMessage(Component.text()
                .append(Component.text(" • ").color(NamedTextColor.GRAY))
                .append(Component.text(type.name()).color(NamedTextColor.AQUA))
                .append(Component.text(" - " + type.getDisplayName()).color(NamedTextColor.GRAY)));
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text("═══ StarCore HUD ═══", NamedTextColor.GOLD)
            .decoration(TextDecoration.OBFUSCATED, false));
        player.sendMessage(Component.text("/hud - 打开主菜单", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud menu - 打开主菜单", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud style <样式> - 更改样式", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud particle <类型> - 更改粒子效果", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud particles - 切换粒子效果", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud list - 列出所有样式", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud preview <样式> - 预览样式", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/hud reload - 重新加载配置", NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "menu", "main", "style", "particle", "particles",
                "breathing", "reload", "list", "preview"
            ));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("style") || sub.equals("preview")) {
                completions.addAll(Arrays.stream(GlassPaneStyle.values())
                    .map(s -> s.name().toLowerCase())
                    .collect(Collectors.toList()));
            } else if (sub.equals("particle")) {
                completions.addAll(Arrays.stream(ModernHudListener.ParticleType.values())
                    .map(t -> t.name().toLowerCase())
                    .collect(Collectors.toList()));
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.startsWith(current))
            .sorted()
            .collect(Collectors.toList());
    }
}