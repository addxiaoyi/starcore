package dev.starcore.starcore.module.weather.gui;

import dev.starcore.starcore.module.weather.WeatherModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

/**
 * 天气 GUI 事件监听器
 */
public class WeatherGuiListener implements Listener {

    private final WeatherModule weatherModule;
    private final WeatherMenu weatherMenu;

    public WeatherGuiListener(WeatherModule weatherModule, WeatherMenu weatherMenu) {
        this.weatherModule = weatherModule;
        this.weatherMenu = weatherMenu;
    }

    /**
     * 处理库存点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // 检查是否是我们打开的 GUI
        if (!isWeatherGui(title)) {
            return;
        }

        event.setCancelled(true);

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || !currentItem.hasItemMeta()) {
            return;
        }

        // 处理点击
        weatherMenu.handleClick(player, currentItem);
    }

    /**
     * 处理玩家右键交互（打开天气菜单）
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理主手
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 检查是否是天气工具（可以用特定物品打开菜单）
        if (isWeatherTool(item)) {
            event.setCancelled(true);
            weatherMenu.openMainMenu(player);
        }
    }

    /**
     * 检查标题是否是天气 GUI
     */
    private boolean isWeatherGui(String title) {
        return title.contains("天气控制") ||
               title.contains("天气预报") ||
               title.contains("选择天气") ||
               title.contains("选择世界") ||
               title.contains("资源影响");
    }

    /**
     * 检查是否是天气工具
     */
    private boolean isWeatherTool(ItemStack item) {
        if (item == null || item.getType().isEmpty()) {
            return false;
        }

        // 可以使用特定的物品作为天气工具
        // 例如：使用时钟或指南针右键打开天气菜单
        return item.getType().name().contains("CLOCK") ||
               item.getType().name().contains("COMPASS");
    }
}
