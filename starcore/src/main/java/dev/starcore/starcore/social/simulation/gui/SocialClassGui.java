package dev.starcore.starcore.social.simulation.gui;

import dev.starcore.starcore.social.simulation.SocialClassService;
import dev.starcore.starcore.social.simulation.SocialClassService.SocialClass;
import dev.starcore.starcore.social.simulation.SocialClassService.ClassHistory;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 社会阶层GUI界面 - TriumphGUI实现
 *
 * 功能:
 * - 显示玩家当前阶层和点数进度
 * - 显示所有阶层等级（平民到神级）
 * - 显示当前阶层的特权列表
 * - 显示下一个阶层的晋升条件
 * - 晋升动画效果
 * - 阶层特权预览
 */
public class SocialClassGui {

    private static final int GUI_SIZE = 54; // 6行
    private static final Logger logger = Logger.getLogger(SocialClassGui.class.getName());

    private final Player player;
    private final SocialClassService classService;

    public SocialClassGui(Player player, SocialClassService classService) {
        this.player = player;
        this.classService = classService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu() {
        Gui gui = Gui.gui()
            .title(Component.text("§6§l⚜ 社会阶层 §⚜", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 填充边框
        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);

        // 顶部标题区域
        buildHeader(gui);

        // 当前阶层信息
        buildCurrentClassInfo(gui);

        // 进度条
        buildProgressBar(gui);

        // 阶层列表（中间区域）
        buildClassList(gui);

        // 底部功能按钮
        buildFooter(gui);

        gui.open(player);
    }

    /**
     * 构建顶部标题
     */
    private void buildHeader(Gui gui) {
        SocialClass currentClass = classService.getClass(player.getUniqueId());
        int points = classService.getClassPoints(player.getUniqueId());

        gui.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            Component.text("§6§l⚜ 社会阶层系统 ⚜", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                Component.text(""),
                Component.text("§7玩家: §f" + player.getName()),
                Component.text("§7当前阶层: " + currentClass.color() + currentClass.displayName()),
                Component.text("§7阶层点数: §e" + points),
                Component.text(""),
                Component.text("§7在社会中争取更高的地位", NamedTextColor.GRAY),
                Component.text("§7解锁更多特权与荣耀", NamedTextColor.GRAY)
            ),
            event -> event.setCancelled(true),
            true
        ));
    }

    /**
     * 构建当前阶层信息面板
     */
    private void buildCurrentClassInfo(Gui gui) {
        SocialClass currentClass = classService.getClass(player.getUniqueId());
        int points = classService.getClassPoints(player.getUniqueId());
        Set<String> privileges = classService.getPrivileges(player.getUniqueId());

        // 当前阶层图标
        Material classMaterial = getClassMaterial(currentClass);
        gui.setItem(20, createGuiItem(
            classMaterial,
            Component.text(currentClass.color() + "§l" + currentClass.displayName(), NamedTextColor.WHITE),
            buildCurrentClassLore(currentClass, points, privileges),
            event -> {
                event.setCancelled(true);
                // 打开特权详情
                openPrivilegesMenu(currentClass);
            },
            true
        ));

        // 当前点数/下一级需求
        gui.setItem(22, createGuiItem(
            Material.EXPERIENCE_BOTTLE,
            Component.text("§e§l阶层进度", NamedTextColor.YELLOW, TextDecoration.BOLD),
            buildProgressLore(currentClass, points),
            event -> event.setCancelled(true),
            false
        ));

        // 晋升预览
        gui.setItem(24, createGuiItem(
            Material.GOLD_INGOT,
            Component.text("§6§l晋升预览", NamedTextColor.GOLD, TextDecoration.BOLD),
            buildPromotionPreviewLore(currentClass),
            event -> {
                event.setCancelled(true);
                openPromotionPreview();
            },
            true
        ));
    }

