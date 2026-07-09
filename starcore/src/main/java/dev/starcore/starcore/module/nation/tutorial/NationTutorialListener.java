package dev.starcore.starcore.module.nation.tutorial;

import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.gui.TriumphNationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * 国家教程事件监听器
 * 处理教程相关的玩家交互事件
 */
public class NationTutorialListener implements Listener {

    private final Plugin plugin;
    private final NationModule nationModule;
    private final NationTutorialService tutorialService;
    private final NationTutorialBubble tutorialBubble;
    private final TriumphNationMenu triumphNationMenu;

    public NationTutorialListener(Plugin plugin, NationModule nationModule,
                                  NationTutorialService tutorialService,
                                  NationTutorialBubble tutorialBubble,
                                  TriumphNationMenu triumphNationMenu) {
        this.plugin = plugin;
        this.nationModule = nationModule;
        this.tutorialService = tutorialService;
        this.tutorialBubble = tutorialBubble;
        this.triumphNationMenu = triumphNationMenu;
    }

    /**
     * 玩家加入事件 - 触发教程检查
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 延迟触发教程检查（等待玩家完全加载）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tutorialService.checkAndTriggerTutorial(player);
        }, 60L); // 3秒后
    }

    /**
     * 玩家退出事件 - 清理教程状态
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 隐藏气泡
        tutorialBubble.hideTutorialBubble(playerId);
    }

    /**
     * 玩家交互事件 - 处理教程导航
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否有活跃教程
        if (!tutorialService.hasActiveTutorial(playerId)) {
            return;
        }

        // 右键交互可以执行当前步骤的命令
        if (event.getAction().name().contains("RIGHT")) {
            event.setCancelled(true);

            // 执行当前步骤的命令
            tutorialService.executeCurrentCommand(player);
        }
    }

    /**
     * 物品栏点击事件 - 处理 GUI 中的教程导航
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // 检查是否有活跃教程
        if (!tutorialService.hasActiveTutorial(playerId)) {
            return;
        }

        // 获取当前步骤
        tutorialService.getActiveTutorial(playerId).ifPresent(active -> {
            var task = active.task();
            if (task == null || !task.isRunning()) {
                return;
            }

            // 检查点击的是否是国家 GUI
            String title = event.getView().getTitle();
            if (title.contains("国家") || title.contains("Nation") || title.contains("管理")) {
                // 在 GUI 中点击可以前进
                event.setCancelled(true);

                // 简单的点击检测 - 点击任意物品前进到下一步
                if (event.getCurrentItem() != null) {
                    // 延迟一点执行，防止立即跳转
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        tutorialService.nextStep(player);
                    }, 2L);
                }
            }
        });
    }

    /**
     * 在打开国家 GUI 时显示教程提示
     */
    public void onNationGuiOpened(Player player) {
        UUID playerId = player.getUniqueId();

        // 检查是否有活跃教程
        if (!tutorialService.hasActiveTutorial(playerId)) {
            return;
        }

        tutorialService.getActiveTutorial(playerId).ifPresent(active -> {
            // 显示教程气泡
            var task = active.task();
            if (task != null && task.isRunning()) {
                task.showCurrentStep();

                // 发送提示消息
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("📖 教程进行中 - 点击任意物品前进", NamedTextColor.AQUA));
                player.sendMessage(Component.text("💡 或使用 /tutorial next 前进，/tutorial prev 返回", NamedTextColor.GRAY));
                player.sendMessage(Component.text(""));
            }
        });
    }

    /**
     * 发送教程提示到玩家
     */
    public void sendTutorialHint(Player player) {
        UUID playerId = player.getUniqueId();

        if (!tutorialService.hasActiveTutorial(playerId)) {
            player.sendMessage(Component.text("你当前没有进行中的教程", NamedTextColor.GRAY));
            player.sendMessage(Component.text("使用 /sc tutorial 重新开始教程", NamedTextColor.GRAY));
            return;
        }

        tutorialService.getActiveTutorial(playerId).ifPresent(active -> {
            var task = active.task();
            if (task != null && task.isRunning()) {
                tutorialService.showStepInfo(player, task);
            }
        });
    }
}
