package dev.starcore.starcore.module.nation.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.gui.NationMenuProvider;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /menu 命令 - 打开国家管理菜单
 */
public class MenuCommand implements CommandExecutor {
    private static final Logger LOGGER = Logger.getLogger(MenuCommand.class.getName());
    private final NationModule nationModule;
    private final MessageService messages;

    public MenuCommand(NationModule nationModule, MessageService messages) {
        this.nationModule = nationModule;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("正在打开菜单...", NamedTextColor.GRAY));

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationModule.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入国家！使用 /sc nation create <名称> 创建国家", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("找到国家: " + nationOpt.get().name(), NamedTextColor.GREEN));

        // 打开菜单
        try {
            nationModule.getMenuProvider().openMainMenu(player);
            player.sendMessage(Component.text("菜单已打开", NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("打开菜单时出错: " + e.getMessage(), NamedTextColor.RED));
            LOGGER.log(Level.WARNING, "Failed to open nation menu for player: " + player.getName(), e);
        }

        return true;
    }
}
