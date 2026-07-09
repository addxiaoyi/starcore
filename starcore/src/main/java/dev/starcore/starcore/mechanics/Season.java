package dev.starcore.starcore.mechanics;

/**
 * 季节枚举
 * 四季系统的核心枚举类
 */
public enum Season {

    SPRING("春季", 1),
    SUMMER("夏季", 2),
    AUTUMN("秋季", 3),
    WINTER("冬季", 4);

    private final String displayName;
    private final int order;

    Season(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    /**
     * 获取下一个季节
     */
    public Season next() {
        Season[] seasons = values();
        return seasons[(this.ordinal() + 1) % seasons.length];
    }

    /**
     * 获取上一个季节
     */
    public Season previous() {
        Season[] seasons = values();
        int index = (this.ordinal() - 1 + seasons.length) % seasons.length;
        return seasons[index];
    }

    /**
     * 根据月份获取季节
     */
    public static Season fromMonth(int month) {
        if (month >= 3 && month <= 5) return SPRING;
        if (month >= 6 && month <= 8) return SUMMER;
        if (month >= 9 && month <= 11) return AUTUMN;
        return WINTER;
    }

    /**
     * 根据游戏天数获取季节
     * @param days 游戏天数
     * @param daysPerSeason 每个季节持续的天数
     */
    public static Season fromGameDays(long days, int daysPerSeason) {
        int seasonIndex = (int) ((days / daysPerSeason) % 4);
        return values()[seasonIndex];
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getOrder() {
        return order;
    }

    /**
     * 获取季节颜色代码
     */
    public String getColorCode() {
        switch (this) {
            case SPRING: return "§a"; // 绿色
            case SUMMER: return "§6"; // 金色
            case AUTUMN: return "§c"; // 红色
            case WINTER: return "§b"; // 青色
            default: return "§f";
        }
    }

    /**
     * 获取季节图标
     */
    public String getIcon() {
        switch (this) {
            case SPRING: return "🌸";
            case SUMMER: return "☀";
            case AUTUMN: return "🍂";
            case WINTER: return "❄";
            default: return "●";
        }
    }
}