    /**
     * 构建进度条
     */
    private void buildProgressBar(Gui gui) {
        SocialClass currentClass = classService.getClass(player.getUniqueId());
        int points = classService.getClassPoints(player.getUniqueId());

        // 查找当前进度
        int currentLevelMin = currentClass.requiredPoints();
        int currentLevelMax = getNextLevelPoints(currentClass);
        int progressPoints = points - currentLevelMin;
        int neededPoints = currentLevelMax - currentLevelMin;

        // 进度条槽位 19, 28, 37 (第二行的中间区域)
        int[] progressSlots = {19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < progressSlots.length; i++) {
            int slot = progressSlots[i];
            boolean filled = false;

            if (neededPoints > 0) {
                double ratio = (double) progressPoints / neededPoints;
                filled = (i + 1) <= ratio * progressSlots.length;
            } else {
                filled = true; // 已经是最高级
            }

            Material mat = filled ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
            int finalI = i;
            gui.setItem(slot, createGuiItem(
                mat,
                Component.text("§" + (filled ? "a" : "7") + "▮"),
                List.of(
                    Component.text("§7进度: " + progressPoints + "/" + neededPoints, NamedTextColor.GRAY)
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 进度文字
        gui.setItem(27, createGuiItem(
            Material.PAPER,
            Component.text("§7进度信息", NamedTextColor.GRAY),
            List.of(
                Component.text(""),
                Component.text("§7当前: §e" + points + " 点"),
                Component.text("§7目标: §e" + currentLevelMax + " 点"),
                Component.text("§7还需: §e" + Math.max(0, currentLevelMax - points) + " 点"),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        ));
    }

    /**
     * 构建阶层列表
     */
    private void buildClassList(Gui gui) {
        // 槽位 30-35 (跳过中间三个已占用)
        int[] classSlots = {30, 31, 32, 33, 34, 35};

        SocialClass[] classes = SocialClass.values();
        SocialClass currentClass = classService.getClass(player.getUniqueId());

        for (int i = 0; i < Math.min(6, classes.length); i++) {
            SocialClass sc = classes[i];
            int slot = classSlots[i];

            Material mat = getClassMaterial(sc);
            boolean isCurrent = sc == currentClass;
            boolean isUnlocked = classService.getClassPoints(player.getUniqueId()) >= sc.requiredPoints();

            Component name = Component.text(
                (isCurrent ? "§a§l▶ " : (isUnlocked ? "§e" : "§8")) + sc.displayName(),
                isCurrent ? NamedTextColor.GREEN : (isUnlocked ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
            );

            gui.setItem(slot, createGuiItem(
                mat,
                name,
                buildClassItemLore(sc, isCurrent, isUnlocked),
                event -> {
                    event.setCancelled(true);
                    // 点击查看该阶层详情
                    openClassDetailMenu(sc);
                },
                isCurrent
            ));
        }
    }

    /**
     * 构建底部功能按钮
     */
    private void buildFooter(Gui gui) {
        // 阶层特权预览
        gui.setItem(48, createGuiItem(
            Material.BOOK,
            Component.text("§b§l📖 特权总览", NamedTextColor.AQUA, TextDecoration.BOLD),
            List.of(
                Component.text(""),
                Component.text("§7查看所有阶层的特权"),
                Component.text(""),
                Component.text("§e点击查看", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                openPrivilegesOverview();
            },
            false
        ));

        // 晋升历史
        gui.setItem(49, createGuiItem(
            Material.WRITABLE_BOOK,
            Component.text("§6§l📜 历史记录", NamedTextColor.GOLD, TextDecoration.BOLD),
            buildHistoryLore(),
            event -> {
                event.setCancelled(true);
                openHistoryMenu();
            },
            false
        ));

        // 刷新按钮
        gui.setItem(50, createGuiItem(
            Material.COMPASS,
            Component.text("§e§l🔄 刷新", NamedTextColor.YELLOW, TextDecoration.BOLD),
            List.of(
                Component.text(""),
                Component.text("§7刷新当前阶层信息"),
                Component.text(""),
                Component.text("§e点击刷新", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                openMainMenu();
            },
            false
        ));

        // 关闭按钮
        gui.setItem(53, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 关闭", NamedTextColor.RED, TextDecoration.BOLD),
            List.of(
                Component.text(""),
                Component.text("§7关闭阶层菜单"),
                Component.text(""),
                Component.text("§e点击关闭", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                player.closeInventory();
            },
            false
        ));
    }

    /**
     * 打开特权详情菜单
     */
    private void openPrivilegesMenu(SocialClass socialClass) {
        Gui gui = Gui.gui()
            .title(Component.text("§b§l📖 " + socialClass.displayName() + " §7特权", NamedTextColor.AQUA))
            .rows(5)
            .disableAllInteractions()
            .create();

        fillBorder(gui, Material.CYAN_STAINED_GLASS_PANE);

        // 返回按钮
        gui.setItem(40, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu();
            },
            false
        ));

        // 特权列表
        Set<String> privileges = socialClass.privileges();
        int[] privilegeSlots = {10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25};

        int slotIndex = 0;
        for (String privilege : privileges) {
            if (slotIndex >= privilegeSlots.length) break;
            int slot = privilegeSlots[slotIndex];

            gui.setItem(slot, createPrivilegeItem(privilege));
            slotIndex++;
        }

        // 空槽位提示
        if (privileges.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§7无特权", NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7此阶层暂无特权"),
                    Component.text("§7晋升后解锁更多特权")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        gui.open(player);
    }

    /**
     * 打开特权总览菜单
     */
    private void openPrivilegesOverview() {
        Gui gui = Gui.gui()
            .title(Component.text("§6§l⚜ 特权总览 ⚜", NamedTextColor.GOLD))
            .rows(6)
            .disableAllInteractions()
            .create();

        fillBorder(gui, Material.ORANGE_STAINED_GLASS_PANE);

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu();
            },
            false
        ));

        // 阶层特权对比
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        SocialClass[] classes = SocialClass.values();

        for (int i = 0; i < Math.min(slots.length, classes.length); i++) {
            SocialClass sc = classes[i];
            int slot = slots[i];
            int playerPoints = classService.getClassPoints(player.getUniqueId());
            boolean unlocked = playerPoints >= sc.requiredPoints();
            boolean current = sc == classService.getClass(player.getUniqueId());

            gui.setItem(slot, createGuiItem(
                getClassMaterial(sc),
                Component.text((current ? "§a§l★ " : (unlocked ? "§e" : "§8")) + sc.displayName(),
                    current ? NamedTextColor.GREEN : (unlocked ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)),
                buildClassItemLore(sc, current, unlocked),
                event -> {
                    event.setCancelled(true);
                    openClassDetailMenu(sc);
                },
                current
            ));
        }

        gui.open(player);
    }

    /**
     * 打开阶层详情菜单
     */
    private void openClassDetailMenu(SocialClass socialClass) {
        Gui gui = Gui.gui()
            .title(Component.text(socialClass.color() + "§l" + socialClass.displayName() + " §7详情", NamedTextColor.WHITE))
            .rows(5)
            .disableAllInteractions()
            .create();

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        int playerPoints = classService.getClassPoints(player.getUniqueId());
        boolean isCurrent = socialClass == classService.getClass(player.getUniqueId());
        boolean isUnlocked = playerPoints >= socialClass.requiredPoints();
        boolean canPromote = isUnlocked && socialClass.ordinal() > classService.getClass(player.getUniqueId()).ordinal();

        // 顶部：阶层图标和信息
        gui.setItem(4, createGuiItem(
            getClassMaterial(socialClass),
            Component.text(socialClass.color() + "§l" + socialClass.displayName(), NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7阶层名称: " + socialClass.color() + socialClass.displayName()),
                Component.text("§7解锁条件: §e" + socialClass.requiredPoints() + " 点"),
                Component.text("§7当前状态: " + (isCurrent ? "§a当前" : (isUnlocked ? "§e已解锁" : "§c未解锁"))),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            isCurrent
        ));

        // 晋升条件（如果不是最高级）
        SocialClass nextClass = getNextClass(socialClass);
        if (nextClass != null) {
            gui.setItem(20, createGuiItem(
                Material.GOLD_INGOT,
                Component.text("§6§l晋升条件", NamedTextColor.GOLD),
                List.of(
                    Component.text(""),
                    Component.text("§7晋升到: " + nextClass.color() + nextClass.displayName()),
                    Component.text("§7所需点数: §e" + nextClass.requiredPoints() + " 点"),
                    Component.text("§7当前点数: §e" + playerPoints + " 点"),
                    Component.text("§7还需: §e" + Math.max(0, nextClass.requiredPoints() - playerPoints) + " 点"),
                    Component.text(""),
                    canPromote ? Component.text("§a✓ 可以晋升！", NamedTextColor.GREEN) : Component.text("§c✗ 暂不可晋升", NamedTextColor.RED)
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 特权列表
        gui.setItem(22, createGuiItem(
            Material.BOOK,
            Component.text("§b§l特权列表", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7此阶层拥有的特权:"),
                Component.text("")
            ).stream()
                .map(c -> c)
                .collect(java.util.stream.Collectors.toList()),
            event -> event.setCancelled(true),
            false
        ));

        // 添加特权物品
        Set<String> privileges = socialClass.privileges();
        int[] privSlots = {28, 29, 30, 31, 32, 33, 34};
        int idx = 0;
        for (String priv : privileges) {
            if (idx >= privSlots.length) break;
            gui.setItem(privSlots[idx], createPrivilegeItem(priv));
            idx++;
        }

        if (privileges.isEmpty()) {
            gui.setItem(31, createGuiItem(
                Material.BARRIER,
                Component.text("§7无特权", NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7此阶层暂无特权")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 返回按钮
        gui.setItem(40, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu();
            },
            false
        ));

        gui.open(player);
    }

    /**
     * 打开晋升预览
     */
    private void openPromotionPreview() {
        SocialClass currentClass = classService.getClass(player.getUniqueId());
        SocialClass nextClass = getNextClass(currentClass);

        if (nextClass == null) {
            player.sendMessage(Component.text("§c你已经达到最高阶层！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(Component.text("§6§l⬆ 晋升预览 ⬆", NamedTextColor.GOLD))
            .rows(5)
            .disableAllInteractions()
            .create();

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 当前阶层
        gui.setItem(20, createGuiItem(
            getClassMaterial(currentClass),
            Component.text(currentClass.color() + "§l" + currentClass.displayName(), NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7当前阶层", NamedTextColor.GRAY),
                Component.text(currentClass.color() + currentClass.displayName()),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        ));

        // 箭头指示
        gui.setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§a§l➡", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§e晋升", NamedTextColor.YELLOW)
            ),
            event -> event.setCancelled(true),
            true
        ));

        // 下一阶层
        gui.setItem(24, createGuiItem(
            getNextClassMaterial(nextClass),
            Component.text(nextClass.color() + "§l" + nextClass.displayName(), NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7下一阶层", NamedTextColor.GRAY),
                Component.text(nextClass.color() + nextClass.displayName()),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        ));

        // 晋升后新增特权
        Set<String> currentPrivs = currentClass.privileges();
        Set<String> nextPrivs = nextClass.privileges();
        Set<String> newPrivs = new HashSet<>(nextPrivs);
        newPrivs.removeAll(currentPrivs);

        gui.setItem(31, createGuiItem(
            Material.DIAMOND,
            Component.text("§b§l新增特权", NamedTextColor.AQUA),
            newPrivs.stream()
                .map(p -> Component.text("§7• §f" + formatPrivilegeName(p), NamedTextColor.GRAY))
                .collect(java.util.stream.Collectors.toList()),
            event -> event.setCancelled(true),
            false
        ));

        // 返回按钮
        gui.setItem(40, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu();
            },
            false
        ));

        gui.open(player);
    }

    /**
     * 打开历史记录菜单
     */
    private void openHistoryMenu() {
        Gui gui = Gui.gui()
            .title(Component.text("§6§l📜 阶层历史 📜", NamedTextColor.GOLD))
            .rows(5)
            .disableAllInteractions()
            .create();

        fillBorder(gui, Material.BROWN_STAINED_GLASS_PANE);

        // 返回按钮
        gui.setItem(40, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu();
            },
            false
        ));

        List<ClassHistory> history = classService.getHistory(player.getUniqueId());

        if (history.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§7暂无历史记录", NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7参与游戏活动获取点数"),
                    Component.text("§7记录将显示在这里")
                ),
                event -> event.setCancelled(true),
                false
            ));
        } else {
            // 显示最近10条记录
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30};
            int idx = 0;
            for (int i = history.size() - 1; i >= 0 && idx < slots.length; i--) {
                ClassHistory h = history.get(i);
                Date date = new Date(h.timestamp());

                gui.setItem(slots[idx], createGuiItem(
                    Material.PAPER,
                    Component.text("§e" + (h.points() >= 0 ? "+" : "") + h.points() + " §7点", NamedTextColor.YELLOW),
                    List.of(
                        Component.text(""),
                        Component.text("§7原因: §f" + h.reason()),
                        Component.text("§7时间: §f" + date.toString()),
                        Component.text("")
                    ),
                    event -> event.setCancelled(true),
                    false
                ));
                idx++;
            }
        }

        gui.open(player);
    }

    /**
     * 播放晋升动画
     */
    public void playPromotionAnimation(Player player, SocialClass oldClass, SocialClass newClass) {
        // 关闭当前GUI
        player.closeInventory();

        // 播放声音
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // 发送消息
        player.sendMessage(Component.text("", NamedTextColor.WHITE)
            .append(Component.text("§6§l═══════════════════════════════", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("§6§l    【 晋 升 ！】", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7恭喜！你已晋升至", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§6§l★ " + newClass.displayName() + " ★", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§7解锁了新的特权:", NamedTextColor.GRAY));

        Set<String> oldPrivs = oldClass.privileges();
        Set<String> newPrivs = newClass.privileges();
        for (String priv : newPrivs) {
            if (!oldPrivs.contains(priv)) {
                player.sendMessage(Component.text("§a  ✓ §f" + formatPrivilegeName(priv)));
            }
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§l═══════════════════════════════", NamedTextColor.GOLD));

        // 延迟打开新的阶层GUI
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("StarCore"),
            () -> openMainMenu(),
            40L // 2秒延迟
        );
    }

    // ==================== 辅助方法 ====================

    private void fillBorder(Gui gui, Material borderMaterial) {
        if (borderMaterial == null) {
            borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        }
        GuiItem borderItem = new GuiItem(new ItemStack(borderMaterial));

        // Top row
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
        }

        // Bottom row
        int rows = gui.getRows();
        for (int i = 0; i < 9; i++) {
            gui.setItem((rows - 1) * 9 + i, borderItem);
        }

        // Left column
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9, borderItem);
        }

        // Right column
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9 + 8, borderItem);
        }
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                  GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action,
                                  boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        if (glow && material != Material.BARRIER) {
            item = addGlow(item);
        }
        return new GuiItem(item, action);
    }

    private ItemStack addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        try {
            var glowEnchant = org.bukkit.enchantments.Enchantment.getByName("UNBREAKING");
            if (glowEnchant != null) {
                meta.addEnchant(glowEnchant, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        } catch (Exception e) {
            logger.warning("添加物品发光效果失败: " + e.getMessage());
        }
        item.setItemMeta(meta);
        return item;
    }

    private GuiItem createPrivilegeItem(String privilege) {
        Material mat = getPrivilegeMaterial(privilege);
        String name = formatPrivilegeName(privilege);
        String desc = formatPrivilegeDescription(privilege);

        return createGuiItem(
            mat,
            Component.text("§a✓ " + name, NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7" + desc, NamedTextColor.GRAY),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        );
    }

    private List<Component> buildCurrentClassLore(SocialClass sc, int points, Set<String> privileges) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7当前阶层: " + sc.color() + sc.displayName(), NamedTextColor.GRAY));
        lore.add(Component.text("§7所需点数: §e" + sc.requiredPoints() + " 点", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("§7特权数量: §b" + privileges.size() + " 个", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("§e▸ 点击查看特权详情", NamedTextColor.YELLOW));
        return lore;
    }

    private List<Component> buildProgressLore(SocialClass sc, int points) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7当前点数: §e" + points, NamedTextColor.GRAY));

        int nextPoints = getNextLevelPoints(sc);
        if (nextPoints > 0) {
            lore.add(Component.text("§7下一级需求: §e" + nextPoints, NamedTextColor.GRAY));
            lore.add(Component.text("§7还需: §e" + Math.max(0, nextPoints - points), NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("§a✓ 已达最高级！", NamedTextColor.GREEN));
        }
        lore.add(Component.text(""));
        return lore;
    }

    private List<Component> buildPromotionPreviewLore(SocialClass sc) {
        SocialClass nextClass = getNextClass(sc);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));

        if (nextClass != null) {
            lore.add(Component.text("§7下一阶层: " + nextClass.color() + nextClass.displayName(), NamedTextColor.GRAY));
            lore.add(Component.text("§7晋升条件: §e" + nextClass.requiredPoints() + " 点", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("§e▸ 点击查看晋升详情", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("§a§l★ 已达最高阶层！★", NamedTextColor.GOLD));
            lore.add(Component.text("§6神级 - 传说存在", NamedTextColor.GOLD));
            lore.add(Component.text(""));
            lore.add(Component.text("§7拥有所有特权", NamedTextColor.GRAY));
        }
        return lore;
    }

    private List<Component> buildClassItemLore(SocialClass sc, boolean isCurrent, boolean isUnlocked) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7解锁条件: §e" + sc.requiredPoints() + " 点", NamedTextColor.GRAY));

        if (isCurrent) {
            lore.add(Component.text("§a✓ 当前阶层", NamedTextColor.GREEN));
        } else if (isUnlocked) {
            lore.add(Component.text("§e○ 已解锁", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("§c✗ 未解锁", NamedTextColor.RED));
        }

        lore.add(Component.text("§7特权数: §b" + sc.privileges().size(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("§e▸ 点击查看详情", NamedTextColor.YELLOW));
        return lore;
    }

    private List<Component> buildHistoryLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7查看最近的点数获取记录", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        List<ClassHistory> history = classService.getHistory(player.getUniqueId());
        lore.add(Component.text("§7记录数量: §e" + history.size(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("§e▸ 点击查看", NamedTextColor.YELLOW));
        return lore;
    }

    private Material getClassMaterial(SocialClass sc) {
        return switch (sc) {
            case PEASANT -> Material.DIRT;
            case WORKER -> Material.OAK_PLANKS;
            case MERCHANT -> Material.GOLD_INGOT;
            case NOBLE -> Material.DIAMOND;
            case ROYALTY -> Material.NETHER_STAR;
            case DIVINITY -> Material.END_CRYSTAL;
        };
    }

    private Material getNextClassMaterial(SocialClass sc) {
        return switch (sc) {
            case PEASANT -> Material.DIRT;
            case WORKER -> Material.GOLD_INGOT;
            case MERCHANT -> Material.DIAMOND;
            case NOBLE -> Material.NETHER_STAR;
            case ROYALTY -> Material.END_CRYSTAL;
            case DIVINITY -> Material.END_CRYSTAL;
        };
    }

    private Material getPrivilegeMaterial(String privilege) {
        return switch (privilege.toLowerCase()) {
            case "basic_trade" -> Material.CHEST;
            case "market_access" -> Material.EMERALD;
            case "title_noble" -> Material.GOLDEN_APPLE;
            case "all_trade" -> Material.BARREL;
            case "all_access" -> Material.BEACON;
            case "title_royalty" -> Material.NETHER_STAR;
            case "nation_founder" -> Material.NETHER_STAR;
            case "event_creator" -> Material.FIREWORK_ROCKET;
            default -> Material.DIAMOND;
        };
    }

    private String formatPrivilegeName(String privilege) {
        return switch (privilege.toLowerCase()) {
            case "basic_trade" -> "基础交易";
            case "market_access" -> "市场访问";
            case "title_noble" -> "贵族称号";
            case "all_trade" -> "全交易权限";
            case "all_access" -> "全区域访问";
            case "title_royalty" -> "皇室称号";
            case "nation_founder" -> "国家创始人";
            case "event_creator" -> "事件创建者";
            default -> privilege;
        };
    }

    private String formatPrivilegeDescription(String privilege) {
        return switch (privilege.toLowerCase()) {
            case "basic_trade" -> "可进行基础物品交易";
            case "market_access" -> "可进入和使用市场功能";
            case "title_noble" -> "可使用贵族专属称号";
            case "all_trade" -> "可进行所有类型的交易";
            case "all_access" -> "可访问所有限制区域";
            case "title_royalty" -> "可使用皇室专属称号";
            case "nation_founder" -> "可创建国家";
            case "event_creator" -> "可创建服务器事件";
            default -> "特殊权限";
        };
    }

    private int getNextLevelPoints(SocialClass sc) {
        SocialClass[] classes = SocialClass.values();
        int idx = sc.ordinal();
        if (idx < classes.length - 1) {
            return classes[idx + 1].requiredPoints();
        }
        return sc.requiredPoints(); // 最高级
    }

    private SocialClass getNextClass(SocialClass sc) {
        SocialClass[] classes = SocialClass.values();
        int idx = sc.ordinal();
        if (idx < classes.length - 1) {
            return classes[idx + 1];
        }
        return null; // 已是最高级
    }

    /**
     * 打开GUI的静态方法入口
     */
    public static void openForPlayer(Player player, SocialClassService classService) {
        new SocialClassGui(player, classService).openMainMenu();
    }

    /**
     * 播放晋升动画的静态方法
     */
    public static void playPromotion(Player player, SocialClassService classService,
                                     SocialClass oldClass, SocialClass newClass) {
        new SocialClassGui(player, classService).playPromotionAnimation(player, oldClass, newClass);
    }
}
