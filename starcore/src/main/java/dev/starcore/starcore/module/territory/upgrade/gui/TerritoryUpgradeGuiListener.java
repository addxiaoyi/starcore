package dev.starcore.starcore.module.territory.upgrade.gui;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.territory.upgrade.TerritoryUpgradeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for Territory Upgrade GUI interactions.
 * 领地升级GUI交互监听器
 */
public class TerritoryUpgradeGuiListener implements Listener {

    private final TerritoryUpgradeService upgradeService;
    private final NationService nationService;
    private final JavaPlugin plugin;
    // E-050: 点击冷却防止快速重复点击
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200;

    public TerritoryUpgradeGuiListener(
            TerritoryUpgradeService upgradeService,
            NationService nationService,
            JavaPlugin plugin) {
        this.upgradeService = upgradeService;
        this.nationService = nationService;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // E-050: 点击冷却防止快速重复点击导致卡顿或重复操作
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldowns.get(playerId);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        clickCooldowns.put(playerId, now);

        String title = event.getView().getTitle();

        // 检查是否是领地升级系统的GUI
        if (!title.contains("领地升级") && !title.contains("升级系统")) {
            return;
        }

        event.setCancelled(true);

        // 忽略边框点击
        if (isBorderSlot(event.getSlot())) {
            return;
        }

        // 处理点击
        handleClick(player, event.getSlot(), title);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (title.contains("领地升级")) {
            // 清理或保存状态
        }
    }

    private boolean isBorderSlot(int slot) {
        // 第一行
        if (slot < 9) {
            return true;
        }

        // 最后一行
        if (slot >= 27) {
            return true;
        }

        // 两边的
        if (slot % 9 == 0 || slot % 9 == 8) {
            return true;
        }

        return false;
    }

    private void handleClick(Player player, int slot, String title) {
        TerritoryUpgradeGui gui = new TerritoryUpgradeGui(upgradeService, nationService, player);

        // 主菜单
        if (title.contains("领地升级系统") && !title.contains(" - ")) {
            handleMainMenuClick(player, slot);
            return;
        }

        // 路径详情页面
        if (title.contains(" - ")) {
            // 如果包含返回按钮
            if (slot == 36) {
                gui.open();
                return;
            }

            // 等级点击处理
            handleLevelClick(player, slot, title);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        TerritoryUpgradeGui gui = new TerritoryUpgradeGui(upgradeService, nationService, player);

        // 状态信息 (slot 4)
        if (slot == 4) {
            gui.open();
            return;
        }

        // 导航按钮
        if (slot == 30) {
            // 刷新菜单
            gui.open();
            return;
        }

        if (slot == 32) {
            // 刷新
            gui.open();
            return;
        }

        if (slot == 35) {
            // 关闭
            player.closeInventory();
            return;
        }

        // 路径点击 (slots 10-16, 19-25)
        if ((slot >= 10 && slot <= 16) || (slot >= 19 && slot <= 25)) {
            String pathId = getPathIdFromSlot(slot);
            if (pathId != null) {
                gui.openPathDetail(pathId);
            }
        }
    }

    private void handleLevelClick(Player player, int slot, String title) {
        // 升级按钮 (slot 40)
        if (slot == 40) {
            String pathId = extractPathId(title);
            if (pathId != null) {
                performUpgrade(player, pathId);
            }
            return;
        }

        // 等级槽位
        if ((slot >= 10 && slot <= 16) || (slot >= 19 && slot <= 25)) {
            String pathId = extractPathId(title);
            if (pathId != null) {
                // 可以在这里打开单个等级的详情
            }
        }
    }

    private void performUpgrade(Player player, String pathId) {
        // 获取玩家国家
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你未加入任何国家");
            return;
        }

        var nation = nationOpt.get();
        var result = upgradeService.startUpgrade(nation.id(), pathId);

        if (result.isSuccess()) {
            player.sendMessage("§a升级成功!");
            // 重新打开GUI显示更新
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                TerritoryUpgradeGui gui = new TerritoryUpgradeGui(upgradeService, nationService, player);
                gui.openPathDetail(pathId);
            }, 1L);
        } else {
            player.sendMessage("§c升级失败: " + result.errorMessage());
        }
    }

    private String getPathIdFromSlot(int slot) {
        // 映射槽位到路径ID
        String[] pathIds = new String[]{
            "basic", "military", "economy"
        };

        int index;
        if (slot >= 10 && slot <= 16) {
            index = slot - 10;
        } else if (slot >= 19 && slot <= 25) {
            index = slot - 19 + 7;
        } else {
            return null;
        }

        if (index >= 0 && index < pathIds.length) {
            return pathIds[index];
        }

        return null;
    }

    private String extractPathId(String title) {
        // 从标题中提取路径ID
        // 格式: "§8§l路径名 - 收益" 或 "§8§l路径名"
        String cleaned = title.replace("§8§l", "").replace(" §7- 收益", "").trim();

        for (String pathId : upgradeService.getAvailablePaths()) {
            var pathOpt = upgradeService.getPathDefinition(pathId);
            if (pathOpt.isPresent()) {
                String pathName = pathOpt.get().pathName();
                if (cleaned.contains(pathName)) {
                    return pathId;
                }
            }
        }

        return null;
    }

    // E-050 修复: 玩家退出时清理点击冷却状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clickCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
