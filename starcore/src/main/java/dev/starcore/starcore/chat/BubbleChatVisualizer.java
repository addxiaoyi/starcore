package dev.starcore.starcore.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 气泡聊天视觉效果管理器
 * 在玩家头顶显示悬浮气泡
 */
public class BubbleChatVisualizer {

    private final org.bukkit.plugin.Plugin plugin;
    private final ChatFormatterService chatFormatter;

    // 玩家头顶的气泡 ArmorStand
    private final Map<UUID, ArmorStand> bubbleStands = new ConcurrentHashMap<>();

    // 气泡可见性管理
    private final Set<UUID> playersWithVisibleBubbles = ConcurrentHashMap.newKeySet();

    // 任务运行器
    private BukkitRunnable cleanupTask;

    public BubbleChatVisualizer(org.bukkit.plugin.Plugin plugin, ChatFormatterService chatFormatter) {
        this.plugin = plugin;
        this.chatFormatter = chatFormatter;
        startCleanupTask();
    }

    /**
     * 为玩家显示气泡聊天
     */
    public void showBubbleChat(Player player) {
        if (!chatFormatter.isBubbleChatEnabled()) return;

        Optional<ChatFormatterService.BubbleChatData> bubbleOpt =
            chatFormatter.getBubbleChat(player.getUniqueId());

        if (bubbleOpt.isEmpty()) {
            hideBubbleChat(player.getUniqueId());
            return;
        }

        ChatFormatterService.BubbleChatData bubble = bubbleOpt.get();
        String message = truncateMessage(bubble.message(), 30);

        // 获取或创建 ArmorStand
        ArmorStand stand = bubbleStands.computeIfAbsent(player.getUniqueId(), uuid -> {
            Location loc = player.getLocation().clone();
            loc.setY(loc.getY() + 2.5);

            ArmorStand armorStand = (ArmorStand) player.getWorld()
                .spawnEntity(loc, EntityType.ARMOR_STAND);

            // 配置 ArmorStand
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName(""); // 初始化为空，稍后设置

            // 移除默认装备
            armorStand.getEquipment().setHelmet(new ItemStack(Material.AIR));

            return armorStand;
        });

        // 格式化气泡内容
        Component bubbleComponent = buildBubbleComponent(player.getName(), message);

        // 更新气泡文本
        stand.setCustomName(serializeComponent(bubbleComponent));
        stand.teleport(player.getLocation().clone().add(0, 2.5, 0));

        // 添加粒子效果
        spawnBubbleParticles(player.getLocation());

        // 标记玩家有可见气泡
        playersWithVisibleBubbles.add(player.getUniqueId());
    }

    /**
     * 隐藏玩家的气泡聊天
     */
    public void hideBubbleChat(UUID playerId) {
        ArmorStand stand = bubbleStands.remove(playerId);
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        playersWithVisibleBubbles.remove(playerId);
    }

    /**
     * 隐藏玩家的气泡聊天（兼容方法）
     */
    public void hideBubbleChat(Player player) {
        hideBubbleChat(player.getUniqueId());
    }

    /**
     * 隐藏所有气泡聊天
     */
    public void hideAllBubbles() {
        bubbleStands.values().forEach(stand -> {
            if (!stand.isDead()) {
                stand.remove();
            }
        });
        bubbleStands.clear();
        playersWithVisibleBubbles.clear();
    }

    /**
     * 为玩家更新所有可见气泡的可见性
     */
    public void updateVisibilityFor(Player viewer) {
        Location viewerLoc = viewer.getLocation();

        for (UUID playerId : playersWithVisibleBubbles) {
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || !target.isOnline()) {
                hideBubbleChat(playerId);
                continue;
            }

            ArmorStand stand = bubbleStands.get(playerId);
            if (stand == null || stand.isDead()) continue;

            // 计算距离
            double distance = viewerLoc.distance(target.getLocation());

            if (distance <= chatFormatter.getBubbleChatRadius()) {
                // 在范围内，显示气泡
                if (!stand.isVisible()) {
                    stand.setVisible(false); // ArmorStand 本身不可见
                }
            } else {
                // 超出范围，隐藏气泡
                if (stand.isCustomNameVisible()) {
                    stand.setCustomNameVisible(false);
                }
            }
        }
    }

    /**
     * 构建气泡组件样式
     */
    private Component buildBubbleComponent(String playerName, String message) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(Component.text(playerName, NamedTextColor.YELLOW))
            .append(Component.text("] ", NamedTextColor.GRAY))
            .append(legacyTextToComponent(message))
            .build();
    }

    /**
     * 将 Legacy 格式文本转换为 Component
     */
    private Component legacyTextToComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
     * 将 Component 序列化为字符串（用于 ArmorStand）
     */
    private String serializeComponent(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    /**
     * 截断过长的消息
     */
    private String truncateMessage(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * 生成气泡粒子效果
     */
    private void spawnBubbleParticles(Location loc) {
        loc.getWorld().spawnParticle(
            Particle.HEART,
            loc.clone().add(0, 1.5, 0),
            1, 0.1, 0.1, 0.1, 0
        );
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 清理过期气泡
                chatFormatter.cleanupExpiredBubbles();

                // 清理无效的 ArmorStand
                bubbleStands.entrySet().removeIf(entry -> {
                    if (entry.getValue().isDead()) {
                        playersWithVisibleBubbles.remove(entry.getKey());
                        return true;
                    }
                    return false;
                });

                // 为所有在线玩家更新气泡
                for (Player player : Bukkit.getOnlinePlayers()) {
                    showBubbleChat(player);
                }
            }
        };
        cleanupTask.runTaskTimer(plugin, 20L, 20L); // 每秒检查一次
    }

    /**
     * 停止清理任务
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        hideAllBubbles();
    }

    /**
     * 获取当前显示气泡的玩家数量
     */
    public int getActiveBubbleCount() {
        return playersWithVisibleBubbles.size();
    }

    /**
     * 检查玩家是否有可见气泡
     */
    public boolean hasVisibleBubble(UUID playerId) {
        return playersWithVisibleBubbles.contains(playerId);
    }
}