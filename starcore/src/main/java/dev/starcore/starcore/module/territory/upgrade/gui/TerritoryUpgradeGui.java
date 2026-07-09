package dev.starcore.starcore.module.territory.upgrade.gui;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.TerritoryUpgradeService;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeBenefit;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeCheckResult;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeTierDefinition;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeCheckError;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Territory Upgrade GUI Menu
 * 领地升级图形界面菜单
 */
public class TerritoryUpgradeGui {

    public static final String MENU_TITLE = "§8§l领地升级系统";
    public static final int MENU_SIZE = 36;

    private final TerritoryUpgradeService upgradeService;
    private final NationService nationService;
    private final Player player;

    public TerritoryUpgradeGui(TerritoryUpgradeService upgradeService, NationService nationService, Player player) {
        this.upgradeService = upgradeService;
        this.nationService = nationService;
        this.player = player;
    }

    /**
     * Open the main upgrade menu.
     */
    public void open() {
        NationId nationId = getPlayerNationId();
        if (nationId == null) {
            openNoNationGui();
            return;
        }

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE));

        // 填充边框
        fillBorder(inv);

        // 设置状态信息
        inv.setItem(4, createStatusItem(nationId));

        // 显示所有升级路径
        int slot = 10;
        int maxSlot = 16;
        int row = 0;

        for (String pathId : upgradeService.getAvailablePaths()) {
            if (slot > maxSlot) {
                if (row == 0) {
                    slot = 19;
                    maxSlot = 25;
                    row = 1;
                } else {
                    break;
                }
            }

            inv.setItem(slot, createPathItem(nationId, pathId));
            slot++;
        }

        // 设置导航按钮
        inv.setItem(30, createButton(Material.ARROW, "§e上一页", "当前页面: 1/1"));
        inv.setItem(32, createButton(Material.BOOK, "§e刷新", "点击刷新状态"));

        // 设置关闭按钮
        inv.setItem(35, createButton(Material.BARRIER, "§c关闭", "点击关闭菜单"));

        player.openInventory(inv);
    }

    /**
     * Open a detailed view for a specific upgrade path.
     */
    public void openPathDetail(String pathId) {
        NationId nationId = getPlayerNationId();
        if (nationId == null) {
            openNoNationGui();
            return;
        }

        Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);
        if (pathOpt.isEmpty()) {
            player.sendMessage("§c无效的升级路径");
            return;
        }

        UpgradeTierDefinition path = pathOpt.get();
        int currentLevel = upgradeService.getCurrentLevel(nationId, pathId);

        Inventory inv = Bukkit.createInventory(null, 45,
            Component.text("§8§l" + path.pathName()));

        // 填充边框
        fillBorder(inv);

        // 路径信息
        inv.setItem(4, createPathHeaderItem(path, currentLevel));

        // 经验信息
        int totalExp = upgradeService.getTotalExp(nationId);
        int spentExp = upgradeService.getExpSpent(nationId);
        int availableExp = totalExp - spentExp;
        inv.setItem(22, createExpInfoItem(totalExp, availableExp, spentExp));

        // 升级按钮
        UpgradeCheckResult checkResult = upgradeService.canUpgrade(nationId, pathId);
        if (checkResult.isSuccess()) {
            int nextLevel = currentLevel + 1;
            int requiredExp = upgradeService.getExpRequiredForNextLevel(nationId, pathId);
            inv.setItem(40, createUpgradeButton(pathId, requiredExp));
        } else {
            inv.setItem(40, createCannotUpgradeButton(checkResult));
        }

        // 显示各等级
        int slot = 10;
        for (var level : path.tiers()) {
            if (slot == 17) {
                slot = 19;
            }
            if (slot > 25) {
                break;
            }

            boolean achieved = level.level() <= currentLevel;
            boolean isNext = level.level() == currentLevel + 1;
            inv.setItem(slot, createLevelItem(path, level, achieved, isNext));
            slot++;
        }

        // 返回按钮
        inv.setItem(36, createButton(Material.ARROW, "§e返回", "返回上一级"));

        player.openInventory(inv);
    }

    /**
     * Open the benefits view for a path.
     */
    public void openBenefitsView(String pathId) {
        NationId nationId = getPlayerNationId();
        if (nationId == null) {
            return;
        }

        Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);
        if (pathOpt.isEmpty()) {
            return;
        }

        UpgradeTierDefinition path = pathOpt.get();
        UpgradeBenefit benefits = upgradeService.getCumulativeBenefits(nationId, pathId);

        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text("§8§l" + path.pathName() + " §7- 收益"));

        fillBorder(inv);

        // 显示累积收益
        int slot = 10;
        for (String benefitKey : benefits.benefits().keySet()) {
            if (slot > 16) {
                break;
            }

            Object value = benefits.benefits().get(benefitKey);
            Material material = getBenefitMaterial(benefitKey);
            String displayName = formatBenefitName(benefitKey);
            String displayValue = formatBenefitValue(benefitKey, value);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7当前值: §e" + displayValue);
            lore.add("");
            lore.add("§a✓ 已激活");

            inv.setItem(slot, createItem(material, displayName, lore));
            slot++;
        }

        // 返回按钮
        inv.setItem(22, createButton(Material.ARROW, "§e返回", "返回路径详情"));

        player.openInventory(inv);
    }

    private NationId getPlayerNationId() {
        if (nationService == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId())
            .map(Nation::id)
            .orElse(null);
    }

    private void openNoNationGui() {
        Inventory inv = Bukkit.createInventory(null, 9,
            Component.text("§8§l领地升级系统"));

        ItemStack item = createItem(Material.BARRIER, "§c未加入国家",
            List.of("", "§7你必须加入一个国家", "§7才能使用升级系统", ""));
        inv.setItem(4, item);

        player.openInventory(inv);
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }

        // 第一行和最后一行
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
            inv.setItem(MENU_SIZE - 9 + i, filler);
        }

        // 两边的
        for (int i = 1; i < MENU_SIZE / 9 - 1; i++) {
            inv.setItem(i * 9, filler);
            inv.setItem(i * 9 + 8, filler);
        }
    }

    private ItemStack createStatusItem(NationId nationId) {
        int totalExp = upgradeService.getTotalExp(nationId);
        int spentExp = upgradeService.getExpSpent(nationId);
        int availableExp = totalExp - spentExp;
        int totalLevel = 0;

        for (String pathId : upgradeService.getAvailablePaths()) {
            totalLevel += upgradeService.getCurrentLevel(nationId, pathId);
        }

        return createItem(Material.NETHER_STAR,
            "§6§l国家升级状态",
            List.of(
                "",
                "§7总经验: §e" + totalExp,
                "§7可用: §a" + availableExp,
                "§7已消耗: §c" + spentExp,
                "",
                "§7总等级: §b" + totalLevel,
                "",
                "§e点击查看详细信息"
            ));
    }

    private ItemStack createPathItem(NationId nationId, String pathId) {
        Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);
        if (pathOpt.isEmpty()) {
            return createItem(Material.BARRIER, "§c无效路径", List.of());
        }

        UpgradeTierDefinition path = pathOpt.get();
        int currentLevel = upgradeService.getCurrentLevel(nationId, pathId);
        int maxLevel = path.maxLevel();
        int progress = upgradeService.getProgressToNextLevel(nationId, pathId);
        boolean isMaxed = currentLevel >= maxLevel;

        Material material;
        if (isMaxed) {
            material = Material.GOLD_INGOT;
        } else if (currentLevel > 0) {
            material = Material.EXPERIENCE_BOTTLE;
        } else {
            material = Material.BOOK;
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7当前等级: §e" + currentLevel + "§7/§e" + maxLevel);
        lore.add(createProgressBar(currentLevel, maxLevel));
        lore.add("");

        if (isMaxed) {
            lore.add("§a✓ 已满级");
        } else if (currentLevel > 0) {
            lore.add("§7进度: §e" + progress + "%");
        } else {
            lore.add("§7未开始升级");
        }

        lore.add("");
        lore.add("§e点击查看详情");

        String colorCode = path.color().replace("&", "§");
        return createItem(material,
            colorCode + "§l" + path.pathName(),
            lore);
    }

    private ItemStack createPathHeaderItem(UpgradeTierDefinition path, int currentLevel) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + path.pathDescription());
        lore.add("");
        lore.add("§7当前等级: §e" + currentLevel + "§7/§e" + path.maxLevel());
        lore.add(createProgressBar(currentLevel, path.maxLevel()));
        lore.add("");

        if (currentLevel >= path.maxLevel()) {
            lore.add("§a✓ 已达最高等级");
        }

        String colorCode = path.color().replace("&", "§");
        return createItem(Material.MAP,
            colorCode + "§l" + path.pathName(),
            lore);
    }

    private ItemStack createExpInfoItem(int totalExp, int availableExp, int spentExp) {
        return createItem(Material.EXPERIENCE_BOTTLE,
            "§b§l经验值信息",
            List.of(
                "",
                "§7总经验: §e" + formatNumber(totalExp),
                "§7可用: §a" + formatNumber(availableExp),
                "§7已消耗: §c" + formatNumber(spentExp),
                "",
                "§7经验可用于升级国家",
                "§7不同升级路径"
            ));
    }

    private ItemStack createLevelItem(UpgradeTierDefinition path,
                                      dev.starcore.starcore.module.territory.upgrade.model.TerritoryUpgradeLevel level,
                                      boolean achieved, boolean isNext) {
        Material material;
        String namePrefix;

        if (achieved) {
            material = Material.LIME_STAINED_GLASS_PANE;
            namePrefix = "§a";
        } else if (isNext) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            namePrefix = "§e";
        } else {
            material = Material.GRAY_STAINED_GLASS_PANE;
            namePrefix = "§7";
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(namePrefix + "需要经验: §e" + formatNumber(level.expRequired()));
        lore.add("");

        if (!level.description().isEmpty()) {
            lore.add("§7" + level.description());
            lore.add("");
        }

        // 显示解锁的收益
        if (!level.benefits().isEmpty()) {
            lore.add("§7解锁收益:");
            for (String key : level.benefits().keySet()) {
                Object value = level.benefits().get(key);
                lore.add("  §7- " + formatBenefitName(key) + ": " + formatBenefitValue(key, value));
            }
            lore.add("");
        }

        if (achieved) {
            lore.add("§a✓ 已达成");
        } else if (isNext) {
            lore.add("§e← 下一等级");
        } else {
            lore.add("§7未解锁");
        }

        return createItem(material,
            namePrefix + "§lLv." + level.level() + " " + level.name(),
            lore);
    }

    private ItemStack createUpgradeButton(String pathId, int requiredExp) {
        return createItem(Material.EMERALD_BLOCK,
            "§a§l开始升级",
            List.of(
                "",
                "§7升级所需经验: §e" + formatNumber(requiredExp),
                "",
                "§a点击开始升级",
                ""
            ));
    }

    private ItemStack createCannotUpgradeButton(UpgradeCheckResult result) {
        String reason = result.errorMessage() != null ? result.errorMessage() :
            (result.error() != null ? result.error().getDescription() : "未知原因");

        return createItem(Material.REDSTONE_BLOCK,
            "§c§l无法升级",
            List.of(
                "",
                "§7原因: " + reason,
                "",
                "§7请先满足升级条件"
            ));
    }

    private ItemStack createButton(Material material, String name, String... lore) {
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }
        return createItem(material, name, loreList);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(name));
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(net.kyori.adventure.text.Component.text(line));
                }
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String createProgressBar(int current, int max) {
        StringBuilder bar = new StringBuilder("§7[");
        int totalBars = 10;
        int filledBars = (current * totalBars) / Math.max(1, max);

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("§a■");
            } else {
                bar.append("§7■");
            }
        }
        bar.append("§7]");

        return bar.toString();
    }

    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    private Material getBenefitMaterial(String benefitKey) {
        if (benefitKey.contains("claim")) {
            return Material.MAP;
        } else if (benefitKey.contains("tax")) {
            return Material.GOLD_INGOT;
        } else if (benefitKey.contains("resource")) {
            return Material.DIAMOND;
        } else if (benefitKey.contains("defense")) {
            return Material.SHIELD;
        } else if (benefitKey.contains("army")) {
            return Material.IRON_SWORD;
        } else if (benefitKey.contains("trade")) {
            return Material.EMERALD;
        } else if (benefitKey.contains("speed")) {
            return Material.CLOCK;
        }
        return Material.PAPER;
    }

    private String formatBenefitName(String key) {
        return key.replace("_", " ")
            .substring(0, 1).toUpperCase()
            + key.replace("_", " ").substring(1);
    }

    private String formatBenefitValue(String key, Object value) {
        if (value instanceof Number) {
            double num = ((Number) value).doubleValue();
            if (key.contains("modifier") || key.contains("rate") ||
                key.contains("bonus") || key.contains("speed")) {
                return String.format("%.0f%%", (num - 1) * 100);
            }
            return String.format("%.1f", num);
        }
        return value.toString();
    }
}
