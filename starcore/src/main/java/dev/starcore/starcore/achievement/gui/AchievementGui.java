package dev.starcore.starcore.achievement.gui;

import dev.starcore.starcore.achievement.Achievement;
import dev.starcore.starcore.achievement.AchievementCategory;
import dev.starcore.starcore.achievement.AchievementModule;
import dev.starcore.starcore.achievement.AchievementProgress;
import dev.starcore.starcore.achievement.AchievementService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 成就GUI界面
 * 提供分类浏览和成就详情查看
 */
public class AchievementGui {

    private final Plugin plugin;
    private final AchievementModule module;

    // GUI 标题
    private static final Component MAIN_TITLE = Component.text("成就", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component CATEGORY_TITLE = Component.text("成就 - ", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component DETAIL_TITLE = Component.text("成就详情", NamedTextColor.GOLD, TextDecoration.BOLD);

    // 每页显示的成就数量
    private static final int ACHIEVEMENTS_PER_PAGE = 45; // 5x9 的主要区域
    private static final int CATEGORIES_PER_PAGE = 36;   // 分类页面大小

    // 当前打开的菜单类型
    private final Map<UUID, MenuState> playerMenuState = new java.util.concurrent.ConcurrentHashMap<>();

    public AchievementGui(Plugin plugin, AchievementModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /**
     * 菜单状态
     */
    private enum MenuType {
        MAIN,
        CATEGORY,
        DETAIL
    }

    private record MenuState(UUID playerId, MenuType type, int page, AchievementCategory category, NamespacedKey detailKey) {
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        playerMenuState.put(player.getUniqueId(), new MenuState(player.getUniqueId(), MenuType.MAIN, 0, null, null));

        Collection<Achievement> allAchievements = module.getAllAchievements();
        int completed = module.getPlayerProgress(player.getUniqueId());
        int total = allAchievements.size();
        double percentage = total > 0 ? (completed * 100.0) / total : 0;

        // 构建标题
        String title = Component.text()
            .append(MAIN_TITLE)
            .append(Component.text(" "))
            .append(Component.text("[" + completed + "/" + total + "] ", NamedTextColor.GRAY))
            .append(Component.text(String.format("%.1f%%", percentage), NamedTextColor.WHITE))
            .build()
            .toString();

        Inventory gui = Bukkit.createInventory(new AchievementHolder(), 54, title);

        // 顶部显示玩家进度
        gui.setItem(0, createProgressItem(player, completed, total));

        // 显示分类按钮
        int slot = 9;
        AchievementCategory[] categories = AchievementCategory.values();
        for (int i = 0; i < Math.min(categories.length, 8); i++) {
            AchievementCategory cat = categories[i];
            int categoryCompleted = countCategoryAchievements(player, cat);
            int categoryTotal = module.getAchievementsByCategory(cat).size();
            gui.setItem(slot++, createCategoryButton(cat, categoryCompleted, categoryTotal));
        }

        // 填充剩余槽位和底部导航
        fillNavigation(gui, player, 0);

        player.openInventory(gui);
    }

    /**
     * 打开分类菜单
     */
    public void openCategoryMenu(Player player, AchievementCategory category) {
        playerMenuState.put(player.getUniqueId(), new MenuState(player.getUniqueId(), MenuType.CATEGORY, 0, category, null));

        String title = Component.text()
            .append(CATEGORY_TITLE)
            .append(category.getColoredName())
            .build()
            .toString();

        Inventory gui = Bukkit.createInventory(new AchievementHolder(), 54, title);

        // 获取该分类的成就
        List<Achievement> achievements = module.getAchievementsByCategory(category).stream()
            .sorted(Comparator.comparing(a -> a.getKey().getKey()))
            .toList();

        // 显示成就列表
        int slot = 0;
        for (int i = 0; i < Math.min(achievements.size(), ACHIEVEMENTS_PER_PAGE); i++) {
            Achievement achievement = achievements.get(i);
            gui.setItem(slot++, createAchievementItem(player, achievement));
        }

        // 填充导航
        fillCategoryNavigation(gui, player, category, 0, achievements.size());

        player.openInventory(gui);
    }

    /**
     * 打开成就详情
     */
    public void openDetailMenu(Player player, NamespacedKey key) {
        Achievement achievement = module.getAchievement(key).orElse(null);
        if (achievement == null) {
            return;
        }

        playerMenuState.put(player.getUniqueId(), new MenuState(player.getUniqueId(), MenuType.DETAIL, 0, null, key));

        String title = Component.text()
            .append(DETAIL_TITLE)
            .append(Component.text(" - "))
            .append(achievement.getTitle())
            .build()
            .toString();

        Inventory gui = Bukkit.createInventory(new AchievementHolder(), 36, title);

        // 成就图标
        gui.setItem(4, createDetailIcon(achievement));

        // 成就标题和描述
        gui.setItem(13, createDetailInfo(achievement));

        // 奖励物品
        gui.setItem(22, createRewardsItem(achievement));

        // 状态信息
        gui.setItem(31, createStatusItem(player, achievement));

        // 返回按钮
        gui.setItem(49, createBackButton());

        player.openInventory(gui);
    }

    /**
     * 创建进度显示物品
     */
    private ItemStack createProgressItem(Player player, int completed, int total) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        Component name = Component.text("成就进度", NamedTextColor.GOLD, TextDecoration.BOLD);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("已完成的成就", NamedTextColor.GRAY));
        lore.add(Component.text(completed + " / " + total, NamedTextColor.GREEN, TextDecoration.BOLD));
        lore.add(Component.empty());

        double percentage = total > 0 ? (completed * 100.0) / total : 0;
        lore.add(createProgressBar(percentage));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建进度条
     */
    private Component createProgressBar(double percentage) {
        int filled = (int) (percentage / 5); // 20个字符
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                bar.append("§a|");
            } else {
                bar.append("§7|");
            }
        }

        return Component.text(bar + " " + String.format("%.1f%%", percentage), NamedTextColor.WHITE);
    }

