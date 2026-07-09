package dev.starcore.starcore.social.simulation;

import java.util.UUID;

/**
 * 排行榜条目数据
 * 记录玩家的排名信息
 */
public record InfluenceLeaderboardEntry(
    int rank,           // 排名
    UUID playerId,      // 玩家UUID
    String playerName,  // 玩家名称
    int influence,      // 影响力分数
    int change,         // 排名变化（正数上升，负数下降，0无变化）
    LeaderboardPeriod period  // 所属周期
) {
    /**
     * 创建带有变化的条目
     */
    public static InfluenceLeaderboardEntry of(int rank, UUID playerId, String playerName,
                                               int influence, int change, LeaderboardPeriod period) {
        return new InfluenceLeaderboardEntry(rank, playerId, playerName, influence, change, period);
    }

    /**
     * 获取排名字符串
     */
    public String getRankString() {
        return switch (rank) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> rank + "th";
        };
    }

    /**
     * 获取排名颜色代码
     */
    public String getRankColor() {
        return switch (rank) {
            case 1 -> "§6";   // 金色
            case 2 -> "§f";   // 银色
            case 3 -> "§c";   // 铜色
            case 4, 5, 6, 7, 8, 9, 10 -> "§e";  // 黄色
            default -> "§7";  // 灰色
        };
    }

    /**
     * 获取变化描述
     */
    public String getChangeDescription() {
        if (change > 0) {
            return "§a▲ +" + change;
        } else if (change < 0) {
            return "§c▼ " + change;
        } else {
            return "§7-";
        }
    }

    /**
     * 获取变化图标
     */
    public String getChangeIcon() {
        if (change > 0) {
            return "§a▲";
        } else if (change < 0) {
            return "§c▼";
        } else {
            return "§7◆";
        }
    }
}
