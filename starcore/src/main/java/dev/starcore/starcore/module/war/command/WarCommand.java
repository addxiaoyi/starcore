package dev.starcore.starcore.module.war.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.gui.WarMenu;
import dev.starcore.starcore.module.war.gui.WarSituationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 战争命令处理器
 * /war <子命令>
 *
 * 中文别名:
 *   gui/menu/gui → 打开战争菜单
 *   declare/d/宣战 → 宣战
 *   end/e/停战 → 停战
 *   status/s/状态 → 战争状态
 *   list/ls/列表 → 战争列表
 *   info/i/信息 → 战争详情
 */
public final class WarCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_DECLARE = "starcore.war.declare";
    private static final String PERMISSION_END = "starcore.war.end";
    private static final String PERMISSION_ADMIN = "starcore.war.admin";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault());

    private final WarService warService;
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final MessageService messages;
    private WarMenu warMenu;
    private WarSituationMenu warSituationMenu;

    public WarCommand(
        WarService warService,
        NationService nationService,
        DiplomacyService diplomacyService,
        MessageService messages
    ) {
        this.warService = warService;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.messages = messages;
    }

    /**
     * 设置战争菜单实例（由 WarModule 调用）
     */
    public void setWarMenu(WarMenu warMenu) {
        this.warMenu = warMenu;
    }

    /**
     * 设置战况预览菜单实例（由 WarModule 调用）
     */
    public void setWarSituationMenu(WarSituationMenu warSituationMenu) {
        this.warSituationMenu = warSituationMenu;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = normalizeSubCommand(args[0].toLowerCase());

        try {
            switch (subCommand) {
                case "gui" -> handleGui(sender);
                case "declare" -> handleDeclare(sender, args);
                case "end" -> handleEnd(sender, args);
                case "status" -> handleStatus(sender, args);
                case "list" -> handleList(sender, args);
                case "info" -> handleInfo(sender, args);
                default -> showHelp(sender);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            sender.sendMessage(Component.text("An error occurred: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            // 打开 GUI
            case "gui", "menu", "菜", "菜单", "界面" -> "gui";
            // 宣战
            case "declare", "d", "宣战", "宣" -> "declare";
            // 停战
            case "end", "e", "停战", "停", "和平", "结束战争" -> "end";
            // 状态
            case "status", "s", "状态", "状", "战况" -> "status";
            // 列表
            case "list", "ls", "列表", "列", "所有" -> "list";
            // 信息
            case "info", "i", "信息", "详", "详情" -> "info";
            default -> input;
        };
    }

    /**
     * 处理打开 GUI 菜单命令
     * /war gui
     */
    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                messages.format("common.player-only"),
                NamedTextColor.RED
            ));
            return;
        }

        if (warSituationMenu != null) {
            warSituationMenu.openMainMenu(player);
        } else if (warMenu != null) {
            warMenu.openMainMenu(player);
        } else {
            player.sendMessage(Component.text(
                messages.format("war.menu.unavailable"),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理宣战命令
     * /war declare <nation1> <nation2>
     */
    private void handleDeclare(CommandSender sender, String[] args) {
        // 检查宣战权限
        if (!sender.hasPermission(PERMISSION_DECLARE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text(
                messages.format("war.permission.declare"),
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text(
                messages.format("war.usage.declare"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        String nation1Name = args[1];
        String nation2Name = args[2];

        // 查找国家
        Optional<Nation> nation1Opt = nationService.nationByName(nation1Name);
        Optional<Nation> nation2Opt = nationService.nationByName(nation2Name);

        if (nation1Opt.isEmpty()) {
            sender.sendMessage(Component.text(
                messages.format("war.common.nation-not-found", nation1Name),
                NamedTextColor.RED
            ));
            return;
        }

        if (nation2Opt.isEmpty()) {
            sender.sendMessage(Component.text(
                messages.format("war.common.nation-not-found", nation2Name),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation1 = nation1Opt.get();
        Nation nation2 = nation2Opt.get();

        // 检查是否是同一个国家
        if (nation1.id().equals(nation2.id())) {
            sender.sendMessage(Component.text(
                messages.format("war.declare.cannot-self"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否已经在战争中
        if (warService.atWar(nation1.id(), nation2.id())) {
            sender.sendMessage(Component.text(
                messages.format("war.common.already-at-war", nation1.name(), nation2.name()),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查外交关系
        DiplomacyRelation relation = diplomacyService.relationBetween(nation1.id(), nation2.id());
        if (relation == DiplomacyRelation.ALLIED || relation.name().contains("ALLIANCE")) {
            sender.sendMessage(Component.text(
                messages.format("war.declare.allied-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        // 执行宣战
        boolean success = warService.declareWar(nation1.id(), nation2.id());

        if (success) {
            // 广播消息
            String declareMsg = messages.format("war.declare.broadcast",
                nation1.name(), nation2.name());
            broadcast(net.kyori.adventure.text.Component.text(declareMsg, NamedTextColor.DARK_RED).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, net.kyori.adventure.text.format.TextDecoration.State.TRUE));

            sender.sendMessage(Component.text(
                messages.format("war.declare.success", nation1.name(), nation2.name()),
                NamedTextColor.GREEN
            ));
        } else {
            sender.sendMessage(Component.text(
                messages.format("war.declare.failed"),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理停战命令
     * /war end <nation1> <nation2>
     */
    private void handleEnd(CommandSender sender, String[] args) {
        // 检查停战权限
        if (!sender.hasPermission(PERMISSION_END) && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text("You don't have permission to end war!", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /war end <nation1> <nation2>", NamedTextColor.YELLOW));
            return;
        }

        String nation1Name = args[1];
        String nation2Name = args[2];

        // 查找国家
        Optional<Nation> nation1Opt = nationService.nationByName(nation1Name);
        Optional<Nation> nation2Opt = nationService.nationByName(nation2Name);

        if (nation1Opt.isEmpty()) {
            sender.sendMessage(Component.text("Nation not found: " + nation1Name, NamedTextColor.RED));
            return;
        }

        if (nation2Opt.isEmpty()) {
            sender.sendMessage(Component.text("Nation not found: " + nation2Name, NamedTextColor.RED));
            return;
        }

        Nation nation1 = nation1Opt.get();
        Nation nation2 = nation2Opt.get();

        // 检查是否处于战争状态
        if (!warService.atWar(nation1.id(), nation2.id())) {
            sender.sendMessage(Component.text(
                nation1.name() + " and " + nation2.name() + " are not at war!",
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 执行停战
        boolean success = warService.endWar(nation1.id(), nation2.id());

        if (success) {
            // 广播消息
            String endMsg = messages.format("war.end.broadcast",
                nation1.name(), nation2.name());
            broadcast(net.kyori.adventure.text.Component.text(endMsg, NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, net.kyori.adventure.text.format.TextDecoration.State.TRUE));

            sender.sendMessage(Component.text(
                "War ended between " + nation1.name() + " and " + nation2.name(),
                NamedTextColor.GREEN
            ));
        } else {
            sender.sendMessage(Component.text(
                "Failed to end war. Please try again.",
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理战争状态命令
     * /war status
     */
    private void handleStatus(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            handleStatusForPlayer(player);
        } else {
            handleStatusForConsole(sender);
        }
    }

    private void handleStatusForPlayer(Player player) {
        // 获取玩家所在国家
        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());

        if (playerNationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                "You are not a member of any nation!",
                NamedTextColor.RED
            ));
            return;
        }

        Nation playerNation = playerNationOpt.get();
        Collection<WarSnapshot> wars = warService.activeWarsOf(playerNation.id());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== War Status ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Your Nation: " + playerNation.name(), NamedTextColor.YELLOW));

        if (wars.isEmpty()) {
            player.sendMessage(Component.text(
                "Your nation is not currently at war.",
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                "Your nation is at war with " + wars.size() + " nation(s):",
                NamedTextColor.RED
            ));

            for (WarSnapshot war : wars) {
                NationId enemyId = war.left().equals(playerNation.id())
                    ? war.right()
                    : war.left();

                Optional<Nation> enemyOpt = nationService.nationById(enemyId);
                String enemyName = enemyOpt.map(Nation::name).orElse("Unknown");

                Duration duration = Duration.between(war.declaredAt(), Instant.now());
                String durationStr = formatDuration(duration);

                Component warInfo = Component.text("  - " + enemyName)
                    .color(NamedTextColor.RED)
                    .append(Component.text(" (Duration: " + durationStr + ")")
                        .color(NamedTextColor.GRAY));

                player.sendMessage(warInfo);
            }
        }

        // 显示所有活跃战争
        Collection<WarSnapshot> allWars = warService.activeWars();
        if (!allWars.isEmpty()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Global Active Wars: " + allWars.size(), NamedTextColor.DARK_RED));

            for (WarSnapshot war : allWars) {
                String name1 = nationService.nationById(war.left())
                    .map(Nation::name).orElse("Unknown");
                String name2 = nationService.nationById(war.right())
                    .map(Nation::name).orElse("Unknown");

                player.sendMessage(Component.text(
                    "  " + name1 + " vs " + name2,
                    NamedTextColor.GRAY
                ));
            }
        }

        player.sendMessage(Component.text(""));
    }

    private void handleStatusForConsole(CommandSender sender) {
        Collection<WarSnapshot> allWars = warService.activeWars();

        sender.sendMessage(Component.text("=== Global War Status ===", NamedTextColor.GOLD));

        if (allWars.isEmpty()) {
            sender.sendMessage(Component.text("No active wars.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                "Active wars: " + allWars.size(),
                NamedTextColor.RED
            ));

            for (WarSnapshot war : allWars) {
                String name1 = nationService.nationById(war.left())
                    .map(Nation::name).orElse("Unknown");
                String name2 = nationService.nationById(war.right())
                    .map(Nation::name).orElse("Unknown");

                Duration duration = Duration.between(war.declaredAt(), Instant.now());
                String durationStr = formatDuration(duration);

                sender.sendMessage(Component.text(
                    "  " + name1 + " vs " + name2 + " (Duration: " + durationStr + ")",
                    NamedTextColor.GRAY
                ));
            }
        }

        sender.sendMessage(Component.text(""));
    }

    /**
     * 处理列出所有战争命令
     * /war list
     */
    private void handleList(CommandSender sender, String[] args) {
        Collection<WarSnapshot> allWars = warService.activeWars();

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Active Wars (" + allWars.size() + ") ===", NamedTextColor.GOLD));

        if (allWars.isEmpty()) {
            sender.sendMessage(Component.text("No active wars.", NamedTextColor.GREEN));
        } else {
            for (WarSnapshot war : allWars) {
                String name1 = nationService.nationById(war.left())
                    .map(Nation::name).orElse("Unknown");
                String name2 = nationService.nationById(war.right())
                    .map(Nation::name).orElse("Unknown");

                Duration duration = Duration.between(war.declaredAt(), Instant.now());
                String durationStr = formatDuration(duration);

                sender.sendMessage(Component.text(
                    "  " + name1 + " vs " + name2,
                    NamedTextColor.RED
                ));
                sender.sendMessage(Component.text(
                    "    Declared: " + DATE_FORMATTER.format(war.declaredAt()) + " | Duration: " + durationStr,
                    NamedTextColor.GRAY
                ));
            }
        }

        sender.sendMessage(Component.text(""));
    }

    /**
     * 处理特定战争详情命令
     * /war info <nation1> <nation2>
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /war info <nation1> <nation2>", NamedTextColor.YELLOW));
            return;
        }

        String nation1Name = args[1];
        String nation2Name = args[2];

        // 查找国家
        Optional<Nation> nation1Opt = nationService.nationByName(nation1Name);
        Optional<Nation> nation2Opt = nationService.nationByName(nation2Name);

        if (nation1Opt.isEmpty()) {
            sender.sendMessage(Component.text("Nation not found: " + nation1Name, NamedTextColor.RED));
            return;
        }

        if (nation2Opt.isEmpty()) {
            sender.sendMessage(Component.text("Nation not found: " + nation2Name, NamedTextColor.RED));
            return;
        }

        Nation nation1 = nation1Opt.get();
        Nation nation2 = nation2Opt.get();

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== War Info: " + nation1.name() + " vs " + nation2.name() + " ===", NamedTextColor.GOLD));

        // 检查战争状态
        boolean isAtWar = warService.atWar(nation1.id(), nation2.id());

        if (isAtWar) {
            sender.sendMessage(Component.text("Status: At War", NamedTextColor.RED));

            // 查找对应的战争记录
            Collection<WarSnapshot> wars = warService.activeWars();
            for (WarSnapshot war : wars) {
                if ((war.left().equals(nation1.id()) && war.right().equals(nation2.id())) ||
                    (war.left().equals(nation2.id()) && war.right().equals(nation1.id()))) {

                    Duration duration = Duration.between(war.declaredAt(), Instant.now());
                    sender.sendMessage(Component.text(
                        "Declared: " + DATE_FORMATTER.format(war.declaredAt()),
                        NamedTextColor.GRAY
                    ));
                    sender.sendMessage(Component.text(
                        "Duration: " + formatDuration(duration),
                        NamedTextColor.GRAY
                    ));

                    // 显示宣战国
                    String aggressorName = nationService.nationById(war.left())
                        .map(Nation::name).orElse("Unknown");
                    sender.sendMessage(Component.text(
                        "Aggressor: " + aggressorName,
                        NamedTextColor.DARK_RED
                    ));
                    break;
                }
            }
        } else {
            sender.sendMessage(Component.text("Status: Peace", NamedTextColor.GREEN));

            // 检查外交关系
            DiplomacyRelation relation = diplomacyService.relationBetween(nation1.id(), nation2.id());
            sender.sendMessage(Component.text(
                "Diplomatic Relation: " + relation.displayName(),
                NamedTextColor.GRAY
            ));
        }

        sender.sendMessage(Component.text(""));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== War Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/war gui (menu) - Open war menu GUI", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/war declare <nation1> <nation2> (d/宣战) - Declare war", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/war end <nation1> <nation2> (e/停战) - End war", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/war status (s/状态) - Show your nation's war status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/war list (ls/列表) - List all active wars", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/war info <nation1> <nation2> (i/信息) - Show war info", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
    }

    private void broadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        // 第一级补全：所有子命令（中英文）
        if (args.length == 1) {
            return List.of(
                "gui", "menu", "菜",
                "declare", "d", "宣战",
                "end", "e", "停战",
                "status", "s", "状态",
                "list", "ls", "列表",
                "info", "i", "信息"
            ).stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        String normalized = normalizeSubCommand(args[0]);

        // 第二级补全：国家名称
        if (args.length == 2 || args.length == 3) {
            String nationInput = args[args.length - 1].toLowerCase();
            return nationService.nations().stream()
                .map(Nation::name)
                .filter(name -> name.toLowerCase().startsWith(nationInput))
                .sorted()
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
