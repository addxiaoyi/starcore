package dev.starcore.starcore.foundation.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 菜单过渡动画器
 * 提供菜单页面之间的平滑过渡效果
 */
public final class MenuTransitionAnimator {
    private final Plugin plugin;
    private final GuiAnimationManager animationManager;
    private final Map<UUID, TransitionState> activeTransitions = new ConcurrentHashMap<>();

    public MenuTransitionAnimator(Plugin plugin, GuiAnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    /**
     * 过渡动画状态
     */
    private static class TransitionState {
        final Player player;
        final Inventory fromInventory;
        final Inventory toInventory;
        final Consumer<Inventory> onTransitionComplete;
        int currentSlot;
        boolean phase; // false = hiding, true = showing

        TransitionState(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
            this.player = player;
            this.fromInventory = from;
            this.toInventory = to;
            this.onTransitionComplete = onComplete;
            this.currentSlot = 0;
            this.phase = false;
        }
    }

    /**
     * 播放滑动过渡动画
     */
    public void playSlideTransition(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
        if (!animationManager.isTransitionEnabled()) {
            onComplete.accept(to);
            return;
        }

        // 播放过渡音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.2f);

        TransitionState state = new TransitionState(player, from, to, onComplete);
        activeTransitions.put(player.getUniqueId(), state);

        // 隐藏阶段：逐个清除物品
        scheduleSlideHide(state);
    }

    private void scheduleSlideHide(TransitionState state) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state.currentSlot < state.fromInventory.getSize()) {
                // 清除当前槽位
                state.fromInventory.setItem(state.currentSlot, createEmptyPane());
                state.currentSlot++;

                // 继续清除下一个
                scheduleSlideHide(state);
            } else {
                // 隐藏完成，开始显示
                state.currentSlot = 0;
                state.phase = true;
                scheduleSlideShow(state);
            }
        }, 2L);
    }

    private void scheduleSlideShow(TransitionState state) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state.currentSlot < state.toInventory.getSize()) {
                // 播放音效
                if (state.currentSlot % 9 == 0) {
                    state.player.playSound(state.player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.1f, 1.5f);
                }

                state.currentSlot++;

                // 继续显示下一个
                scheduleSlideShow(state);
            } else {
                // 显示完成
                state.onTransitionComplete.accept(state.toInventory);
                activeTransitions.remove(state.player.getUniqueId());
            }
        }, 1L);
    }

    /**
     * 播放淡入淡出过渡动画
     */
    public void playFadeTransition(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
        if (!animationManager.isTransitionEnabled()) {
            onComplete.accept(to);
            return;
        }

        // 先关闭当前菜单
        player.closeInventory();

        // 播放淡出音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BANJO, 0.2f, 0.8f);

        // 淡出动画
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 打开新菜单
            player.openInventory(to);

            // 淡入动画
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BANJO, 0.2f, 1.2f);
                player.sendActionBar(Component.text(">> 页面已加载", NamedTextColor.GREEN));
                onComplete.accept(to);
            }, 3L);
        }, 5L);
    }

    /**
     * 播放旋转过渡动画
     */
    public void playRotateTransition(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
        if (!animationManager.isTransitionEnabled()) {
            onComplete.accept(to);
            return;
        }

        player.closeInventory();

        // 旋转音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.3f, 1.3f);

        int[] spiralOrder = generateSpiralOrder(54);

        // 逐个槽位旋转切换
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.openInventory(to);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
            onComplete.accept(to);
        }, 5L);
    }

    /**
     * 生成螺旋顺序数组
     */
    private int[] generateSpiralOrder(int size) {
        List<Integer> order = new ArrayList<>();
        int rows = size / 9;
        int cols = 9;

        int top = 0, bottom = rows - 1;
        int left = 0, right = cols - 1;

        while (top <= bottom && left <= right) {
            // 左到右
            for (int i = left; i <= right; i++) order.add(top * 9 + i);
            top++;

            // 上到下
            for (int i = top; i <= bottom; i++) order.add(i * 9 + right);
            right--;

            // 右到左
            if (top <= bottom) {
                for (int i = right; i >= left; i--) order.add(bottom * 9 + i);
                bottom--;
            }

            // 下到上
            if (left <= right) {
                for (int i = bottom; i >= top; i--) order.add(i * 9 + left);
                left++;
            }
        }

        return order.stream().mapToInt(i -> i).toArray();
    }

    /**
     * 播放弹跳过渡动画
     */
    public void playBounceTransition(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
        if (!animationManager.isTransitionEnabled()) {
            onComplete.accept(to);
            return;
        }

        player.closeInventory();

        // 弹跳音效序列
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 1.0f);

        // 简化版本：直接打开并播放动画
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.openInventory(to);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
            onComplete.accept(to);
        }, 5L);
    }

    /**
     * 播放闪烁过渡动画
     */
    public void playBlinkTransition(Player player, Inventory from, Inventory to, Consumer<Inventory> onComplete) {
        if (!animationManager.isTransitionEnabled()) {
            onComplete.accept(to);
            return;
        }

        // 闪烁音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        // 简化版本：关闭旧菜单，等待后打开新菜单
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.openInventory(to);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, 1.3f);
            onComplete.accept(to);
        }, 3L);
    }

    /**
     * 取消玩家的过渡动画
     */
    public void cancelTransition(Player player) {
        TransitionState state = activeTransitions.remove(player.getUniqueId());
        if (state != null) {
            player.closeInventory();
        }
    }

    /**
     * 取消所有过渡动画
     */
    public void cancelAllTransitions() {
        activeTransitions.values().forEach(state -> {
            state.player.closeInventory();
        });
        activeTransitions.clear();
    }

    /**
     * 创建空的面板物品
     */
    private ItemStack createEmptyPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
