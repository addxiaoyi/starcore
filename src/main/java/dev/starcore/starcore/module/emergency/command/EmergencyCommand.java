package dev.starcore.starcore.module.emergency.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.emergency.EmergencyService;
import dev.starcore.starcore.module.emergency.model.EmergencyState;
import dev.starcore.starcore.module.emergency.model.EmergencySnapshot;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 紧急状态命令处理器
 * /emergency <declare|cancel|status|list|extend> [参数]
 */
public final class EmergencyCommand implements CommandExecutor, TabCompleter {

    private final EmergencyService emergencyService;
    private final NationService nationService;
    private final MessageService messages;

    public EmergencyCommand(EmergencyService emergencyService, NationService nationService, MessageService messages) {
        this.emergencyService = emergencyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "declare", "d" -> handleDeclare(sender, args);
                case "cancel", "c" -> handleCancel(sender, args);
                case "status", "s" -> handleStatus(sender, args);
                case "list", "l" -> handleList(sender);
                case "extend", "e" -> handleExtend(sender, args);
                case "types", "t" -> handleTypes(sender);
                default -> showHelp(sender);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 处理宣布紧急状态
     */
    private void handleDeclare(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return;
        }

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("emergency.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否有权限（创始人或有 emergency 权限的官员）
        if (!nation.founderId().equals(player.getUniqueId())
            && !nation.hasPermission(player.getUniqueId(), "admin")
            && !nation.hasPermission(player.getUniqueId(), "emergency")) {
            player.sendMessage(Component.text(
                messages.format("emergency.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析参数
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("emergency.declare.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 解析紧急状态类型
        EmergencyState.EmergencyType type;
        try {
            type = EmergencyState.EmergencyType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("emergency.invalid-type", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析持续时间
        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("emergency.invalid-duration", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 获取原因（可选）
        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;

        // 宣布紧急状态
        boolean success = emergencyService.declareEmergency(nation.id(), type, reason, durationMinutes);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("emergency.declared", type.displayName(), durationMinutes),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("emergency.declare-failed"),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理取消紧急状态
     */
    private void handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return;
        }

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("emergency.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否有权限
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("emergency.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        // 取消紧急状态
        boolean success = emergencyService.cancelEmergency(nation.id());

        if (success) {
            player.sendMessage(Component.text(
                messages.format("emergency.cancelled"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("emergency.no-active-emergency"),
                NamedTextColor.YELLOW
            ));
        }
    }

    /**
     * 处理查看紧急状态
     */
    private void handleStatus(CommandSender sender, String[] args) {
        NationId nationId;

        if (args.length > 1) {
            // 查看指定国家的紧急状态
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                sender.sendMessage(Component.text(
                    messages.format("nation.not-found", args[1]),
                    NamedTextColor.RED
                ));
                return;
            }
            nationId = nationOpt.get().id();
        } else if (sender instanceof Player player) {
            // 查看自己国家的紧急状态
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("emergency.not-in-nation"),
                    NamedTextColor.RED
                ));
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            sender.sendMessage(Component.text(
                messages.format("emergency.status.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取紧急状态
        Optional<EmergencyState> emergencyOpt = emergencyService.getEmergencyState(nationId);

        if (emergencyOpt.isEmpty()) {
            sender.sendMessage(Component.text(
                messages.format("emergency.no-emergency"),
                NamedTextColor.GRAY
            ));
            return;
        }

        EmergencyState emergency = emergencyOpt.get();
        EmergencySnapshot snapshot = EmergencySnapshot.from(emergency);

        // 显示紧急状态详情
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== " + messages.format("emergency.status.title") + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(messages.format("emergency.status.type", emergency.type().displayName()), NamedTextColor.RED));
        sender.sendMessage(Component.text(messages.format("emergency.status.reason", emergency.reason() != null ? emergency.reason() : "-"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.status.remaining", snapshot.remainingMinutes()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(messages.format("emergency.status.progress", String.format("%.0f%%", snapshot.remainingPercentage() * 100)), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
    }

    /**
     * 处理列出所有紧急状态
     */
    private void handleList(CommandSender sender) {
        var emergencies = emergencyService.getAllEmergencies();

        if (emergencies.isEmpty()) {
            sender.sendMessage(Component.text(
                messages.format("emergency.list.empty"),
                NamedTextColor.GRAY
            ));
            return;
        }

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== " + messages.format("emergency.list.title") + " ===", NamedTextColor.GOLD));

        for (EmergencyState emergency : emergencies) {
            String nationName = nationService.nationById(emergency.nationId())
                .map(Nation::name)
                .orElse("Unknown");

            Component entry = Component.text()
                .append(Component.text("• ", NamedTextColor.GOLD))
                .append(Component.text(nationName, NamedTextColor.WHITE))
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(emergency.type().displayName(), NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(emergency.remainingMinutes() + "m", NamedTextColor.YELLOW))
                .build();

            sender.sendMessage(entry);
        }

        sender.sendMessage(Component.text(""));
    }

    /**
     * 处理延长紧急状态
     */
    private void handleExtend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("emergency.extend.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("emergency.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否有权限
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("emergency.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析延长时间
        int additionalMinutes;
        try {
            additionalMinutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("emergency.invalid-duration", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 延长紧急状态
        boolean success = emergencyService.extendEmergency(nation.id(), additionalMinutes);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("emergency.extended", additionalMinutes),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("emergency.no-active-emergency"),
                NamedTextColor.YELLOW
            ));
        }
    }

    /**
     * 显示紧急状态类型列表
     */
    private void handleTypes(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== " + messages.format("emergency.types.title") + " ===", NamedTextColor.GOLD));

        for (EmergencyState.EmergencyType type : EmergencyState.EmergencyType.values()) {
            Component entry = Component.text()
                .append(Component.text("• ", NamedTextColor.GOLD))
                .append(Component.text(type.name(), NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" - " + type.displayName(), NamedTextColor.GRAY))
                .build();
            sender.sendMessage(entry);
        }

        sender.sendMessage(Component.text(""));
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== " + messages.format("emergency.help.title") + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(messages.format("emergency.help.declare"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.help.cancel"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.help.status"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.help.list"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.help.extend"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("emergency.help.types"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("declare", "cancel", "status", "list", "extend", "types");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("declare")) {
                return Arrays.stream(EmergencyState.EmergencyType.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("extend")) {
                return List.of("30", "60", "120", "240", "480", "1440");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("declare")) {
            return List.of("30", "60", "120", "240", "480", "1440");
        }

        return List.of();
    }
}