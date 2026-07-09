package dev.starcore.starcore.title;

import java.util.List;

/**
 * 称号显示配置
 * 控制称号在各个位置的显示方式
 */
public class TitleDisplayConfig {

    /**
     * Tab列表显示配置
     */
    public static class TabDisplayConfig {
        private boolean enabled;
        private String prefixFormat;
        private String suffixFormat;
        private int priority;

        public TabDisplayConfig(boolean enabled, String prefixFormat, String suffixFormat, int priority) {
            this.enabled = enabled;
            this.prefixFormat = prefixFormat;
            this.suffixFormat = suffixFormat;
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getPrefixFormat() {
            return prefixFormat;
        }

        public String getSuffixFormat() {
            return suffixFormat;
        }

        public int getPriority() {
            return priority;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setPrefixFormat(String prefixFormat) {
            this.prefixFormat = prefixFormat;
        }

        public void setSuffixFormat(String suffixFormat) {
            this.suffixFormat = suffixFormat;
        }
    }

    /**
     * 名字前缀显示配置
     */
    public static class NamePrefixConfig {
        private boolean enabled;
        private String format;

        public NamePrefixConfig(boolean enabled, String format) {
            this.enabled = enabled;
            this.format = format;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getFormat() {
            return format;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    /**
     * 全息投影显示配置
     */
    public static class HologramConfig {
        private boolean enabled;
        private List<String> lines;
        private double offset;
        private boolean followPlayer;

        public HologramConfig(boolean enabled, List<String> lines, double offset, boolean followPlayer) {
            this.enabled = enabled;
            this.lines = lines;
            this.offset = offset;
            this.followPlayer = followPlayer;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getLines() {
            return lines;
        }

        public double getOffset() {
            return offset;
        }

        public boolean isFollowPlayer() {
            return followPlayer;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setLines(List<String> lines) {
            this.lines = lines;
        }

        public void setOffset(double offset) {
            this.offset = offset;
        }
    }

    /**
     * 聊天前缀配置
     */
    public static class ChatPrefixConfig {
        private boolean enabled;
        private String format;
        private int priority;

        public ChatPrefixConfig(boolean enabled, String format, int priority) {
            this.enabled = enabled;
            this.format = format;
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getFormat() {
            return format;
        }

        public int getPriority() {
            return priority;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    /**
     * 记分板显示配置
     */
    public static class ScoreboardConfig {
        private boolean enabled;
        private String teamFormat;
        private int priority;

        public ScoreboardConfig(boolean enabled, String teamFormat, int priority) {
            this.enabled = enabled;
            this.teamFormat = teamFormat;
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getTeamFormat() {
            return teamFormat;
        }

        public int getPriority() {
            return priority;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setTeamFormat(String teamFormat) {
            this.teamFormat = teamFormat;
        }
    }

    // 配置实例
    private TabDisplayConfig tabConfig;
    private NamePrefixConfig namePrefixConfig;
    private HologramConfig hologramConfig;
    private ChatPrefixConfig chatConfig;
    private ScoreboardConfig scoreboardConfig;

    public TitleDisplayConfig() {
        // 默认配置
        this.tabConfig = new TabDisplayConfig(true, "[{title}] ", "", 100);
        this.namePrefixConfig = new NamePrefixConfig(true, "[{badge}]");
        this.hologramConfig = new HologramConfig(
            true,
            List.of("&e{title}", "&7{nation}"),
            -0.3,
            true
        );
        this.chatConfig = new ChatPrefixConfig(true, "[{title}] ", 100);
        this.scoreboardConfig = new ScoreboardConfig(true, "{title}", 100);
    }

    // Getters and Setters

    public TabDisplayConfig getTabConfig() {
        return tabConfig;
    }

    public void setTabConfig(TabDisplayConfig tabConfig) {
        this.tabConfig = tabConfig;
    }

    public NamePrefixConfig getNamePrefixConfig() {
        return namePrefixConfig;
    }

    public void setNamePrefixConfig(NamePrefixConfig namePrefixConfig) {
        this.namePrefixConfig = namePrefixConfig;
    }

    public HologramConfig getHologramConfig() {
        return hologramConfig;
    }

    public void setHologramConfig(HologramConfig hologramConfig) {
        this.hologramConfig = hologramConfig;
    }

    public ChatPrefixConfig getChatConfig() {
        return chatConfig;
    }

    public void setChatConfig(ChatPrefixConfig chatConfig) {
        this.chatConfig = chatConfig;
    }

    public ScoreboardConfig getScoreboardConfig() {
        return scoreboardConfig;
    }

    public void setScoreboardConfig(ScoreboardConfig scoreboardConfig) {
        this.scoreboardConfig = scoreboardConfig;
    }

    /**
     * 应用占位符替换
     */
    public String applyPlaceholders(String format, TitlePlaceholders placeholders) {
        if (format == null) {
            return "";
        }

        return format
            .replace("{title}", placeholders.title())
            .replace("{badge}", placeholders.badge())
            .replace("{nation}", placeholders.nation())
            .replace("{rank}", placeholders.rank())
            .replace("{player}", placeholders.playerName());
    }

    /**
     * 占位符数据记录
     */
    public record TitlePlaceholders(
        String title,
        String badge,
        String nation,
        String rank,
        String playerName
    ) {
        public static TitlePlaceholders empty(String playerName) {
            return new TitlePlaceholders("", "", "", "", playerName);
        }
    }
}
