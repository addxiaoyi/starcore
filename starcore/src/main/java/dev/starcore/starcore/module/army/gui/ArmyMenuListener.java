package dev.starcore.starcore.module.army.gui;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyState;
import dev.starcore.starcore.module.army.model.ArmyType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 军队 GUI 事件监听器
 * 处理所有军队管理界面和玩家输入的交互
 */
public final class ArmyMenuListener implements Listener {

    private final ArmyService armyService;
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final WarService warService;
    private final TerritoryService territoryService;
    private final MessageService messages;
    private final ArmyMenuActions actions;

    // 打开的菜单（改为按玩家 UUID 索引，便于 PlayerQuitEvent 清理）
    private final Map<UUID, ArmyManagementMenu> openMenus = new ConcurrentHashMap<>();
    // 打开的目标选择器
    private final Map<UUID, TargetSelectorMenu> openSelectors = new ConcurrentHashMap<>();
    // 打开的创建军队菜单
    private final Map<UUID, ArmyCreationMenu> openCreationMenus = new ConcurrentHashMap<>();
    // 等待聊天输入的玩家
    private final Set<UUID> waitingForChatInput = ConcurrentHashMap.newKeySet();
    // E-050: 点击冷却防止快速重复点击
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200;
    // E-031 修复: 添加 Logger 以便记录静默吞没的异常
    private final Logger logger;

    public ArmyMenuListener(
        ArmyService armyService,
        NationService nationService,
        DiplomacyService diplomacyService,
        WarService warService,
        TerritoryService territoryService,
        MessageService messages
    ) {
        this.armyService = armyService;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.territoryService = territoryService;
        this.messages = messages;
        this.logger = Bukkit.getLogger();
        this.actions = new ArmyMenuActions(
            armyService, nationService, diplomacyService,
            warService, territoryService, messages
        );
    }

    /**
     * 打开主菜单
     */
    public void openMenu(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("army.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        List<ArmyUnit> armies = armyService.getNationArmies(nation.id().value());

        ArmyManagementMenu menu = ArmyManagementMenu.createMainMenu(nation.id().value(), armies, messages);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();

        // E-050: 点击冷却防止快速重复点击导致卡顿或重复操作
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldowns.get(playerId);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        clickCooldowns.put(playerId, now);

        Inventory clickedTopInventory = event.getView().getTopInventory();

        // 检查是否是创建军队菜单
        ArmyCreationMenu creationMenu = openCreationMenus.get(playerId);
        if (creationMenu != null && creationMenu.getInventory().equals(clickedTopInventory)) {
            handleCreationMenuClick(event, creationMenu);
            return;
        }

        // 检查是否是军队管理菜单
        ArmyManagementMenu managedMenu = openMenus.get(playerId);
        if (managedMenu != null && managedMenu.getInventory().equals(clickedTopInventory)) {
            handleMenuClick(event, managedMenu);
            return;
        }

        // 检查是否是目标选择器
        TargetSelectorMenu selector = openSelectors.get(playerId);
        if (selector != null && selector.getInventory().equals(clickedTopInventory)) {
            handleSelectorClick(event, selector);
        }
    }

    private void handleMenuClick(InventoryClickEvent event, ArmyManagementMenu menu) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();

        handleClick(player, menu, slot, clickedItem);
    }

    private void handleSelectorClick(InventoryClickEvent event, TargetSelectorMenu selector) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        Material type = clickedItem.getType();

        // 导航按钮
        if (slot == 45 && type == Material.ARROW) {
            selector.prevPage();
            return;
        }
        if (slot == 53 && type == Material.ARROW) {
            selector.nextPage();
            return;
        }

