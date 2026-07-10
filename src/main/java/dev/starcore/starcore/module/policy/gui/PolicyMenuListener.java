package dev.starcore.starcore.module.policy.gui;

import dev.starcore.starcore.module.policy.PolicyModule;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 政策菜单点击事件监听器
 */
public class PolicyMenuListener implements Listener {

    private final PolicyModule policyModule;
    private final PolicyService policyService;
    private final NationService nationService;
    private final Map<UUID, PolicyCreationMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, MenuState> menuStates = new ConcurrentHashMap<>();

    public PolicyMenuListener(PolicyModule policyModule, PolicyService policyService,
                             NationService nationService, JavaPlugin plugin) {
        this.policyModule = policyModule;
        this.policyService = policyService;
        this.nationService = nationService;
    }

    /**
     * 打开政策创建菜单
     */
    public void openCreationMenu(Player player) {
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "你需要先加入一个国家");
            return;
        }

        PolicyCreationMenu menu = new PolicyCreationMenu(
                policyModule, policyService, nationService,
                player.getUniqueId(), nation.id()
        );
        openMenus.put(player.getUniqueId(), menu);
        menuStates.put(player.getUniqueId(), MenuState.CATEGORY_SELECTION);
        menu.openCategorySelection(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PolicyCreationMenu menu = openMenus.get(playerId);
        if (menu == null) {
            return;
        }

        // 检查是否是政策菜单
        String title = event.getView().title().toString();
        if (!title.contains("Create Policy")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        MenuState state = menuStates.getOrDefault(playerId, MenuState.CATEGORY_SELECTION);

        switch (state) {
            case CATEGORY_SELECTION -> handleCategorySelection(player, menu, slot);
            case POLICY_LIST -> handlePolicyList(player, menu, slot);
            case CONFIRM -> handleConfirm(player, menu, slot);
        }
    }

    private void handleCategorySelection(Player player, PolicyCreationMenu menu, int slot) {
        // 槽位 10-16, 19-25 对应政策类别
        PolicyCategory[] categories = PolicyCategory.values();
        int[] categorySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int categoryIndex = -1;

        for (int i = 0; i < categorySlots.length; i++) {
            if (slot == categorySlots[i]) {
                categoryIndex = i;
                break;
            }
        }

        if (categoryIndex >= 0 && categoryIndex < categories.length) {
            PolicyCategory category = categories[categoryIndex];
            menu.setSelectedCategory(category);
            menuStates.put(player.getUniqueId(), MenuState.POLICY_LIST);
            menu.openPolicyList(player, category);
        } else if (slot == 31) {
            // 返回按钮
            player.closeInventory();
        }
    }

    private void handlePolicyList(Player player, PolicyCreationMenu menu, int slot) {
        // 获取当前类别的政策列表
        PolicyCategory category = menu.getSelectedCategory();
        if (category == null) {
            player.closeInventory();
            return;
        }

        // 槽位 10-16, 19-25 对应政策
        int policyIndex = -1;
        if (slot >= 10 && slot <= 16) {
            policyIndex = slot - 10;
        } else if (slot >= 19 && slot <= 25) {
            policyIndex = slot - 19 + 7;
        }

        if (policyIndex >= 0) {
            var loader = new dev.starcore.starcore.module.policy.config.YamlPolicyDefinitionLoader();
            var definitions = loader.loadAllDefinitions().stream()
                    .filter(p -> p.category() == category)
                    .toList();

            if (policyIndex < definitions.size()) {
                PolicyDefinition policy = definitions.get(policyIndex);
                menu.setSelectedPolicy(policy);
                menuStates.put(player.getUniqueId(), MenuState.CONFIRM);
                menu.openPolicyConfirm(player, policy);
            }
        } else if (slot == 31) {
            // 返回按钮
            menuStates.put(player.getUniqueId(), MenuState.CATEGORY_SELECTION);
            menu.openCategorySelection(player);
        }
    }

    private void handleConfirm(Player player, PolicyCreationMenu menu, int slot) {
        if (slot == 22) {
            // 确认按钮
            boolean success = menu.activatePolicy(player);
            if (success) {
                player.closeInventory();
            }
        } else if (slot == 31) {
            // 返回按钮
            PolicyCategory category = menu.getSelectedCategory();
            if (category != null) {
                menuStates.put(player.getUniqueId(), MenuState.POLICY_LIST);
                menu.openPolicyList(player, category);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            openMenus.remove(playerId);
            menuStates.remove(playerId);
        }
    }

    private enum MenuState {
        CATEGORY_SELECTION,
        POLICY_LIST,
        CONFIRM
    }
}
