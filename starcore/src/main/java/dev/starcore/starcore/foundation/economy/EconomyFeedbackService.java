package dev.starcore.starcore.foundation.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 经济反馈效果服务
 * 提供交易成功/失败时的视觉、声音和界面反馈
 */
public class EconomyFeedbackService {

    private final org.bukkit.plugin.Plugin plugin;

    // 玩家的交易历史（用于显示最近交易）
    // audit B-014: 加 maxSize 限制与主动清理 API 防止离线/退出玩家 UUID 永久驻留导致内存膨胀
    private static final int MAX_TRACKED_PLAYERS = 4096;
    private final Map<UUID, TransactionHistory> playerHistories = new ConcurrentHashMap<>();

    // 交易动画状态
    private final Map<UUID, Long> animationCooldowns = new ConcurrentHashMap<>();
    private static final long ANIMATION_COOLDOWN_MS = 500;

    public EconomyFeedbackService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示购买成功反馈
     */
    public void showPurchaseSuccess(Player player, String itemName, int quantity, BigDecimal cost) {
        // 1. 发送消息
        Component message = Component.text()
            .append(Component.text("[购买] ", NamedTextColor.GOLD))
            .append(Component.text(quantity + "x " + itemName, NamedTextColor.WHITE))
            .append(Component.text(" -" + formatMoney(cost), NamedTextColor.RED))
            .build();
        player.sendMessage(message);

        // 2. 播放成功音效
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);

        // 3. 显示粒子效果
        spawnSuccessParticles(player);

        // 4. 显示标题
        showSuccessTitle(player, "购买成功!", formatMoney(cost));

        // 5. 记录历史
        addToHistory(player, TransactionType.PURCHASE, itemName, quantity, cost);