        // 取消按钮
        if (slot == 48 && type == Material.BARRIER) {
            player.closeInventory();
            player.sendMessage(Component.text(
                messages.format("army.gui.cancelled"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 目标选择
        selector.selectTarget(slot);
    }

    private void handleCreationMenuClick(InventoryClickEvent event, ArmyCreationMenu menu) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        Material type = clickedItem.getType();

        // 兵种选择 (槽位 0-4)
        if (slot >= 0 && slot <= 4) {
            ArmyType selectedType = ArmyType.values()[slot];
            menu.setSelectedType(selectedType);
            return;
        }

        // 快捷士兵数量 (槽位 3-5)
        if (slot == 3) {
            menu.setSoldiers(50);
            return;
        }
        if (slot == 4) {
            menu.setSoldiers(100);
            return;
        }
        if (slot == 5) {
            menu.setSoldiers(200);
            return;
        }

        // 士兵数量调整 (槽位 19-21, 23-25)
        if (slot == 19) {
            menu.adjustSoldiers(-50);
            return;
        }
        if (slot == 20) {
            menu.adjustSoldiers(-10);
            return;
        }
        if (slot == 21) {
            menu.adjustSoldiers(-1);
            return;
        }
        if (slot == 23) {
            menu.adjustSoldiers(1);
            return;
        }
        if (slot == 24) {
            menu.adjustSoldiers(10);
            return;
        }
        if (slot == 25) {
            menu.adjustSoldiers(50);
            return;
        }

        // 确认按钮 (槽位 11)
        if (slot == 11 && type == Material.LIME_CONCRETE) {
            confirmArmyCreation(player, menu);
            return;
        }

        // 取消按钮 (槽位 15)
        if (slot == 15 && type == Material.RED_CONCRETE) {
            player.closeInventory();
            player.sendMessage(Component.text(
                messages.format("army.gui.create.cancelled"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 关闭按钮 (槽位 26)
        if (slot == 26) {
            player.closeInventory();
        }
    }

    private void confirmArmyCreation(Player player, ArmyCreationMenu menu) {
        try {
            Location location = player.getLocation();
            ArmyUnit army = armyService.createArmy(
                menu.getNationId(),
                menu.getSelectedType(),
                menu.getSoldiers(),
                location
            );

            player.closeInventory();
            player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.created",
                    army.type().key(), army.soldiers(),
                    army.id().toString().substring(0, 8)), NamedTextColor.GREEN)));

        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format(e.getMessage()),
                NamedTextColor.RED
            ));
        }
    }

    private void handleClick(Player player, ArmyManagementMenu menu, int slot, ItemStack item) {
        switch (menu.getMenuType()) {
            case MAIN -> handleMainMenuClick(player, menu, slot, item);
            case DETAIL -> handleDetailMenuClick(player, menu, slot, item);
        }

        // 通用导航
        if (slot == 45 && menu.getMenuType() == ArmyManagementMenu.MenuType.DETAIL) {
            openMenu(player);
        } else if (slot == 49) {
            player.closeInventory();
        } else if (slot == 53) {
            showHelp(player);
        }
    }

    private void handleMainMenuClick(Player player, ArmyManagementMenu menu, int slot, ItemStack item) {
        if (slot >= 9 && slot < 45) {
            // 点击了某支军队
            int index = slot - 9;
            List<ArmyUnit> armies = menu.getArmies();
            if (index < armies.size()) {
                ArmyUnit army = armies.get(index);
                openArmyDetail(player, menu.getNationId(), army);
            }
        } else if (slot == 0) {
            // 创建军队 - 打开创建菜单
            openArmyCreationMenu(player, menu.getNationId());
        } else if (slot == 1) {
            // 补给全部
            supplyAll(player, menu);
        } else if (slot == 2) {
            // 召回全部
            actions.recallAll(player);
            player.closeInventory();
        }
    }

    private void openArmyCreationMenu(Player player, UUID nationId) {
        ArmyCreationMenu creationMenu = ArmyCreationMenu.create(
            nationId,
            player.getUniqueId(),
            messages,
            armyService
        );
        openCreationMenus.put(player.getUniqueId(), creationMenu);
        player.openInventory(creationMenu.getInventory());
    }

    private void handleDetailMenuClick(Player player, ArmyManagementMenu menu, int slot, ItemStack item) {
        ArmyUnit army = menu.getSelectedArmy();
        if (army == null) {
            return;
        }

        switch (slot) {
            case 19 -> openMoveSelector(player, army);
            case 21 -> openAttackSelector(player, army);
            case 23 -> supplyArmy(player, army);
            case 25 -> requestDisbandConfirm(player, army);
            case 37 -> actions.changeArmyState(player, army, ArmyState.STATIONARY);
            case 38 -> actions.changeArmyState(player, army, ArmyState.MARCHING);
            case 39 -> actions.changeArmyState(player, army, ArmyState.DEFENDING);
        }
    }

