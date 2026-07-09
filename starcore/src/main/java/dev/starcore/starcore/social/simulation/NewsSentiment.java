package dev.starcore.starcore.social.simulation;

/**
 * 新闻情感分析
 */
public enum NewsSentiment {
    POSITIVE("正面", "§a"),
    NEUTRAL("中立", "§f"),
    NEGATIVE("负面", "§c");

    private final String name;
    private final String color;

    NewsSentiment(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public String getColor() { return color; }
}