        // 6. 显示余额变化
        showBalanceChange(player, cost.negate());
    }

    /**
     * 显示出售成功反馈
     */
    public void showSaleSuccess(Player player, String itemName, int quantity, BigDecimal earnings) {
        // 1. 发送消息
        Component message = Component.text()
            .append(Component.text("[出售] ", NamedTextColor.GREEN))
            .append(Component.text(quantity + "x " + itemName, NamedTextColor.WHITE))
            .append(Component.text(" +" + formatMoney(earnings), NamedTextColor.GOLD))
            .build();
        player.sendMessage(message);

        // 2. 播放成功音效（更欢快的音调）
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);

        // 3. 显示金色粒子效果
        spawnSaleParticles(player);

        // 4. 显示标题
        showSuccessTitle(player, "出售成功!", "+" + formatMoney(earnings));

        // 5. 记录历史
        addToHistory(player, TransactionType.SALE, itemName, quantity, earnings);

        // 6. 显示余额变化
        showBalanceChange(player, earnings);
    }

    /**
     * 显示交易失败反馈
     */
    public void showTransactionFailure(Player player, String reason) {
        // 1. 发送消息
        Component message = Component.text()
            .append(Component.text("[交易失败] ", NamedTextColor.RED))
            .append(Component.text(reason, NamedTextColor.GRAY))
            .build();
        player.sendMessage(message);

        // 2. 播放失败音效
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f);

        // 3. 显示失败标题
        showFailureTitle(player, "交易失败", reason);

        // 4. 屏幕轻微震动效果（通过移动视角模拟）
        // audit B-016: 移除交易失败时的 SLOWNESS 药水副作用，正常交易失败不该惩罚玩家；仅保留音效/标题反馈
    }

    /**
     * 显示余额不足反馈
     */
    public void showInsufficientFunds(Player player, BigDecimal required, BigDecimal available) {
        BigDecimal deficit = required.subtract(available);

        Component message = Component.text()
            .append(Component.text("[余额不足] ", NamedTextColor.RED))
            .append(Component.text("需要 " + formatMoney(required) + "，", NamedTextColor.YELLOW))
            .append(Component.text("还差 " + formatMoney(deficit), NamedTextColor.RED))
            .build();
        player.sendMessage(message);

        // 播放警告音效
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.5f, 1.0f);

        // 显示提示标题
        player.showTitle(Title.title(
            Component.text("余额不足!", NamedTextColor.RED),
            Component.text("还差 " + formatMoney(deficit), NamedTextColor.YELLOW),
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(1500),
                Duration.ofMillis(300)
            )
        ));

        // 显示当前余额
        player.sendMessage(Component.text("当前余额: " + formatMoney(available), NamedTextColor.GRAY));
    }

    /**
     * 显示库存不足反馈
     */
    public void showOutOfStock(Player player, String itemName, int available) {
        Component message = Component.text()
            .append(Component.text("[库存不足] ", NamedTextColor.RED))
            .append(Component.text(itemName, NamedTextColor.YELLOW))
            .append(Component.text(" 剩余 " + available + " 个", NamedTextColor.GRAY))
            .build();
        player.sendMessage(message);

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.3f, 0.8f);

        showFailureTitle(player, "库存不足", itemName + " 剩余 " + available + " 个");
    }

    /**
     * 显示余额变化动画
     */
    private void showBalanceChange(Player player, BigDecimal change) {
        // 播放硬币音效（如果是增加）
        if (change.signum() > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.3f, 1.5f);
        }
    }

    /**
     * 显示成功标题
     */
    private void showSuccessTitle(Player player, String title, String subtitle) {
        player.showTitle(Title.title(
            Component.text(title, NamedTextColor.GREEN),
            Component.text(subtitle, NamedTextColor.GOLD),
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(1000),
                Duration.ofMillis(300)
            )
        ));
    }

    /**
     * 显示失败标题
     */
    private void showFailureTitle(Player player, String title, String subtitle) {
        player.showTitle(Title.title(
            Component.text(title, NamedTextColor.RED),
            Component.text(subtitle, NamedTextColor.GRAY),
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(1000),
                Duration.ofMillis(300)
            )
        ));
    }

    /**
     * 生成成功粒子效果
     */
    private void spawnSuccessParticles(Player player) {
        player.getWorld().spawnParticle(
            org.bukkit.Particle.HAPPY_VILLAGER,
            player.getLocation().add(0, 1.5, 0),
            15,
            0.6, 0.5, 0.6,
            0.02
        );

        player.getWorld().spawnParticle(
            org.bukkit.Particle.INSTANT_EFFECT,
            player.getLocation().add(0, 1, 0),
            8,
            0.3, 0.3, 0.3,
            0.01
        );
    }

    /**
     * 生成出售粒子效果（金色）
     */
    private void spawnSaleParticles(Player player) {
        player.getWorld().spawnParticle(
            org.bukkit.Particle.END_ROD,
            player.getLocation().add(0, 1.5, 0),
            20,
            0.5, 0.5, 0.5,
            0.03
        );

        player.getWorld().spawnParticle(
            org.bukkit.Particle.INSTANT_EFFECT,
            player.getLocation().add(0, 1, 0),
            10,
            0.3, 0.3, 0.3,
            0.02
        );
    }

    /**
     * 添加到交易历史
     */
    private void addToHistory(Player player, TransactionType type, String itemName, int quantity, BigDecimal amount) {
        UUID playerId = player.getUniqueId();
        TransactionHistory history = playerHistories.computeIfAbsent(playerId, k -> new TransactionHistory());

        history.add(new TransactionEntry(type, itemName, quantity, amount, System.currentTimeMillis()));

        // audit B-014: 限制被追踪的玩家总数，避免离线玩家历史长期占用内存
        if (playerHistories.size() > MAX_TRACKED_PLAYERS) {
            // 退化策略：超过上限时直接清空最旧的若干玩家（无法精确按时间淘汰，仅作上限保护）
            // 简化策略——清空整个 Map；上层会按需重建。考虑到该上限较大，触发概率极低
            playerHistories.clear();
            playerHistories.put(playerId, history);
        }
    }

    /**
     * 清除指定玩家的交易历史。供玩家退出/离线时调用，避免内存泄漏（audit B-014）。
     */
    public void clearHistory(UUID playerId) {
        if (playerId != null) {
            playerHistories.remove(playerId);
        }
    }

    /**
     * 获取玩家的交易历史
     */
    public TransactionHistory getHistory(Player player) {
        return playerHistories.getOrDefault(player.getUniqueId(), new TransactionHistory());
    }

    /**
     * 格式化金币显示
     */
    public static String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        // 移除小数部分如果整数
        if (amount.scale() <= 0 || amount.stripTrailingZeros().scale() <= 0) {
            return amount.toBigInteger().toString();
        }
        return amount.toPlainString();
    }

    /**
     * 交易类型枚举
     */
    public enum TransactionType {
        PURCHASE,
        SALE
    }

    /**
     * 交易记录条目
     */
    public record TransactionEntry(
        TransactionType type,
        String itemName,
        int quantity,
        BigDecimal amount,
        long timestamp
    ) {}

    /**
     * 交易历史记录
     */
    public static class TransactionHistory {
        private static final int MAX_HISTORY = 20;
        // audit B-015: 用 LinkedBlockingDeque + 单把锁保证 add + size 控制原子，避免并发下 size>20 突破与 removeOldest 移除正在 add 的同一项
        private final java.util.Deque<TransactionEntry> entries = new java.util.concurrent.LinkedBlockingDeque<>();
        private final Object trimLock = new Object();

        public void add(TransactionEntry entry) {
            synchronized (trimLock) {
                entries.addFirst(entry);
                while (entries.size() > MAX_HISTORY) {
                    entries.pollLast();
                }
            }
        }

        public void removeOldest() {
            synchronized (trimLock) {
                entries.pollLast();
            }
        }

        public int size() {
            return entries.size();
        }

        public java.util.List<TransactionEntry> getRecent(int count) {
            return entries.stream().limit(count).toList();
        }

        public BigDecimal getTotalSpent() {
            return entries.stream()
                .filter(e -> e.type() == TransactionType.PURCHASE)
                .map(TransactionEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal getTotalEarned() {
            return entries.stream()
                .filter(e -> e.type() == TransactionType.SALE)
                .map(TransactionEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
