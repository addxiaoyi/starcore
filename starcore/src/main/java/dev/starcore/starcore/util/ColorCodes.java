package dev.starcore.starcore.util;

/**
 * Minecraft 颜色代码常量类
 * 统一管理所有硬编码的颜色代码，便于主题修改和维护
 *
 * 颜色代码说明:
 * - 基础颜色: 0-9 (黑、深蓝、深绿、深青、深红、深粉、金、灰、深灰、蓝)
 * - 格式代码: k=随机 l=粗体 m=删除线 n=下划线 o=斜体 r=重置
 */
public final class ColorCodes {

    private ColorCodes() {
        // 工具类，禁止实例化
    }

    // ==================== 基础颜色 ====================

    /** 黑色 - §0 */
    public static final String BLACK = "§0";

    /** 深蓝色 - §1 */
    public static final String DARK_BLUE = "§1";

    /** 深绿色 - §2 */
    public static final String DARK_GREEN = "§2";

    /** 深青色 (蓝绿色) - §3 */
    public static final String DARK_AQUA = "§3";

    /** 深红色 - §4 */
    public static final String DARK_RED = "§4";

    /** 深粉色 (紫色) - §5 */
    public static final String DARK_PURPLE = "§5";

    /** 金色 (橙色) - §6 */
    public static final String GOLD = "§6";

    /** 灰色 - §7 */
    public static final String GRAY = "§7";

    /** 深灰色 - §8 */
    public static final String DARK_GRAY = "§8";

    /** 蓝色 - §9 */
    public static final String BLUE = "§9";

    /** 绿色 - §a */
    public static final String GREEN = "§a";

    /** 青色 (蓝绿色) - §b */
    public static final String AQUA = "§b";

    /** 红色 - §c */
    public static final String RED = "§c";

    /** 粉色 (浅紫色) - §d */
    public static final String LIGHT_PURPLE = "§d";

    /** 黄色 - §e */
    public static final String YELLOW = "§e";

    /** 白色 - §f */
    public static final String WHITE = "§f";

    // ==================== 格式代码 ====================

    /** 随机字符 - §k */
    public static final String MAGIC = "§k";

    /** 粗体 - §l */
    public static final String BOLD = "§l";

    /** 删除线 - §m */
    public static final String STRIKETHROUGH = "§m";

    /** 下划线 - §n */
    public static final String UNDERLINE = "§n";

    /** 斜体 - §o */
    public static final String ITALIC = "§o";

    /** 重置所有格式 - §r */
    public static final String RESET = "§r";

    // ==================== 常用组合 ====================

    // 标题类 (金色 + 粗体)
    public static final String TITLE = GOLD + BOLD;
    public static final String TITLE_DARK = DARK_PURPLE + BOLD;
    public static final String TITLE_AQUA = AQUA + BOLD;

    // 粗体组合
    public static final String GOLD_BOLD = GOLD + BOLD;
    public static final String RED_BOLD = RED + BOLD;
    public static final String WHITE_BOLD = WHITE + BOLD;

    // 状态提示
    public static final String SUCCESS = GREEN;      // 成功/可用
    public static final String WARNING = YELLOW;      // 警告/待处理
    public static final String ERROR = RED;           // 错误/不可用
    public static final String INFO = GRAY;          // 信息/次要
    public static final String SECONDARY = GRAY;     // 次要信息 (INFO 别名)
    public static final String HIGHLIGHT = GOLD;     // 高亮/强调

    // 边框/分隔线
    public static final String SEPARATOR = GRAY;

    // ==================== 便捷方法 ====================

    /**
     * 将文字包裹在指定颜色中
     */
    public static String color(String text, String colorCode) {
        return colorCode + text + RESET;
    }

    /**
     * 将文字设为金色粗体 (标题用)
     */
    public static String title(String text) {
        return TITLE + text + RESET;
    }

    /**
     * 将文字设为成功色 (绿色)
     */
    public static String success(String text) {
        return SUCCESS + text + RESET;
    }

    /**
     * 将文字设为错误色 (红色)
     */
    public static String error(String text) {
        return ERROR + text + RESET;
    }

    /**
     * 将文字设为次要信息色 (灰色)
     */
    public static String info(String text) {
        return INFO + text + RESET;
    }

    /**
     * 将文字设为高亮色 (黄色)
     */
    public static String highlight(String text) {
        return HIGHLIGHT + text + RESET;
    }

    /**
     * 创建进度条
     * @param filled 已填充的格数
     * @param total 总格数
     * @return 格式化后的进度条字符串
     */
    public static String progressBar(int filled, int total) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            if (i < filled) {
                sb.append(GREEN).append("█");
            } else {
                sb.append(GRAY).append("█");
            }
        }
        sb.append(RESET);
        return sb.toString();
    }
}
