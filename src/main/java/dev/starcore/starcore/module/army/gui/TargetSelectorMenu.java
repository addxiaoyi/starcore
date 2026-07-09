package dev.starcore.starcore.module.army.gui;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 交互式目标选择器 GUI
 * 用于选择移动目标或攻击目标
 */
public final class TargetSelectorMenu implements InventoryHolder {

    private final Player player;
    private final ArmyUnit sourceArmy;
    private final SelectionType selectionType;
    private final ArmyService armyService;
    private final WarService warService;
    private final NationService nationService;
    private final MessageService messages;
    private final Inventory inventory;

    // 当前页码
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 36; // 6x6 格子

    private TargetSelectorMenu(
        Player player,
        ArmyUnit sourceArmy,
        SelectionType selectionType,
        ArmyService armyService,
        WarService warService,
        NationService nationService,
        MessageService messages
    ) {
        this.player = player;
        this.sourceArmy = sourceArmy;
        this.selectionType = selectionType;
        this.armyService = armyService;
        this.warService = warService;
        this.nationService = nationService;
        this.messages = messages;

        String title = selectionType == SelectionType.MOVE ?
            messages.format("army.gui.move.title") :
            messages.format("army.gui.attack.title");

        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));
        buildMenu();
    }

    /**
     * 创建移动目标选择器
     */
    public static TargetSelectorMenu createMoveSelector(
        Player player,
        ArmyUnit sourceArmy,
        ArmyService armyService,
        NationService nationService,
        MessageService messages
    ) {
        return new TargetSelectorMenu(
            player,
            sourceArmy,
            SelectionType.MOVE,
            armyService,
            null,
            nationService,
            messages
        );
    }

    /**
     * 创建攻击目标选择器
     */
    public static TargetSelectorMenu createAttackSelector(
        Player player,
        ArmyUnit sourceArmy,
        ArmyService armyService,
        WarService warService,
        NationService nationService,
        MessageService messages
    ) {
        return new TargetSelectorMenu(
            player,
            sourceArmy,
            SelectionType.ATTACK,
            armyService,
            warService,
            nationService,
            messages
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public ArmyUnit getSourceArmy() {
        return sourceArmy;
    }

    private void buildMenu() {
        inventory.clear();

        // 根据选择类型获取目标列表
        List<TargetOption> targets;
        if (selectionType == SelectionType.MOVE) {
            targets = buildMoveTargets();
        } else {
            targets = buildAttackTargets();
        }

        // 分页显示
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, targets.size());

        for (int i = startIndex; i < endIndex; i++) {
            TargetOption target = targets.get(i);
            int slot = (i - startIndex);
            inventory.setItem(slot, createTargetItem(target));
        }

        // 添加导航和操作按钮
        addNavigationButtons(targets.size());
    }

    /**
     * 构建移动目标选项
     */
    private List<TargetOption> buildMoveTargets() {
        List<TargetOption> targets = new ArrayList<>();
        NationId nationId = new NationId(sourceArmy.nationId());

        // 获取国家信息
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return targets;
        }

        Nation nation = nationOpt.get();

        // 1. 首都位置
        if (nation.capitalLocation() != null) {
            targets.add(new TargetOption(
                "首都",
                nation.capitalLocation(),
                Material.EMERALD,
                NamedTextColor.GREEN
            ));
        }

        // 2. 城镇位置
        nation.getTownLocations().forEach((name, loc) -> {
            targets.add(new TargetOption(
                name,
                loc,
                Material.BEACON,
                NamedTextColor.AQUA
            ));
        });

        // 3. 当前位置（设为驻扎）
        targets.add(new TargetOption(
            "当前位置",
            sourceArmy.location(),
            Material.ARROW,
            NamedTextColor.GRAY
        ));

        // 4. 玩家当前位置
        targets.add(new TargetOption(
            "我的位置",
            player.getLocation(),
            Material.COMPASS,
            NamedTextColor.WHITE
        ));

        return targets;
    }

    /**
     * 构建攻击目标选项
     */
    private List<TargetOption> buildAttackTargets() {
        List<TargetOption> targets = new ArrayList<>();
        NationId sourceNationId = new NationId(sourceArmy.nationId());

        // 获取附近可攻击的敌军
        List<ArmyUnit> nearbyArmies = armyService.getArmiesNear(sourceArmy.location(), 200);

        for (ArmyUnit enemy : nearbyArmies) {
            // 检查是否敌对
            NationId enemyNationId = new NationId(enemy.nationId());
            if (!warService.atWar(sourceNationId, enemyNationId)) {
                continue;
            }

            // 获取敌军所属国家名称
            String nationName = nationService.nationById(enemyNationId)
                .map(Nation::name)
                .orElse("Unknown");

            Material material = switch (enemy.type().key()) {
                case "infantry" -> Material.IRON_SWORD;
                case "cavalry" -> Material.SADDLE;
                case "archer" -> Material.BOW;
                case "siege" -> Material.TNT;
                default -> Material.PLAYER_HEAD;
            };

            targets.add(new TargetOption(
                nationName + " - " + enemy.type().key() + " [" + enemy.id().toString().substring(0, 8) + "]",
                enemy.location(),
                material,
                NamedTextColor.RED,
                enemy.id() // 额外数据：敌军ID
            ));
        }

        return targets;
    }

    private ItemStack createTargetItem(TargetOption option) {
        ItemStack item = new ItemStack(option.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(option.name(), option.color()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));

        // 位置信息
        if (option.location() != null) {
            lore.add(Component.text("位置: " +
                option.location().getBlockX() + ", " +
                option.location().getBlockY() + ", " +
                option.location().getBlockZ(), NamedTextColor.GRAY));
        }

        // 距离
        if (option.location() != null && sourceArmy.location().getWorld().equals(option.location().getWorld())) {
            double distance = sourceArmy.location().distance(option.location());
            lore.add(Component.text("距离: " + String.format("%.0f", distance) + " 格", NamedTextColor.GRAY));

            // 检查是否超出移动范围
            double maxDistance = sourceArmy.type().mobility() * 10;
            if (distance > maxDistance) {
                lore.add(Component.text("超出移动范围!", NamedTextColor.DARK_RED));
            } else {
                lore.add(Component.text("可到达", NamedTextColor.GREEN));
            }
        }

        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("army.gui.click-to-select"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private void addNavigationButtons(int totalTargets) {
        // 上一页按钮
        if (currentPage > 0) {
            inventory.setItem(45, createNavigationItem(
                Material.ARROW,
                messages.format("army.gui.page.prev"),
                messages.format("army.gui.page.prev.desc")
            ));
        }

        // 下一页按钮
        int totalPages = (int) Math.ceil((double) totalTargets / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, createNavigationItem(
                Material.ARROW,
                messages.format("army.gui.page.next"),
                messages.format("army.gui.page.next.desc")
            ));
        }

        // 页码显示
        if (totalPages > 1) {
            ItemStack pageItem = new ItemStack(Material.BOOK);
            ItemMeta meta = pageItem.getItemMeta();
            meta.displayName(Component.text(
                messages.format("army.gui.page.number", currentPage + 1, totalPages),
                NamedTextColor.GOLD
            ));
            pageItem.setItemMeta(meta);
            inventory.setItem(49, pageItem);
        }

        // 取消按钮
        inventory.setItem(48, createCancelButton());
    }

    private ItemStack createNavigationItem(Material material, String name, String desc) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(List.of(
            Component.text(""),
            Component.text(desc, NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(messages.format("army.gui.button.cancel"), NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 处理目标选择
     */
    public void selectTarget(int slot) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE) {
            return;
        }

        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        int targetIndex = currentPage * ITEMS_PER_PAGE + slot;
        List<TargetOption> targets = selectionType == SelectionType.MOVE ?
            buildMoveTargets() : buildAttackTargets();

        if (targetIndex >= targets.size()) {
            return;
        }

        TargetOption selected = targets.get(targetIndex);

        // 根据选择类型处理
        if (selectionType == SelectionType.MOVE) {
            handleMoveSelection(selected);
        } else {
            handleAttackSelection(selected);
        }
    }

    /**
     * 处理移动选择
     */
    private void handleMoveSelection(TargetOption target) {
        if (target.location() == null) {
            player.sendMessage(Component.text("无效的目标位置", NamedTextColor.RED));
            return;
        }

        try {
            armyService.moveArmy(sourceArmy.id(), target.location());
            player.sendMessage(Component.text()
                .append(Component.text("军队已移动至: " + target.name(), NamedTextColor.GREEN)));
            player.closeInventory();
        } catch (Exception e) {
            player.sendMessage(Component.text("移动失败: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 处理攻击选择
     */
    private void handleAttackSelection(TargetOption target) {
        if (target.extraData() == null) {
            player.sendMessage(Component.text("无效的攻击目标", NamedTextColor.RED));
            return;
        }

        UUID targetArmyId = (UUID) target.extraData();

        // 确认攻击
        player.sendMessage(Component.text()
            .append(Component.text("正在与目标交战...", NamedTextColor.YELLOW)));

        // 关闭 GUI 并执行攻击
        player.closeInventory();

        // 通过命令执行攻击
        player.performCommand("army attack " + sourceArmy.id().toString().substring(0, 8) + " " +
            targetArmyId.toString().substring(0, 8));
    }

    /**
     * 翻页
     */
    public void nextPage() {
        currentPage++;
        buildMenu();
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            buildMenu();
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    // ==================== 内部类 ====================

    public enum SelectionType {
        MOVE,
        ATTACK
    }

    /**
     * 目标选项
     */
    public record TargetOption(
        String name,
        Location location,
        Material material,
        NamedTextColor color,
        Object extraData
    ) {
        public TargetOption(String name, Location location, Material material, NamedTextColor color) {
            this(name, location, material, color, null);
        }
    }
}
