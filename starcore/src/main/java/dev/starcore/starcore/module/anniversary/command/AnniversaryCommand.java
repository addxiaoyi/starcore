package dev.starcore.starcore.module.anniversary.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.anniversary.AnniversaryService;
import dev.starcore.starcore.module.anniversary.model.AnniversaryType;
import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 纪念日命令处理器
 * /sc anniversary <子命令>
 */
public final class AnniversaryCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AnniversaryService anniversaryService;
    private final NationService nationService;
    private final MessageService messages;

    public AnniversaryCommand(
        AnniversaryService anniversaryService,
        NationService nationService,
        MessageService messages
    ) {
        this.anniversaryService = anniversaryService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create", "add", "c" -> handleCreate(player, args);
                case "list", "ls", "l" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "delete", "del", "d" -> handleDelete(player, args);
                case "today", "t" -> handleToday(player);
                case "upcoming", "u" -> handleUpcoming(player, args);
                case "celebrate" -> handleCelebrate(player, args);
                case "setmessage" -> handleSetMessage(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException | DateTimeParseException e) {
            player.sendMessage(Component.text("错误: " + e.getMessage(), NamedTextColor.RED));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                messages.format("anniversary.create.usage", "/sc anniversary create <名称> <日期(yyyy-MM-dd)> <类型>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        String name = args[1];

        // 解析日期
        LocalDate date;
        try {
            date = LocalDate.parse(args[2], DATE_FORMAT);
        } catch (DateTimeParseException e) {
            player.sendMessage(Component.text(
                "日期格式错误，请使用 yyyy-MM-dd 格式",
                NamedTextColor.RED
            ));
            return;
        }

        // 解析类型
        AnniversaryType type;
        try {
            type = AnniversaryType.fromString(args[3]);
        } catch (Exception e) {
            player.sendMessage(Component.text(
                "无效的纪念日类型: " + args[3],
                NamedTextColor.RED
            ));
            return;
        }

        // 可选的描述
        String description = "";
        if (args.length > 4) {
            description = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        }

        // 创建纪念日
        NationAnniversary anniversary = anniversaryService.createAnniversary(
            nation.id().value(), name, date, type, description
        );

        player.sendMessage(Component.text(
            messages.format("anniversary.created", anniversary.name(), type.getDisplayName()),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        List<NationAnniversary> anniversaries = anniversaryService.getAnniversaries(nation.id().value());

        if (anniversaries.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.no-anniversaries"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("anniversary.list.header", nation.name()),
            NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        for (NationAnniversary ann : anniversaries) {
            String emoji = ann.type().getEmoji();
            String dateStr = ann.date().format(DATE_FORMAT);
            int year = ann.getCurrentYear();
            int daysUntil = ann.daysUntil();

            String status;
            if (ann.isToday()) {
                status = messages.format("anniversary.status.today");
            } else if (daysUntil > 0) {
                status = messages.format("anniversary.status.upcoming", daysUntil);
            } else {
                status = messages.format("anniversary.status.passed", Math.abs(daysUntil));
            }

            player.sendMessage(Component.text(
                String.format(" %s %s - %s (第%d周年) %s",
                    emoji, ann.name(), dateStr, year, status),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("anniversary.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID anniversaryId = parseAnniversaryId(player, args[1]);
        Optional<NationAnniversary> annOpt = anniversaryService.getAnniversary(anniversaryId);

        if (annOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        NationAnniversary ann = annOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("anniversary.info.header"), NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text(messages.format("anniversary.info.id", ann.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.info.name", ann.name()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.info.type", ann.type().getEmoji() + " " + ann.type().getDisplayName()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.info.date", ann.date().format(DATE_FORMAT)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.info.year", ann.getCurrentYear()), NamedTextColor.GRAY));

        if (ann.description() != null && !ann.description().isEmpty()) {
            player.sendMessage(Component.text(messages.format("anniversary.info.description", ann.description()), NamedTextColor.GRAY));
        }

        if (ann.isToday()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.info.today"),
                NamedTextColor.GREEN
            ).decoration(TextDecoration.BOLD, true));
        } else {
            int daysUntil = ann.daysUntil();
            if (daysUntil > 0) {
                player.sendMessage(Component.text(
                    messages.format("anniversary.info.days-until", daysUntil),
                    NamedTextColor.YELLOW
                ));
            }
        }

        if (ann.isMilestone()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.info.milestone"),
                NamedTextColor.GOLD
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("anniversary.delete.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID anniversaryId = parseAnniversaryId(player, args[1]);

        // 验证所有权
        Optional<NationAnniversary> annOpt = anniversaryService.getAnniversary(anniversaryId);
        if (annOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否为创始人
        NationAnniversary ann = annOpt.get();
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() || !nationOpt.get().founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("anniversary.delete.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        anniversaryService.deleteAnniversary(anniversaryId);

        player.sendMessage(Component.text(
            messages.format("anniversary.deleted", ann.name()),
            NamedTextColor.GREEN
        ));
    }

    private void handleToday(Player player) {
        List<NationAnniversary> todayAnniversaries = anniversaryService.getAllTodayAnniversaries();

        if (todayAnniversaries.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.today.none"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("anniversary.today.header"), NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));

        for (NationAnniversary ann : todayAnniversaries) {
            Optional<Nation> nation = nationService.nationById(
                new dev.starcore.starcore.module.nation.model.NationId(ann.nationId())
            );
            String nationName = nation.map(Nation::name).orElse("Unknown");

            player.sendMessage(Component.text(
                String.format(" %s %s (%s) - %s",
                    ann.type().getEmoji(),
                    ann.name(),
                    nationName,
                    messages.format("anniversary.year.format", ann.getCurrentYear())),
                NamedTextColor.GREEN
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleUpcoming(Player player, String[] args) {
        int days = 7; // 默认7天
        if (args.length > 1) {
            try {
                days = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                    "天数必须是数字",
                    NamedTextColor.RED
                ));
                return;
            }
        }

        List<NationAnniversary> upcoming = anniversaryService.getAllUpcomingAnniversaries(days);

        if (upcoming.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.upcoming.none", days),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("anniversary.upcoming.header", days),
            NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        for (NationAnniversary ann : upcoming) {
            Optional<Nation> nation = nationService.nationById(
                new dev.starcore.starcore.module.nation.model.NationId(ann.nationId())
            );
            String nationName = nation.map(Nation::name).orElse("Unknown");

            player.sendMessage(Component.text(
                String.format(" %s %s (%s) - %s",
                    ann.type().getEmoji(),
                    ann.name(),
                    nationName,
                    messages.format("anniversary.days-until", ann.daysUntil())),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleCelebrate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("anniversary.celebrate.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID anniversaryId = parseAnniversaryId(player, args[1]);
        Optional<NationAnniversary> annOpt = anniversaryService.getAnniversary(anniversaryId);

        if (annOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        NationAnniversary ann = annOpt.get();

        if (!ann.isToday()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.celebrate.not-today", ann.daysUntil()),
                NamedTextColor.RED
            ));
            return;
        }

        anniversaryService.markAsCelebrated(anniversaryId, java.time.LocalDateTime.now());

        player.sendMessage(Component.text(
            messages.format("anniversary.celebrated", ann.name()),
            NamedTextColor.GREEN
        ));
    }

    private void handleSetMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("anniversary.setmessage.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID anniversaryId = parseAnniversaryId(player, args[1]);
        Optional<NationAnniversary> annOpt = anniversaryService.getAnniversary(anniversaryId);

        if (annOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("anniversary.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        NationAnniversary ann = annOpt.get();
        String message = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        ann.setCelebrationMessage(message);
        anniversaryService.updateAnniversary(ann);

        player.sendMessage(Component.text(
            messages.format("anniversary.message.set"),
            NamedTextColor.GREEN
        ));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("anniversary.help.header"), NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text(messages.format("anniversary.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.delete"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.today"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.upcoming"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("anniversary.help.celebrate"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseAnniversaryId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位），从玩家国家的纪念日中查找
        if (idStr.length() <= 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<NationAnniversary> anns = anniversaryService.getAnniversaries(nationOpt.get().id().value());
                Optional<UUID> match = anns.stream()
                    .map(NationAnniversary::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("纪念日未找到: " + idStr);
        }
        throw new IllegalArgumentException("无效的纪念日ID格式: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "delete", "today", "upcoming", "celebrate");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("info") ||
            args[0].equalsIgnoreCase("delete") ||
            args[0].equalsIgnoreCase("celebrate"))) {
            // 返回玩家国家的纪念日
            if (sender instanceof Player player) {
                Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
                if (nationOpt.isPresent()) {
                    return anniversaryService.getAnniversaries(nationOpt.get().id().value())
                        .stream()
                        .map(a -> a.id().toString().substring(0, 8))
                        .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("upcoming")) {
            return List.of("7", "14", "30", "60", "90");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(AnniversaryType.values())
                .map(AnniversaryType::name)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
