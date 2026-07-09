package dev.starcore.starcore.title;

import dev.starcore.starcore.achievement.AchievementService;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.i18n.I18nManager;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.ranking.RankPeriod;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * StarCore PlaceholderAPI 扩展
 * 提供所有StarCore相关的占位符变量。
 * 通过 ServiceRegistry 延迟获取各模块服务，因此可独立于称号模块注册。
 */
public class StarCorePlaceholderExpansion extends PlaceholderExpansion {
    private final TitleService titleService;
    private final ServiceRegistry serviceRegistry;

    public StarCorePlaceholderExpansion(TitleService titleService, ServiceRegistry serviceRegistry) {
        this.titleService = titleService;
        this.serviceRegistry = serviceRegistry;
    }

    private NationService nations() {
        return serviceRegistry == null ? null : serviceRegistry.find(NationService.class).orElse(null);
    }

    private EconomyService economy() {
        return serviceRegistry == null ? null : serviceRegistry.find(EconomyService.class).orElse(null);
    }

    private AchievementService achievements() {
        return serviceRegistry == null ? null : serviceRegistry.find(AchievementService.class).orElse(null);
    }

    private RankingService rankingService() {
        return serviceRegistry == null ? null : serviceRegistry.find(RankingService.class).orElse(null);
    }

    private PvPStatsService pvpStatsService() {
        return serviceRegistry == null ? null : serviceRegistry.find(PvPStatsService.class).orElse(null);
    }

    private WarService warService() {
        return serviceRegistry == null ? null : serviceRegistry.find(WarService.class).orElse(null);
    }

    private I18nManager i18nManager() {
        return serviceRegistry == null ? null : serviceRegistry.find(I18nManager.class).orElse(null);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "starcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "StarCore";
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
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // 称号相关
        switch (params.toLowerCase()) {
            case "title":
                return getEquippedTitle(player);

            case "title_color":
                return getTitleColor(player);

            case "titles_count":
                return String.valueOf(getTitlesCount(player));

            case "titles_unlocked":
                return String.valueOf(getTitlesCount(player));

            case "badge":
                return getEquippedBadge(player);

            case "badges_unlocked":
                return String.valueOf(getBadgesCount(player));

            case "badges_count":
                return String.valueOf(getBadgesCount(player));

            // 成就相关
            case "achievements":
                return getAchievementsProgress(player);

            case "achievements_count":
                return String.valueOf(getAchievementsCount(player));

            // 国家相关
            case "nation_name":
                return getNationName(player);

            case "nation_rank":
                return getNationRank(player);

            case "nation_leader":
                return getNationLeader(player);

            case "nation_population":
                return getNationPopulation(player);

            case "nation_territory_size":
                return getNationTerritorySize(player);

            case "nation_treasury":
                return getNationTreasury(player);

            // 经济相关
            case "balance":
                return getBalance(player);

            case "balance_formatted":
                return formatBalance(getBalance(player));

            // 统计相关
            case "online_time":
                return getOnlineTime(player);

            case "online_time_formatted":
                return formatTime(getOnlineTime(player));

            case "join_date":
                return getJoinDate(player);

            case "last_seen":
                return getLastSeen(player);

            // 排名相关
            case "rank":
                return getPlayerRank(player);

            case "rank_position":
                return getRankPosition(player);

            // 击杀相关
            case "kills":
                return getKills(player);

            case "kills_alltime":
                return getKillsAlltime(player);

            // 政府相关
            case "government_type":
                return getGovernmentType(player);

            case "government_role":
                return getGovernmentRole(player);

            // 战争相关
            case "war_status":
                return getWarStatus(player);

            case "war_score":
                return getWarScore(player);

            case "nation_at_war":
                return isNationAtWar(player) ? "参战中" : "和平";

            case "war_count":
                return getWarCount(player);

            default:
                // 检查是否是带参数的占位符
                return handleParameterizedPlaceholder(player, params);
        }
    }

