package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.simulation.SocialActivityService;
import dev.starcore.starcore.social.simulation.SocialActivityService.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.WeakHashMap;

/**
 * 活动推荐 GUI 监听器
 */
public final class ActivityRecommendationListener implements Listener {
    private final SocialActivityService activityService;

    // 跟踪打开的活动推荐菜单
    private final WeakHashMap<Player, ActivityRecommendationGui> openMenus = new WeakHashMap<>();

    public ActivityRecommendationListener(SocialActivityService activityService) {
        this.activityService = activityService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ActivityRecommendationGui activityGui)) {
            return;
        }

        // 阻止玩家移动物品
        event.setCancelled(true);

        int slot = event.getRawSlot();
        ActivityRecommendationGui.ActivityAction action = ActivityRecommendationGui.getActionFromSlot(slot);

        handleAction(player, action, activityGui);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player);
        }
    }

    private void handleAction(Player player, ActivityRecommendationGui.ActivityAction action,
                             ActivityRecommendationGui gui) {
        switch (action) {
            case PARTY, CELEBRATION, COMPETITION, GATHERING, SOCIAL_MISSION, NETWORKING -> {
                // 显示该类型活动的详细信息
                player.sendMessage(Component.text("=== 活动类型 ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("请使用 /activity create " + action.name().toLowerCase() + " <名称> 创建活动",
                        NamedTextColor.GRAY));
            }
            case MY_ACTIVITIES -> {
                showMyActivities(player);
            }
            case HOT_ACTIVITIES -> {
                showHotActivities(player);
            }
            case JOIN_ACTIVITY -> {
                // JOIN_ACTIVITY 槽位是 28-33，获取第一个活动的详细信息
                SocialActivity firstActivity = gui.getActivityFromSlot(28);
                if (firstActivity != null) {
                    joinActivity(player, firstActivity);
                } else {
                    player.sendMessage(Component.text("当前没有可加入的活动", NamedTextColor.GRAY));
                }
            }
            case STATS -> {
                showActivityStats(player);
            }
            case CREATE -> {
                showCreateHelp(player);
            }
            case BACK -> {
                // 关闭当前 GUI，返回主社交菜单
                player.closeInventory();
                player.sendMessage(Component.text("使用 /socialgui 重新打开社交中心", NamedTextColor.YELLOW));
            }
            case NONE -> {
                // 不做任何操作
            }
        }
    }

    private void showMyActivities(Player player) {
        var activityIds = activityService.getPlayerActivities(player.getUniqueId());

        if (activityIds.isEmpty()) {
            player.sendMessage(Component.text("你还没有参与任何活动", NamedTextColor.GRAY));
            player.sendMessage(Component.text("在活动中心发现有趣的活动并加入吧！", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 我的活动 ===", NamedTextColor.GOLD));

        for (String activityId : activityIds) {
            var activityOpt = activityService.getActivity(activityId);
            if (activityOpt.isPresent()) {
                SocialActivity activity = activityOpt.get();
                String status = switch (activity.status()) {
                    case PREPARING -> "准备中";
                    case ACTIVE -> "进行中";
                    case ENDED -> "已结束";
                    case CANCELLED -> "已取消";
                };

                player.sendMessage(Component.text()
                        .append(Component.text("• " + activity.name(), NamedTextColor.YELLOW))
                        .append(Component.text(" [" + activity.type().getName() + "] ", NamedTextColor.GRAY))
                        .append(Component.text("[" + status + "]", activity.status() == ActivityStatus.ACTIVE ?
                                NamedTextColor.GREEN : NamedTextColor.GRAY)));
            }
        }
    }

    private void showHotActivities(Player player) {
        var activities = activityService.getPublicActivities();

        if (activities.isEmpty()) {
            player.sendMessage(Component.text("目前没有公开活动", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("=== 热门活动 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("显示前10个活动:", NamedTextColor.GRAY));

        int count = 0;
        for (SocialActivity activity : activities) {
            if (count++ >= 10) break;

            String status = switch (activity.status()) {
                case PREPARING -> "准备中";
                case ACTIVE -> "进行中";
                case ENDED -> "已结束";
                case CANCELLED -> "已取消";
            };

            player.sendMessage(Component.text()
                    .append(Component.text(count + ". " + activity.name(), NamedTextColor.YELLOW))
                    .append(Component.text(" - " + activity.type().getName() + " - 参与: " +
                            activity.participantCount() + "/" + activity.maxParticipants(), NamedTextColor.GRAY))
                    .append(Component.text(" [" + status + "]", activity.status() == ActivityStatus.ACTIVE ?
                            NamedTextColor.GREEN : NamedTextColor.GRAY)));
        }
    }

    private void joinActivity(Player player, SocialActivity activity) {
        if (activityService.joinActivity(player.getUniqueId(), activity.id())) {
            player.sendMessage(Component.text("成功加入活动: " + activity.name(), NamedTextColor.GREEN));
            player.sendMessage(Component.text("活动类型: " + activity.type().getName() + " " + activity.type().emoji(),
                    NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("加入活动失败！", NamedTextColor.RED));
            if (activity.participantCount() >= activity.maxParticipants()) {
                player.sendMessage(Component.text("活动已满员", NamedTextColor.GRAY));
            } else if (activity.status() == ActivityStatus.ENDED) {
                player.sendMessage(Component.text("活动已结束", NamedTextColor.GRAY));
            }
        }
    }

    private void showActivityStats(Player player) {
        var allActivities = activityService.getAllActivities();

        player.sendMessage(Component.text("=== 活动统计 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总活动数: " + allActivities.size(), NamedTextColor.GRAY));

        // 按状态统计
        long preparing = allActivities.stream().filter(a -> a.status() == ActivityStatus.PREPARING).count();
        long active = allActivities.stream().filter(a -> a.status() == ActivityStatus.ACTIVE).count();
        long ended = allActivities.stream().filter(a -> a.status() == ActivityStatus.ENDED).count();

        player.sendMessage(Component.text("准备中: " + preparing, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("进行中: " + active, NamedTextColor.GREEN));
        player.sendMessage(Component.text("已结束: " + ended, NamedTextColor.GRAY));

        // 按类型统计
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("按类型统计:", NamedTextColor.GOLD));

        for (SocialActivityType type : SocialActivityType.values()) {
            long count = allActivities.stream().filter(a -> a.type() == type).count();
            if (count > 0) {
                player.sendMessage(Component.text(type.getName() + " " + type.emoji() + ": " + count, NamedTextColor.GRAY));
            }
        }

        // 我的活动
        var myActivities = activityService.getPlayerActivities(player.getUniqueId());
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("我的活动: " + myActivities.size(), NamedTextColor.AQUA));
    }

    private void showCreateHelp(Player player) {
        player.sendMessage(Component.text("=== 创建活动 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("命令格式:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/activity create <类型> <名称> [描述]", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("活动类型:", NamedTextColor.YELLOW));

        for (SocialActivityType type : SocialActivityType.values()) {
            player.sendMessage(Component.text("  " + type.name().toLowerCase() + " - " + type.getName() +
                    " " + type.emoji(), NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("示例:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/activity create party 我的生日派对 欢迎来玩~", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/activity create competition 周末PVP赛", NamedTextColor.GRAY));
    }

    // 引用事件槽位
    private int eventSlot;

    /**
     * 打开活动推荐菜单
     */
    public void openMenu(Player player) {
        ActivityRecommendationGui menu = new ActivityRecommendationGui(player, activityService);
        openMenus.put(player, menu);
        player.openInventory(menu.getInventory());
    }
}
