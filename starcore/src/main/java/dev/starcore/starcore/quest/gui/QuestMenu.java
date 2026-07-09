package dev.starcore.starcore.quest.gui;

import dev.starcore.starcore.quest.*;
import dev.starcore.starcore.quest.Commission.CommissionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务系统GUI界面
 * 提供任务分类浏览、任务详情查看、进度追踪等功能
 * D-138: 已知问题：任务数量 1000+ 时 GUI 渲染会卡。
 * 建议：使用分页/延迟渲染（已有 page 参数，需确认 QUESTS_PER_PAGE 限制有效）
 */
public class QuestMenu {

    private final QuestService questService;
    private final DailyQuestService dailyQuestService;
    private final CommissionService commissionService;

    // GUI标题
    private static final Component MAIN_TITLE = Component.text("任务中心", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component DAILY_TITLE = Component.text("每日任务", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component QUEST_LIST_TITLE = Component.text("任务列表", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component DETAIL_TITLE = Component.text("任务详情", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component COMMISSION_TITLE = Component.text("委托中心", NamedTextColor.GOLD, TextDecoration.BOLD);
    private static final Component PROGRESS_TITLE = Component.text("任务进度", NamedTextColor.GOLD, TextDecoration.BOLD);

    // GUI尺寸
    private static final int MAIN_SIZE = 54;  // 6行的GUI
    private static final int LIST_SIZE = 54;
    private static final int DETAIL_SIZE = 36;

    // 每页显示数量
    private static final int QUESTS_PER_PAGE = 36;
    private static final int COMMISSIONS_PER_PAGE = 36;

    // 玩家菜单状态
    private final Map<UUID, MenuState> playerMenuState = new ConcurrentHashMap<>();

    public QuestMenu(QuestService questService, DailyQuestService dailyQuestService, CommissionService commissionService) {
        this.questService = questService;
        this.dailyQuestService = dailyQuestService;
        this.commissionService = commissionService;
    }

    // ==================== 菜单状态 ====================

    private enum MenuType {
        MAIN,           // 主菜单
        DAILY,          // 每日任务
        QUEST_LIST,     // 任务列表
        QUEST_DETAIL,   // 任务详情
        COMMISSION,     // 委托中心
        PROGRESS        // 任务进度
    }

    private record MenuState(
        UUID playerId,
        MenuType type,
        int page,
        Quest selectedQuest,
        QuestType filterType,
        String filterCategory
    ) {}

    // ==================== 主菜单 ====================

    public void openMainMenu(Player player) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.MAIN, 0, null, null, null
        ));

        Component title = Component.text()
            .append(MAIN_TITLE)
            .append(Component.text(" - 任务中心", NamedTextColor.GRAY))
            .build();

        Inventory gui = createInventory(MAIN_SIZE, title);

        // 顶部：玩家统计
        gui.setItem(4, createPlayerStatsItem(player));

        // 第一行：主要功能
        gui.setItem(19, createDailyQuestItem(player));
        gui.setItem(21, createActiveQuestItem(player));
        gui.setItem(23, createAvailableQuestItem(player));
        gui.setItem(25, createCommissionItem(player));

        // 第二行：分类快捷入口
        gui.setItem(37, createCategoryItem(QuestType.MAIN, "主线任务"));
        gui.setItem(39, createCategoryItem(QuestType.SIDE, "支线任务"));
        gui.setItem(41, createCategoryItem(QuestType.WEEKLY, "每周任务"));
        gui.setItem(43, createCategoryItem(QuestType.EVENT, "事件任务"));

        // 底部导航
        gui.setItem(49, createProgressOverviewItem(player));
        gui.setItem(51, createRefreshItem());
        gui.setItem(53, createCloseItem());

        // 填充边框
        fillBorder(gui);

        player.openInventory(gui);
    }

    // ==================== 每日任务 ====================

    public void openDailyQuests(Player player) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.DAILY, 0, null, QuestType.DAILY, null
        ));

        UUID playerId = player.getUniqueId();
        List<Quest> dailyQuests = dailyQuestService.generateDailyQuests(player);
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        Component title = Component.text()
            .append(DAILY_TITLE)
            .append(Component.text(" [" + progress.getCompleted() + "/" + progress.getTotal() + "]", NamedTextColor.GRAY))
            .build();

        Inventory gui = createInventory(LIST_SIZE, title);

        // 进度显示
        gui.setItem(4, createDailyProgressItem(progress));

        // 任务列表
        int slot = 9;
        for (int i = 0; i < Math.min(dailyQuests.size(), QUESTS_PER_PAGE); i++) {
            Quest quest = dailyQuests.get(i);
            boolean completed = playerQuest.hasCompletedQuest(quest.getId());
            gui.setItem(slot++, createQuestItem(player, quest, completed));
        }

        // 导航
        gui.setItem(49, createBackButton("返回主菜单"));
        gui.setItem(51, createRefreshDailyItem());
        gui.setItem(53, createCloseItem());

        fillBorder(gui);
        player.openInventory(gui);
    }

    // ==================== 任务列表 ====================

    public void openQuestList(Player player, QuestType type, int page) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.QUEST_LIST, page, null, type, null
        ));

        UUID playerId = player.getUniqueId();
        List<Quest> quests;

        if (type == null) {
            // 所有可接取任务
            quests = questService.getAvailableQuests(player);
        } else {
            quests = questService.getQuestsByType(type);
        }

        // 分页
        int totalPages = (int) Math.ceil(quests.size() / (double) QUESTS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * QUESTS_PER_PAGE;
        int end = Math.min(start + QUESTS_PER_PAGE, quests.size());

        Component titleComponent = type == null ? QUEST_LIST_TITLE :
            Component.text(getQuestTypeName(type), NamedTextColor.GOLD, TextDecoration.BOLD);

        Component title = Component.text()
            .append(titleComponent)
            .append(Component.text(" (" + quests.size() + ")", NamedTextColor.GRAY))
            .build();

        Inventory gui = createInventory(LIST_SIZE, title);

        // 显示任务
        int slot = 0;
        for (int i = start; i < end; i++) {
            Quest quest = quests.get(i);
            PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
            boolean hasActive = playerQuest.hasActiveQuest(quest.getId());
            boolean hasCompleted = playerQuest.hasCompletedQuest(quest.getId());
            gui.setItem(slot++, createQuestListItem(quest, hasActive, hasCompleted));
        }

        // 分页导航
        gui.setItem(45, page > 0 ? createPrevPageItem() : createPlaceholder());
        gui.setItem(49, createBackButton("返回"));
        gui.setItem(53, page < totalPages - 1 ? createNextPageItem() : createPlaceholder());

        // 页码显示
        ItemStack pageItem = new ItemStack(Material.BOOK);
        ItemMeta meta = pageItem.getItemMeta();
        meta.displayName(Component.text("第 " + (page + 1) + " / " + Math.max(1, totalPages) + " 页", NamedTextColor.YELLOW));
        pageItem.setItemMeta(meta);
        gui.setItem(49, pageItem);

        fillBorder(gui);
        player.openInventory(gui);
    }

    // ==================== 任务详情 ====================

    public void openQuestDetail(Player player, Quest quest) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.QUEST_DETAIL, 0, quest, null, null
        ));

        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        boolean isActive = playerQuest.hasActiveQuest(quest.getId());
        boolean isCompleted = playerQuest.hasCompletedQuest(quest.getId());
        Quest activeQuest = isActive ? playerQuest.getActiveQuest(quest.getId()) : null;

        Component title = Component.text()
            .append(DETAIL_TITLE)
            .append(Component.text(" - " + quest.getName(), NamedTextColor.WHITE))
            .build();

        Inventory gui = createInventory(DETAIL_SIZE, title);

        // 任务图标和名称
        gui.setItem(4, createQuestDetailIcon(quest));

        // 任务信息
        gui.setItem(11, createQuestInfoItem(quest));

        // 目标进度
        gui.setItem(13, createObjectivesItem(quest, activeQuest));

        // 奖励预览
        gui.setItem(15, createRewardsItem(quest.getReward()));

        // 状态和操作
        gui.setItem(22, createStatusItem(quest, isActive, isCompleted, activeQuest));

        // 操作按钮
        if (!isActive && !isCompleted) {
            gui.setItem(30, createAcceptButton(quest));
        } else if (isActive) {
            gui.setItem(30, createAbandonButton(quest));
            if (activeQuest != null && activeQuest.isAllObjectivesCompleted()) {
                gui.setItem(32, createCompleteButton(quest));
            }
        }

        // 返回按钮
        gui.setItem(49, createBackButton("返回列表"));

        fillBorder(gui);
        player.openInventory(gui);
    }

    // ==================== 委托中心 ====================

    public void openCommissionBoard(Player player) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.COMMISSION, 0, null, null, null
        ));

        UUID playerId = player.getUniqueId();
        List<Commission> commissions = commissionService.getCommissionBoard().getAllCommissions();

        Component title = Component.text()
            .append(COMMISSION_TITLE)
            .append(Component.text(" (" + commissions.size() + ")", NamedTextColor.GRAY))
            .build();

        Inventory gui = createInventory(LIST_SIZE, title);

        // 玩家统计
        gui.setItem(4, createCommissionStatsItem(player));

        // 委托列表
        int slot = 9;
        for (int i = 0; i < Math.min(commissions.size(), COMMISSIONS_PER_PAGE); i++) {
            Commission commission = commissions.get(i);
            boolean isAccepted = commission.getAcceptorId() != null &&
                commission.getAcceptorId().equals(playerId);
            boolean isPublisher = commission.getPublisherId().equals(playerId);
            gui.setItem(slot++, createCommissionItem(commission, isAccepted, isPublisher));
        }

        // 导航
        gui.setItem(45, createBackButton("返回"));
        gui.setItem(49, createCreateCommissionButton());
        gui.setItem(53, createCloseItem());

        fillBorder(gui);
        player.openInventory(gui);
    }

    // ==================== 任务进度 ====================

    public void openProgress(Player player) {
        playerMenuState.put(player.getUniqueId(), new MenuState(
            player.getUniqueId(), MenuType.PROGRESS, 0, null, null, null
        ));

        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        Map<String, Quest> activeQuests = playerQuest.getActiveQuests();
        DailyQuestService.DailyProgress dailyProgress = dailyQuestService.getDailyProgress(playerId);

        Component title = Component.text()
            .append(PROGRESS_TITLE)
            .append(Component.text(" [" + activeQuests.size() + "]", NamedTextColor.GRAY))
            .build();

        Inventory gui = createInventory(LIST_SIZE, title);

        // 总体进度
        gui.setItem(4, createOverallProgressItem(player, activeQuests.size(), dailyProgress));

        // 进行中的任务
        int slot = 9;
        for (Quest quest : activeQuests.values()) {
            if (slot >= 45) break;
            gui.setItem(slot++, createActiveQuestProgressItem(player, quest));
        }

        // 导航
        gui.setItem(49, createBackButton("返回"));
        gui.setItem(53, createCloseItem());

        fillBorder(gui);
        player.openInventory(gui);
    }

    // ==================== 创建物品方法 ====================

    private ItemStack createPlayerStatsItem(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);
        List<Commission> acceptedCommissions = commissionService.getPlayerAcceptedCommissions(playerId);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("玩家任务统计", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("进行中任务: " + playerQuest.getActiveQuestCount(), NamedTextColor.WHITE));
        lore.add(Component.text("已完成任务: " + playerQuest.getCompletedQuestIds().size(), NamedTextColor.GREEN));
        lore.add(Component.text("每日任务: " + progress.getCompleted() + "/" + progress.getTotal(), NamedTextColor.YELLOW));
        lore.add(Component.text("接取委托: " + acceptedCommissions.size(), NamedTextColor.AQUA));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyQuestItem(Player player) {
        UUID playerId = player.getUniqueId();
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("每日任务", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看每日刷新的任务", NamedTextColor.GRAY));
        lore.add(Component.text("进度: " + progress.getCompleted() + "/" + progress.getTotal(), NamedTextColor.YELLOW));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text(progress.getProgressBar()));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActiveQuestItem(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("进行中任务", NamedTextColor.YELLOW, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看当前进行中的任务", NamedTextColor.GRAY));
        lore.add(Component.text("数量: " + playerQuest.getActiveQuestCount(), NamedTextColor.WHITE));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAvailableQuestItem(Player player) {
        List<Quest> available = questService.getAvailableQuests(player);

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("可接取任务", NamedTextColor.AQUA, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看可以接取的任务", NamedTextColor.GRAY));
        lore.add(Component.text("数量: " + available.size(), NamedTextColor.WHITE));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCommissionItem(Player player) {
        List<Commission> accepted = commissionService.getPlayerAcceptedCommissions(player.getUniqueId());

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("委托中心", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看和发布委托任务", NamedTextColor.GRAY));
        lore.add(Component.text("已接取: " + accepted.size(), NamedTextColor.WHITE));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCategoryItem(QuestType type, String name) {
        Material material = switch (type) {
            case MAIN -> Material.MAP;
            case SIDE -> Material.PAPER;
            case WEEKLY -> Material.DAYLIGHT_DETECTOR;
            case EVENT -> Material.FIREWORK_ROCKET;
            default -> Material.BOOK;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看" + name, NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressOverviewItem(Player player) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("任务进度总览", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("查看所有任务进度", NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击打开", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshItem() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("刷新", NamedTextColor.YELLOW, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("刷新当前页面", NamedTextColor.GRAY));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("关闭", NamedTextColor.RED, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyProgressItem(DailyQuestService.DailyProgress progress) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("每日任务进度", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("完成情况: " + progress.getCompleted() + "/" + progress.getTotal(), NamedTextColor.YELLOW));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text(progress.getProgressBar()));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text(String.format("进度: %.1f%%", progress.getPercentage()), NamedTextColor.GRAY));

        if (progress.isAllCompleted()) {
            lore.add(Component.text(" ", NamedTextColor.WHITE));
            lore.add(Component.text("恭喜！今日任务全部完成！", NamedTextColor.GREEN));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuestItem(Player player, Quest quest, boolean completed) {
        Material material = completed ? Material.LIME_STAINED_GLASS : getQuestDifficultyMaterial(quest.getDifficulty());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor nameColor = completed ? NamedTextColor.GRAY : getQuestDifficultyColor(quest.getDifficulty());
        meta.displayName(Component.text(quest.getName(), nameColor, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        if (completed) {
            lore.add(Component.text("[ 已完成 ]", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("[ " + quest.getDifficulty().name() + " ]", getQuestDifficultyColor(quest.getDifficulty())));
        }
        lore.add(Component.text(quest.getDescription(), NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("奖励: " + formatReward(quest.getReward()), NamedTextColor.YELLOW));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("点击查看详情", NamedTextColor.GREEN));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuestListItem(Quest quest, boolean hasActive, boolean hasCompleted) {
        Material material;
        if (hasCompleted) {
            material = Material.LIME_STAINED_GLASS;
        } else if (hasActive) {
            material = Material.YELLOW_STAINED_GLASS;
        } else {
            material = getQuestDifficultyMaterial(quest.getDifficulty());
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor nameColor = hasCompleted ? NamedTextColor.GRAY : getQuestDifficultyColor(quest.getDifficulty());
        meta.displayName(Component.text(quest.getName(), nameColor));

        List<Component> lore = new ArrayList<>();
        if (hasCompleted) {
            lore.add(Component.text("[ 已完成 ]", NamedTextColor.GREEN));
        } else if (hasActive) {
            lore.add(Component.text("[ 进行中 ]", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("[ 可接取 ]", NamedTextColor.GREEN));
        }
        lore.add(Component.text("难度: " + quest.getDifficulty().name(), getQuestDifficultyColor(quest.getDifficulty())));
        lore.add(Component.text("类型: " + getQuestTypeName(quest.getType()), NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("奖励: " + formatRewardBrief(quest.getReward()), NamedTextColor.YELLOW));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuestDetailIcon(Quest quest) {
        Material material = getQuestDifficultyMaterial(quest.getDifficulty());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(quest.getName(), getQuestDifficultyColor(quest.getDifficulty()), TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(quest.getDescription(), NamedTextColor.WHITE));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("难度: " + quest.getDifficulty().name(), getQuestDifficultyColor(quest.getDifficulty())));
        lore.add(Component.text("类型: " + getQuestTypeName(quest.getType()), NamedTextColor.GRAY));
        if (quest.getMinLevel() > 0) {
            lore.add(Component.text("最低等级: " + quest.getMinLevel(), NamedTextColor.RED));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuestInfoItem(Quest quest) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("任务信息", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + quest.getId(), NamedTextColor.GRAY));
        lore.add(Component.text("名称: " + quest.getName(), NamedTextColor.WHITE));
        lore.add(Component.text("描述: " + quest.getDescription(), NamedTextColor.WHITE));
        lore.add(Component.text("分类: " + quest.getCategory(), NamedTextColor.GRAY));
        if (quest.getTimeLimit() > 0) {
            long hours = quest.getTimeLimit() / (60 * 60 * 1000);
            lore.add(Component.text("限时: " + hours + " 小时", NamedTextColor.RED));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createObjectivesItem(Quest quest, Quest activeQuest) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("任务目标", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective obj = objectives.get(i);
            boolean completed = false;
            int progress = 0;
            if (activeQuest != null && i < activeQuest.getObjectives().size()) {
                QuestObjective activeObj = activeQuest.getObjectives().get(i);
                completed = activeObj.isCompleted();
                progress = activeObj.getCurrentProgress();
            }

            String status = completed ? "[V]" : "[ ]";
            NamedTextColor color = completed ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            lore.add(Component.text(status + " " + obj.getDescription() + " (" + progress + "/" + obj.getRequiredAmount() + ")", color));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRewardsItem(QuestReward reward) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("任务奖励", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        if (reward.getMoney() > 0) {
            lore.add(Component.text("金币: " + String.format("%.2f", reward.getMoney()), NamedTextColor.YELLOW));
        }
        if (reward.getExperience() > 0) {
            lore.add(Component.text("经验: " + reward.getExperience(), NamedTextColor.AQUA));
        }
        if (!reward.getItems().isEmpty()) {
            lore.add(Component.text("物品: " + reward.getItems().size() + " 件", NamedTextColor.WHITE));
        }
        if (!reward.getReputations().isEmpty()) {
            lore.add(Component.text("声望: " + reward.getReputations(), NamedTextColor.LIGHT_PURPLE));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem(Quest quest, boolean isActive, boolean isCompleted, Quest activeQuest) {
        Material material;
        NamedTextColor color;
        String status;

        if (isCompleted) {
            material = Material.LIME_STAINED_GLASS_PANE;
            color = NamedTextColor.GREEN;
            status = "已完成";
        } else if (isActive) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            color = NamedTextColor.YELLOW;
            status = "进行中";

            if (activeQuest != null && activeQuest.isAllObjectivesCompleted()) {
                status = "可完成！";
                material = Material.LIME_STAINED_GLASS_PANE;
                color = NamedTextColor.GREEN;
            }
        } else {
            material = Material.GRAY_STAINED_GLASS_PANE;
            color = NamedTextColor.GRAY;
            status = "未接取";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("状态: " + status, color, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        if (isActive && activeQuest != null) {
            double progress = activeQuest.getCompletionPercentage() * 100;
            lore.add(Component.text("进度: " + String.format("%.1f%%", progress), NamedTextColor.WHITE));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAcceptButton(Quest quest) {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("接取任务", NamedTextColor.GREEN, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("点击接取此任务", NamedTextColor.GRAY));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAbandonButton(Quest quest) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("放弃任务", NamedTextColor.RED, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("点击放弃此任务", NamedTextColor.GRAY));
        lore.add(Component.text("警告: 放弃后需重新接取", NamedTextColor.DARK_RED));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCompleteButton(Quest quest) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("完成任务", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("所有目标已完成！", NamedTextColor.GREEN));
        lore.add(Component.text("点击领取奖励", NamedTextColor.GRAY));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton(String text) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, NamedTextColor.YELLOW, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshDailyItem() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("刷新每日任务", NamedTextColor.YELLOW, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("消耗金币刷新任务", NamedTextColor.GRAY));
        lore.add(Component.text("费用: " + dailyQuestService.getRefreshCost(), NamedTextColor.RED));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrevPageItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("上一页", NamedTextColor.YELLOW, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("下一页", NamedTextColor.YELLOW, TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlaceholder() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCommissionStatsItem(Player player) {
        UUID playerId = player.getUniqueId();
        CommissionService.CommissionStats stats = commissionService.getPlayerStats(playerId);
        List<Commission> accepted = commissionService.getPlayerAcceptedCommissions(playerId);
        List<Commission> published = commissionService.getPlayerPublishedCommissions(playerId);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("我的委托统计", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("已完成: " + stats.getTotalCompleted(), NamedTextColor.GREEN));
        lore.add(Component.text("总收益: " + String.format("%.2f", stats.getTotalEarned()), NamedTextColor.YELLOW));
        lore.add(Component.text("已接取: " + accepted.size() + "/" + commissionService.getMaxAcceptedCommissions(), NamedTextColor.AQUA));
        lore.add(Component.text("已发布: " + published.size() + "/" + commissionService.getMaxCommissionsPerPlayer(), NamedTextColor.AQUA));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCommissionItem(Commission commission, boolean isAccepted, boolean isPublisher) {
        Material material;
        if (isAccepted) {
            material = Material.YELLOW_STAINED_GLASS;
        } else if (isPublisher) {
            material = Material.LIGHT_BLUE_STAINED_GLASS;
        } else {
            material = Material.CHEST;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = switch (commission.getDifficulty()) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.GOLD;
            case EXPERT -> NamedTextColor.RED;
            case LEGENDARY -> NamedTextColor.DARK_PURPLE;
            case NIGHTMARE -> NamedTextColor.DARK_RED;
        };

        meta.displayName(Component.text(commission.getTitle(), color));

        List<Component> lore = new ArrayList<>();
        if (isAccepted) {
            lore.add(Component.text("[ 已接取 ]", NamedTextColor.YELLOW));
        } else if (isPublisher) {
            lore.add(Component.text("[ 我发布的 ]", NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("[ 可接取 ]", NamedTextColor.GREEN));
        }
        lore.add(Component.text(commission.getDescription(), NamedTextColor.GRAY));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text("赏金: " + String.format("%.2f", commission.getReward()), NamedTextColor.YELLOW));
        lore.add(Component.text("难度: " + commission.getDifficulty().name(), color));
        lore.add(Component.text("剩余时间: " + commission.getRemainingTimeText(), NamedTextColor.GRAY));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCreateCommissionButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("发布委托", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("发布新的委托任务", NamedTextColor.GRAY));
        lore.add(Component.text("费用: " + commissionService.getCommissionCreationCost(), NamedTextColor.RED));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createOverallProgressItem(Player player, int activeCount, DailyQuestService.DailyProgress dailyProgress) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("总体进度", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("进行中任务: " + activeCount, NamedTextColor.YELLOW));
        lore.add(Component.text("每日任务: " + dailyProgress.getCompleted() + "/" + dailyProgress.getTotal(), NamedTextColor.AQUA));
        lore.add(Component.text(" ", NamedTextColor.WHITE));
        lore.add(Component.text(dailyProgress.getProgressBar()));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActiveQuestProgressItem(Player player, Quest quest) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        double progress = quest.getCompletionPercentage();
        NamedTextColor progressColor = progress >= 1.0 ? NamedTextColor.GREEN :
            progress >= 0.5 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        meta.displayName(Component.text(quest.getName(), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("进度: " + String.format("%.1f%%", progress * 100), progressColor));
        lore.add(Component.text(" ", NamedTextColor.WHITE));

        for (QuestObjective obj : quest.getObjectives()) {
            String status = obj.isCompleted() ? "[V]" : "[ ]";
            NamedTextColor color = obj.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            lore.add(Component.text(status + " " + obj.getDescription(), color));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    private void fillBorder(Inventory gui) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.WHITE));
        border.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }
    }

    private Inventory createInventory(int size, Component title) {
        QuestHolder holder = new QuestHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private Material getQuestDifficultyMaterial(QuestDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Material.GREEN_CONCRETE;
            case NORMAL -> Material.YELLOW_CONCRETE;
            case HARD -> Material.ORANGE_CONCRETE;
            case EXPERT -> Material.RED_CONCRETE;
            case LEGENDARY -> Material.PURPLE_CONCRETE;
            case NIGHTMARE -> Material.BROWN_CONCRETE;
        };
    }

    private NamedTextColor getQuestDifficultyColor(QuestDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.GOLD;
            case EXPERT -> NamedTextColor.RED;
            case LEGENDARY -> NamedTextColor.DARK_PURPLE;
            case NIGHTMARE -> NamedTextColor.DARK_RED;
        };
    }

    private String getQuestTypeName(QuestType type) {
        if (type == null) return "所有任务";
        return switch (type) {
            case DAILY -> "每日任务";
            case WEEKLY -> "每周任务";
            case MAIN -> "主线任务";
            case SIDE -> "支线任务";
            case COMMISSION -> "委托任务";
            case REPEATABLE -> "重复任务";
            case ACHIEVEMENT -> "成就任务";
            case EVENT -> "事件任务";
        };
    }

    private String formatReward(QuestReward reward) {
        StringBuilder sb = new StringBuilder();
        if (reward.getMoney() > 0) {
            sb.append(String.format("%.2f", reward.getMoney())).append("金币");
        }
        if (reward.getExperience() > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getExperience()).append("经验");
        }
        if (!reward.getItems().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getItems().size()).append("件物品");
        }
        return sb.length() > 0 ? sb.toString() : "无";
    }

    private String formatRewardBrief(QuestReward reward) {
        StringBuilder sb = new StringBuilder();
        if (reward.getMoney() > 0) {
            sb.append(String.format("%.0f金", reward.getMoney()));
        }
        if (reward.getExperience() > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getExperience()).append("经验");
        }
        return sb.length() > 0 ? sb.toString() : "无";
    }

    // ==================== 事件处理 ====================

    private Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("StarCore");
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        MenuState state = playerMenuState.get(player.getUniqueId());
        if (state == null || clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 关闭按钮
        if (slot == 53 && clickedItem.getType() == Material.BARRIER) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            player.closeInventory();
            return;
        }

        // 边框不响应
        if (isBorder(clickedItem)) {
            return;
        }

        // 播放点击音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);

        switch (state.type()) {
            case MAIN -> handleMainClick(player, slot, clickedItem);
            case DAILY -> handleDailyClick(player, slot, clickedItem);
            case QUEST_LIST -> handleQuestListClick(player, slot, clickedItem, state.page());
            case QUEST_DETAIL -> handleDetailClick(player, slot, clickedItem, state.selectedQuest());
            case COMMISSION -> handleCommissionClick(player, slot, clickedItem);
            case PROGRESS -> handleProgressClick(player, slot, clickedItem);
        }
    }

    private void handleMainClick(Player player, int slot, ItemStack item) {
        switch (slot) {
            case 19 -> openDailyQuests(player);
            case 21 -> openQuestList(player, null, 0);
            case 23 -> openQuestList(player, null, 0);
            case 25 -> openCommissionBoard(player);
            case 37 -> openQuestList(player, QuestType.MAIN, 0);
            case 39 -> openQuestList(player, QuestType.SIDE, 0);
            case 41 -> openQuestList(player, QuestType.WEEKLY, 0);
            case 43 -> openQuestList(player, QuestType.EVENT, 0);
            case 49 -> openProgress(player);
            case 51 -> openMainMenu(player); // 刷新
        }
    }

    private void handleDailyClick(Player player, int slot, ItemStack item) {
        if (slot >= 9 && slot <= 44) {
            // 任务列表
            int index = slot - 9;
            List<Quest> dailyQuests = dailyQuestService.generateDailyQuests(player);
            if (index < dailyQuests.size()) {
                openQuestDetail(player, dailyQuests.get(index));
            }
        } else if (slot == 49 && item.getType() == Material.ARROW) {
            openMainMenu(player);
        } else if (slot == 51) {
            dailyQuestService.manualRefresh(player, false);
            openDailyQuests(player);
        }
    }

    private void handleQuestListClick(Player player, int slot, ItemStack item, int currentPage) {
        MenuState state = playerMenuState.get(player.getUniqueId());
        // H-005 修复: 添加 null 检查避免 NPE
        if (state == null) {
            player.closeInventory();
            return;
        }
        if (slot == 45 && item.getType() == Material.ARROW) {
            openQuestList(player, state.filterType(), currentPage - 1);
        } else if (slot == 53 && item.getType() == Material.ARROW) {
            openQuestList(player, state.filterType(), currentPage + 1);
        } else if (slot == 49 && item.getType() == Material.ARROW) {
            openMainMenu(player);
        } else if (slot >= 0 && slot <= 44) {
            QuestType type = state.filterType();
            List<Quest> quests = type == null ?
                questService.getAvailableQuests(player) :
                questService.getQuestsByType(type);

            int index = currentPage * QUESTS_PER_PAGE + slot;
            if (index < quests.size()) {
                openQuestDetail(player, quests.get(index));
            }
        }
    }

    private void handleDetailClick(Player player, int slot, ItemStack item, Quest quest) {
        MenuState state = playerMenuState.get(player.getUniqueId());
        if (slot == 49 && item.getType() == Material.ARROW) {
            // H-005 修复: 添加 null 检查避免 NPE
            if (state != null) {
                openQuestList(player, state.filterType(), 0);
            } else {
                openMainMenu(player);
            }
        } else if (slot == 30 && item.getType() == Material.LIME_DYE) {
            // 接取任务
            player.closeInventory();
            QuestService.QuestAcceptResult result = questService.acceptQuest(player, quest.getId());
            switch (result) {
                case SUCCESS -> player.sendMessage(Component.text("任务接取成功！", NamedTextColor.GREEN));
                case QUEST_LIMIT_REACHED -> player.sendMessage(Component.text("任务数量已达上限", NamedTextColor.RED));
                case DAILY_QUEST_LIMIT_REACHED -> player.sendMessage(Component.text("每日任务数量已达上限", NamedTextColor.RED));
                case ON_COOLDOWN -> player.sendMessage(Component.text("任务冷却中", NamedTextColor.RED));
                case REQUIREMENTS_NOT_MET -> player.sendMessage(Component.text("不满足任务要求", NamedTextColor.RED));
                default -> player.sendMessage(Component.text("接取失败: " + result.name(), NamedTextColor.RED));
            }
            // 刷新界面
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("StarCore"),
                () -> openQuestDetail(player, quest), 5L
            );
        } else if (slot == 30 && item.getType() == Material.RED_DYE) {
            // 放弃任务确认
            player.closeInventory();
            questService.abandonQuest(player.getUniqueId(), quest.getId());
            player.sendMessage(Component.text("任务已放弃", NamedTextColor.YELLOW));
            // 返回主菜单
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("StarCore"),
                () -> openMainMenu(player), 5L
            );
        } else if (slot == 32 && item.getType() == Material.NETHER_STAR) {
            // 完成任务
            player.closeInventory();
            QuestService.QuestCompleteResult result = questService.completeQuest(player, quest.getId());
            if (result.isSuccess()) {
                player.sendMessage(Component.text("任务完成！获得奖励！", NamedTextColor.GREEN));
                // 显示奖励信息
                if (result.getReward() != null) {
                    var reward = result.getReward();
                    StringBuilder rewardText = new StringBuilder();
                    rewardText.append("获得: ");
                    if (reward.getMoney() > 0) {
                        rewardText.append(String.format("%.0f金币 ", reward.getMoney()));
                    }
                    if (reward.getExperience() > 0) {
                        rewardText.append(reward.getExperience()).append("经验 ");
                    }
                    if (!reward.getItems().isEmpty()) {
                        rewardText.append(reward.getItems().size()).append("件物品 ");
                    }
                    player.sendMessage(Component.text(rewardText.toString(), NamedTextColor.GOLD));
                }
            } else {
                player.sendMessage(Component.text("完成失败: " + result.getMessage(), NamedTextColor.RED));
            }
            // 返回主菜单
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("StarCore"),
                () -> openMainMenu(player), 5L
            );
        }
    }

    private void handleCommissionClick(Player player, int slot, ItemStack item) {
        if (slot == 45 && item.getType() == Material.ARROW) {
            openMainMenu(player);
        }
    }

    private void handleProgressClick(Player player, int slot, ItemStack item) {
        if (slot == 49 && item.getType() == Material.ARROW) {
            openMainMenu(player);
        } else if (slot >= 9 && slot <= 44) {
            UUID playerId = player.getUniqueId();
            PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
            Map<String, Quest> activeQuests = playerQuest.getActiveQuests();
            int index = slot - 9;

            if (index < activeQuests.size()) {
                Quest quest = activeQuests.values().stream().toList().get(index);
                openQuestDetail(player, quest);
            }
        }
    }

    private boolean isBorder(ItemStack item) {
        return item != null && item.getType() == Material.BLACK_STAINED_GLASS_PANE;
    }

    public void clearState(UUID playerId) {
        playerMenuState.remove(playerId);
    }

    // ==================== GUI持有者 ====================

    static class QuestHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * 安全的打开菜单方法
     */
    public void safeOpenInventory(Player player, Inventory inventory) {
        if (inventory == null) {
            player.sendMessage(Component.text("菜单加载失败，请稍后重试", NamedTextColor.RED));
            return;
        }
        player.openInventory(inventory);
    }
}
