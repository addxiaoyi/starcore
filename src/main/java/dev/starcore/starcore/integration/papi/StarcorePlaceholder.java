package dev.starcore.starcore.integration.papi;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 集成
 * 提供 STARCORE 的所有占位符
 */
public final class StarcorePlaceholder extends PlaceholderExpansion {
    private final Plugin plugin;
    private final EconomyService economyService;
    private final PvPStatsService statsService;

    public StarcorePlaceholder(
        Plugin plugin,
        EconomyService economyService,
        PvPStatsService statsService
    ) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.statsService = statsService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "starcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "STARCORE Team";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // 经济占位符
        if (params.equals("balance")) {
            return String.format("%.2f", economyService.getBalance(player.getUniqueId()).doubleValue());
        }

        if (params.equals("balance_formatted")) {
            return formatNumber(economyService.getBalance(player.getUniqueId()).doubleValue());
        }

        // PvP统计占位符
        PvPStats stats = statsService.getStats(player.getUniqueId());
        if (stats != null) {
            return switch (params) {
                case "kills" -> String.valueOf(stats.getKills());
                case "deaths" -> String.valueOf(stats.getDeaths());
                case "assists" -> String.valueOf(stats.getAssists());
                case "kd" -> String.format("%.2f", stats.getKDRatio());
                case "kda" -> String.format("%.2f", stats.getKDA());
                case "killstreak" -> String.valueOf(stats.getCurrentKillStreak());
                case "best_killstreak" -> String.valueOf(stats.getBestKillStreak());
                case "duel_wins" -> String.valueOf(stats.getDuelWins());
                case "duel_losses" -> String.valueOf(stats.getDuelLosses());
                case "duel_winrate" -> String.format("%.1f%%", stats.getDuelWinRate());
                case "damage_dealt" -> String.format("%.0f", stats.getTotalDamageDealt());
                case "damage_taken" -> String.format("%.0f", stats.getTotalDamageTaken());
                default -> "";
            };
        }

        return "";
    }

    /**
     * 格式化数字
     */
    private String formatNumber(double number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000);
        } else {
            return String.format("%.2f", number);
        }
    }
}
