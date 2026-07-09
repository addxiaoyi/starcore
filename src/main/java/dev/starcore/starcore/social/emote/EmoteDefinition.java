package dev.starcore.starcore.social.emote;

import java.util.*;

/**
 * 动作定义模型
 */
public class EmoteDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final String animationType; // "pose", "arm", "fullbody", "particle"
    private final String animationData; // 动画数据（如粒子效果、姿势等）
    private final int durationTicks; // 持续时间（tick）
    private final int cooldownSeconds; // 冷却时间
    private final boolean requiresTarget; // 是否需要目标玩家
    private final boolean isGlobal; // 是否全局可见
    private final String permission; // 需要的权限
    private final String category; // 分类：greeting, emotion, combat, social, etc.

    public EmoteDefinition(String id, String name, String description, String animationType,
                          String animationData, int durationTicks, int cooldownSeconds,
                          boolean requiresTarget, boolean isGlobal, String permission, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.animationType = animationType;
        this.animationData = animationData;
        this.durationTicks = durationTicks;
        this.cooldownSeconds = cooldownSeconds;
        this.requiresTarget = requiresTarget;
        this.isGlobal = isGlobal;
        this.permission = permission;
        this.category = category;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAnimationType() { return animationType; }
    public String getAnimationData() { return animationData; }
    public int getDurationTicks() { return durationTicks; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public boolean requiresTarget() { return requiresTarget; }
    public boolean isGlobal() { return isGlobal; }
    public String getPermission() { return permission; }
    public String getCategory() { return category; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String name;
        private String description = "";
        private String animationType = "pose";
        private String animationData = "";
        private int durationTicks = 40;
        private int cooldownSeconds = 5;
        private boolean requiresTarget = false;
        private boolean isGlobal = true;
        private String permission = "";
        private String category = "emotion";

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public Builder animationType(String t) { this.animationType = t; return this; }
        public Builder animationData(String d) { this.animationData = d; return this; }
        public Builder durationTicks(int t) { this.durationTicks = t; return this; }
        public Builder cooldownSeconds(int s) { this.cooldownSeconds = s; return this; }
        public Builder requiresTarget(boolean r) { this.requiresTarget = r; return this; }
        public Builder isGlobal(boolean g) { this.isGlobal = g; return this; }
        public Builder permission(String p) { this.permission = p; return this; }
        public Builder category(String c) { this.category = c; return this; }

        public EmoteDefinition build() {
            return new EmoteDefinition(id, name, description, animationType,
                animationData, durationTicks, cooldownSeconds, requiresTarget, isGlobal, permission, category);
        }
    }
}