    /**
     * 处理带参数的占位符
     */
    private String handleParameterizedPlaceholder(OfflinePlayer player, String params) {
        I18nManager i18n = i18nManager();
        Locale locale = player != null && player.getUniqueId() != null && i18n != null
            ? i18n.getPlayerLocale(player.getUniqueId())
            : Locale.SIMPLIFIED_CHINESE;

        // %starcore_title_<id>_unlocked% - 检查特定称号是否解锁
        if (params.startsWith("title_") && params.endsWith("_unlocked")) {
            String titleId = params.substring(6, params.length() - 9);
            return isTitleUnlocked(player, titleId) ? "true" : "false";
        }

        // %starcore_badge_<id>_unlocked% - 检查特定徽章是否解锁
        if (params.startsWith("badge_") && params.endsWith("_unlocked")) {
            String badgeId = params.substring(6, params.length() - 9);
            return isBadgeUnlocked(player, badgeId) ? "true" : "false";
        }

        // %starcore_lang% - 获取玩家当前语言代码
        if (params.equalsIgnoreCase("lang") || params.equalsIgnoreCase("language")) {
            return locale.toString();
        }

        // %starcore_lang_name% - 获取玩家当前语言显示名称
        if (params.equalsIgnoreCase("lang_name") || params.equalsIgnoreCase("language_name")) {
            if (i18n != null) {
                return i18n.getLocaleDisplayName(locale.toString());
            }
            return locale.getDisplayLanguage(locale);
        }

        // %starcore_msg_<key>% - 获取玩家的本地化消息
        if (params.startsWith("msg_")) {
            String key = params.substring(4);
            if (i18n != null) {
                String message = i18n.getMessage(locale, key);
                // 移除颜色代码，只返回纯文本
                return message.replaceAll("§[0-9a-fk-or]", "");
            }
            return key;
        }

        return "";
    }

    // ========== 称号相关方法 ==========

    private String getEquippedTitle(OfflinePlayer player) {
        CompletableFuture<String> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(data -> {
                if (data.getEquippedTitle().isPresent()) {
                    String titleId = data.getEquippedTitle().get();
                    return titleService.getTitle(titleId)
                        .map(Title::getPlainText)
                        .orElse("");
                }
                return "";
            });

        return future.getNow("");
    }

    private String getTitleColor(OfflinePlayer player) {
        CompletableFuture<String> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(data -> {
                if (data.getEquippedTitle().isPresent()) {
                    String titleId = data.getEquippedTitle().get();
                    return titleService.getTitle(titleId)
                        .map(Title::color)
                        .orElse("§f");
                }
                return "§f";
            });

        return future.getNow("§f");
    }

    private int getTitlesCount(OfflinePlayer player) {
        CompletableFuture<Integer> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(PlayerTitle::getTitleCount);

        return future.getNow(0);
    }

    private boolean isTitleUnlocked(OfflinePlayer player, String titleId) {
        CompletableFuture<Boolean> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(data -> data.hasTitleUnlocked(titleId));

        return future.getNow(false);
    }

    // ========== 徽章相关方法 ==========

    private String getEquippedBadge(OfflinePlayer player) {
        CompletableFuture<String> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(data -> {
                if (data.getEquippedBadge().isPresent()) {
                    String badgeId = data.getEquippedBadge().get();
                    return titleService.getBadge(badgeId)
                        .map(Badge::getFormatted)
                        .orElse("");
                }
                return "";
            });

        return future.getNow("");
    }

    private int getBadgesCount(OfflinePlayer player) {
        CompletableFuture<Integer> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(PlayerTitle::getBadgeCount);

        return future.getNow(0);
    }

    private boolean isBadgeUnlocked(OfflinePlayer player, String badgeId) {
        CompletableFuture<Boolean> future = titleService.getPlayerData(player.getUniqueId())
            .thenApply(data -> data.hasBadgeUnlocked(badgeId));

        return future.getNow(false);
    }

    // ========== 国家相关方法 ==========

    private String getNationName(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "未加入国家";
        return nations.nationOf(player.getUniqueId()).map(Nation::name).orElse("未加入国家");
    }

    private String getNationRank(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "无";
        return nations.nationOf(player.getUniqueId())
            .map(n -> n.founderId().equals(player.getUniqueId()) ? "君主/领袖" : "成员")
            .orElse("无");
    }

