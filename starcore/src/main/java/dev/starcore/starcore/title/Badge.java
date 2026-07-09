package dev.starcore.starcore.title;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 徽章定义
 * 代表一个可装备的玩家徽章（图标）
 */
public record Badge(
    String id,
    Component name,
    String icon,
    Rarity rarity,
    Component description,
    List<String> unlockConditions
) {

    /**
     * 徽章稀有度
     */
    public enum Rarity {
        /** 普通 - 白色 */
        COMMON("§f", "⭐"),
        /** 罕见 - 绿色 */
        UNCOMMON("§a", "⭐⭐"),
        /** 稀有 - 蓝色 */
        RARE("§9", "⭐⭐⭐"),
        /** 史诗 - 紫色 */
        EPIC("§5", "⭐⭐⭐⭐"),
        /** 传奇 - 金色 */
        LEGENDARY("§6", "⭐⭐⭐⭐⭐");

        private final String color;
        private final String stars;

        Rarity(String color, String stars) {
            this.color = color;
            this.stars = stars;
        }

        public String getColor() {
            return color;
        }

        public String getStars() {
            return stars;
        }
    }

    /**
     * 获取格式化的徽章显示
     */
    public String getFormatted() {
        return rarity.getColor() + icon;
    }

    /**
     * 获取带颜色的名称
     */
    public Component getColoredName() {
        return Component.text(rarity.getColor()).append(name);
    }

    /**
     * 创建徽章构建器
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * 徽章构建器
     */
    public static class Builder {
        private final String id;
        private Component name;
        private String icon = "★";
        private Rarity rarity = Rarity.COMMON;
        private Component description;
        private List<String> unlockConditions = List.of();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(Component name) {
            this.name = name;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder unlockConditions(List<String> conditions) {
            this.unlockConditions = conditions;
            return this;
        }

        public Badge build() {
            if (name == null) {
                throw new IllegalStateException("Badge name is required");
            }
            if (description == null) {
                throw new IllegalStateException("Badge description is required");
            }
            return new Badge(id, name, icon, rarity, description, unlockConditions);
        }
    }
}
