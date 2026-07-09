package dev.starcore.starcore.achievement;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 成就解锁事件
 * 当玩家解锁成就时触发
 */
public class AchievementUnlockedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final NamespacedKey achievementKey;
    private final Component title;
    private final Component description;
    private final Material icon;
    private final Achievement.FrameType frameType;
    private final int experience;
    private final java.util.List<String> rewards;

    public AchievementUnlockedEvent(Player player, Achievement achievement) {
        this.player = player;
        this.achievementKey = achievement.getKey();
        this.title = achievement.getTitle();
        this.description = achievement.getDescription();
        this.icon = achievement.getIcon();
        this.frameType = achievement.getFrameType();
        this.experience = achievement.getExperience();
        this.rewards = achievement.getRewards();
    }

    public Player getPlayer() {
        return player;
    }

    public NamespacedKey getAchievementKey() {
        return achievementKey;
    }

    public Component getTitle() {
        return title;
    }

    public Component getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public Achievement.FrameType getFrameType() {
        return frameType;
    }

    public int getExperience() {
        return experience;
    }

    public java.util.List<String> getRewards() {
        return rewards;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}