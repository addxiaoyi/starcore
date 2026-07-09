package dev.starcore.starcore.module.army.gui;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 创建军队 GUI
 * 允许玩家选择兵种和士兵数量
 */
public final class ArmyCreationMenu implements InventoryHolder {
    private final UUID nationId;
    private final UUID playerId;
    private final MessageService messages;
    private final ArmyService armyService;
    private final Inventory inventory;

    private ArmyType selectedType = ArmyType.INFANTRY;
    private int soldiers = 100;

    private ArmyCreationMenu(
        UUID nationId,
        UUID playerId,
        MessageService messages,
        ArmyService armyService
    ) {
        this.nationId = nationId;
        this.playerId = playerId;
        this.messages = messages;
        this.armyService = armyService;
        this.inventory = Bukkit.createInventory(this, 27,
            Component.text(messages.format("army.gui.create.title")));
        buildMenu();
    }

    public static ArmyCreationMenu create(
        UUID nationId,
        UUID playerId,
        MessageService messages,
        ArmyService armyService
    ) {
        return new ArmyCreationMenu(nationId, playerId, messages, armyService);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID getNationId() {
        return nationId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ArmyType getSelectedType() {
        return selectedType;
    }

    public int getSoldiers() {
        return soldiers;
    }

    public void setSelectedType(ArmyType type) {
        this.selectedType = type;
        buildMenu();
    }

    public void setSoldiers(int soldiers) {
        this.soldiers = Math.max(10, Math.min(soldiers, armyService.getConfig().maxSoldiersPerArmy()));
        buildMenu();
    }

    public void adjustSoldiers(int delta) {
        setSoldiers(soldiers + delta);
    }

    private void buildMenu() {
        inventory.clear();

        // 兵种选择 (第一行: 0-4)
        int slot = 0;
        for (ArmyType type : ArmyType.values()) {
            inventory.setItem(slot++, createTypeButton(type));
        }

        // 第二行: 详情显示
        // 位置10: 兵种图标
        inventory.setItem(10, createSelectedTypeDisplay());
        // 位置12: 士兵数量
        inventory.setItem(12, createSoldiersDisplay());
        // 位置14: 成本显示
        inventory.setItem(14, createCostDisplay());

        // 第三行: 士兵数量调整 (19-21)
        inventory.setItem(19, createAdjustButton(-50, Material.REDSTONE_BLOCK, "-50"));
        inventory.setItem(20, createAdjustButton(-10, Material.REDSTONE, "-10"));
        inventory.setItem(21, createAdjustButton(-1, Material.IRON_INGOT, "-1"));

        // 士兵数量显示区域 (22)
        inventory.setItem(22, createSoldiersCountDisplay());

        // 士兵数量调整 (23-25)
        inventory.setItem(23, createAdjustButton(1, Material.GOLD_INGOT, "+1"));
        inventory.setItem(24, createAdjustButton(10, Material.GOLD_INGOT, "+10"));
        inventory.setItem(25, createAdjustButton(50, Material.EMERALD_BLOCK, "+50"));

        // 快捷数量按钮 (位置 3-5)
        inventory.setItem(3, createQuickSoldiersButton(50, "50"));
        inventory.setItem(4, createQuickSoldiersButton(100, "100"));
        inventory.setItem(5, createQuickSoldiersButton(200, "200"));

        // 创建按钮
        inventory.setItem(11, createConfirmButton());

        // 取消按钮
        inventory.setItem(15, createCancelButton());

        // 关闭按钮
        inventory.setItem(26, createCloseButton());
    }

    private ItemStack createTypeButton(ArmyType type) {
        Material material = switch (type) {
            case INFANTRY -> Material.IRON_SWORD;
            case CAVALRY -> Material.SADDLE;
            case ARCHER -> Material.BOW;
            case SIEGE -> Material.TNT;
            case DEFENSIVE -> Material.SHIELD;
        };

        boolean selected = (type == selectedType);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        meta.displayName(Component.text(
            messages.format(armyTypeKey(type)),
            color
        ));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("army.gui.create.stats.attack",
            type.baseAttack()), NamedTextColor.RED));
        lore.add(Component.text(messages.format("army.gui.create.stats.defense",
            type.baseDefense()), NamedTextColor.BLUE));
        lore.add(Component.text(messages.format("army.gui.create.stats.health",
            type.baseHealth()), NamedTextColor.GREEN));
        lore.add(Component.text(messages.format("army.gui.create.stats.mobility",
            String.format("%.1f", type.mobility())), NamedTextColor.AQUA));
        lore.add(Component.text(messages.format("army.gui.create.stats.cost",
            type.costPerUnit()), NamedTextColor.GOLD));
        lore.add(Component.text(""));
        if (selected) {
            lore.add(Component.text(messages.format("army.gui.create.selected"), NamedTextColor.GREEN));
        } else {
            lore.add(Component.text(messages.format("army.gui.create.click-to-select"), NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectedTypeDisplay() {
        Material material = switch (selectedType) {
            case INFANTRY -> Material.IRON_SWORD;
            case CAVALRY -> Material.SADDLE;
            case ARCHER -> Material.BOW;
            case SIEGE -> Material.TNT;
            case DEFENSIVE -> Material.SHIELD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            messages.format("army.gui.create.selected-type",
                messages.format(armyTypeKey(selectedType))),
            NamedTextColor.GOLD
        ));

        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format(armyTypeDescriptionKey(selectedType)), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private String armyTypeKey(ArmyType type) {
        return "army.gui.create.type." + type.key();
    }

    private String armyTypeDescriptionKey(ArmyType type) {
        return "army.gui.create.type-desc." + type.key();
    }

    private ItemStack createSoldiersDisplay() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            messages.format("army.gui.create.soldiers"),
            NamedTextColor.YELLOW
        ));

        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.create.soldiers-range",
                10, armyService.getConfig().maxSoldiersPerArmy()), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCostDisplay() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int totalCost = selectedType.totalCost(soldiers);
        meta.displayName(Component.text(
            messages.format("army.gui.create.total-cost"),
            NamedTextColor.GOLD
        ));

        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.create.cost-breakdown",
                selectedType.costPerUnit(), soldiers, totalCost), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSoldiersCountDisplay() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            String.valueOf(soldiers),
            NamedTextColor.WHITE
        ));

        meta.lore(List.of(
            Component.text(messages.format("army.gui.create.soldiers"), NamedTextColor.YELLOW)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAdjustButton(int delta, Material material, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        NamedTextColor color = delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        meta.displayName(Component.text(label, color));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuickSoldiersButton(int amount, String label) {
        ItemStack item = new ItemStack(Material.BRICK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(label, NamedTextColor.YELLOW));
        meta.lore(List.of(
            Component.text(messages.format("army.gui.create.quick-soldiers"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            messages.format("army.gui.create.button.confirm"),
            NamedTextColor.GREEN
        ));

        int totalCost = selectedType.totalCost(soldiers);
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.create.confirm-desc",
                selectedType.key(), soldiers, totalCost), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            messages.format("army.gui.create.button.cancel"),
            NamedTextColor.RED
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(
            messages.format("army.gui.button.close"),
            NamedTextColor.RED
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 获取士兵数量调整对应的槽位
     */
    public static int getSoldiersAdjustSlot(int delta) {
        return switch (delta) {
            case -50 -> 19;
            case -10 -> 20;
            case -1 -> 21;
            case 1 -> 23;
            case 10 -> 24;
            case 50 -> 25;
            default -> -1;
        };
    }
}
