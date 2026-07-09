package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.simulation.*;
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
 * 社交排行榜 GUI 监听器
 */
public final class SocialLeaderboardListener implements Listener {
    private final SocialInfluenceService influenceService;
    private final InfluenceLeaderboardService leaderboardService;
    private final FriendRecommendationService recommendationService;

    // 跟踪打开的排行榜菜单
    private final WeakHashMap<Player, SocialLeaderboardGui> openMenus = new WeakHashMap<>();

    public SocialLeaderboardListener(SocialInfluenceService influenceService,
                                    InfluenceLeaderboardService leaderboardService,
                                    FriendRecommendationService recommendationService) {
        this.influenceService = influenceService;
        this.leaderboardService = leaderboardService;
        this.recommendationService = recommendationService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof SocialLeaderboardGui leaderboardGui)) {
            return;
        }

        // 阻止玩家移动物品
        event.setCancelled(true);

        int slot = event.getRawSlot();
        SocialLeaderboardGui.LeaderboardAction action = SocialLeaderboardGui.getActionFromSlot(slot);

        handleAction(player, action, leaderboardGui);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player);
        }
    }

    private void handleAction(Player player, SocialLeaderboardGui.LeaderboardAction action,
                             SocialLeaderboardGui gui) {
        switch (action) {
            case INFLUENCE -> {
                gui.setCurrentType(SocialLeaderboardGui.LeaderboardType.INFLUENCE);
                player.sendMessage(Component.text("已切换到影响力排行榜", NamedTextColor.GREEN));
            }
            case ACTIVITY -> {
                gui.setCurrentType(SocialLeaderboardGui.LeaderboardType.ACTIVITY);
                player.sendMessage(Component.text("已切换到活跃度排行榜", NamedTextColor.GREEN));
            }
            case FRIENDS -> {
                gui.setCurrentType(SocialLeaderboardGui.LeaderboardType.FRIENDS);
                player.sendMessage(Component.text("已切换到好友数量排行榜", NamedTextColor.GREEN));
            }
            case REPUTATION -> {
                gui.setCurrentType(SocialLeaderboardGui.LeaderboardType.REPUTATION);
                player.sendMessage(Component.text("已切换到声望排行榜", NamedTextColor.GREEN));
            }
            case RELATIONSHIP -> {
                gui.setCurrentType(SocialLeaderboardGui.LeaderboardType.RELATIONSHIP);
                player.sendMessage(Component.text("已切换到社交关系排行榜", NamedTextColor.GREEN));
            }
            case DAILY -> {
                gui.setCurrentPeriod(LeaderboardPeriod.DAILY);
                player.sendMessage(Component.text("已切换到今日排行", NamedTextColor.GREEN));
            }
            case WEEKLY -> {
                gui.setCurrentPeriod(LeaderboardPeriod.WEEKLY);
                player.sendMessage(Component.text("已切换到本周排行", NamedTextColor.GREEN));
            }
            case MONTHLY -> {
                gui.setCurrentPeriod(LeaderboardPeriod.MONTHLY);
                player.sendMessage(Component.text("已切换到本月排行", NamedTextColor.GREEN));
            }
            case ALLTIME -> {
                gui.setCurrentPeriod(LeaderboardPeriod.ALLTIME);
                player.sendMessage(Component.text("已切换到全部时间排行", NamedTextColor.GREEN));
            }
            case VIEW_ENTRY -> {
                // 显示排行榜条目详情
                player.sendMessage(Component.text("排行榜详情功能开发中...", NamedTextColor.YELLOW));
            }
            case REFRESH -> {
                // 刷新排行榜
                leaderboardService.refreshCache();
                gui.rebuildMenu();
                player.sendMessage(Component.text("排行榜已刷新", NamedTextColor.GREEN));
            }
            case NONE -> {
                // 不做任何操作
            }
        }
    }

    /**
     * 打开排行榜菜单
     */
    public void openMenu(Player player) {
        SocialLeaderboardGui menu = new SocialLeaderboardGui(
                player,
                influenceService,
                leaderboardService,
                recommendationService
        );
        openMenus.put(player, menu);
        player.openInventory(menu.getInventory());
    }
}