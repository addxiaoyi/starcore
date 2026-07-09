package dev.starcore.starcore.foundation.tooltip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.Action;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 快捷栏提示监听器
 * 监听玩家切换物品并显示智能提示
 */
public class HotbarTooltipListener implements Listener {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final SmartTooltipService tooltipService;
    private final Map<UUID, Long> lastHintTime;
    private final Map<UUID, ScheduledFuture<?>> pendingHints;
    private final ScheduledExecutorService scheduler;

    public HotbarTooltipListener(@NotNull SmartTooltipService tooltipService) {
        this.tooltipService = tooltipService;
        this.lastHintTime = new ConcurrentHashMap<>();
        this.pendingHints = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * 监听玩家切换快捷栏物品
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem == null || newItem.getType() == Material.AIR) {
            return;
        }

        // 取消之前的待处理提示
        cancelPendingHint(player);

        // 检查冷却
        if (!canShowHint(player)) {
            return;
        }

        // 生成快捷栏提示
        String hint = tooltipService.generateHotbarHint(player, newItem);
        if (hint != null && !hint.isEmpty()) {
            showHotbarHint(player, hint);
        }
    }

    /**
     * 监听玩家使用物品
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // 检查是否有自定义提示
        if (tooltipService.hasCustomTooltip(player, item)) {
            // 可以在这里添加额外的交互反馈
        }
    }

    /**
     * 监听玩家切换主副手
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack offhandItem = event.getOffHandItem();

        if (offhandItem == null || offhandItem.getType() == Material.AIR) {
            return;
        }

        // 生成副手物品的提示
        String hint = tooltipService.generateHotbarHint(player, offhandItem);
        if (hint != null && !hint.isEmpty()) {
            // 副手物品的提示可能需要特殊处理
        }
    }

    /**
     * 显示快捷栏提示
     */
    private void showHotbarHint(@NotNull Player player, @NotNull String hint) {
        lastHintTime.put(player.getUniqueId(), System.currentTimeMillis());

        Component component = Component.text(hint)
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

        player.sendActionBar(component);

        // 设置自动消失
        int duration = tooltipService.getConfig().getHotbarHintDuration();
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            player.sendActionBar(Component.empty());
        }, duration, TimeUnit.SECONDS);
        pendingHints.put(player.getUniqueId(), future);
    }

    /**
     * 检查是否可以显示提示（冷却检查）
     */
    private boolean canShowHint(@NotNull Player player) {
        if (!tooltipService.isEnabled() || !tooltipService.getConfig().isHotbarHintsEnabled()) {
            return false;
        }

        long lastTime = lastHintTime.getOrDefault(player.getUniqueId(), 0L);
        long cooldown = tooltipService.getConfig().getHotbarHintCooldown();

        return System.currentTimeMillis() - lastTime >= cooldown;
    }

    /**
     * 取消待处理的提示
     */
    private void cancelPendingHint(@NotNull Player player) {
        ScheduledFuture<?> future = pendingHints.remove(player.getUniqueId());
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭监听器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingHints.clear();
        lastHintTime.clear();
    }

    // E-053 修复: 玩家退出时清理提示状态，防止 ScheduledFuture 内存泄漏
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ScheduledFuture<?> future = pendingHints.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
        lastHintTime.remove(playerId);
    }
}