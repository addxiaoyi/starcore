package dev.starcore.starcore.module.policy.gui;

import dev.starcore.starcore.module.policy.PolicyModule;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Simplified policy GUI menu
 *
 * audit B-164 (partial): 占位实现，activeCount 恒 0、无 click 监听器、"New Policy" 项看似可创建政策但 PolicyModule 无创建逻辑。
 * 本菜单目前没有任何调用方，仅保留类型骨架。完整菜单（点击展示已激活政策、可激活列表与冷却）
 * 需新增 InventoryHolder + InventoryClickEvent 监听器；为避免与未来 GUI 改造冲突，暂以 TODO 形式占位，
 * TODO: 后续接入 InventoryHolder/Listener 完善玩家可见菜单。
 */
public class PolicyMenu {

    public static final String MENU_TITLE = "Policy";

    private final PolicyModule policyModule;
    private final PolicyService policyService;
    private final NationService nationService;
    private final UUID playerId;

    public PolicyMenu(PolicyModule policyModule, PolicyService policyService, NationService nationService, UUID playerId) {
        this.policyModule = policyModule;
        this.policyService = policyService;
        this.nationService = nationService;
        this.playerId = playerId;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(MENU_TITLE));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        Optional<Nation> nation = nationService.nationOf(player.getUniqueId());
        if (nation.isPresent()) {
            // audit B-164: 修正 activeCount 恒 0 的占位 bug，真正查询活跃政策表
            int activeCount = policyService.activePolicy(nation.get().id()).isPresent() ? 1 : 0;
            inv.setItem(11, createMenuItem(Material.BOOK, "Active Policies", String.valueOf(activeCount)));
            inv.setItem(13, createMenuItem(Material.PAPER, "All Policies", "View all policies"));
            inv.setItem(15, createMenuItem(Material.WRITABLE_BOOK, "New Policy", "Create new policy (TODO)"));
        } else {
            inv.setItem(13, createMenuItem(Material.BARRIER, "No Nation", "Join a nation first"));
        }

        player.openInventory(inv);
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore.length > 0) {
                List<Component> loreList = new java.util.ArrayList<>();
                for (String line : lore) {
                    loreList.add(Component.text(line));
                }
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
