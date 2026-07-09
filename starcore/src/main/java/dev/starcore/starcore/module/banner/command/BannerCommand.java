package dev.starcore.starcore.module.banner.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.banner.BannerService;
import dev.starcore.starcore.module.banner.model.BannerColor;
import dev.starcore.starcore.module.banner.model.BannerPattern;
import dev.starcore.starcore.module.banner.model.NationBanner;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Banner command handler
 * /sc banner <subcommand>
 */
public final class BannerCommand implements CommandExecutor, TabCompleter {
    private final BannerService bannerService;
    private final NationService nationService;
    private final MessageService messages;

    public BannerCommand(BannerService bannerService, NationService nationService, MessageService messages) {
        this.bannerService = bannerService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "view", "v" -> handleView(player);
                case "list", "ls" -> handleList(player);
                case "pattern", "p" -> handlePattern(player, args);
                case "base", "b" -> handleBaseColor(player, args);
                case "color", "c" -> handlePatternColor(player, args);
                case "design", "d" -> handleDesign(player, args);
                case "reset", "r" -> handleReset(player);
                case "cost", "price" -> handleCost(player, args);
                case "preview", "previewcolors" -> handlePreviewColors(player);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("错误: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleView(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        NationBanner banner = bannerService.getBannerByUUID(nationId).orElseGet(() ->
            bannerService.createDefaultBanner(nationId)
        );

        player.sendMessage(Component.text("========== 国家旗帜 ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("样式: " + banner.getPatternDisplayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("基础色: " + getColorDisplay(banner.baseColor()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("图案色: " + getColorDisplay(banner.patternColor()), NamedTextColor.GRAY));

        if (banner.isDefault()) {
            player.sendMessage(Component.text("状态: 默认旗帜 (未自定义)", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("状态: 已自定义", NamedTextColor.GREEN));
        }
        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void handleList(Player player) {
        Collection<NationBanner> allBanners = bannerService.getAllBanners();

        player.sendMessage(Component.text("========== 旗帜列表 ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总计: " + allBanners.size() + " 个旗帜", NamedTextColor.GRAY));

        int count = 0;
        for (NationBanner banner : allBanners) {
            if (count >= 20) {
                player.sendMessage(Component.text("... 还有更多旗帜", NamedTextColor.GRAY));
                break;
            }

            nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(banner.nationId()))
                .ifPresent(nation -> {
                    Component line = Component.text()
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text(nation.name(), NamedTextColor.AQUA))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(Component.text(banner.getPatternDisplayName(), NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(getColorDisplay(banner.baseColor()), NamedTextColor.WHITE))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(getColorDisplay(banner.patternColor()), NamedTextColor.WHITE))
                        .build();
                    player.sendMessage(line);
                });
            count++;
        }
        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void handlePattern(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // Check if player is leader
        if (!nation.isFounder(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创始人可以更改旗帜！", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /sc banner pattern <样式>", NamedTextColor.YELLOW));
            showPatternList(player);
            return;
        }

        String patternKey = args[1].toLowerCase();
        BannerPattern pattern = BannerPattern.fromKey(patternKey);

        UUID nationId = nation.id().value();
        NationBanner updated = bannerService.updatePattern(nationId, pattern.key());

        player.sendMessage(Component.text("旗帜样式已更新为: " + pattern.displayName(), NamedTextColor.GREEN));
        showBannerPreview(player, updated);
    }

    private void handleBaseColor(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        if (!nation.isFounder(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创始人可以更改旗帜！", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /sc banner base <颜色>", NamedTextColor.YELLOW));
            showColorList(player);
            return;
        }

        String colorKey = args[1].toUpperCase();
        BannerColor color = BannerColor.fromKey(colorKey);

        UUID nationId = nation.id().value();
        NationBanner updated = bannerService.updateBaseColor(nationId, color.key());

        player.sendMessage(Component.text("基础色已更新为: " + color.displayName(), NamedTextColor.GREEN));
        showBannerPreview(player, updated);
    }

    private void handlePatternColor(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        if (!nation.isFounder(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创始人可以更改旗帜！", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /sc banner color <颜色>", NamedTextColor.YELLOW));
            showColorList(player);
            return;
        }

        String colorKey = args[1].toUpperCase();
        BannerColor color = BannerColor.fromKey(colorKey);

        UUID nationId = nation.id().value();
        NationBanner updated = bannerService.updatePatternColor(nationId, color.key());

        player.sendMessage(Component.text("图案色已更新为: " + color.displayName(), NamedTextColor.GREEN));
        showBannerPreview(player, updated);
    }

