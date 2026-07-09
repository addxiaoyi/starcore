package dev.starcore.starcore.achievement;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.*;

/**
 * 成就定义
 * 完全模仿Minecraft原版成就系统
 */
public final class Achievement {
    private final NamespacedKey key;
    private final Component title;
    private final Component description;
    private final Material icon;
    private final FrameType frameType;
    private final AchievementTrigger trigger;

    // 成就树结构
    private final NamespacedKey parent;
    private final float x;
    private final float y;

    // 显示设置
    private final boolean showToast;
    private final boolean announceToChat;
    private final boolean hidden;

    // 奖励
    private final int experience;
    private final List<String> rewards;

    private Achievement(Builder builder) {
        this.key = builder.key;
        this.title = builder.title;
        this.description = builder.description;
        this.icon = builder.icon;
        this.frameType = builder.frameType;
        this.trigger = builder.trigger;
        this.parent = builder.parent;
        this.x = builder.x;
        this.y = builder.y;
        this.showToast = builder.showToast;
        this.announceToChat = builder.announceToChat;
        this.hidden = builder.hidden;
        this.experience = builder.experience;
        this.rewards = builder.rewards;
    }

    /**
     * 成就框架类型
     */
    public enum FrameType {
        TASK("task", "§a"),           // 任务（绿色）
        CHALLENGE("challenge", "§5"), // 挑战（紫色）
        GOAL("goal", "§e");           // 目标（黄色）

        private final String name;
        private final String color;

        FrameType(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
    }

    /**
     * 成就构建器
     */
    public static class Builder {
        private final NamespacedKey key;
        private Component title;
        private Component description;
        private Material icon = Material.GRASS_BLOCK;
        private FrameType frameType = FrameType.TASK;
        private AchievementTrigger trigger;

        private NamespacedKey parent;
        private float x = 0;
        private float y = 0;

        private boolean showToast = true;
        private boolean announceToChat = false;
        private boolean hidden = false;

        private int experience = 0;
        private List<String> rewards = new ArrayList<>();

        public Builder(NamespacedKey key) {
            this.key = key;
        }

        public Builder title(Component title) {
            this.title = title;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        public Builder frameType(FrameType frameType) {
            this.frameType = frameType;
            return this;
        }

        public Builder trigger(AchievementTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder parent(NamespacedKey parent) {
            this.parent = parent;
            return this;
        }

        public Builder position(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder showToast(boolean showToast) {
            this.showToast = showToast;
            return this;
        }

        public Builder announceToChat(boolean announce) {
            this.announceToChat = announce;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder experience(int experience) {
            this.experience = experience;
            return this;
        }

        public Builder reward(String reward) {
            this.rewards.add(reward);
            return this;
        }

        public Achievement build() {
            if (title == null) {
                throw new IllegalStateException("Title is required");
            }
            if (description == null) {
                throw new IllegalStateException("Description is required");
            }
            if (trigger == null) {
                throw new IllegalStateException("Trigger is required");
            }
            return new Achievement(this);
        }
    }

    // Getters
    public NamespacedKey getKey() { return key; }
    public Component getTitle() { return title; }
    public Component getDescription() { return description; }
    public Material getIcon() { return icon; }
    public FrameType getFrameType() { return frameType; }
    public AchievementTrigger getTrigger() { return trigger; }
    public NamespacedKey getParent() { return parent; }
    public float getX() { return x; }
    public float getY() { return y; }
    public boolean isShowToast() { return showToast; }
    public boolean isAnnounceToChat() { return announceToChat; }
    public boolean isHidden() { return hidden; }
    public int getExperience() { return experience; }
    public List<String> getRewards() { return new ArrayList<>(rewards); }

    /**
     * 检查是否有父成就
     */
    public boolean hasParent() {
        return parent != null;
    }
}
