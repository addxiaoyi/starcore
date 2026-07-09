package dev.starcore.starcore.util;

import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 消息工具类
 * 统一管理所有消息发送和格式化
 */
public class MessageUtil {

    // 插件前缀
    private static final String PREFIX = "§6[StarCore] §r";

    // 静态服务引用（通过 init 方法初始化）
    private static NationService nationService;
    private static ClanManager clanManager;

    // 颜色代码常量
    public static final String SUCCESS = "§a";
    public static final String ERROR = "§c";
    public static final String WARNING = "§e";
    public static final String INFO = "§7";
    public static final String HIGHLIGHT = "§e";
    public static final String PRIMARY = "§6";
    public static final String SECONDARY = "§b";

    // 基础颜色
    public static final String BLACK = "§0";
    public static final String DARK_BLUE = "§1";
    public static final String DARK_GREEN = "§2";
    public static final String DARK_AQUA = "§3";
    public static final String DARK_RED = "§4";
    public static final String DARK_PURPLE = "§5";
    public static final String GOLD = "§6";
    public static final String GRAY = "§7";
    public static final String DARK_GRAY = "§8";
    public static final String BLUE = "§9";
    public static final String GREEN = "§a";
    public static final String AQUA = "§b";
    public static final String RED = "§c";
    public static final String LIGHT_PURPLE = "§d";
    public static final String YELLOW = "§e";
    public static final String WHITE = "§f";

    // 格式代码
    public static final String BOLD = "§l";
    public static final String STRIKETHROUGH = "§m";
    public static final String UNDERLINE = "§n";
    public static final String ITALIC = "§o";
    public static final String RESET = "§r";