    private void handleDesign(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        if (!nation.isFounder(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创始人可以更改旗帜！", NamedTextColor.RED));
            return;
        }

        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /sc banner design <样式> <基础色> <图案色>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("示例: /sc banner design cross white black", NamedTextColor.GRAY));
            return;
        }

        String patternKey = args[1].toLowerCase();
        String baseColor = args[2].toUpperCase();
        String patternColor = args[3].toUpperCase();

        BannerPattern pattern = BannerPattern.fromKey(patternKey);
        BannerColor base = BannerColor.fromKey(baseColor);
        BannerColor patternC = BannerColor.fromKey(patternColor);

        UUID nationId = nation.id().value();
        NationBanner updated = bannerService.updateDesign(nationId, pattern.key(), base.key(), patternC.key());

        player.sendMessage(Component.text("旗帜设计已更新！", NamedTextColor.GREEN));
        player.sendMessage(Component.text("样式: " + pattern.displayName() + " | 基础色: " + base.displayName() + " | 图案色: " + patternC.displayName(), NamedTextColor.GRAY));
        showBannerPreview(player, updated);
    }

    private void handleReset(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家！", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        if (!nation.isFounder(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创始人可以重置旗帜！", NamedTextColor.RED));
            return;
        }

        UUID nationId = nation.id().value();
        NationBanner reset = bannerService.resetToDefault(nationId);

        player.sendMessage(Component.text("旗帜已重置为默认样式", NamedTextColor.YELLOW));
        showBannerPreview(player, reset);
    }

    private void handleCost(Player player, String[] args) {
        player.sendMessage(Component.text("========== 样式费用表 ==========", NamedTextColor.GOLD));

        for (BannerPattern pattern : BannerPattern.values()) {
            String costStr = pattern.cost() == 0 ? "免费" : pattern.cost() + " 金币";
            player.sendMessage(Component.text()
                .append(Component.text(String.format("%-15s", pattern.displayName()), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(costStr, pattern.cost() == 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .build());
        }

        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void handlePreviewColors(Player player) {
        player.sendMessage(Component.text("========== 可用颜色 ==========", NamedTextColor.GOLD));

        for (BannerColor color : BannerColor.values()) {
            TextColor textColor = getTextColor(color.key());
            player.sendMessage(Component.text()
                .append(Component.text("[●] ", textColor))
                .append(Component.text(color.displayName(), NamedTextColor.WHITE))
                .append(Component.text(" (" + color.key() + ")", NamedTextColor.GRAY))
                .build());
        }

        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
    }

    private void showPatternList(Player player) {
        player.sendMessage(Component.text("可用样式:", NamedTextColor.GRAY));
        for (BannerPattern pattern : BannerPattern.values()) {
            if (pattern.ordinal() % 2 == 0) {
                player.sendMessage(Component.text()
                    .append(Component.text(String.format("%-15s", pattern.displayName()), NamedTextColor.WHITE))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(pattern.key(), NamedTextColor.DARK_GRAY))
                    .build());
            }
        }
    }

    private void showColorList(Player player) {
        player.sendMessage(Component.text("可用颜色:", NamedTextColor.GRAY));
        for (BannerColor color : BannerColor.values()) {
            TextColor textColor = getTextColor(color.key());
            player.sendMessage(Component.text()
                .append(Component.text("[●] ", textColor))
                .append(Component.text(color.key(), NamedTextColor.WHITE))
                .append(Component.text(" - " + color.displayName(), NamedTextColor.GRAY))
                .build());
        }
    }

    private void showBannerPreview(Player player, NationBanner banner) {
        TextColor baseTextColor = getTextColor(banner.baseColor());
        TextColor patternTextColor = getTextColor(banner.patternColor());

        player.sendMessage(Component.text("旗帜预览:", NamedTextColor.GRAY));
        Component preview = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("■", baseTextColor))  // Base color
            .append(Component.text("★", patternTextColor)) // Pattern color
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .append(Component.text(" " + banner.getPatternDisplayName() + " " + getColorDisplay(banner.baseColor()) + "/" + getColorDisplay(banner.patternColor()), NamedTextColor.WHITE))
            .build();
        player.sendMessage(preview);
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("========== 国家旗帜命令 ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/sc banner view", NamedTextColor.YELLOW) .append(Component.text(" - 查看当前旗帜", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner list", NamedTextColor.YELLOW) .append(Component.text(" - 列出所有旗帜", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner pattern <样式>", NamedTextColor.YELLOW) .append(Component.text(" - 设置旗帜样式", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner base <颜色>", NamedTextColor.YELLOW) .append(Component.text(" - 设置基础色", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner color <颜色>", NamedTextColor.YELLOW) .append(Component.text(" - 设置图案色", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner design <样式> <基础色> <图案色>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/sc banner reset", NamedTextColor.YELLOW) .append(Component.text(" - 重置为默认", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner cost", NamedTextColor.YELLOW) .append(Component.text(" - 查看样式费用", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/sc banner previewcolors", NamedTextColor.YELLOW) .append(Component.text(" - 预览所有颜色", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("==================================", NamedTextColor.GOLD));
    }

    private String getColorDisplay(String colorKey) {
        BannerColor color = BannerColor.fromKey(colorKey);
        return color.displayName() + " (" + color.key() + ")";
    }

    private TextColor getTextColor(String colorKey) {
        return switch (colorKey.toUpperCase()) {
            case "WHITE" -> TextColor.fromCSSHexString("#FFFFFF");
            case "ORANGE" -> TextColor.fromCSSHexString("#FF8C00");
            case "MAGENTA" -> TextColor.fromCSSHexString("#FF00FF");
            case "LIGHT_BLUE" -> TextColor.fromCSSHexString("#ADD8E6");
            case "YELLOW" -> TextColor.fromCSSHexString("#FFFF00");
            case "LIME" -> TextColor.fromCSSHexString("#00FF00");
            case "PINK" -> TextColor.fromCSSHexString("#FFB6C1");
            case "GRAY" -> TextColor.fromCSSHexString("#808080");
            case "LIGHT_GRAY" -> TextColor.fromCSSHexString("#C0C0C0");
            case "CYAN" -> TextColor.fromCSSHexString("#00FFFF");
            case "PURPLE" -> TextColor.fromCSSHexString("#800080");
            case "BLUE" -> TextColor.fromCSSHexString("#0000FF");
            case "BROWN" -> TextColor.fromCSSHexString("#8B4513");
            case "GREEN" -> TextColor.fromCSSHexString("#008000");
            case "RED" -> TextColor.fromCSSHexString("#FF0000");
            case "BLACK" -> TextColor.fromCSSHexString("#000000");
            default -> TextColor.fromCSSHexString("#FFFFFF");
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return List.of("view", "list", "pattern", "base", "color", "design", "reset", "cost", "previewcolors");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "pattern" -> Arrays.stream(BannerPattern.values())
                    .map(BannerPattern::key)
                    .collect(Collectors.toList());
                case "base", "color" -> Arrays.stream(BannerColor.values())
                    .map(BannerColor::key)
                    .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("design")) {
            return Arrays.stream(BannerColor.values())
                .map(BannerColor::key)
                .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("design")) {
            return Arrays.stream(BannerColor.values())
                .map(BannerColor::key)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
