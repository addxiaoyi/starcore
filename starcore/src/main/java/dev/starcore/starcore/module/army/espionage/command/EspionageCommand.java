package dev.starcore.starcore.module.army.espionage.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.espionage.EspionageService;
import dev.starcore.starcore.module.army.espionage.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
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
 * 间谍命令处理器
 * /sc spy <子命令>
 */
public final class EspionageCommand implements CommandExecutor, TabCompleter {
    private final EspionageService espionageService;
    private final NationService nationService;
    private final MessageService messages;

    public EspionageCommand(EspionageService espionageService, NationService nationService, MessageService messages) {
        this.espionageService = espionageService;
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
                case "train", "t" -> handleTrain(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "dismiss" -> handleDismiss(player, args);
                case "operation", "op" -> handleOperation(player, args);
                case "active", "a" -> handleActive(player, args);
                case "history", "h" -> handleHistory(player, args);
                case "ci", "counter" -> handleCounterIntelligence(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            String key = e.getMessage();
            if (key != null && key.startsWith("espionage.")) {
                player.sendMessage(Component.text(messages.format(key), NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
        }

        return true;
    }

    private void handleTrain(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("espionage.train.usage"), NamedTextColor.YELLOW));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        SpyType type;
        try {
            type = SpyType.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(messages.format("espionage.invalid-type", args[1]), NamedTextColor.RED));
            return;
        }

        Spy spy = espionageService.trainSpy(nation.id().value(), nation.name(), player.getUniqueId(), type);

        player.sendMessage(Component.text(
                messages.format("espionage.trained", type.key(), spy.id().toString().substring(0, 8)),
                NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        List<Spy> spies = espionageService.getNationSpies(nation.id().value());

        if (spies.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.no-spies"), NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.list.header"), NamedTextColor.GOLD));
        for (Spy spy : spies) {
            String shortId = spy.id().toString().substring(0, 8);
            player.sendMessage(Component.text(
                    messages.format("espionage.list.entry", shortId, spy.type().key(),
                            spy.experience(), spy.missionsCompleted(), spy.missionsFailed(),
                            String.format("%.0f%%", spy.morale())),
                    NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("espionage.info.usage"), NamedTextColor.YELLOW));
            return;
        }

        UUID spyId = parseSpyId(player, args[1]);
        Optional<Spy> spyOpt = espionageService.getSpy(spyId);

        if (spyOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-found", args[1]), NamedTextColor.RED));
            return;
        }

        Spy spy = spyOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("espionage.info.id", spy.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.info.type", spy.type().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.info.experience", spy.experience()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.info.missions", spy.missionsCompleted(), spy.missionsFailed()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.info.morale", String.format("%.0f%%", spy.morale())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.info.stealth", String.format("%.0f%%", spy.effectiveStealth() * 100)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleDismiss(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("espionage.dismiss.usage"), NamedTextColor.YELLOW));
            return;
        }

        UUID spyId = parseSpyId(player, args[1]);
        Optional<Spy> spyOpt = espionageService.getSpy(spyId);

        if (spyOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-found", args[1]), NamedTextColor.RED));
            return;
        }

        espionageService.dismissSpy(spyId);
        player.sendMessage(Component.text(messages.format("espionage.dismissed", args[1]), NamedTextColor.GREEN));
    }

    private void handleOperation(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(messages.format("espionage.operation.usage"), NamedTextColor.YELLOW));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // 获取间谍
        UUID spyId = parseSpyId(player, args[1]);
        Optional<Spy> spyOpt = espionageService.getSpy(spyId);
        if (spyOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-found", args[1]), NamedTextColor.RED));
            return;
        }

        // 获取目标国家
        String targetName = args[2];
        Optional<Nation> targetOpt = nationService.nationByName(targetName);
        if (targetOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.target-not-found", targetName), NamedTextColor.RED));
            return;
        }

        Nation target = targetOpt.get();

        // 检查不能对自己行动
        if (nation.id().value().equals(target.id().value())) {
            player.sendMessage(Component.text(messages.format("espionage.cannot-target-self"), NamedTextColor.RED));
            return;
        }

        // 解析行动类型
        OperationType opType;
        try {
            opType = OperationType.fromString(args[3]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(messages.format("espionage.invalid-operation", args[3]), NamedTextColor.RED));
            return;
        }

        EspionageOperation operation = espionageService.startOperation(
                spyId, target.id().value(), target.name(), opType
        );

        long durationMinutes = operation.durationTicks() / 1200;
        player.sendMessage(Component.text(
                messages.format("espionage.operation.started", opType.key(), target.name(), durationMinutes),
                NamedTextColor.GREEN
        ));
    }

