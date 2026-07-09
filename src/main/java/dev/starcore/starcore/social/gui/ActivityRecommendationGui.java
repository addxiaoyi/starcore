package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.simulation.SocialActivityService;
import dev.starcore.starcore.social.simulation.SocialActivityService.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 活动推荐 GUI
 * 显示当前热门活动、推荐参与的活动
 */
public final class ActivityRecommendationGui implements InventoryHolder {
    private static final int SIZE = 54;
    private static final String GUI_TITLE = "§6§l活动中心";

    private final Player player;
    private final SocialActivityService activityService;
    private final Inventory inventory;

    public ActivityRecommendationGui(Player player, SocialActivityService activityService) {
        this.player = player;
        this.activityService = activityService;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("活动中心", NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题
        inventory.setItem(4, createTitleItem());

        // 获取活动数据
        List<SocialActivity> publicActivities = activityService.getPublicActivities();
        List<SocialActivity> myActivities = activityService.getPlayerActivities(player.getUniqueId()).stream()
                .map(activityService::getActivity)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // 第一行：活动类型选择
        inventory.setItem(10, createActivityTypeItem(SocialActivityType.PARTY, "聚会", "与朋友一起开派对"));
        inventory.setItem(11, createActivityTypeItem(SocialActivityType.CELEBRATION, "庆典", "大型庆典活动"));
        inventory.setItem(12, createActivityTypeItem(SocialActivityType.COMPETITION, "比赛", "PVP/PVE竞赛"));
        inventory.setItem(14, createActivityTypeItem(SocialActivityType.GATHERING, "集会", "社交集会"));
        inventory.setItem(15, createActivityTypeItem(SocialActivityType.SOCIAL_MISSION, "任务", "社交任务"));
        inventory.setItem(16, createActivityTypeItem(SocialActivityType.NETWORKING, "联谊", "社交联谊"));

        // 第二行：我的活动
        inventory.setItem(19, createMyActivitiesButton(myActivities));

        // 热门活动
        inventory.setItem(21, createHotActivitiesButton(publicActivities));

        // 第三行：显示活动列表（最多显示6个）
        int slot = 28;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int i = 0; i < Math.min(6, publicActivities.size()); i++) {
            SocialActivity activity = publicActivities.get(i);
            inventory.setItem(slot++, createActivityItem(activity, formatter));
        }

        // 第四行：统计信息
        inventory.setItem(46, createStatsButton(publicActivities.size(), myActivities.size()));

        // 创建活动按钮
        inventory.setItem(49, createCreateActivityButton());

        // 返回按钮
        inventory.setItem(53, createBackButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("活动中心", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发现和参与精彩活动", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("聚会/庆典/比赛/集会...", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActivityTypeItem(SocialActivityType type, String name, String desc) {
        Material material = switch (type) {
            case PARTY -> Material.FIREWORK_STAR;
            case CELEBRATION -> Material.FIREWORK_ROCKET;
            case COMPETITION -> Material.DIAMOND_SWORD;
            case GATHERING -> Material.PLAYER_HEAD;
            case SOCIAL_MISSION -> Material.BOOK;
            case NETWORKING -> Material.EMERALD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name, NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(desc, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看此类活动", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMyActivitiesButton(List<SocialActivity> myActivities) {
        Material material = myActivities.isEmpty() ? Material.BARRIER : Material.NETHER_STAR;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("我的活动", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("参与中的活动: " + myActivities.size(), NamedTextColor.GRAY));

        if (!myActivities.isEmpty()) {
            for (SocialActivity activity : myActivities.stream().limit(3).toList()) {
                String status = activity.status() == ActivityStatus.ACTIVE ? "进行中" : "准备中";
                lore.add(Component.text("- " + activity.name() + " [" + status + "]",
                        activity.status() == ActivityStatus.ACTIVE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
        } else {
            lore.add(Component.text("你还没有参与任何活动", NamedTextColor.GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看我的活动", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHotActivitiesButton(List<SocialActivity> activities) {
        Material material = Material.BLAZE_POWDER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("热门活动", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前公开活动: " + activities.size(), NamedTextColor.GRAY));

        // 找出参与人数最多的活动
        if (!activities.isEmpty()) {
            SocialActivity hottest = activities.stream()
                    .max(Comparator.comparingInt(SocialActivity::participantCount))
                    .orElse(null);
            if (hottest != null) {
                lore.add(Component.text(""));
                lore.add(Component.text("最热门: " + hottest.name(), NamedTextColor.GOLD));
                lore.add(Component.text("参与: " + hottest.participantCount() + "/" + hottest.maxParticipants(),
                        NamedTextColor.GRAY));
            }
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看全部活动", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActivityItem(SocialActivity activity, DateTimeFormatter dateFormatter) {
        Material material = switch (activity.type()) {
            case PARTY -> Material.FIREWORK_STAR;
            case CELEBRATION -> Material.FIREWORK_ROCKET;
            case COMPETITION -> Material.DIAMOND_SWORD;
            case GATHERING -> Material.PLAYER_HEAD;
            case SOCIAL_MISSION -> Material.BOOK;
            case NETWORKING -> Material.EMERALD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String statusText = switch (activity.status()) {
            case PREPARING -> "准备中";
            case ACTIVE -> "进行中";
            case ENDED -> "已结束";
            case CANCELLED -> "已取消";
        };

        String statusColor = switch (activity.status()) {
            case PREPARING -> "#FFFF00";
            case ACTIVE -> "#00FF00";
            case ENDED -> "#888888";
            case CANCELLED -> "#FF0000";
        };

        meta.displayName(Component.text(activity.name(),
                activity.status() == ActivityStatus.ACTIVE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("类型: " + activity.type().getName() + " " + activity.type().emoji(), NamedTextColor.GRAY));

        // 主办人
        OfflinePlayer host = Bukkit.getOfflinePlayer(activity.host());
        lore.add(Component.text("主办: " + (host.getName() != null ? host.getName() : "Unknown"), NamedTextColor.GRAY));

        // 参与人数
        lore.add(Component.text("参与: " + activity.participantCount() + "/" + activity.maxParticipants(),
                NamedTextColor.AQUA));

        // 状态
        lore.add(Component.text("状态: " + statusText,
                activity.status() == ActivityStatus.ACTIVE ? NamedTextColor.GREEN : NamedTextColor.GRAY));

        // 描述
        if (activity.description() != null && !activity.description().isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text(activity.description(), NamedTextColor.DARK_GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("[加入活动]", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsButton(int totalActivities, int myActivities) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("活动统计", NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 活动统计 ===", NamedTextColor.GOLD));
        lore.add(Component.text(""));
        lore.add(Component.text("公开活动: " + totalActivities, NamedTextColor.GRAY));
        lore.add(Component.text("我的活动: " + myActivities, NamedTextColor.GRAY));
        lore.add(Component.text(""));

        // 活动类型统计
        Map<SocialActivityType, Long> typeCount = new HashMap<>();
        for (SocialActivity activity : activityService.getAllActivities()) {
            typeCount.merge(activity.type(), 1L, Long::sum);
        }

        for (SocialActivityType type : SocialActivityType.values()) {
            long count = typeCount.getOrDefault(type, 0L);
            if (count > 0) {
                lore.add(Component.text(type.getName() + ": " + count, NamedTextColor.GRAY));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCreateActivityButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("创建活动", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("创建你自己的活动", NamedTextColor.GRAY));
        lore.add(Component.text("邀请朋友参与", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("/activity create <类型> <名称>", NamedTextColor.YELLOW));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看帮助", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回社交中心", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击返回上一级", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取按钮对应的操作
     */
    public static ActivityAction getActionFromSlot(int slot) {
        return switch (slot) {
            // 活动类型
            case 10 -> ActivityAction.PARTY;
            case 11 -> ActivityAction.CELEBRATION;
            case 12 -> ActivityAction.COMPETITION;
            case 14 -> ActivityAction.GATHERING;
            case 15 -> ActivityAction.SOCIAL_MISSION;
            case 16 -> ActivityAction.NETWORKING;
            // 功能按钮
            case 19 -> ActivityAction.MY_ACTIVITIES;
            case 21 -> ActivityAction.HOT_ACTIVITIES;
            // 活动列表 (28-33)
            case 28, 29, 30, 31, 32, 33 -> ActivityAction.JOIN_ACTIVITY;
            // 底部按钮
            case 46 -> ActivityAction.STATS;
            case 49 -> ActivityAction.CREATE;
            case 53 -> ActivityAction.BACK;
            default -> ActivityAction.NONE;
        };
    }

    /**
     * 获取活动列表中的活动
     */
    public SocialActivity getActivityFromSlot(int slot) {
        if (slot < 28 || slot > 33) return null;

        List<SocialActivity> publicActivities = activityService.getPublicActivities();
        int index = slot - 28;

        if (index < publicActivities.size()) {
            return publicActivities.get(index);
        }
        return null;
    }

    /**
     * 活动动作枚举
     */
    public enum ActivityAction {
        NONE,
        PARTY,
        CELEBRATION,
        COMPETITION,
        GATHERING,
        SOCIAL_MISSION,
        NETWORKING,
        MY_ACTIVITIES,
        HOT_ACTIVITIES,
        JOIN_ACTIVITY,
        STATS,
        CREATE,
        BACK
    }
}