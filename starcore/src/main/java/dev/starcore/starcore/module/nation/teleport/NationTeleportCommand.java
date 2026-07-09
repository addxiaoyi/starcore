package dev.starcore.starcore.module.nation.teleport;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家传送命令处理器
 * /sc n tp <城镇名> - 传送到城镇
 * /sc n spawn - 传送到首都
 */
public final class NationTeleportCommand implements CommandExecutor, TabCompleter {
    private final NationTeleportService teleportService;
    private final NationService nationService;
    private final MessageService messages;

    public NationTeleportCommand(
        NationTeleportService teleportService,
        NationService nationService,
        MessageService messages
    ) {
        this.teleportService = teleportService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                messages.format("command.player-only"),
                NamedTextColor.RED
            ));
            return true;
        }

        if (args.length == 0) {
            // 默认传送到首都
            teleportService.teleportToCapital(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "spawn", "capital", "首都" -> {
                teleportService.teleportToCapital(player);
            }
            case "town", "城镇" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                        messages.format("teleport.specify-town"),
                        NamedTextColor.YELLOW
                    ));
                    return true;
                }
                String townName = args[1];
                teleportService.teleportToTown(player, townName);
            }
            default -> {
                // 直接将参数作为城镇名
                teleportService.teleportToTown(player, args[0]);
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("spawn");
            completions.add("capital");
            completions.add("town");

            // 添加玩家所在国家的城镇列表
            nationService.getNationByMember(player.getUniqueId())
                .ifPresent(nation -> completions.addAll(nation.getTownNames()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("town")) {
            // 城镇名称补全
            nationService.getNationByMember(player.getUniqueId())
                .ifPresent(nation -> completions.addAll(nation.getTownNames()));
        }

        // 过滤匹配项
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .toList();
    }
}
