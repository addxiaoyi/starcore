package dev.starcore.starcore.module.banner.model;

/**
 * Available banner pattern types
 */
public enum BannerPattern {
    PLAIN("plain", "纯色旗帜", 0),
    STRIPE_VERTICAL("stripe_v", "竖条纹", 100),
    STRIPE_HORIZONTAL("stripe_h", "横条纹", 100),
    CROSS("cross", "十字", 150),
    DIAGONAL("diagonal", "斜纹", 120),
    DIAGONAL_REVERSE("diagonal_r", "反斜纹", 120),
    BORDER("border", "边框", 200),
    BORDER_GRADIENT("border_g", "渐变边框", 300),
    FLOWER("flower", "花纹", 500),
    GRADIENT("gradient", "渐变", 250),
    TRIANGLE("triangle", "三角", 180),
    TRIANGLE_INVERTED("triangle_i", "倒三角", 180),
    DIAMOND("diamond", "菱形", 250),
    GRADIENT_DIAMOND("diamond_g", "渐变菱形", 350),
    CIRCLE("circle", "圆形", 300),
    RHOMBUS("rhombus", "方块", 200),
    SQUARE("square", "方形", 150),
    CREEPER("creeper", "苦力怕", 800),
    SKULL("skull", "骷髅", 800),
    FLOWER_CHARGE("flower_charge", "花朵纹章", 600),
    MOJANG("mojang", "Mojang", 1000),
    GLOBE("globe", "地球", 1200),
    ILLAGER("illager", "掠夺者", 900);

    private final String key;
    private final String displayName;
    private final int cost;

    BannerPattern(String key, String displayName, int cost) {
        this.key = key;
        this.displayName = displayName;
        this.cost = cost;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int cost() {
        return cost;
    }

    public static BannerPattern fromKey(String key) {
        for (BannerPattern pattern : values()) {
            if (pattern.key.equalsIgnoreCase(key)) {
                return pattern;
            }
        }
        return PLAIN;
    }

    public static BannerPattern fromName(String name) {
        for (BannerPattern pattern : values()) {
            if (pattern.displayName.equalsIgnoreCase(name) || pattern.name().equalsIgnoreCase(name)) {
                return pattern;
            }
        }
        return PLAIN;
    }
}
