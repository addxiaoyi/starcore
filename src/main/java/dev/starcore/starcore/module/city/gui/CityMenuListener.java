package dev.starcore.starcore.module.city.gui;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.city.CityService;
import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 城市 GUI 事件监听器
 */
public final class CityMenuListener implements Listener {

    private final CityService cityService;
    private final NationService nationService;
    private final EconomyService economyService;
    private final Map<UUID, CityMenuGui> openMenus = new ConcurrentHashMap<>();

    public CityMenuListener(CityService cityService, NationService nationService, EconomyService economyService, Plugin plugin) {
        this.cityService = cityService;
        this.nationService = nationService;
        this.economyService = economyService;
        // 注意：事件监听器由 CityModule 在 enable() 中显式注册
    }

    /**
     * 打开城市菜单
     */
    public void openMenu(Player player) {
        CityMenuGui gui = new CityMenuGui(player, cityService, nationService, economyService);
        openMenus.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    /**
     * 关闭城市菜单
     */
    public void closeMenu(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        CityMenuGui gui = openMenus.get(player.getUniqueId());
        if (gui == null) {
            return;
        }

        // 检查是否点击的是我们的菜单
        if (!event.getInventory().equals(gui.getInventory())) {
            return;
        }

        // 取消点击事件
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= gui.getInventory().getSize()) {
            return;
        }

        // 处理点击
        CityMenuGui.CityAction action = CityMenuGui.getActionFromSlot(slot);
        handleAction(player, gui, action);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 处理菜单动作
     */
    private void handleAction(Player player, CityMenuGui gui, CityMenuGui.CityAction action) {
        switch (action) {
            case INFO, MEMBERS -> {
                player.sendMessage(Component.text("使用 /city info 查看详细信息", NamedTextColor.GRAY));
            }
            case INVITE -> {
                player.sendMessage(Component.text("用法: /city invite <玩家名>", NamedTextColor.YELLOW));
            }
            case KICK -> {
                player.sendMessage(Component.text("用法: /city kick <玩家名>", NamedTextColor.YELLOW));
            }
            case PROMOTE -> {
                player.sendMessage(Component.text("用法: /city promote <玩家名>", NamedTextColor.YELLOW));
            }
            case JOIN_CITY -> {
                player.sendMessage(Component.text("用法: /city join <城市名>", NamedTextColor.YELLOW));
            }
            case CREATE_INFO -> {
                player.sendMessage(Component.text("用法: /city create <城市名>", NamedTextColor.YELLOW));
            }
            case DEPOSIT -> {
                Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt.get();
                BigDecimal balance = economyService.getBalance(player.getUniqueId());
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    // 存款 10% 余额
                    BigDecimal depositAmount = balance.multiply(BigDecimal.valueOf(0.1)).setScale(2, BigDecimal.ROUND_DOWN);
                    if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
                        if (economyService.withdraw(player.getUniqueId(), depositAmount)) {
                            cityService.deposit(city.id(), depositAmount.doubleValue());
                            player.sendMessage(Component.text(String.format("已存入 %.2f 金币到城市国库", depositAmount.doubleValue()), NamedTextColor.GREEN));
                        }
                    }
                } else {
                    player.sendMessage(Component.text("你的余额不足", NamedTextColor.RED));
                }
            }
            case WITHDRAW -> {
                Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt.get();
                if (!city.isMayor(player.getUniqueId())) {
                    player.sendMessage(Component.text("只有市长可以取款", NamedTextColor.RED));
                    return;
                }
                if (city.treasury() > 0) {
                    // 取款 10% 国库
                    double withdrawAmount = city.treasury() * 0.1;
                    if (cityService.withdraw(city.id(), withdrawAmount)) {
                        economyService.deposit(player.getUniqueId(), BigDecimal.valueOf(withdrawAmount));
                        player.sendMessage(Component.text(String.format("已取出 %.2f 金币", withdrawAmount), NamedTextColor.GREEN));
                    }
                } else {
                    player.sendMessage(Component.text("城市国库余额不足", NamedTextColor.RED));
                }
            }
            case SETTINGS -> {
                Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt.get();
                if (!city.isMayor(player.getUniqueId())) {
                    player.sendMessage(Component.text("只有市长可以修改设置", NamedTextColor.RED));
                    return;
                }

                // 循环切换设置
                StringBuilder msg = new StringBuilder();
                msg.append(Component.text("城市设置:\n", NamedTextColor.YELLOW));

                // PVP 设置
                boolean newPvp = !city.isPvpEnabled();
                cityService.updateSettings(city.id(), player.getUniqueId(), newPvp, null, null);
                msg.append(Component.text("PVP: " + (newPvp ? "开启" : "关闭"), newPvp ? NamedTextColor.GREEN : NamedTextColor.RED));
                msg.append("\n");

                player.sendMessage(Component.text("设置已更新!\n" + msg.toString(), NamedTextColor.GREEN));
                // 刷新菜单
                player.closeInventory();
                openMenu(player);
            }
            case SETTINGS_PUBLIC_SPAWN -> {
                Optional<City> cityOpt2 = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt2.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt2.get();
                if (!city.isMayor(player.getUniqueId())) {
                    player.sendMessage(Component.text("只有市长可以修改设置", NamedTextColor.RED));
                    return;
                }
                boolean newPublicSpawn = !city.isPublicSpawn();
                cityService.updateSettings(city.id(), player.getUniqueId(), null, newPublicSpawn, null);
                player.sendMessage(Component.text("公开传送点: " + (newPublicSpawn ? "开启" : "关闭"), NamedTextColor.GREEN));
                player.closeInventory();
                openMenu(player);
            }
            case SETTINGS_RECRUITMENT -> {
                Optional<City> cityOpt3 = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt3.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt3.get();
                if (!city.isMayor(player.getUniqueId())) {
                    player.sendMessage(Component.text("只有市长可以修改设置", NamedTextColor.RED));
                    return;
                }
                boolean newRecruitment = !city.isOpenRecruitment();
                cityService.updateSettings(city.id(), player.getUniqueId(), null, null, newRecruitment);
                player.sendMessage(Component.text("公开招募: " + (newRecruitment ? "开启" : "关闭"), NamedTextColor.GREEN));
                player.closeInventory();
                openMenu(player);
            }
            case ANNOUNCEMENT -> {
                player.sendMessage(Component.text("用法: /city announcement <内容>", NamedTextColor.YELLOW));
            }
            case TELEPORT -> {
                Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                City city = cityOpt.get();
                if (city.spawnChunk() != null) {
                    player.sendMessage(Component.text("正在传送到城市...", NamedTextColor.YELLOW));
                    cityService.teleportToSpawn(player, city.id());
                } else {
                    player.sendMessage(Component.text("城市传送点未设置", NamedTextColor.RED));
                    player.sendMessage(Component.text("使用 /city setspawn 设置", NamedTextColor.GRAY));
                }
            }
            case TOP -> {
                player.sendMessage(Component.text("使用 /city top 查看排行榜", NamedTextColor.GRAY));
            }
            case LEAVE -> {
                Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
                if (cityOpt.isEmpty()) {
                    player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("用法: /city leave 离开城市", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("警告: 离开后将失去城市所有权限", NamedTextColor.RED));
            }
            case CLOSE -> {
                player.closeInventory();
            }
            case NONE -> {
                // 不处理
            }
        }
    }
}