    /**
     * 发送成功消息
     */
    public static void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + SUCCESS + message);
    }

    /**
     * 发送错误消息
     */
    public static void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + ERROR + message);
    }

    /**
     * 发送警告消息
     */
    public static void warning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + WARNING + message);
    }

    /**
     * 发送信息消息
     */
    public static void info(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + INFO + message);
    }

    /**
     * 发送普通消息（无前缀）
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    /**
     * 颜色代码转换（将 & 转换为 §）
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return message.replace('&', '§');
    }

    /**
     * 发送多行消息
     */
    public static void sendLines(CommandSender sender, String... lines) {
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    /**
     * 发送多行消息（列表）
     */
    public static void sendLines(CommandSender sender, List<String> lines) {
        lines.forEach(sender::sendMessage);
    }

    /**
     * 发送标题分隔符
     */
    public static void sendHeader(CommandSender sender, String title) {
        sender.sendMessage("§6§l==== " + title + " §6§l====");
    }

    /**
     * 发送分隔线
     */
    public static void sendDivider(CommandSender sender) {
        sender.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 广播消息（所有在线玩家）
     */
    public static void broadcast(String message) {
        Bukkit.broadcastMessage(PREFIX + message);
    }

    // ==================== 静态初始化 ====================

    /**
     * 初始化静态服务引用
     * 在插件启用时调用
     */
    public static void init(NationService nationService, ClanManager clanManager) {
        MessageUtil.nationService = nationService;
        MessageUtil.clanManager = clanManager;
    }

    /**
     * 清理静态引用
     * 在插件禁用时调用
     */
    public static void shutdown() {
        nationService = null;
        clanManager = null;
    }

    // ==================== 成员消息发送 ====================

    /**
     * 广播给特定Nation的所有成员
     */
    public static void broadcastToNation(UUID nationId, String message) {
        if (nationService == null) {
            Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(PREFIX + message)
            );
            return;
        }

        NationId id;
        try {
            id = NationId.of(UUID.fromString(nationId.toString()));
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<Nation> nationOpt = nationService.nationById(id);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        for (NationMember member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + message);
            }
        }
    }

    /**
     * 广播给特定Clan的所有成员
     */
    public static void broadcastToClan(UUID clanId, String message) {
        if (clanManager == null) {
            Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(PREFIX + message)
            );
            return;
        }

        Clan clan = clanManager.getClan(clanId);
        if (clan == null) {
            return;
        }

        for (UUID memberId : clan.getMembers()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + message);
            }
        }
    }

    /**
     * 发送消息给国家成员（Component版本）
     */
    public static void sendMessageToNationMembers(UUID nationId, Component message) {
        if (nationService == null) {
            return;
        }

        NationId id;
        try {
            id = NationId.of(UUID.fromString(nationId.toString()));
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<Nation> nationOpt = nationService.nationById(id);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        for (NationMember member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 发送消息给氏族成员（Component版本）
     */
    public static void sendMessageToClanMembers(UUID clanId, Component message) {
        if (clanManager == null) {
            return;
        }

        Clan clan = clanManager.getClan(clanId);
        if (clan == null) {
            return;
        }

        for (UUID memberId : clan.getMembers()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 发送Action Bar消息
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
    }

    /**
     * 发送Title消息
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, 10, 70, 20);
    }

    /**
     * 发送Title消息（自定义时间）
     */
    public static void sendTitle(Player player, String title, String subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    // ==================== 格式化方法 ====================

    /**
     * 格式化金额
     */
    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000);
        } else {
            return String.format("%.2f", amount);
        }
    }

    /**
     * 格式化数字
     */
    public static String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * 格式化时间（秒）
     */
    public static String formatTime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%d天%d小时", days, hours);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, secs);
        } else {
            return secs + "秒";
        }
    }

    /**
     * 格式化百分比
     */
    public static String formatPercentage(double value) {
        return String.format("%.1f%%", value * 100);
    }

    /**
     * 格式化位置
     */
    public static String formatLocation(org.bukkit.Location loc) {
        return String.format("%s (%d, %d, %d)",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        );
    }

    /**
     * 格式化玩家名称（添加Nation/Clan前缀颜色）
     */
    public static String formatPlayerName(Player player) {
        StringBuilder prefix = new StringBuilder();
        String playerColor = "§e"; // 默认金色

        // 添加Clan标签前缀
        if (clanManager != null) {
            Clan clan = clanManager.getPlayerClan(player.getUniqueId());
            if (clan != null) {
                prefix.append(clan.getColoredTag()).append(" ");
                playerColor = "§f"; // 白名
            }
        }

        // 添加Nation前缀
        if (nationService != null) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                Nation nation = nationOpt.get();
                // 国家名称颜色
                String nationColor = "§6"; // 默认金色
                if (nation.founderId().equals(player.getUniqueId())) {
                    nationColor = "§c"; // 创始人用红色
                }
                prefix.append(nationColor).append("[").append(nation.name()).append("]§r ");
                playerColor = "§f"; // 白名
            }
        }

        return prefix + playerColor + player.getName();
    }

    /**
     * 格式化玩家名称（根据玩家ID获取）
     */
    public static String formatPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            return formatPlayerName(player);
        }

        // 玩家不在线时只返回ID（无法获取完整前缀）
        return "§7" + playerId.toString().substring(0, 8);
    }

    /**
     * 格式化布尔值
     */
    public static String formatBoolean(boolean value) {
        return value ? "§a是" : "§c否";
    }

    /**
     * 格式化启用/禁用
     */
    public static String formatEnabled(boolean enabled) {
        return enabled ? "§a启用" : "§c禁用";
    }

    // ==================== 进度条 ====================

    /**
     * 创建进度条
     */
    public static String createProgressBar(double current, double max, int length) {
        double percentage = Math.min(1.0, current / max);
        int filled = (int) (length * percentage);
        int empty = length - filled;

        String color = percentage >= 0.75 ? "§a" :
                      percentage >= 0.50 ? "§e" :
                      percentage >= 0.25 ? "§6" : "§c";

        return color + "█".repeat(filled) + "§7█".repeat(empty) +
               " §f" + String.format("%.1f%%", percentage * 100);
    }

    /**
     * 创建健康条
     */
    public static String createHealthBar(double health, double maxHealth) {
        return createProgressBar(health, maxHealth, 10);
    }

    // ==================== 列表格式化 ====================

    /**
     * 创建列表项
     */
    public static String listItem(String bullet, String text) {
        return "  " + bullet + " §7" + text;
    }

    /**
     * 创建编号列表项
     */
    public static String numberedItem(int number, String text) {
        return "  §e" + number + ". §7" + text;
    }

    /**
     * 创建键值对
     */
    public static String keyValue(String key, String value) {
        return "§7" + key + ": §f" + value;
    }

    /**
     * 创建彩色键值对
     */
    public static String keyValue(String key, String value, String valueColor) {
        return "§7" + key + ": " + valueColor + value;
    }

    // ==================== 特殊消息 ====================

    /**
     * 发送使用说明
     */
    public static void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage("§7用法: §e" + usage);
    }

    /**
     * 发送权限不足消息
     */
    public static void noPermission(CommandSender sender) {
        error(sender, "你没有权限执行此命令");
    }

    /**
     * 发送玩家不在线消息
     */
    public static void playerNotOnline(CommandSender sender, String playerName) {
        error(sender, "玩家 " + playerName + " 不在线");
    }

    /**
     * 发送玩家未找到消息
     */
    public static void playerNotFound(CommandSender sender, String playerName) {
        error(sender, "找不到玩家: " + playerName);
    }

    /**
     * 发送无效参数消息
     */
    public static void invalidArgument(CommandSender sender, String argument) {
        error(sender, "无效的参数: " + argument);
    }

    /**
     * 发送必须是玩家消息
     */
    public static void mustBePlayer(CommandSender sender) {
        error(sender, "此命令只能由玩家执行");
    }

    // ==================== 确认消息 ====================

    /**
     * 发送确认请求
     */
    public static void sendConfirmation(Player player, String action) {
        sendLines(player,
            "§c§l[!] 确认操作",
            "§7你确定要 " + action + " 吗？",
            "§7输入 §a/confirm §7确认",
            "§7输入 §c/cancel §7取消"
        );
    }

    /**
     * 发送冷却时间消息
     */
    public static void cooldown(Player player, long remainingSeconds) {
        error(player, "请等待 " + formatTime(remainingSeconds) + " 后再试");
    }
}
