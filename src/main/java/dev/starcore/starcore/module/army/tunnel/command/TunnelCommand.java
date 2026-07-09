package dev.starcore.starcore.module.army.tunnel.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.tunnel.TunnelService;
import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import dev.starcore.starcore.module.army.tunnel.model.TunnelEntrance;
import dev.starcore.starcore.module.army.tunnel.model.TunnelState;
import dev.starcore.starcore.module.army.tunnel.model.TunnelType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Tunnel Warfare Command Handler
 * /tunnel <subcommand>
 */
public final class TunnelCommand implements CommandExecutor, TabCompleter {

    private final TunnelService tunnelService;
    private final NationService nationService;
    private final MessageService messages;

    public TunnelCommand(TunnelService tunnelService, NationService nationService, MessageService messages) {
        this.tunnelService = tunnelService;
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
                case "create", "new" -> handleCreate(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "enter" -> handleEnter(player, args);
                case "exit" -> handleExit(player);
                case "addentrance", "add" -> handleAddEntrance(player, args);
                case "removeentrance", "remove" -> handleRemoveEntrance(player, args);
                case "discover" -> handleDiscover(player, args);
                case "ambush" -> handleAmbush(player, args);
                case "trap" -> handleTrap(player, args);
                case "collapse" -> handleCollapse(player, args);
                case "destroy" -> handleDestroy(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    // ==================== Command Handlers ====================

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("tunnel.create.usage", "/tunnel create <name> <type>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // Check nation membership
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        String name = args[1];
        TunnelType type;

        try {
            type = TunnelType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                "Invalid tunnel type. Options: SUPPLY, ESCAPE, MILITARY, SECRET",
                NamedTextColor.RED
            ));
            return;
        }

        Tunnel tunnel = tunnelService.createTunnel(
            nationOpt.get().id(),
            name,
            type,
            player.getLocation()
        );

        player.sendMessage(Component.text(
            messages.format("tunnel.created", name, type.displayName()),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        List<Tunnel> tunnels = tunnelService.getNationTunnels(nationOpt.get().id());

        if (tunnels.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.no-tunnels"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("tunnel.list.header") + " ===", NamedTextColor.GOLD));
        for (Tunnel tunnel : tunnels) {
            player.sendMessage(Component.text(
                String.format("  %s [%s] %s - %s",
                    tunnel.id().toString().substring(0, 8),
                    tunnel.type().colorCode() + tunnel.type().displayName(),
                    tunnel.name(),
                    tunnel.state().formatted()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            // Show info about nearest tunnel
            Optional<Tunnel> nearest = getNearestTunnel(player);
            if (nearest.isEmpty()) {
                player.sendMessage(Component.text(
                    "No tunnel nearby. Specify tunnel ID: /tunnel info <id>",
                    NamedTextColor.YELLOW
                ));
                return;
            }
            showTunnelInfo(player, nearest.get());
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        Optional<Tunnel> tunnelOpt = tunnelService.getTunnel(tunnelId);

        if (tunnelOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        showTunnelInfo(player, tunnelOpt.get());
    }

    private void handleEnter(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("tunnel.enter.usage", "/tunnel enter <entrance_id>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID entranceId = UUID.fromString(args[1]);

        if (tunnelService.enterTunnel(player.getUniqueId(), entranceId)) {
            player.sendMessage(Component.text(
                messages.format("tunnel.entered"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("tunnel.enter.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleExit(Player player) {
        Optional<UUID> currentTunnel = tunnelService.getPlayerTunnel(player.getUniqueId());
        if (currentTunnel.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-inside"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        if (tunnelService.exitTunnel(player.getUniqueId()) != null) {
            player.sendMessage(Component.text(
                messages.format("tunnel.exited"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("tunnel.exit.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleAddEntrance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("tunnel.addentrance.usage", "/tunnel addentrance <tunnel_id> [hidden]"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        boolean hidden = args.length > 2 && args[2].equalsIgnoreCase("hidden");

        TunnelEntrance entrance = tunnelService.addEntrance(tunnelId, player.getLocation(), hidden);

        player.sendMessage(Component.text(
            messages.format("tunnel.entrance.added", entrance.id().toString().substring(0, 8)),
            NamedTextColor.GREEN
        ));
    }

    private void handleRemoveEntrance(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("tunnel.removeentrance.usage", "/tunnel removeentrance <tunnel_id> <entrance_id>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        UUID entranceId = UUID.fromString(args[2]);

        if (tunnelService.removeEntrance(tunnelId, entranceId)) {
            player.sendMessage(Component.text(
                messages.format("tunnel.entrance.removed"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("tunnel.entrance.remove-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleDiscover(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                "Usage: /tunnel discover <tunnel_id>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        tunnelService.discoverTunnel(tunnelId, nationOpt.get().id());
        player.sendMessage(Component.text(
            messages.format("tunnel.discovered"),
            NamedTextColor.GREEN
        ));
    }

    private void handleAmbush(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("tunnel.ambush.usage", "/tunnel ambush <tunnel_id> [range]"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        double range = args.length > 2 ? Double.parseDouble(args[2]) : 30.0;
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        int affected = tunnelService.ambush(tunnelId, nationOpt.get().id().value(), player.getLocation(), range);
        player.sendMessage(Component.text(
            messages.format("tunnel.ambush.result", affected),
            NamedTextColor.GREEN
        ));
    }

    private void handleTrap(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("tunnel.trap.usage", "/tunnel trap <tunnel_id> <entrance_id> <trap_type>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        UUID entranceId = UUID.fromString(args[2]);
        TunnelService.TrapType trapType;

        try {
            trapType = TunnelService.TrapType.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                "Invalid trap type. Options: PRESSURE_PLATE, FALL_TRAP, CEILING_COLLAPSE, POISON_GAS, DEAFENING_ROCKS",
                NamedTextColor.RED
            ));
            return;
        }

        if (tunnelService.setTrap(tunnelId, entranceId, trapType)) {
            player.sendMessage(Component.text(
                messages.format("tunnel.trap.set", trapType.displayName()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("tunnel.trap.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleCollapse(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("tunnel.collapse.usage", "/tunnel collapse <tunnel_id> [radius]"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);
        double radius = args.length > 2 ? Double.parseDouble(args[2]) : 5.0;

        if (tunnelService.collapseSection(tunnelId, player.getLocation(), radius)) {
            player.sendMessage(Component.text(
                messages.format("tunnel.collapsed"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("tunnel.collapse.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleDestroy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("tunnel.destroy.usage", "/tunnel destroy <tunnel_id>"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID tunnelId = parseTunnelId(player, args[1]);

        // Verify ownership
        Optional<Tunnel> tunnelOpt = tunnelService.getTunnel(tunnelId);
        if (tunnelOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() || !tunnelOpt.get().nationId().value().equals(nationOpt.get().id().value())) {
            player.sendMessage(Component.text(
                messages.format("tunnel.not-owned"),
                NamedTextColor.RED
            ));
            return;
        }

        tunnelService.destroyTunnel(tunnelId);
        player.sendMessage(Component.text(
            messages.format("tunnel.destroyed"),
            NamedTextColor.GREEN
        ));
    }

    // ==================== Utility Methods ====================

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("tunnel.help.header") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("tunnel.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.enter"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.exit"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.addentrance"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.removeentrance"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.discover"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.ambush"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.trap"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.collapse"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("tunnel.help.destroy"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showTunnelInfo(Player player, Tunnel tunnel) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + tunnel.name() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("ID: " + tunnel.id().toString().substring(0, 8), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Type: " + tunnel.type().colorCode() + tunnel.type().displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("State: " + tunnel.state().formatted(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Entrances: " + tunnel.entranceCount(), NamedTextColor.GRAY));

        if (tunnel.isUnderConstruction()) {
            player.sendMessage(Component.text("Progress: " + tunnel.progress() + "%", NamedTextColor.YELLOW));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Entrance List:", NamedTextColor.AQUA));
        for (TunnelEntrance entrance : tunnel.entrances()) {
            String hiddenStr = entrance.isHidden() ? " (Hidden)" : "";
            player.sendMessage(Component.text(
                String.format("  [%s] %s at %s%s",
                    entrance.id().toString().substring(0, 8),
                    entrance.worldName(),
                    entrance.coordinates(),
                    hiddenStr),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private UUID parseTunnelId(Player player, String idStr) {
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        if (idStr.length() == 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<Tunnel> nationTunnels = tunnelService.getNationTunnels(nationOpt.get().id());
                Optional<UUID> match = nationTunnels.stream()
                    .map(Tunnel::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Tunnel not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid tunnel ID format. Use full UUID or 8-char prefix.");
    }

    private Optional<Tunnel> getNearestTunnel(Player player) {
        List<Tunnel> tunnels = tunnelService.getTunnelsAt(player.getLocation());
        if (tunnels.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tunnels.get(0));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "enter", "exit", "addentrance", "removeentrance", "discover", "ambush", "trap", "collapse", "destroy");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("<name>");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("trap")) {
            return List.of("<tunnel_id>");
        }

        if (args.length == 2) {
            return Arrays.stream(TunnelType.values())
                .map(TunnelType::name)
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("trap")) {
            return Arrays.stream(TunnelService.TrapType.values())
                .map(TunnelService.TrapType::name)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
