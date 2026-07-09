package dev.starcore.starcore.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * 启动横幅和日志
 * 美化的控制台输出
 */
public final class StarCoreBanner {
    private static final Logger logger = Logger.getLogger("STARCORE");

    /**
     * 显示启动横幅
     */
    public static void printBanner(String version) {
        String[] banner = {
            "",
            "§6   _____ _______       _____   _____ ____  _____  ______ ",
            "§6  / ____|__   __|/\\   |  __ \\ / ____/ __ \\|  __ \\|  ____|",
            "§e | (___    | |  /  \\  | |__) | |   | |  | | |__) | |__   ",
            "§e  \\___ \\   | | / /\\ \\ |  _  /| |   | |  | |  _  /|  __|  ",
            "§6  ____) |  | |/ ____ \\| | \\ \\| |___| |__| | | \\ \\| |____ ",
            "§6 |_____/   |_/_/    \\_\\_|  \\_\\\\_____\\____/|_|  \\_\\______|",
            "",
            "§b                    ✦ 星核系统 ✦",
            "§7              下一代 Minecraft 核心插件",
            ""
        };

        System.out.println("");
        for (String line : banner) {
            System.out.println(line);
        }

        // 版本信息
        printCentered("§f版本: §e" + version, 60);
        printCentered("§f作者: §bSTARCORE Team", 60);
        printCentered("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", 60);
        System.out.println("");
    }

    /**
     * 打印加载信息
     */
    public static void printLoadingInfo() {
        log("§e▶ §f正在初始化 STARCORE 系统...");
        log("");
    }

    /**
     * 打印模块加载
     */
    public static void printModuleLoad(String moduleName, boolean success) {
        if (success) {
            log("§a✓ §f模块加载: §b" + moduleName + " §a成功");
        } else {
            log("§c✗ §f模块加载: §b" + moduleName + " §c失败");
        }
    }

    /**
     * 打印统计信息
     */
    public static void printStatistics(
        int totalModules,
        int loadedModules,
        int commands,
        int achievements,
        long loadTime
    ) {
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("§6                  ✦ 加载统计 ✦");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
        log("  §e模块:     §f" + loadedModules + "§7/§f" + totalModules + " §a已加载");
        log("  §e命令:     §f" + commands + " §a个");
        log("  §e成就:     §f" + achievements + " §a个");
        log("  §e加载时间: §f" + loadTime + "ms");
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
    }

    /**
     * 打印启动成功
     */
    public static void printStartupSuccess(String version) {
        log("");
        log("§a╔══════════════════════════════════════════════════╗");
        log("§a║                                                  ║");
        log("§a║         §f✦ §b§lSTARCORE §r§e" + version + " §a启动成功！ §f✦         §a║");
        log("§a║                                                  ║");
        log("§a║  §f感谢使用 STARCORE - 下一代 Minecraft 核心插件  §a║");
        log("§a║                                                  ║");
        log("§a╚══════════════════════════════════════════════════╝");
        log("");
        log("§7  插件文档: §bhttps://github.com/starcore");
        log("§7  问题反馈: §bhttps://github.com/starcore/issues");
        log("");
    }

    /**
     * 打印启动失败
     */
    public static void printStartupFailure(String reason) {
        log("");
        log("§c╔══════════════════════════════════════════════════╗");
        log("§c║                                                  ║");
        log("§c║              §f✗ §c§l启动失败！ §f✗                   §c║");
        log("§c║                                                  ║");
        log("§c╚══════════════════════════════════════════════════╝");
        log("");
        log("§c  原因: §f" + reason);
        log("");
        log("§7  请检查配置文件和依赖项");
        log("§7  需要帮助？访问: §bhttps://github.com/starcore/issues");
        log("");
    }

    /**
     * 打印关闭信息
     */
    public static void printShutdown() {
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("§e              ✦ STARCORE 正在关闭 ✦");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
        log("§e▶ §f保存玩家数据...");
    }

    /**
     * 打印关闭完成
     */
    public static void printShutdownComplete() {
        log("");
        log("§a✓ §fSTARCORE 已安全关闭");
        log("§7  感谢使用！再见！");
        log("");
    }

    /**
     * 打印性能信息
     */
    public static void printPerformanceInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("§6                  ✦ 性能信息 ✦");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
        log("  §e服务器: §f" + Bukkit.getName() + " " + Bukkit.getVersion());
        log("  §eJava:   §f" + System.getProperty("java.version"));
        log("  §e内存:   §f" + usedMemory + "MB §7/ §f" + maxMemory + "MB");
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
    }

    /**
     * 打印特性列表
     */
    public static void printFeatures() {
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("§6                  ✦ 核心特性 ✦");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
        log("  §a✓ §fEssentials 功能替代");
        log("  §a✓ §f高性能 PvP/决斗系统");
        log("  §a✓ §f完整的经济系统");
        log("  §a✓ §f智能成就系统");
        log("  §a✓ §fAI 训练机器人");
        log("  §a✓ §f公会/好友/派对系统");
        log("  §a✓ §f赛季通行证");
        log("  §a✓ §f现代化 GUI");
        log("  §a✓ §f高性能缓存系统");
        log("  §a✓ §f异步数据库操作");
        log("");
        log("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log("");
    }

    /**
     * 居中打印
     */
    private static void printCentered(String text, int width) {
        int padding = (width - stripColor(text).length()) / 2;
        String spaces = " ".repeat(Math.max(0, padding));
        System.out.println(spaces + text);
    }

    /**
     * 移除颜色代码
     */
    private static String stripColor(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * 日志输出
     */
    private static void log(String message) {
        // 移除颜色代码用于日志文件
        String cleanMessage = stripColor(message);

        // 控制台输出（带颜色）
        System.out.println(message);
    }

    /**
     * 打印调试信息
     */
    public static void printDebug(String message) {
        log("§8[DEBUG] §7" + message);
    }

    /**
     * 打印警告
     */
    public static void printWarning(String message) {
        log("§e⚠ [警告] §f" + message);
    }

    /**
     * 打印错误
     */
    public static void printError(String message) {
        log("§c✗ [错误] §f" + message);
    }

    /**
     * 打印信息
     */
    public static void printInfo(String message) {
        log("§b✦ [信息] §f" + message);
    }

    /**
     * 打印成功
     */
    public static void printSuccess(String message) {
        log("§a✓ [成功] §f" + message);
    }
}
