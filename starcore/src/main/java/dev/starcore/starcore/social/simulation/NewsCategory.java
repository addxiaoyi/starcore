package dev.starcore.starcore.social.simulation;

/**
 * 新闻分类
 */
public enum NewsCategory {
    SOCIAL("社交", "§a"),
    WAR("战争", "§c"),
    POLITICS("政治", "§b"),
    ECONOMY("经济", "§e"),
    CULTURE("文化", "§d"),
    FESTIVAL("节日", "§6"),
    ANNOUNCEMENT("公告", "§f");

    private final String name;
    private final String color;

    NewsCategory(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public String getColor() { return color; }
}
