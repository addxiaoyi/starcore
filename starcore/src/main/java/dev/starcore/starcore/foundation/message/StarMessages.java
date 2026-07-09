package dev.starcore.starcore.foundation.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

/**
 * 现代化聊天消息样式工具。
 * 统一前缀、语义化配色、分隔线与高亮，供全插件命令反馈使用。
 *
 * 配色规范：
 * - 成功 #5BF0A5  失败 #FF6B6B  警告 #FFC83D  信息 #8AB4FF
 * - 强调/数值 #FFD479  次要说明 灰色
 */
public final class StarMessages {
    private StarMessages() {}

    public static final TextColor BRAND_A = TextColor.color(0x5BC8FF); // 青
    public static final TextColor BRAND_B = TextColor.color(0xFFD479); // 金
    public static final TextColor SUCCESS = TextColor.color(0x5BF0A5);
    public static final TextColor ERROR   = TextColor.color(0xFF6B6B);
    public static final TextColor WARN    = TextColor.color(0xFFC83D);
    public static final TextColor INFO    = TextColor.color(0x8AB4FF);
    public static final TextColor ACCENT  = TextColor.color(0xFFD479);
    public static final TextColor MUTED   = TextColor.color(0x8A93A6);

    /** 品牌前缀：✦ 星核 ✦ （青→金） */
    public static Component prefix() {
        return Component.text("✦", BRAND_B)
            .append(Component.text(" 星核 ", BRAND_A).decoration(TextDecoration.BOLD, true))
            .append(Component.text("✦ ", BRAND_B));
    }

    private static Component line(TextColor accent, String iconText, String message) {
        return prefix()
            .append(Component.text(iconText + " ", accent))
            .append(Component.text(message, NamedTextColor.WHITE));
    }

    public static void success(CommandSender to, String message) {
        to.sendMessage(line(SUCCESS, "✔", message));
    }

    public static void error(CommandSender to, String message) {
        to.sendMessage(line(ERROR, "✘", message));
    }

    public static void warn(CommandSender to, String message) {
        to.sendMessage(line(WARN, "⚠", message));
    }

    public static void info(CommandSender to, String message) {
        to.sendMessage(line(INFO, "✦", message));
    }

    /** 标题分隔行：───── 标题 ───── */
    public static void header(CommandSender to, String title) {
        Component bar = Component.text("─────", MUTED);
        to.sendMessage(Component.empty()
            .append(bar)
            .append(Component.text(" " + title + " ", ACCENT).decoration(TextDecoration.BOLD, true))
            .append(bar));
    }

    /**
     * 帮助/列表条目： ▸ 用法  - 说明
     * 鼠标悬停显示提示文本。
     */
    public static void entry(CommandSender to, String usage, String description) {
        Component c = Component.text(" ▸ ", BRAND_A)
            .append(Component.text(usage, ACCENT))
            .append(Component.text("  " + description, MUTED));
        to.sendMessage(c);
    }

    /** 键值高亮： 标签: 值 */
    public static Component keyValue(String key, String value) {
        return Component.text(key + ": ", MUTED)
            .append(Component.text(value, ACCENT));
    }

    /** 带悬停说明的成功消息 */
    public static void successHover(CommandSender to, String message, String hover) {
        to.sendMessage(line(SUCCESS, "✔", message)
            .hoverEvent(HoverEvent.showText(Component.text(hover, MUTED))));
    }
}
