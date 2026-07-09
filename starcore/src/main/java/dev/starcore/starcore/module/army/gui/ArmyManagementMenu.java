package dev.starcore.starcore.module.army.gui;

import dev.starcore.starcore.foundation.animation.ParticleEffectManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.model.ArmyState;
import dev.starcore.starcore.module.army.model.ArmyType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 军队管理 GUI
 * 显示军队状态、战斗力、补给等信息
 */
public final class ArmyManagementMenu implements InventoryHolder {
    private final UUID nationId;
    private final List<ArmyUnit> armies;
    private final MessageService messages;
    private final Inventory inventory;
    private final MenuType menuType;
    private final ArmyUnit selectedArmy;
    private final ParticleEffectManager particleManager;

    // 菜单槽位定义
    private static final int STATS_START = 0;
    private static final int ARMY_START = 9;
    private static final int ACTION_START = 45;
    private static final int NAVIGATION_START = 49;

    private ArmyManagementMenu(
        UUID nationId,
        List<ArmyUnit> armies,
        MessageService messages,
        MenuType menuType,
        ArmyUnit selectedArmy,
        ParticleEffectManager particleManager
    ) {
        this.nationId = nationId;
        this.armies = armies;
        this.messages = messages;
        this.menuType = menuType;
        this.selectedArmy = selectedArmy;
        this.particleManager = particleManager;
        this.inventory = Bukkit.createInventory(
            this,
            54,
            Component.text(messages.format("army.gui.title"))
        );

        buildMenu();
    }

    public static ArmyManagementMenu createMainMenu(UUID nationId, List<ArmyUnit> armies, MessageService messages) {
        return createMainMenu(nationId, armies, messages, null);
    }

    public static ArmyManagementMenu createMainMenu(
        UUID nationId,
        List<ArmyUnit> armies,
        MessageService messages,
        ParticleEffectManager particleManager
    ) {
        return new ArmyManagementMenu(nationId, armies, messages, MenuType.MAIN, null, particleManager);
    }

    public static ArmyManagementMenu createArmyDetailMenu(
        UUID nationId,
        List<ArmyUnit> armies,
        ArmyUnit army,
        MessageService messages
    ) {
        return createArmyDetailMenu(nationId, armies, army, messages, null);
    }

