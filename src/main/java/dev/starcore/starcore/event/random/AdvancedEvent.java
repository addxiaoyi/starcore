package dev.starcore.starcore.event.random;

import java.util.*;

/**
 * 高级事件定义
 * 包含稀有度、触发条件、效果和事件链
 */
public class AdvancedEvent {

    private final String id;
    private final String name;
    private final String description;
    private final EventRarity rarity;

    // 触发条件
    private final EventCondition condition;

    // 效果
    private final List<EventEffect> effects;

    // 事件链
    private final List<String> chainEventIds;
    private final int chainDelaySeconds;

    // 配置
    private final boolean global;
    private final int priority;
    private final String icon;
    private final String sound;

    // 特殊标记
    private final boolean canIgnore;
    private final boolean persistent;
    private final int minStageLevel;

    private AdvancedEvent(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.rarity = builder.rarity;
        this.condition = builder.condition;
        this.effects = Collections.unmodifiableList(builder.effects);
        this.chainEventIds = Collections.unmodifiableList(builder.chainEventIds);
        this.chainDelaySeconds = builder.chainDelaySeconds;
        this.global = builder.global;
        this.priority = builder.priority;
        this.icon = builder.icon;
        this.sound = builder.sound;
        this.canIgnore = builder.canIgnore;
        this.persistent = builder.persistent;
        this.minStageLevel = builder.minStageLevel;
    }

    // ==================== 触发检查 ====================

    /**
     * 检查事件是否匹配上下文
     */
    public boolean matches(NationEventContext context) {
        if (context == null) {
            return !global;  // 非全局事件需要国家
        }

        // 阶段门槛
        if (context.getStageLevel() < minStageLevel) {
            return false;
        }

        // 稀有度门槛
        if (rarity.ordinal() > context.getPreferredRarity().ordinal()) {
            return false;
        }

        // 条件检查
        return condition == null || condition.check(context);
    }

    /**
     * 检查是否可以触发
     */
    public boolean canTrigger(NationEventContext context) {
        // 冷却检查（由系统处理）
        // 这里只检查事件本身的条件
        return matches(context);
    }

    // ==================== 效果应用 ====================

    /**
     * 应用所有效果
     */
    public void apply(NationEventContext context) {
        for (EventEffect effect : effects) {
            effect.apply(this, context);
        }
    }

    // ==================== 事件链 ====================

    public List<String> getChainEventIds() {
        return chainEventIds;
    }

    public int getChainDelaySeconds() {
        return chainDelaySeconds;
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public EventRarity getRarity() { return rarity; }
    public boolean isGlobal() { return global; }
    public int getPriority() { return priority; }
    public String getIcon() { return icon; }
    public String getSound() { return sound; }
    public boolean canIgnore() { return canIgnore; }
    public boolean isPersistent() { return persistent; }
    public int getMinStageLevel() { return minStageLevel; }
    public List<EventEffect> getEffects() { return effects; }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== Builder ====================

    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private EventRarity rarity = EventRarity.COMMON;
        private EventCondition condition;
        private List<EventEffect> effects = new ArrayList<>();
        private List<String> chainEventIds = new ArrayList<>();
        private int chainDelaySeconds = 60;
        private boolean global = false;
        private int priority = 0;
        private String icon = "BOOK";
        private String sound = "entity.experience_orb.pickup";
        private boolean canIgnore = false;
        private boolean persistent = false;
        private int minStageLevel = 1;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder rarity(EventRarity rarity) { this.rarity = rarity; return this; }
        public Builder condition(EventCondition cond) { this.condition = cond; return this; }
        public Builder addEffect(EventEffect effect) { this.effects.add(effect); return this; }

        public Builder effect(EventEffect effect) { this.effects.add(effect); return this; }

        public Builder minStage(int stage) { this.minStageLevel = stage; return this; }
        public Builder chainEvent(String chainId) { this.chainEventIds.add(chainId); return this; }
        public Builder chainDelay(int seconds) { this.chainDelaySeconds = seconds; return this; }
        public Builder global() { this.global = true; return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        public Builder icon(String icon) { this.icon = icon; return this; }
        public Builder sound(String sound) { this.sound = sound; return this; }
        public Builder canIgnore() { this.canIgnore = true; return this; }
        public Builder persistent() { this.persistent = true; return this; }

        public AdvancedEvent build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Event ID is required");
            }
            return new AdvancedEvent(this);
        }
    }
}
