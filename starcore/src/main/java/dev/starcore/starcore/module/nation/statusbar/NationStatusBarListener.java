package dev.starcore.starcore.module.nation.statusbar;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * 国家状态栏监听器
 * 处理玩家加入/退出时的状态栏显示
 */
public class NationStatusBarListener implements Listener {

    private final NationStatusBarService statusBarService;
    private final NationService nationService;

    public NationStatusBarListener(
            NationStatusBarService statusBarService,
            NationService nationService,
            Plugin plugin) {
        this.statusBarService = statusBarService;
        this.nationService = nationService;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 玩家加入时自动显示状态栏
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 延迟1秒后检查并显示状态栏
        player.getServer().getScheduler().runTaskLater(
                statusBarService.getPlugin(),
                () -> {
                    if (player.isOnline() && isInNation(player)) {
                        statusBarService.showStatusBar(player);
                    }
                },
                20L // 1秒延迟
        );
    }

    /**
     * 玩家退出时隐藏状态栏
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        statusBarService.hideStatusBar(event.getPlayer());
    }

    /**
     * 检查玩家是否在国家中
     */
    private boolean isInNation(Player player) {
        return nationService.nationOf(player.getUniqueId()).isPresent();
    }
}