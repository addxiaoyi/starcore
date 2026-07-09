package dev.starcore.starcore.foundation.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * HUD 毛玻璃样式枚举
 * 提供多种渐变色玻璃板组合来模拟毛玻璃效果
 */
public enum GlassPaneStyle {
    /**
     * 夜幕风格 - 深蓝紫色调
     */
    NIGHTMARE(
        new GlassColor(0, 25, 51, 0),      // 深蓝背景
        new GlassColor(25, 25, 112, 1),    // 午夜蓝边框
        new GlassColor(138, 43, 226, 2),   // 蓝紫色装饰
        NamedTextColor.DARK_BLUE,
        NamedTextColor.AQUA
    ),

    /**
     * 水晶风格 - 透明冰蓝色调
     */
    CRYSTAL(
        new GlassColor(173, 216, 230, 0),  // 浅蓝色背景
        new GlassColor(0, 191, 255, 1),    // 深天蓝边框
        new GlassColor(135, 206, 250, 2),  // 淡蓝色装饰
        NamedTextColor.AQUA,
        NamedTextColor.WHITE
    ),

    /**
     * 森林风格 - 自然绿色调
     */
    FOREST(
        new GlassColor(34, 139, 34, 0),    // 森林绿背景
        new GlassColor(0, 100, 0, 1),      // 深绿边框
        new GlassColor(144, 238, 144, 2),  // 浅绿装饰
        NamedTextColor.DARK_GREEN,
        NamedTextColor.GREEN
    ),

    /**
     * 烈焰风格 - 火红橙色调
     */
    INFERNO(
        new GlassColor(139, 0, 0, 0),      // 深红背景
        new GlassColor(255, 69, 0, 1),     // 橙红色边框
        new GlassColor(255, 140, 0, 2),    // 深橙色装饰
        NamedTextColor.DARK_RED,
        NamedTextColor.GOLD
    ),

    /**
     * 皇家风格 - 金色紫色调
     */
    ROYAL(
        new GlassColor(75, 0, 130, 0),     // 靛蓝背景
        new GlassColor(148, 0, 211, 1),    // 紫色边框
        new GlassColor(255, 215, 0, 2),    // 金色装饰
        NamedTextColor.DARK_PURPLE,
        NamedTextColor.GOLD
    ),

    /**
     * 极光风格 - 霓虹色调
     */
    AURORA(
        new GlassColor(0, 128, 128, 0),    // 青色背景
        new GlassColor(255, 0, 255, 1),    // 品红边框
        new GlassColor(0, 255, 255, 2),    // 青色装饰
        NamedTextColor.DARK_AQUA,
        NamedTextColor.LIGHT_PURPLE
    ),

    /**
     * 经典风格 - 中性灰白色调
     */
    CLASSIC(
        new GlassColor(64, 64, 64, 0),     // 深灰背景
        new GlassColor(128, 128, 128, 1),  // 中灰边框
        new GlassColor(192, 192, 192, 2),  // 浅灰装饰
        NamedTextColor.GRAY,
        NamedTextColor.WHITE
    ),

    /**
     * 彩虹风格 - 动态彩色（需配合动画）
     */
    RAINBOW(
        new GlassColor(255, 0, 0, 0),      // 红色背景（会被动画覆盖）
        new GlassColor(255, 127, 0, 1),    // 橙色边框
        new GlassColor(255, 255, 0, 2),    // 黄色装饰
        NamedTextColor.RED,
        NamedTextColor.YELLOW
    );

    private final GlassColor background;
    private final GlassColor border;
    private final GlassColor accent;
    private final TextColor titleColor;
    private final TextColor highlightColor;

    GlassPaneStyle(GlassColor background, GlassColor border, GlassColor accent,
                   TextColor titleColor, TextColor highlightColor) {
        this.background = background;
        this.border = border;
        this.accent = accent;
        this.titleColor = titleColor;
        this.highlightColor = highlightColor;
    }

    public GlassColor getBackground() { return background; }
    public GlassColor getBorder() { return border; }
    public GlassColor getAccent() { return accent; }
    public TextColor getTitleColor() { return titleColor; }
    public TextColor getHighlightColor() { return highlightColor; }

    /**
     * 获取背景玻璃材质
     */
    public Material getBackgroundMaterial() {
        return background.material;
    }

    /**
     * 获取边框玻璃材质
     */
    public Material getBorderMaterial() {
        return border.material;
    }

    /**
     * 获取装饰玻璃材质
     */
    public Material getAccentMaterial() {
        return accent.material;
    }

    /**
     * 玻璃颜色配置
     */
    public static class GlassColor {
        private final int r, g, b;
        private final int layer;
        private final Material material;

        public GlassColor(int r, int g, int b, int layer) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.layer = layer;
            this.material = determineMaterial(r, g, b);
        }

        private Material determineMaterial(int r, int g, int b) {
            // 根据颜色确定最接近的染色玻璃板
            // 黑色到白色的灰度
            int avg = (r + g + b) / 3;
            if (avg < 32) return Material.BLACK_STAINED_GLASS_PANE;
            if (avg < 96) return Material.GRAY_STAINED_GLASS_PANE;
            if (avg < 160) return Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            if (avg < 224) return Material.WHITE_STAINED_GLASS_PANE;
            return Material.WHITE_STAINED_GLASS_PANE;
        }

        public int getR() { return r; }
        public int getG() { return g; }
        public int getB() { return b; }
        public int getLayer() { return layer; }
        public Material getMaterial() { return material; }

        /**
         * 获取十六进制颜色代码
         */
        public String toHex() {
            return String.format("#%02X%02X%02X", r, g, b);
        }
    }

    /**
     * 获取所有样式
     */
    public static GlassPaneStyle[] valuesExcludingRainbow() {
        GlassPaneStyle[] styles = new GlassPaneStyle[values().length - 1];
        int index = 0;
        for (GlassPaneStyle style : values()) {
            if (style != RAINBOW) {
                styles[index++] = style;
            }
        }
        return styles;
    }
}