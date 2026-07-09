package dev.starcore.starcore.module.army.wounded.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.wounded.WoundedService;
import dev.starcore.starcore.module.army.wounded.WoundedService.*;
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
 * 伤兵命令处理器
 * /sc wounded <子命令>
 * /sc heal <子命令>
 */
public final class WoundedCommand implements CommandExecutor, TabCompleter {
    private final WoundedService woundedService;
    private final NationService nationService;
    private final MessageService messages;

    public WoundedCommand(WoundedService woundedService, NationService nationService, MessageService messages) {
        this.woundedService = woundedService;
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
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "heal", "h" -> handleHeal(player, args);
                case "cancel", "c" -> handleCancel(player, args);
                case "hospital", "hp" -> handleHospital(player, args);
                case "status", "s" -> handleStatus(player, args);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        List<WoundedRecord> woundedList = woundedService.getNationWounded(nation.id().value());

        if (woundedList.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.no-wounded"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("wounded.list.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            messages.format("wounded.list.total", woundedList.size(), woundedService.getNationWoundedCount(nation.id().value())),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(""));

        for (WoundedRecord record : woundedList) {
            String shortId = record.id().toString().substring(0, 8);
            String statusIcon = switch (record.status()) {
                case WAITING -> "⏳";
                case HEALING -> "🏥";
                case RECOVERED -> "✅";
                case DEAD -> "💀";
            };

            String statusText = switch (record.status()) {
                case WAITING -> messages.format("wounded.status.waiting");
                case HEALING -> messages.format("wounded.status.healing", record.healingProgressPercent());
                case RECOVERED -> messages.format("wounded.status.recovered");
                case DEAD -> messages.format("wounded.status.dead");
            };

            player.sendMessage(Component.text(
                String.format("  %s %s %s - %s (%s)",
                    statusIcon,
                    shortId,
                    record.severity().key(),
                    record.currentWounded(),
                    statusText
                ),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("wounded.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID woundedId = parseWoundedId(player, args[1]);
        Optional<WoundedRecord> recordOpt = woundedService.getWounded(woundedId);

        if (recordOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        WoundedRecord record = recordOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("wounded.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("wounded.info.id", record.id().toString().substring(0, 8)), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.info.severity", record.severity().key()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.info.soldiers", record.currentWounded()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.info.original", record.originalSoldiers()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.info.status", record.status().key()), NamedTextColor.GRAY));

        if (record.isHealing()) {
            player.sendMessage(Component.text(
                messages.format("wounded.info.progress", record.healingProgressPercent(), record.remainingHealingTime()),
                NamedTextColor.GREEN
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleHeal(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("wounded.heal.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID woundedId = parseWoundedId(player, args[1]);

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查伤兵是否属于同一国家
        Optional<WoundedRecord> recordOpt = woundedService.getWounded(woundedId);
        if (recordOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        WoundedRecord record = recordOpt.get();
        if (!record.nationId().equals(nationOpt.get().id().value())) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-owned"),
                NamedTextColor.RED
            ));
            return;
        }

        // 开始治疗
        boolean success = woundedService.startHealing(woundedId, player.getLocation());

        if (success) {
            player.sendMessage(Component.text(
                messages.format("wounded.heal.started", record.currentWounded()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("wounded.heal.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("wounded.cancel.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID woundedId = parseWoundedId(player, args[1]);

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查伤兵是否属于同一国家
        Optional<WoundedRecord> recordOpt = woundedService.getWounded(woundedId);
        if (recordOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        WoundedRecord record = recordOpt.get();
        if (!record.nationId().equals(nationOpt.get().id().value())) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-owned"),
                NamedTextColor.RED
            ));
            return;
        }

        // 取消治疗
        boolean success = woundedService.cancelHealing(woundedId);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("wounded.cancel.success"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("wounded.cancel.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleHospital(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        int total = woundedService.getNationWoundedCount(nationId);
        int healing = woundedService.getNationHealingCount(nationId);
        int limit = woundedService.getWoundedLimit(nationId);
        int waiting = total - healing;

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("wounded.hospital.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            messages.format("wounded.hospital.total", total, limit),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("wounded.hospital.healing", healing),
            NamedTextColor.GREEN
        ));
        player.sendMessage(Component.text(
            messages.format("wounded.hospital.waiting", waiting),
            NamedTextColor.YELLOW
        ));
        player.sendMessage(Component.text(""));
    }

    private void handleStatus(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("wounded.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        int total = woundedService.getNationWoundedCount(nationId);
        int healing = woundedService.getNationHealingCount(nationId);
        int limit = woundedService.getWoundedLimit(nationId);
        WoundedConfig config = woundedService.getConfig();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("wounded.status.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            messages.format("wounded.status.wounded", total, limit),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("wounded.status.healing", healing),
            NamedTextColor.GREEN
        ));
        player.sendMessage(Component.text(
            messages.format("wounded.status.death-chance", String.format("%.1f%%", config.deathChanceOnArrival() * 100)),
            NamedTextColor.YELLOW
        ));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("wounded.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("wounded.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.help.heal"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.help.cancel"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.help.hospital"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("wounded.help.status"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseWoundedId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位），从玩家国家的伤兵中查找
        if (idStr.length() == 8) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<WoundedRecord> nationWounded = woundedService.getNationWounded(nationOpt.get().id().value());
                Optional<UUID> match = nationWounded.stream()
                    .map(WoundedRecord::id)
                    .filter(id -> id.toString().startsWith(idStr))
                    .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
            throw new IllegalArgumentException("Wounded record not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid wounded ID format: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("list", "info", "heal", "cancel", "hospital", "status");
        }

        // 对于 info, heal, cancel 命令，提供当前国家伤兵的ID补全
        if (args.length == 2 && (args[0].equalsIgnoreCase("info")
            || args[0].equalsIgnoreCase("heal")
            || args[0].equalsIgnoreCase("cancel"))) {

            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                List<WoundedRecord> nationWounded = woundedService.getNationWounded(nationOpt.get().id().value());
                return nationWounded.stream()
                    .map(r -> r.id().toString().substring(0, 8))
                    .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}