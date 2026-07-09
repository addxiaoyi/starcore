package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.message.MessageService;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 国家GUI命令
 * /sc n gui - 打开国家管理GUI
 * /sc n menu - 同上
 */
public final class NationGuiCommand implements CommandExecutor, TabCompleter {
    private final NationService nationService;
    private final NationManagementMenuListener menuListener;
    private final MessageService messages;

    public NationGuiCommand(
        NationService nationService,
        NationManagementMenuListener menuListener,
        MessageService messages
    ) {
        this.nationService = nationService;
        this.menuListener = menuListener;
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

        UUID playerId = player.getUniqueId();

        // 检查玩家是否属于某个国家
        Optional<Nation> nationOpt = nationService.getNationByMember(playerId);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("nation.gui.not-in-nation"),
                NamedTextColor.RED
            ));
            return true;
        }

        Nation nation = nationOpt.get();

        // 打开GUI
        menuListener.openMenu(player, nation);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return List.of();
    }
}
