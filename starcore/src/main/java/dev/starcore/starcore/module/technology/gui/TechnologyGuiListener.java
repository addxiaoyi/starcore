package dev.starcore.starcore.module.technology.gui;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 科技 GUI 事件监听器
 * 处理科技菜单的点击事件和命令
 */
public class TechnologyGuiListener implements Listener {

    private final TechnologyModule technologyModule;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final org.bukkit.plugin.Plugin plugin;
    private final TechnologyTreeGui treeGui;

    // 打开的 GUI 追踪
    private final ConcurrentMap<UUID, String> openMenus = new ConcurrentHashMap<>();

    public TechnologyGuiListener(TechnologyModule technologyModule,
                                NationService nationService,
                                TreasuryService treasuryService,
                                Plugin plugin) {
        this.technologyModule = technologyModule;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.plugin = plugin;
        this.treeGui = new TechnologyTreeGui(technologyModule, nationService, treasuryService, plugin);
    }

    /**
     * 处理库存点击事件
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 检查是否是 Triumph GUI
        if (isTriumphGui(event.getView().getTitle())) {
            event.setCancelled(true);
            handleTriumphGuiClick(player, event.getSlot());
            return;
        }

        // 检查是否是科技相关菜单
        String title = event.getView().getTitle();
        if (title.contains("科技")) {
            event.setCancelled(true);
        }
    }

    /**
     * 处理 Triumph GUI 点击
     */
    private void handleTriumphGuiClick(Player player, int slot) {
        String title = player.getOpenInventory().getTitle();

        // 科技树按钮
        if (slot == 22 && title.contains("国家管理")) {
            nationService.nationOf(player.getUniqueId()).ifPresent(nation -> {
                Bukkit.getScheduler().runTask(plugin, () -> treeGui.openMainMenu(player));
            });
        }
    }

    /**
     * 检查标题是否是 Triumph GUI
     */
    private boolean isTriumphGui(String title) {
        return title.contains("国家管理") || title.contains("Nation");
    }

    /**
     * 打开科技主菜单
     */
    public void openMainMenu(Player player) {
        treeGui.openMainMenu(player);
        openMenus.put(player.getUniqueId(), "main");
    }

    /**
     * 打开科技树视图
     */
    public void openTechTreeView(Player player, Nation nation, int eraPage) {
        treeGui.openTechTreeView(player, nation, eraPage);
        openMenus.put(player.getUniqueId(), "tree");
    }

    /**
     * 打开科技详情
     */
    public void openTechDetailMenu(Player player, Nation nation, String techId) {
        treeGui.openTechDetailMenu(player, nation, techId);
        openMenus.put(player.getUniqueId(), "detail:" + techId);
    }

    /**
     * 获取科技树 GUI 实例
     */
    public TechnologyTreeGui getTreeGui() {
        return treeGui;
    }

    /**
     * 检查玩家是否在科技菜单中
     */
    public boolean isInTechMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    /**
     * 清除玩家的菜单状态
     */
    public void clearMenuState(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    // audit H-004: 修复 PlayerQuitEvent 未清理 openMenus Map 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }
}
