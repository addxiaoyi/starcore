package dev.starcore.starcore.integration.placeholder;

import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.city.City;
import dev.starcore.starcore.city.CityManager;
import dev.starcore.starcore.ranking.NationRankingCache;
import dev.starcore.starcore.ranking.RankPeriod;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * PlaceholderAPI集成
 * 提供StarCore的所有变量
 */
public class StarCorePlaceholder extends PlaceholderExpansion {

    private final ClanManager clanManager;
    private final CityManager cityManager;
    private final NationRankingCache rankingCache;

    public StarCorePlaceholder(ClanManager clanManager,
                               CityManager cityManager,
                               NationRankingCache rankingCache) {
        this.clanManager = clanManager;
        this.cityManager = cityManager;
        this.rankingCache = rankingCache;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "stc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "StarCore Team";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
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

        UUID playerId = player.getUniqueId();

        // ==================== Clan变量 ====================

        // %starcore_clan_tag%
        if (params.equals("clan_tag")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? clan.getTag() : "";
        }

        // %starcore_clan_name%
        if (params.equals("clan_name")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? clan.getName() : "";
        }

        // %starcore_clan_colored_tag%
        if (params.equals("clan_colored_tag")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? clan.getColoredTag() : "";
        }

        // %starcore_clan_display_name%
        if (params.equals("clan_display_name")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? clan.getDisplayName() : "";
        }

        // %starcore_clan_members%
        if (params.equals("clan_members")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? String.valueOf(clan.getMemberCount()) : "0";
        }

        // %starcore_clan_kills%
        if (params.equals("clan_kills")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? String.valueOf(clan.getKills()) : "0";
        }

        // %starcore_clan_deaths%
        if (params.equals("clan_deaths")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? String.valueOf(clan.getDeaths()) : "0";
        }

        // %starcore_clan_kdr%
        if (params.equals("clan_kdr")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null ? String.format("%.2f", clan.getKDR()) : "0.00";
        }

        // %starcore_clan_is_leader%
        if (params.equals("clan_is_leader")) {
            Clan clan = clanManager.getPlayerClan(playerId);
            return clan != null && clan.isLeader(playerId) ? "true" : "false";
        }

        // ==================== City变量 ====================

        // %starcore_city_name%
        if (params.equals("city_name")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null ? city.getName() : "";
        }

        // %starcore_city_type%
        if (params.equals("city_type")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null ? city.getType().getDisplayName() : "";
        }

        // %starcore_city_colored_type%
        if (params.equals("city_colored_type")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null ? city.getColoredTypeName() : "";
        }

        // %starcore_city_level%
        if (params.equals("city_level")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null ? String.valueOf(city.getLevel()) : "0";
        }

        // %starcore_city_residents%
        if (params.equals("city_residents")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null ? String.valueOf(city.getResidentCount()) : "0";
        }

        // %starcore_city_is_mayor%
        if (params.equals("city_is_mayor")) {
            City city = cityManager.getPlayerCity(playerId);
            return city != null && city.isMayor(playerId) ? "true" : "false";
        }

        // ==================== 排行榜变量 ====================

        // %starcore_rank_kills_daily%
        if (params.equals("rank_kills_daily")) {
            return getRankPosition(playerId, "kills", RankPeriod.DAILY);
        }

        // %starcore_rank_kills_weekly%
        if (params.equals("rank_kills_weekly")) {
            return getRankPosition(playerId, "kills", RankPeriod.WEEKLY);
        }

        // %starcore_rank_kills_monthly%
        if (params.equals("rank_kills_monthly")) {
            return getRankPosition(playerId, "kills", RankPeriod.MONTHLY);
        }

        // %starcore_rank_kills_alltime%
        if (params.equals("rank_kills_alltime")) {
            return getRankPosition(playerId, "kills", RankPeriod.ALLTIME);
        }

        // %starcore_kills_daily%
        if (params.equals("kills_daily")) {
            return getStatValue(playerId, "kills", RankPeriod.DAILY);
        }

        // %starcore_kills_weekly%
        if (params.equals("kills_weekly")) {
            return getStatValue(playerId, "kills", RankPeriod.WEEKLY);
        }

        // %starcore_kills_monthly%
        if (params.equals("kills_monthly")) {
            return getStatValue(playerId, "kills", RankPeriod.MONTHLY);
        }

        // %starcore_kills_alltime%
        if (params.equals("kills_alltime")) {
            return getStatValue(playerId, "kills", RankPeriod.ALLTIME);
        }

        return null;
    }

    /**
     * 获取排名位置
     */
    private String getRankPosition(UUID playerId, String statType, RankPeriod period) {
        try {
            int position = rankingCache.getPosition(playerId, statType, period)
                .get(); // 阻塞获取
            return position > 0 ? String.valueOf(position) : "-";
        } catch (Exception e) {
            return "-";
        }
    }

    /**
     * 获取统计值
     */
    private String getStatValue(UUID playerId, String statType, RankPeriod period) {
        try {
            var future = rankingCache.getStats(playerId);
            var data = future.join();
            if (data == null) {
                return "0";
            }
            return switch (period) {
                case DAILY -> String.valueOf(data.getDailyValue());
                case WEEKLY -> String.valueOf(data.getWeeklyValue());
                case MONTHLY -> String.valueOf(data.getMonthlyValue());
                case ALLTIME -> String.valueOf(data.getAllTimeValue());
                default -> "0";
            };
        } catch (Exception e) {
            return "0";
        }
    }
}
