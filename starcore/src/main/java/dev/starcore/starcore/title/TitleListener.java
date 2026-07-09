package dev.starcore.starcore.title;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

/**
 * 称号系统事件监听器
 * 处理玩家加入、退出等事件
 */
public class TitleListener implements Listener {
    private final Plugin plugin;
    private final TitleService titleService;
    private final TitleDisplayService displayService;

    public TitleListener(Plugin plugin, TitleService titleService, TitleDisplayService displayService) {
        this.plugin = plugin;
        this.titleService = titleService;
        this.displayService = displayService;
    }

    /**
     * 玩家加入时加载数据并更新显示
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 异步加载玩家数据
        CompletableFuture<PlayerTitle> dataFuture = titleService.getPlayerData(player.getUniqueId());
        dataFuture.thenAccept(data -> {
            // 检查是否是首次加入（数据库中没有任何称号）
            if (data.getTitleCount() == 0) {
                // 使用 CompletableFuture 链式调用确保异步解锁
                titleService.unlockTitle(player.getUniqueId(), "newcomer")
                    .thenCompose(success -> {
                        if (success) {
                            return titleService.unlockBadge(player.getUniqueId(), "starter");
                        }
                        return CompletableFuture.completedFuture(false);
                    });
            }

            // 延迟更新显示，确保玩家完全加载
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    displayService.updateAllDisplays(player);
                }
            }, 20L); // 1秒后
        });
    }

    /**
     * 玩家退出时保存数据并清理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName(); // 离线后无法获取name，保存副本

        // 保存玩家数据
        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            titleService.savePlayerData(data);
        });

        // 立即清理缓存（不需要延迟）
        titleService.invalidateCache(player.getUniqueId());
        displayService.clearCache(player.getUniqueId());
        displayService.removeHologramDisplay(player);
    }
}
