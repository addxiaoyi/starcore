package dev.starcore.starcore.title;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.time.Instant;
import java.util.List;

/**
 * 称号定义
 * 代表一个可装备的玩家称号
 */
public record Title(
    String id,
    Component name,
    Component description,
    String color,
    int priority,
    TitleType type,
    Material icon,
    List<String> unlockConditions,
    List<String> rewards,
    boolean hidden
) {

    /**
     * 称号类型
     */
    public enum TitleType {
        /** 新手称号 - 白色 */
        NOVICE("§f"),
        /** 成就称号 - 绿色 */
        ACHIEVEMENT("§a"),
        /** 排名称号 - 金色 */
        RANKING("§6"),
        /** 特殊称号 - 紫色 */
        SPECIAL("§5"),
        /** 节日称号 - 黄色 */
        SEASONAL("§e"),
        /** 传奇称号 - 红色 */
        LEGENDARY("§c"),
        /** 荣誉称号 - 蓝色 */
        HONOR("§9");

        private final String defaultColor;

        TitleType(String defaultColor) {
            this.defaultColor = defaultColor;
        }

        public String getDefaultColor() {
            return defaultColor;
        }
    }

    /**
     * 获取格式化的称号名称
     */
    public Component getFormattedName() {
        return Component.text(color).append(name);
    }

    /**
     * 获取纯文本称号（用于PlaceholderAPI）
     */
    public String getPlainText() {
        return color + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(name);
    }

    /**
     * 创建称号构建器
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * 称号构建器
     */
    public static class Builder {
        private final String id;
        private Component name;
        private Component description;
        private String color;
        private int priority = 0;
        private TitleType type = TitleType.NOVICE;
        private Material icon = Material.NAME_TAG;
        private List<String> unlockConditions = List.of();
        private List<String> rewards = List.of();
        private boolean hidden = false;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(Component name) {
            this.name = name;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder type(TitleType type) {
            this.type = type;
            this.color = type.getDefaultColor();
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        public Builder unlockConditions(List<String> conditions) {
            this.unlockConditions = conditions;
            return this;
        }

        public Builder rewards(List<String> rewards) {
            this.rewards = rewards;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Title build() {
            if (name == null) {
                throw new IllegalStateException("Title name is required");
            }
            if (description == null) {
                throw new IllegalStateException("Title description is required");
            }
            if (color == null) {
                color = type.getDefaultColor();
            }
            return new Title(id, name, description, color, priority, type, icon,
                           unlockConditions, rewards, hidden);
        }
    }
}
