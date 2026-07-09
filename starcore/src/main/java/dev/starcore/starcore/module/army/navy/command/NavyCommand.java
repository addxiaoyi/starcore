package dev.starcore.starcore.module.army.navy.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.navy.NavyBattleCalculator;
import dev.starcore.starcore.module.army.navy.NavyService;
import dev.starcore.starcore.module.army.navy.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 海军命令处理器
 * /sc navy <子命令>
 */
public final class NavyCommand implements CommandExecutor, TabCompleter {
    private final NavyService navyService;
    private final NationService nationService;
    private final MessageService messages;

    public NavyCommand(NavyService navyService, NationService nationService, MessageService messages) {
        this.navyService = navyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                case "create", "c" -> handleCreate(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "move", "m" -> handleMove(player, args);
                case "engage", "e" -> handleEngage(player, args);
                case "supply", "s" -> handleSupply(player, args);
                case "disband", "d" -> handleDisband(player, args);
                case "rename", "rn" -> handleRename(player, args);
                case "embark", "emb" -> handleEmbark(player, args);
                case "disembark", "dis" -> handleDisembark(player, args);
                case "predict", "p" -> handlePredict(player, args);
                case "state", "st" -> handleState(player, args);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                messages.format("navy.create.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("navy.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 解析舰船类型
        NavyType type;
        try {
            type = NavyType.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("navy.invalid-type", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析数量
        int ships;
        try {
            ships = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("navy.invalid-number", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 获取名称
        String name = args.length > 3 ? args[3] : type.key() + "-" + System.currentTimeMillis() % 10000;

        // 创建舰队
        NavyUnit navy = navyService.createFleet(nation.id().value(), type, ships, player.getLocation(), name);

        player.sendMessage(Component.text(
            messages.format("navy.created", name, type.key(), ships, navy.id().toString().substring(0, 8)),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("navy.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        List<NavyUnit> navies = navyService.getNationNavies(nation.id().value());

        if (navies.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("navy.no-navies"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("navy.list.header"), NamedTextColor.GOLD));
        for (NavyUnit navy : navies) {
            player.sendMessage(Component.text(
                messages.format("navy.list.entry", navy.name(), navy.type().key(), navy.ships(), navy.state().key()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("navy.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        Optional<NavyUnit> navyOpt = navyService.getFleet(navyId);

        if (navyOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("navy.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        NavyUnit navy = navyOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("navy.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("navy.info.id", navy.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.name", navy.name()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.type", navy.type().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.ships", navy.ships()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.health", String.format("%.1f%%", navy.health())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.morale", String.format("%.1f%%", navy.morale())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.supply", navy.supply()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.state", navy.state().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.info.embarked", navy.embarkedUnits(), navy.transportCapacity()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleMove(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                messages.format("navy.move.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        int x = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        Location destination = new Location(player.getWorld(), x, player.getLocation().getY(), z);

        navyService.moveFleet(navyId, destination);

        player.sendMessage(Component.text(
            messages.format("navy.moved", args[1], x, z),
            NamedTextColor.GREEN
        ));
    }

    private void handleEngage(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.engage.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID attackerId = parseNavyId(player, args[1]);
        UUID defenderId = parseNavyId(player, args[2]);

        NavyBattleResult result = navyService.engage(attackerId, defenderId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("navy.battle.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(result.formatReport(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleSupply(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("navy.supply.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        navyService.resupplyFleet(navyId);

        player.sendMessage(Component.text(
            messages.format("navy.supplied", args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handleDisband(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("navy.disband.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        navyService.disbandFleet(navyId);

        player.sendMessage(Component.text(
            messages.format("navy.disbanded", args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.rename.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        String newName = args[2];

        navyService.renameFleet(navyId, newName);

        player.sendMessage(Component.text(
            messages.format("navy.renamed", args[1], newName),
            NamedTextColor.GREEN
        ));
    }

    private void handleEmbark(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.embark.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        int units = Integer.parseInt(args[2]);

        boolean success = navyService.embarkUnits(navyId, units);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("navy.embarked", units, args[1]),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("navy.embark-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleDisembark(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.disembark.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        int units = Integer.parseInt(args[2]);

        int actual = navyService.disembarkUnits(navyId, units);

        player.sendMessage(Component.text(
            messages.format("navy.disembarked", actual, args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handlePredict(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.predict.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID attackerId = parseNavyId(player, args[1]);
        UUID defenderId = parseNavyId(player, args[2]);

        NavyBattleCalculator.BattlePrediction prediction = navyService.predictBattle(attackerId, defenderId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("navy.predict.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(prediction.formatPrediction(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleState(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("navy.state.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID navyId = parseNavyId(player, args[1]);
        String stateStr = args[2];

        try {
            NavyState state = NavyState.fromString(stateStr);
            navyService.setFleetState(navyId, state);

            player.sendMessage(Component.text(
                messages.format("navy.state-set", args[1], state.key()),
                NamedTextColor.GREEN
            ));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("navy.invalid-state", stateStr),
                NamedTextColor.RED
            ));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("navy.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("navy.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.move"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.engage"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.supply"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.disband"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.rename"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.embark"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("navy.help.disembark"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseNavyId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位），从玩家国家的舰队中查找
        if (idStr.length() <= 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<NavyUnit> nationNavies = navyService.getNationNavies(nationOpt.get().id().value());
                Optional<UUID> match = nationNavies.stream()
                    .map(NavyUnit::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Navy not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid navy ID format: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "move", "engage", "supply", "disband", "rename", "embark", "disembark", "predict", "state");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(NavyType.values())
                .map(NavyType::key)
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("state")) {
            return Arrays.stream(NavyState.values())
                .map(NavyState::key)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