    private void openArmyDetail(Player player, UUID nationId, ArmyUnit army) {
        List<ArmyUnit> armies = armyService.getNationArmies(nationId);
        ArmyManagementMenu menu = ArmyManagementMenu.createArmyDetailMenu(nationId, armies, army, messages);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    private void openMoveSelector(Player player, ArmyUnit army) {
        if (!army.canMove()) {
            player.sendMessage(Component.text(
                messages.format("army.gui.move.cannot-move"),
                NamedTextColor.RED
            ));
            return;
        }

        player.closeInventory();

        // 创建移动目标选择器
        TargetSelectorMenu selector = TargetSelectorMenu.createMoveSelector(
            player, army, armyService, nationService, messages
        );
        openSelectors.put(player.getUniqueId(), selector);
        player.openInventory(selector.getInventory());
    }

    private void openAttackSelector(Player player, ArmyUnit army) {
        if (!army.canFight()) {
            player.sendMessage(Component.text(
                messages.format("army.gui.attack.cannot-fight"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否处于战争状态
        NationId nationId = new NationId(army.nationId());
        if (!warService.activeWarsOf(nationId).isEmpty()) {
            player.closeInventory();

            // 创建攻击目标选择器
            TargetSelectorMenu selector = TargetSelectorMenu.createAttackSelector(
                player, army, armyService, warService, nationService, messages
            );
            openSelectors.put(player.getUniqueId(), selector);
            player.openInventory(selector.getInventory());
        } else {
            // 没有交战，直接执行攻击
            actions.initiateAttack(player, army);
        }
    }

    private void supplyAll(Player player, ArmyManagementMenu menu) {
        List<ArmyUnit> armies = menu.getArmies();
        int count = 0;
        for (ArmyUnit army : armies) {
            if (army.needsSupply()) {
                try {
                    armyService.resupplyArmy(army.id());
                    count++;
                } catch (Exception e) {
                    // E-031 修复: 记录异常而不是静默吞没
                    logger.warning("[ArmyMenu] 补给所有军队时出错: " + e.getMessage());
                }
            }
        }

        player.sendMessage(Component.text(
            messages.format("army.gui.supplied-all", count),
            NamedTextColor.GREEN
        ));
        player.closeInventory();
    }

    private void supplyArmy(Player player, ArmyUnit army) {
        try {
            armyService.resupplyArmy(army.id());
            player.sendMessage(Component.text(
                messages.format("army.supplied", army.id().toString().substring(0, 8)),
                NamedTextColor.GREEN
            ));
            player.closeInventory();
        } catch (Exception e) {
            // E-031 修复: 记录异常而不是静默吞没
            logger.warning("[ArmyMenu] 补给军队 " + army.id() + " 时出错: " + e.getMessage());
            player.sendMessage(Component.text(
                messages.format("error.generic"),
                NamedTextColor.RED
            ));
        }
    }

    private void requestDisbandConfirm(Player player, ArmyUnit army) {
        String confirmCode = army.id().toString().substring(0, 8);

        actions.requestDisbandConfirm(player, army, () -> {
            // 确认后的回调
            player.closeInventory();
        });

        waitingForChatInput.add(player.getUniqueId());
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text());
        player.sendMessage(Component.text("=== 军队管理帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("主菜单:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  位置0: 创建军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  位置1: 补给所有军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  位置2: 召回所有军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  9-44: 选择军队管理", NamedTextColor.GRAY));
        player.sendMessage(Component.text());
        player.sendMessage(Component.text("军队详情:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  19: 移动军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  21: 进攻", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  23: 补给军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  25: 解散军队", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  37-39: 切换状态", NamedTextColor.GRAY));
        player.sendMessage(Component.text());
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否是军队操作相关的输入
        if (actions.isWaitingForInput(player, ArmyMenuActions.InputType.MOVE_TARGET)) {
            event.setCancelled(true);
            String input = event.getMessage();
            if (actions.handleMoveTargetInput(player, input)) {
                waitingForChatInput.remove(playerId);
            }
            return;
        }

        if (actions.isWaitingForInput(player, ArmyMenuActions.InputType.ATTACK_TARGET)) {
            event.setCancelled(true);
            String input = event.getMessage();
            if (actions.handleAttackTargetInput(player, input)) {
                waitingForChatInput.remove(playerId);
            }
            return;
        }

        if (actions.isWaitingForInput(player, ArmyMenuActions.InputType.DISBAND_CONFIRM)) {
            event.setCancelled(true);
            String input = event.getMessage();
            if (actions.handleDisbandConfirm(player, input, null)) {
                waitingForChatInput.remove(playerId);
            }
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            openMenus.remove(playerId);
            openSelectors.remove(playerId);
            openCreationMenus.remove(playerId);
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 Map 状态导致的内存泄漏和状态泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openMenus.remove(playerId);
        openSelectors.remove(playerId);
        openCreationMenus.remove(playerId);
        waitingForChatInput.remove(playerId);
        clickCooldowns.remove(playerId);
    }

    /**
     * 获取动作处理器（用于外部访问）
     */
    public ArmyMenuActions getActions() {
        return actions;
    }

    /**
     * 检查玩家是否在等待输入
     */
    public boolean isWaitingForInput(Player player) {
        return waitingForChatInput.contains(player.getUniqueId());
    }
}