    /**
     * 创建分类按钮
     */
    private ItemStack createCategoryButton(AchievementCategory category, int completed, int total) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        Component name = Component.text(category.getDisplayName(), category.getColor(), TextDecoration.BOLD);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("分类: ", NamedTextColor.GRAY).append(category.getColoredName()));
        lore.add(category.getDescription());
        lore.add(Component.empty());
        lore.add(Component.text("进度: ", NamedTextColor.GRAY)
            .append(Component.text(completed + "/" + total, NamedTextColor.WHITE)));
        lore.add(Component.text("点击查看", NamedTextColor.YELLOW));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建成就物品
     */
    private ItemStack createAchievementItem(Player player, Achievement achievement) {
        boolean completed = module.hasAchievement(player.getUniqueId(), achievement.getKey());

        // 根据完成状态选择不同的边框材质
        Material baseMaterial = achievement.getIcon();
        Material frameMaterial = completed ? Material.LIGHT_GRAY_CONCRETE : Material.BLACK_CONCRETE;

        ItemStack item = new ItemStack(completed ? baseMaterial : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        // 名称
        Component name = Component.text()
            .append(achievement.getTitle())
            .color(completed ? NamedTextColor.GREEN : NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .build();
        meta.displayName(name);

        // 描述
        List<Component> lore = new ArrayList<>();
        if (!completed) {
            lore.add(Component.text("[ 未完成 ]", NamedTextColor.RED));
            if (achievement.isHidden()) {
                lore.add(Component.text("（隐藏成就）", NamedTextColor.DARK_GRAY));
            } else {
                lore.add(achievement.getDescription().color(NamedTextColor.GRAY));
            }
        } else {
            lore.add(Component.text("[ 已完成 ]", NamedTextColor.GREEN));
            lore.add(achievement.getDescription().color(NamedTextColor.GRAY));
        }

        // 显示奖励预览
        if (!achievement.getRewards().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("奖励:", NamedTextColor.GOLD));
            for (String reward : achievement.getRewards().stream().limit(3).toList()) {
                lore.add(Component.text(" - " + reward, NamedTextColor.YELLOW));
            }
        }

        // 显示经验奖励
        if (achievement.getExperience() > 0) {
            lore.add(Component.text(" +" + achievement.getExperience() + " 经验", NamedTextColor.AQUA));
        }

        // 提示点击查看详情
        if (!completed && !achievement.isHidden()) {
            lore.add(Component.empty());
            lore.add(Component.text("点击查看详情", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建详情图标
     */
    private ItemStack createDetailIcon(Achievement achievement) {
        ItemStack item = new ItemStack(achievement.getIcon());
        ItemMeta meta = item.getItemMeta();

        Component name = Component.text()
            .append(Component.text("[ "))
            .append(achievement.getTitle())
            .append(Component.text(" ]"))
            .color(getFrameColor(achievement.getFrameType()))
            .build();
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("框架类型: " + achievement.getFrameType().getName(), NamedTextColor.GRAY));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建详情信息物品
     */
    private ItemStack createDetailInfo(Achievement achievement) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("成就信息", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("标题", NamedTextColor.YELLOW));
        lore.add(achievement.getTitle());
        lore.add(Component.empty());
        lore.add(Component.text("描述", NamedTextColor.YELLOW));
        lore.add(achievement.getDescription());
        lore.add(Component.empty());
        lore.add(Component.text("类型", NamedTextColor.YELLOW));
        lore.add(Component.text(achievement.getFrameType().getName() + " ("
            + achievement.getFrameType().getColor() + ")"));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建奖励物品
     */
    private ItemStack createRewardsItem(Achievement achievement) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("奖励", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();

        // 经验
        if (achievement.getExperience() > 0) {
            lore.add(Component.text("经验: +" + achievement.getExperience(), NamedTextColor.AQUA));
        }

        // 其他奖励
        if (achievement.getRewards().isEmpty()) {
            lore.add(Component.text("无额外奖励", NamedTextColor.GRAY));
        } else {
            for (String reward : achievement.getRewards()) {
                lore.add(Component.text("- " + reward, NamedTextColor.GREEN));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建状态物品
     */
    private ItemStack createStatusItem(Player player, Achievement achievement) {
        boolean completed = module.hasAchievement(player.getUniqueId(), achievement.getKey());

        Material material = completed ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (completed) {
            meta.displayName(Component.text("已达成", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            meta.displayName(Component.text("未达成", NamedTextColor.RED, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("提示", NamedTextColor.YELLOW));
            lore.add(Component.text("完成此成就的条件来解锁", NamedTextColor.GRAY));
            meta.lore(lore);
        }

        item.setItemMeta(meta);

        return item;
    }

    /**
     * 创建返回按钮
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("返回", NamedTextColor.YELLOW, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 填充主菜单导航
     */
    private void fillNavigation(Inventory gui, Player player, int page) {
        // 关闭按钮
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.displayName(Component.text("关闭", NamedTextColor.RED));
        closeBtn.setItemMeta(closeMeta);
        gui.setItem(49, closeBtn);

        // 底部边框
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);

        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }
    }

    /**
     * 填充分类菜单导航
     */
    private void fillCategoryNavigation(Inventory gui, Player player, AchievementCategory category, int page, int total) {
        // 返回主菜单按钮
        gui.setItem(45, createBackButton());

        // 关闭按钮
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        closeMeta.displayName(Component.text("关闭", NamedTextColor.RED));
        closeBtn.setItemMeta(closeMeta);
        gui.setItem(53, closeBtn);

        // 底部边框
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);

        for (int i = 46; i < 53; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }
    }

    /**
     * 获取框架颜色
     */
    private NamedTextColor getFrameColor(Achievement.FrameType frameType) {
        return switch (frameType) {
            case TASK -> NamedTextColor.GREEN;
            case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
            case GOAL -> NamedTextColor.YELLOW;
        };
    }

    /**
     * 统计分类成就完成数
     */
    private int countCategoryAchievements(Player player, AchievementCategory category) {
        return (int) module.getAchievementsByCategory(category).stream()
            .filter(a -> module.hasAchievement(player.getUniqueId(), a.getKey()))
            .count();
    }

    /**
     * 处理点击事件
     */
    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        MenuState state = playerMenuState.get(player.getUniqueId());
        if (state == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        switch (state.type()) {
            case MAIN -> handleMainClick(player, slot, clickedItem);
            case CATEGORY -> handleCategoryClick(player, slot, clickedItem, state.category());
            case DETAIL -> handleDetailClick(player, slot, clickedItem);
        }
    }

    /**
     * 处理主菜单点击
     */
    private void handleMainClick(Player player, int slot, ItemStack item) {
        // 检查是否是分类按钮
        if (slot >= 9 && slot <= 16) {
            int categoryIndex = slot - 9;
            AchievementCategory[] categories = AchievementCategory.values();
            if (categoryIndex < categories.length) {
                openCategoryMenu(player, categories[categoryIndex]);
                return;
            }
        }

        // 关闭按钮
        if (slot == 49 && item.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }

    /**
     * 处理分类菜单点击
     */
    private void handleCategoryClick(Player player, int slot, ItemStack item, AchievementCategory category) {
        // 检查是否是成就物品
        if (slot >= 0 && slot < ACHIEVEMENTS_PER_PAGE) {
            Achievement achievement = getAchievementAtSlot(player, category, slot);
            if (achievement != null && !achievement.isHidden()) {
                openDetailMenu(player, achievement.getKey());
                return;
            }
        }

        // 返回按钮
        if (slot == 45 && item.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }

        // 关闭按钮
        if (slot == 53 && item.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }

    /**
     * 处理详情菜单点击
     */
    private void handleDetailClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        if (slot == 49 && item.getType() == Material.ARROW) {
            player.closeInventory();
            // 返回之前的分类或主菜单
            MenuState state = playerMenuState.get(player.getUniqueId());
            if (state != null && state.detailKey() != null) {
                Achievement achievement = module.getAchievement(state.detailKey()).orElse(null);
                if (achievement != null) {
                    openCategoryMenu(player, module.getAchievementCategory(state.detailKey()));
                }
            }
        }

        // 关闭按钮
        if (slot == 49 && item.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }

    /**
     * 获取指定槽位的成就
     */
    private Achievement getAchievementAtSlot(Player player, AchievementCategory category, int slot) {
        List<Achievement> achievements = module.getAchievementsByCategory(category).stream()
            .sorted(Comparator.comparing(a -> a.getKey().getKey()))
            .toList();

        if (slot < achievements.size()) {
            return achievements.get(slot);
        }
        return null;
    }

    /**
     * GUI持有者标记
     */
    private static class AchievementHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // audit H-001: 提供状态清理方法供 Listener 调用
    public void clearPlayerState(Player player) {
        playerMenuState.remove(player.getUniqueId());
    }
}