    public static ArmyManagementMenu createArmyDetailMenu(
        UUID nationId,
        List<ArmyUnit> armies,
        ArmyUnit army,
        MessageService messages,
        ParticleEffectManager particleManager
    ) {
        return new ArmyManagementMenu(nationId, armies, messages, MenuType.DETAIL, army, particleManager);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public MenuType getMenuType() {
        return menuType;
    }

    public UUID getNationId() {
        return nationId;
    }

    public ArmyUnit getSelectedArmy() {
        return selectedArmy;
    }

    public List<ArmyUnit> getArmies() {
        return armies;
    }

    private void buildMenu() {
        switch (menuType) {
            case MAIN -> buildMainMenu();
            case DETAIL -> buildDetailMenu();
        }

        addNavigationButtons();
    }

    private void buildMainMenu() {
        // 顶部统计栏
        buildStatsBar();

        // 显示所有军队
        int slot = ARMY_START;
        for (int i = 0; i < armies.size() && slot < 45; i++) {
            ArmyUnit army = armies.get(i);
            // E-XXX: 防御性空检查，防止列表中意外出现 null 元素
            if (army == null) {
                continue;
            }
            inventory.setItem(slot, createArmyItem(army));
            slot++;
        }

        // 操作按钮（如果还有空间）
        if (armies.size() < 36) {
            inventory.setItem(ARMIES_END_SLOT(), createCreateArmyButton());
        }
    }

    /**
     * 构建统计栏
     */
    private void buildStatsBar() {
        // 总兵力
        int totalSoldiers = armies.stream().mapToInt(ArmyUnit::soldiers).sum();
        inventory.setItem(STATS_START, createStatItem(
            Material.IRON_CHESTPLATE,
            "§e⚔️ 总兵力",
            formatNumber(totalSoldiers),
            "所有军队士兵总数"
        ));

        // 军队数量
        inventory.setItem(1, createStatItem(
            Material.PLAYER_HEAD,
            "§a👥 军队数量",
            String.valueOf(armies.size()),
            "当前拥有的军队"
        ));

        // 总战斗力
        double totalPower = armies.stream().mapToDouble(ArmyUnit::combatRating).sum();
        inventory.setItem(2, createStatItem(
            Material.DIAMOND_SWORD,
            "§c⚡ 总战斗力",
            String.format("%.0f", totalPower),
            "综合战斗力评分"
        ));

        // 总体状态
        int healthyCount = (int) armies.stream().filter(a -> a.health() >= 80).count();
        int warningCount = (int) armies.stream().filter(a -> a.health() >= 50 && a.health() < 80).count();
        int criticalCount = (int) armies.stream().filter(a -> a.health() < 50).count();

        Material statusMat = criticalCount > 0 ? Material.RED_BANNER :
                           warningCount > 0 ? Material.YELLOW_BANNER :
                           Material.GREEN_BANNER;
        String statusText = criticalCount > 0 ? "§c需要支援" :
                          warningCount > 0 ? "§e部分受损" :
                          "§a状态良好";

        inventory.setItem(3, createStatItem(
            statusMat,
            "§e🏳️ 军队状态",
            statusText,
            "§7健康:" + healthyCount + " §e受损:" + warningCount + " §c危急:" + criticalCount
        ));

        // 快捷操作
        inventory.setItem(5, createQuickActionItem(
            Material.BREAD,
            "§6🍖 补给全部",
            "为所有军队补充物资"
        ));

        inventory.setItem(6, createQuickActionItem(
            Material.BELL,
            "§b🔔 召回全部",
            "召回所有行军中的军队"
        ));

        inventory.setItem(7, createQuickActionItem(
            Material.BEACON,
            "§d⚔️ 战斗总览",
            "查看所有军队战斗力"
        ));
    }

    /**
     * 获取军队显示区域结束槽位
     */
    private int ARMIES_END_SLOT() {
        return 44;
    }

    private void buildDetailMenu() {
        if (selectedArmy == null) {
            return;
        }

        // 军队主信息（中央）
        inventory.setItem(4, createArmyDetailItem(selectedArmy));

        // 详细属性面板
        buildDetailStatsPanel();

        // 操作按钮
        inventory.setItem(19, createMoveButton());
        inventory.setItem(21, createAttackButton());
        inventory.setItem(23, createSupplyButton());
        inventory.setItem(25, createDisbandButton());

        // 状态切换按钮
        inventory.setItem(37, createStateButton(ArmyState.STATIONARY));
        inventory.setItem(38, createStateButton(ArmyState.MARCHING));
        inventory.setItem(39, createStateButton(ArmyState.DEFENDING));
    }

    /**
     * 构建详细属性面板
     */
    private void buildDetailStatsPanel() {
        if (selectedArmy == null) return;

        // 攻击力
        inventory.setItem(10, createAttributeItem(
            Material.IRON_SWORD,
            "§c⚔️ 攻击力",
            String.format("%.1f", selectedArmy.effectiveAttack()),
            "实际攻击力（已计算加成）"
        ));

        // 防御力
        inventory.setItem(11, createAttributeItem(
            Material.SHIELD,
            "§9🛡️ 防御力",
            String.format("%.1f", selectedArmy.effectiveDefense()),
            "实际防御力（已计算加成）"
        ));

        // 机动性
        inventory.setItem(12, createAttributeItem(
            Material.LEATHER_BOOTS,
            "§e🏃 机动性",
            String.valueOf(selectedArmy.type().mobility()),
            "每点机动性移动10格"
        ));

        // 补给状态
        NamedTextColor supplyColor = getSupplyColor(selectedArmy.supply());
        inventory.setItem(14, createAttributeItem(
            Material.COOKED_BEEF,
            "§6🍖 补给",
            selectedArmy.supply() + "%",
            supplyColor != NamedTextColor.RED ? "补给充足" : "需要补给"
        ));

        // 士气状态
        NamedTextColor moraleColor = getMoraleColor(selectedArmy.morale());
        inventory.setItem(15, createAttributeItem(
            Material.GOLDEN_APPLE,
            "§a💚 士气",
            String.format("%.0f%%", selectedArmy.morale()),
            moraleColor != NamedTextColor.RED ? "士气高昂" : "士气低落"
        ));

        // 战斗力评分
        inventory.setItem(16, createAttributeItem(
            Material.NETHER_STAR,
            "§c⭐ 战斗力",
            String.format("%.0f", selectedArmy.combatRating()),
            "综合战斗力评分"
        ));
    }

    private ItemStack createStatItem(Material material, String label, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for stat item");
        }

        meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§e" + value, NamedTextColor.GOLD));
        lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuickActionItem(Material material, String label, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for quick action item");
        }

        meta.displayName(Component.text(label, NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("§e点击执行", NamedTextColor.YELLOW));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAttributeItem(Material material, String label, String value, String note) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for attribute item");
        }

        meta.displayName(Component.text(label, NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§e" + value, NamedTextColor.GOLD));
        lore.add(Component.text("§7" + note, NamedTextColor.GRAY));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArmyItem(ArmyUnit army) {
        Material material = getArmyMaterial(army.type());

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for army item: " + army.id());
        }

        // E-XXX: 防御性空检查，防止 army.id() 返回 null 导致 NPE
        String armyIdStr = army.id() != null ? army.id().toString() : "unknown";
        String shortId = armyIdStr.length() >= 8 ? armyIdStr.substring(0, 8) : armyIdStr;
        meta.displayName(Component.text(
            messages.format("army.gui.army.name", army.type().key(), shortId),
            NamedTextColor.GOLD
        ));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));

        // 士兵数量（带颜色）
        lore.add(Component.text("§7士兵: §f" + army.soldiers(), NamedTextColor.GRAY));

        // 生命值（带颜色条）
        NamedTextColor healthColor = getHealthColor(army.health());
        String healthBar = getHealthBar(army.health());
        lore.add(Component.text("§7生命: " + healthBar + " §f" + String.format("%.0f%%", army.health()), healthColor));

        // 士气（带颜色）
        NamedTextColor moraleColor = getMoraleColor(army.morale());
        lore.add(Component.text("§7士气: §f" + String.format("%.0f%%", army.morale()), moraleColor));

        // 补给（带颜色）
        NamedTextColor supplyColor = getSupplyColor(army.supply());
        lore.add(Component.text("§7补给: §f" + army.supply() + "%", supplyColor));

        // 状态
        lore.add(Component.text("§7状态: §f" + getStateDisplayName(army.state()), NamedTextColor.YELLOW));

        // 战斗力评分
        lore.add(Component.text("§7战力: §c" + String.format("%.0f", army.combatRating()), NamedTextColor.RED));

        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("army.gui.click-to-manage"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArmyDetailItem(ArmyUnit army) {
        Material material = getArmyMaterial(army.type());
        ItemStack item = createArmyItem(army);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to get ItemMeta for army detail item: " + army.id());
        }

        List<Component> lore = new ArrayList<>(meta.lore());
        if (lore == null) {
            lore = new ArrayList<>();
        }

        // 额外详细信息
        lore.add(Component.text(""));
        lore.add(Component.text("§6=== 详细属性 ===", NamedTextColor.GOLD));
        lore.add(Component.text("§c攻击力: §f" + String.format("%.1f", army.effectiveAttack())));
        lore.add(Component.text("§9防御力: §f" + String.format("%.1f", army.effectiveDefense())));
        lore.add(Component.text("§e机动性: §f" + army.type().mobility()));
        lore.add(Component.text("§a战斗力: §f" + String.format("%.0f", army.combatRating())));

        // 克制信息
        String counterInfo = getCounterInfo(army.type());
        if (!counterInfo.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("§d" + counterInfo, NamedTextColor.LIGHT_PURPLE));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCreateArmyButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for create army button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.create"), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("army.gui.button.create.desc"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("army.gui.button.click-to-create"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSupplyAllButton() {
        ItemStack item = new ItemStack(Material.BREAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for supply all button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.supply-all"), NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.supply-all.desc"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRecallAllButton() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for recall all button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.recall"), NamedTextColor.AQUA));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.recall.desc"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMoveButton() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for move button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.move"), NamedTextColor.YELLOW));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.move.desc"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAttackButton() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for attack button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.attack"), NamedTextColor.RED));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.attack.desc"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSupplyButton() {
        ItemStack item = new ItemStack(Material.COOKED_BEEF);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for supply button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.supply"), NamedTextColor.GREEN));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.supply.desc"), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDisbandButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for disband button");
        }

        meta.displayName(Component.text(messages.format("army.gui.button.disband"), NamedTextColor.RED));
        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format("army.gui.button.disband.warning"), NamedTextColor.DARK_RED)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStateButton(ArmyState state) {
        Material material = switch (state) {
            case STATIONARY -> Material.WHITE_WOOL;
            case MARCHING -> Material.LEATHER_BOOTS;
            case DEFENDING -> Material.IRON_CHESTPLATE;
            default -> Material.GRAY_WOOL;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for state button: " + state);
        }

        meta.displayName(Component.text(
            messages.format(armyStateKey(state)),
            NamedTextColor.YELLOW
        ));

        meta.lore(List.of(
            Component.text(""),
            Component.text(messages.format(armyStateDescriptionKey(state)), NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private String armyStateKey(ArmyState state) {
        return "army.gui.state." + state.key();
    }

    private String armyStateDescriptionKey(ArmyState state) {
        return armyStateKey(state) + ".desc";
    }

    private void addNavigationButtons() {
        // 返回按钮
        if (menuType == MenuType.DETAIL) {
            inventory.setItem(45, createBackButton());
        }

        // 关闭按钮
        inventory.setItem(49, createCloseButton());

        // 帮助按钮
        inventory.setItem(53, createHelpButton());
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for back button");
        }
        meta.displayName(Component.text(messages.format("army.gui.button.back"), NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for close button");
        }
        meta.displayName(Component.text(messages.format("army.gui.button.close"), NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for help button");
        }
        meta.displayName(Component.text(messages.format("army.gui.button.help"), NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    private Material getArmyMaterial(ArmyType type) {
        return switch (type) {
            case INFANTRY -> Material.IRON_SWORD;
            case CAVALRY -> Material.SADDLE;
            case ARCHER -> Material.BOW;
            case SIEGE -> Material.TNT;
            case DEFENSIVE -> Material.SHIELD;
        };
    }

    private NamedTextColor getHealthColor(double health) {
        if (health >= 80) return NamedTextColor.GREEN;
        if (health >= 50) return NamedTextColor.YELLOW;
        if (health >= 20) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    private NamedTextColor getMoraleColor(double morale) {
        if (morale >= 80) return NamedTextColor.GREEN;
        if (morale >= 50) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getSupplyColor(int supply) {
        if (supply >= 60) return NamedTextColor.GREEN;
        if (supply >= 30) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    /**
     * 获取生命值条形图
     */
    private String getHealthBar(double health) {
        int bars = 10;
        int filled = (int) (health / 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                bar.append("§a|");
            } else {
                bar.append("§7|");
            }
        }
        return bar.toString();
    }

    /**
     * 获取状态显示名称
     */
    private String getStateDisplayName(ArmyState state) {
        return switch (state) {
            case STATIONARY -> "驻扎";
            case MARCHING -> "行军";
            case DEFENDING -> "防御";
            case SIEGING -> "攻城";
            case ATTACKING -> "进攻中";
        };
    }

    /**
     * 获取克制信息
     */
    private String getCounterInfo(ArmyType type) {
        return switch (type) {
            case INFANTRY -> "克制: 弓兵 | 被克制: 骑兵";
            case CAVALRY -> "克制: 步兵 | 被克制: 防御";
            case ARCHER -> "克制: 防御 | 被克制: 步兵";
            case SIEGE -> "克制: 城市建筑 | 机动性低";
            case DEFENSIVE -> "克制: 弓兵/骑兵 | 机动性低";
        };
    }

    /**
     * 格式化数字（添加千分位）
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    public enum MenuType {
        MAIN,
        DETAIL
    }
}
