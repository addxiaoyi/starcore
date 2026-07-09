package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;

/**
 * 基于 TriumphGUI 的菜单提供者（箱子界面）
 */
public class TriumphChestMenuProvider implements NationMenuProvider {
    private final NationModule nationModule;
    private final MessageService messages;

    public TriumphChestMenuProvider(NationModule nationModule, MessageService messages) {
        this.nationModule = nationModule;
        this.messages = messages;
    }

    @Override
    public void openMainMenu(Player player) {
        Optional<Nation> nationOpt = nationModule.getNationByMember(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你还没有加入任何国家", NamedTextColor.YELLOW));
            return;
        }

        Nation nation = nationOpt.get();

        Gui gui = Gui.gui()
            .title(Component.text("⚔ " + nation.name() + " 国家管理", NamedTextColor.GOLD))
            .rows(3)
            .disableAllInteractions()
            .create();

        // 添加功能按钮
        gui.setItem(11, createButton(Material.BEACON, "国家信息", p -> p.performCommand("sc nation info")));
        gui.setItem(13, createButton(Material.PLAYER_HEAD, "成员管理", p -> p.performCommand("sc nation members")));
        gui.setItem(15, createButton(Material.FILLED_MAP, "领土管理", p -> p.performCommand("sc nation claims")));
        gui.setItem(22, createButton(Material.BARRIER, "关闭", Player::closeInventory));

        gui.open(player);
    }

    @Override
    public void openSubMenu(Player player, Nation nation, String submenuId) {
        player.sendMessage(Component.text("⚠ 子菜单功能暂不可用", NamedTextColor.RED));
    }

    private GuiItem createButton(Material material, String name, java.util.function.Consumer<Player> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GREEN));
            item.setItemMeta(meta);
        }
        return new GuiItem(item, event -> {
            if (event.getWhoClicked() instanceof Player p) {
                action.accept(p);
            }
        });
    }

    @Override
    public String getProviderType() {
        return "TriumphGUI (Chest)";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("dev.triumphteam.gui.guis.Gui");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