    private String getNationLeader(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "无";
        return nations.nationOf(player.getUniqueId())
            .map(n -> {
                OfflinePlayer leader = org.bukkit.Bukkit.getOfflinePlayer(n.founderId());
                return leader.getName() == null ? "未知" : leader.getName();
            })
            .orElse("无");
    }

    // ========== 经济相关方法 ==========

    private String getBalance(OfflinePlayer player) {
        EconomyService economy = economy();
        if (economy == null) return "0";
        BigDecimal bal = economy.getBalance(player.getUniqueId());
        return bal == null ? "0" : bal.toPlainString();
    }

    private String formatBalance(String balance) {
        try {
            double amount = Double.parseDouble(balance);
            if (amount >= 1_000_000) {
                return String.format("%.1fM", amount / 1_000_000);
            } else if (amount >= 1_000) {
                return String.format("%.1fK", amount / 1_000);
            }
            return String.format("%.0f", amount);
        } catch (NumberFormatException e) {
            return balance;
        }
    }

    // ========== 统计相关方法 ==========

    private String getOnlineTime(OfflinePlayer player) {
        RankingService ranking = rankingService();
        if (ranking == null) {
            return "0";
        }
        try {
            long seconds = ranking.getOnlineTime(player.getUniqueId(), RankPeriod.ALLTIME).join();
            return String.valueOf(seconds);
        } catch (Exception e) {
            return "0";
        }
    }

    private String getKills(OfflinePlayer player) {
        PvPStatsService pvp = pvpStatsService();
        if (pvp == null) {
            RankingService ranking = rankingService();
            if (ranking == null) return "0";
            try {
                return String.valueOf(ranking.getKillCount(player.getUniqueId(), RankPeriod.ALLTIME).join());
            } catch (Exception e) {
                return "0";
            }
        }
        return String.valueOf(pvp.getStats(player.getUniqueId()).getKills());
    }

    private String getKillsAlltime(OfflinePlayer player) {
        return getKills(player);
    }

    private String getPlayerRank(OfflinePlayer player) {
        RankingService ranking = rankingService();
        if (ranking == null) {
            return "未上榜";
        }
        try {
            int rank = ranking.getKillRank(player.getUniqueId(), RankPeriod.ALLTIME).join();
            return rank > 0 ? "#" + rank : "未上榜";
        } catch (Exception e) {
            return "未上榜";
        }
    }

