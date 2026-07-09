package dev.starcore.starcore.module.dungeon.gui;

import dev.starcore.starcore.module.dungeon.DungeonService;
import dev.starcore.starcore.module.dungeon.DungeonServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 副本GUI事件监听器
 */
public class DungeonGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final DungeonService dungeonService;

    public DungeonGuiListener(JavaPlugin plugin, DungeonService dungeonService) {
        this.plugin = plugin;
        this.dungeonService = dungeonService;
    }

    /**
     * 打开副本选择GUI
     */
    public void openDungeonGui(org.bukkit.entity.Player player) {
        if (dungeonService instanceof DungeonServiceImpl impl) {
            new DungeonGui(player, impl, null);
        }
    }

    /**
     * 处理物品点击
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DungeonGui gui)) {
            return;
        }

        // 取消事件
        event.setCancelled(true);

        // 检查点击的是玩家物品栏还是GUI
        if (event.getClickedInventory() == event.getInventory()) {
            gui.handleClick(event.getSlot());
        }
    }

    /**
     * 处理GUI关闭
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DungeonGui) {
            // 可以添加关闭动画或保存状态
        }
    }
}
