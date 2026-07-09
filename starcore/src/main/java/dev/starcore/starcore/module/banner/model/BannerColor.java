package dev.starcore.starcore.module.banner.model;

/**
 * Available banner colors
 */
public enum BannerColor {
    WHITE("WHITE", "白色", 15, 0),
    ORANGE("ORANGE", "橙色", 14, 1),
    MAGENTA("MAGENTA", "品红", 13, 2),
    LIGHT_BLUE("LIGHT_BLUE", "浅蓝", 11, 3),
    YELLOW("YELLOW", "黄色", 4, 4),
    LIME("LIME", "黄绿", 5, 5),
    PINK("PINK", "粉色", 6, 6),
    GRAY("GRAY", "灰色", 8, 7),
    LIGHT_GRAY("LIGHT_GRAY", "浅灰", 7, 8),
    CYAN("CYAN", "青色", 9, 9),
    PURPLE("PURPLE", "紫色", 10, 10),
    BLUE("BLUE", "蓝色", 3, 11),
    BROWN("BROWN", "棕色", 12, 12),
    GREEN("GREEN", "绿色", 2, 13),
    RED("RED", "红色", 1, 14),
    BLACK("BLACK", "黑色", 0, 15);

    private final String key;
    private final String displayName;
    private final int dyeData;
    private final int woolData;

    BannerColor(String key, String displayName, int dyeData, int woolData) {
        this.key = key;
        this.displayName = displayName;
        this.dyeData = dyeData;
        this.woolData = woolData;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int dyeData() {
        return dyeData;
    }

    public int woolData() {
        return woolData;
    }

    public static BannerColor fromKey(String key) {
        for (BannerColor color : values()) {
            if (color.key.equalsIgnoreCase(key)) {
                return color;
            }
        }
        return WHITE;
    }

    public static BannerColor fromDyeData(int data) {
        for (BannerColor color : values()) {
            if (color.dyeData == data) {
                return color;
            }
        }
        return WHITE;
    }
}