    private String getRankPosition(OfflinePlayer player) {
        RankingService ranking = rankingService();
        if (ranking == null) {
            return "0";
        }
        try {
            int rank = ranking.getKillRank(player.getUniqueId(), RankPeriod.ALLTIME).join();
            return rank > 0 ? String.valueOf(rank) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String formatTime(String time) {
        try {
            long ticks = Long.parseLong(time);
            long hours = ticks / (20 * 60 * 60);
            long minutes = (ticks % (20 * 60 * 60)) / (20 * 60);
            return String.format("%d小时%d分钟", hours, minutes);
        } catch (NumberFormatException e) {
            return time;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private String getJoinDate(OfflinePlayer player) {
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed == 0) {
            return "未知";
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(firstPlayed));
    }

    private String getLastSeen(OfflinePlayer player) {
        if (player.isOnline()) {
            return "在线";
        }
        long lastPlayed = player.getLastPlayed();
        if (lastPlayed == 0) {
            return "未知";
        }
        long diff = System.currentTimeMillis() - lastPlayed;
        long days = diff / (24 * 60 * 60 * 1000);
        if (days > 0) {
            return days + "天前";
        }
        long hours = diff / (60 * 60 * 1000);
        if (hours > 0) {
            return hours + "小时前";
        }
        long minutes = diff / (60 * 1000);
        return minutes + "分钟前";
    }

    // ========== 成就相关方法 ==========

    private String getAchievementsProgress(OfflinePlayer player) {
        AchievementService svc = achievements();
        if (svc == null) return "0/0";
        int completed = svc.getPlayerProgress(player.getUniqueId());
        int total = svc.getTotalAchievements();
        return completed + "/" + total;
    }

    private String getAchievementsCount(OfflinePlayer player) {
        AchievementService svc = achievements();
        if (svc == null) return "0";
        return String.valueOf(svc.getPlayerProgress(player.getUniqueId()));
    }

    // ========== 政府相关方法 ==========

    private String getNationPopulation(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "0";
        return nations.nationOf(player.getUniqueId())
            .map(n -> {
                // 刷新缓存以确保数据最新
                if (nations instanceof dev.starcore.starcore.module.nation.NationModule nationModule) {
                    nationModule.refreshNationCache(n.id());
                }
                return String.valueOf(n.getPopulation());
            })
            .orElse("0");
    }

    private String getNationTerritorySize(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "0";
        return nations.nationOf(player.getUniqueId())
            .map(n -> {
                // 刷新缓存以确保数据最新
                if (nations instanceof dev.starcore.starcore.module.nation.NationModule nationModule) {
                    nationModule.refreshNationCache(n.id());
                }
                return String.valueOf(n.getTerritorySize());
            })
            .orElse("0");
    }

    private String getNationTreasury(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "0";
        return nations.nationOf(player.getUniqueId())
            .map(n -> {
                // 刷新缓存以确保数据最新
                if (nations instanceof dev.starcore.starcore.module.nation.NationModule nationModule) {
                    nationModule.refreshNationCache(n.id());
                }
                return n.getTreasuryBalance().toPlainString();
            })
            .orElse("0");
    }

    private String getGovernmentType(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "无";
        return nations.nationOf(player.getUniqueId())
            .map(n -> n.governmentType().displayName())
            .orElse("无");
    }

    private String getGovernmentRole(OfflinePlayer player) {
        NationService nations = nations();
        if (nations == null) return "公民";
        return nations.nationOf(player.getUniqueId())
            .map(n -> n.founderId().equals(player.getUniqueId()) ? "国家领袖" : "公民")
            .orElse("公民");
    }

    // ========== 战争相关方法 ==========

    private String getWarStatus(OfflinePlayer player) {
        NationService nationSvc = nations();
        WarService warSvc = warService();

        if (nationSvc == null || warSvc == null) {
            return "无国家";
        }

        return nationSvc.nationOf(player.getUniqueId())
            .map(nation -> {
                Collection<WarSnapshot> wars = warSvc.activeWarsOf(nation.id());
                if (wars == null || wars.isEmpty()) {
                    return "和平";
                }
                return "参战中(" + wars.size() + "场)";
            })
            .orElse("无国家");
    }

    private String getWarScore(OfflinePlayer player) {
        NationService nationSvc = nations();
        WarService warSvc = warService();

        if (nationSvc == null || warSvc == null) {
            return "0";
        }

        return nationSvc.nationOf(player.getUniqueId())
            .map(nation -> {
                // 计算该国家参与的战争数
                Collection<WarSnapshot> wars = warSvc.activeWarsOf(nation.id());
                if (wars == null || wars.isEmpty()) {
                    return "0";
                }

                long warCount = wars.stream()
                    .filter(ws -> ws.left().equals(nation.id()))
                    .count();
                return String.valueOf(warCount);
            })
            .orElse("0");
    }

    private boolean isNationAtWar(OfflinePlayer player) {
        NationService nationSvc = nations();
        WarService warSvc = warService();

        if (nationSvc == null || warSvc == null) {
            return false;
        }

        return nationSvc.nationOf(player.getUniqueId())
            .map(nation -> {
                Collection<WarSnapshot> wars = warSvc.activeWarsOf(nation.id());
                return wars != null && !wars.isEmpty();
            })
            .orElse(false);
    }

    private String getWarCount(OfflinePlayer player) {
        NationService nationSvc = nations();
        WarService warSvc = warService();

        if (nationSvc == null || warSvc == null) {
            return "0";
        }

        return nationSvc.nationOf(player.getUniqueId())
            .map(nation -> {
                Collection<WarSnapshot> wars = warSvc.activeWarsOf(nation.id());
                return String.valueOf(wars == null ? 0 : wars.size());
            })
            .orElse("0");
    }
}