    private void handleActive(Player player, String[] args) {
        List<EspionageOperation> operations = espionageService.getActiveOperations();

        if (operations.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.no-active-operations"), NamedTextColor.YELLOW));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        UUID nationId = nationOpt.map(n -> n.id().value()).orElse(null);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.active.header"), NamedTextColor.GOLD));

        for (EspionageOperation op : operations) {
            if (nationId != null && !op.sourceNationId().equals(nationId)) {
                continue; // 只显示自己的行动
            }

            player.sendMessage(Component.text(
                    messages.format("espionage.active.entry", op.type().key(), op.targetNationName(),
                            formatRemainingTime(op)),
                    NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleHistory(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-in-nation"), NamedTextColor.RED));
            return;
        }

        List<EspionageOperation> history = espionageService.getNationOperationHistory(nationOpt.get().id().value());

        if (history.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.no-history"), NamedTextColor.YELLOW));
            return;
        }

        // 只显示最近10条
        List<EspionageOperation> recent = history.size() > 10 ? history.subList(history.size() - 10, history.size()) : history;

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.history.header"), NamedTextColor.GOLD));
        for (EspionageOperation op : recent) {
            String statusIcon = op.success() ? "✔" : (op.status() == OperationStatus.EXPOSED ? "✘" : "✘");
            player.sendMessage(Component.text(
                    messages.format("espionage.history.entry", statusIcon, op.type().key(), op.targetNationName(),
                            op.status().name()),
                    op.success() ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleCounterIntelligence(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("espionage.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        int level = espionageService.getCounterIntelligenceLevel(nation.id().value());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.ci.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("espionage.ci.level", level), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.ci.description"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("espionage.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("espionage.help.train"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.dismiss"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.operation"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.active"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("espionage.help.history"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseSpyId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位）
        if (idStr.length() == 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<Spy> nationSpies = espionageService.getNationSpies(nationOpt.get().id().value());
                Optional<UUID> match = nationSpies.stream()
                        .map(Spy::id)
                        .filter(id -> id.toString().startsWith(idStr))
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Spy not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid spy ID format: " + idStr);
    }

    private String formatRemainingTime(EspionageOperation op) {
        long remainingTicks = op.getRemainingTicks(java.time.Instant.now());
        long minutes = remainingTicks / 1200;
        if (minutes >= 60) {
            return String.format("%dh %dm", minutes / 60, minutes % 60);
        }
        return minutes + "m";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("train", "list", "info", "dismiss", "operation", "active", "history", "ci");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("train")) {
                return Arrays.stream(SpyType.values())
                        .map(SpyType::key)
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("dismiss")) {
                Optional<Nation> nationOpt = nationService.nationOf(((Player) sender).getUniqueId());
                if (nationOpt.isPresent()) {
                    return espionageService.getNationSpies(nationOpt.get().id().value()).stream()
                            .map(s -> s.id().toString().substring(0, 8))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("operation")) {
                // 返回所有国家名称
                return nationService.nations().stream()
                        .map(Nation::name)
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("operation")) {
                return Arrays.stream(OperationType.values())
                        .map(OperationType::key)
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
