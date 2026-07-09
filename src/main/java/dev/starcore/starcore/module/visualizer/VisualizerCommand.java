package dev.starcore.starcore.module.visualizer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class VisualizerCommand implements CommandExecutor, TabCompleter {
    private final VisualizerService service;

    VisualizerCommand(VisualizerService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(VisualizerText.color("&a" + service.summary()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("starcore.visualizer.admin")) {
                    sender.sendMessage(VisualizerText.color("&cNo permission."));
                    return true;
                }
                service.reload();
                sender.sendMessage(VisualizerText.color("&aInteraction visualizer reloaded."));
                return true;
            }
            case "cleanup" -> {
                if (!sender.hasPermission("starcore.visualizer.admin")) {
                    sender.sendMessage(VisualizerText.color("&cNo permission."));
                    return true;
                }
                service.cleanup();
                sender.sendMessage(VisualizerText.color("&aInteraction visualizer displays cleaned."));
                return true;
            }
            case "toggle" -> {
                handleToggle(sender, args);
                return true;
            }
            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.visualizer")) {
            sender.sendMessage(VisualizerText.color("&cNo permission."));
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "interactionvisualizer");
            return;
        }
        Player target = targetPlayer(sender, args.length >= 5 ? args[4] : args.length >= 4 && isBoolean(args[2]) ? args[3] : null);
        if (target == null) {
            return;
        }
        if (!target.equals(sender) && !sender.hasPermission("starcore.visualizer.admin")) {
            sender.sendMessage(VisualizerText.color("&cNo permission to change another player's preferences."));
            return;
        }

        if ("entry".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                sendUsage(sender, "interactionvisualizer");
                return;
            }
            boolean enabled = parseBoolean(args[3]);
            toggleEntry(sender, target, args[2], enabled);
            return;
        }

        VisualizerDisplayMode mode = VisualizerDisplayMode.from(args[1]).orElse(null);
        if (mode == null) {
            VisualizerEntry.fromKey(args[1]).ifPresentOrElse(
                entry -> toggleEntry(sender, target, entry.key(), parseBoolean(args[2])),
                () -> sender.sendMessage(VisualizerText.color("&cUnknown mode or entry: " + args[1]))
            );
            return;
        }

        if (args.length == 3 || isBoolean(args[2])) {
            boolean enabled = parseBoolean(args[2]);
            service.setMode(target, mode, enabled);
            sender.sendMessage(VisualizerText.color("&aToggled " + mode.displayName() + " " + (enabled ? "on" : "off") + " for " + target.getName() + "."));
            return;
        }
        boolean enabled = args.length >= 4 && parseBoolean(args[3]);
        toggleEntry(sender, target, args[2], enabled);
    }

    private void toggleEntry(CommandSender sender, Player target, String rawEntry, boolean enabled) {
        if ("all".equalsIgnoreCase(rawEntry)) {
            for (VisualizerEntry entry : VisualizerEntry.values()) {
                service.setEntry(target, entry, enabled);
            }
            sender.sendMessage(VisualizerText.color("&aToggled all visualizer entries " + (enabled ? "on" : "off") + " for " + target.getName() + "."));
            return;
        }
        VisualizerEntry entry = VisualizerEntry.fromKey(rawEntry).orElse(null);
        if (entry == null) {
            sender.sendMessage(VisualizerText.color("&cUnknown entry: " + rawEntry));
            return;
        }
        service.setEntry(target, entry, enabled);
        sender.sendMessage(VisualizerText.color("&aToggled " + entry.displayName() + " " + (enabled ? "on" : "off") + " for " + target.getName() + "."));
    }

    private Player targetPlayer(CommandSender sender, String explicitTarget) {
        if (explicitTarget != null && !explicitTarget.isBlank()) {
            Player player = Bukkit.getPlayerExact(explicitTarget);
            if (player == null) {
                sender.sendMessage(VisualizerText.color("&cPlayer is not online."));
            }
            return player;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(VisualizerText.color("&cConsole must provide a player name."));
        return null;
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value);
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(VisualizerText.color("&e/" + label + " status"));
        sender.sendMessage(VisualizerText.color("&e/" + label + " toggle <hologram|itemstand|itemdrop> <on|off> [player]"));
        sender.sendMessage(VisualizerText.color("&e/" + label + " toggle entry <entry|all> <on|off> [player]"));
        sender.sendMessage(VisualizerText.color("&e/" + label + " reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "toggle", "reload", "cleanup"), args[0]);
        }
        if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>(List.of("entry", "hologram", "itemstand", "itemdrop"));
            Arrays.stream(VisualizerEntry.values()).map(VisualizerEntry::key).forEach(values::add);
            return filter(values, args[1]);
        }
        if (args.length == 3 && "toggle".equalsIgnoreCase(args[0])) {
            if ("entry".equalsIgnoreCase(args[1])) {
                List<String> values = new ArrayList<>(List.of("all"));
                Arrays.stream(VisualizerEntry.values()).map(VisualizerEntry::key).forEach(values::add);
                return filter(values, args[2]);
            }
            return filter(List.of("on", "off", "true", "false"), args[2]);
        }
        if (args.length == 4 && "toggle".equalsIgnoreCase(args[0])) {
            return filter(List.of("on", "off", "true", "false"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}

