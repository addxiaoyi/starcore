package dev.starcore.starcore.module.city;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.city.model.CityRank;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 城市系统 PlaceholderAPI 扩展
 * 提供城市相关的占位符变量
 */
public class CityPlaceholderExpansion extends PlaceholderExpansion {

    private final ServiceRegistry serviceRegistry;

    public CityPlaceholderExpansion(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    private CityService cityService() {
        return serviceRegistry == null ? null : serviceRegistry.find(CityService.class).orElse(null);
    }

    private NationService nationService() {
        return serviceRegistry == null ? null : serviceRegistry.find(NationService.class).orElse(null);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "city";
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

        CityService cityService = cityService();
        if (cityService == null) {
            return "";
        }

        switch (params.toLowerCase()) {
            // 城市名称
            case "name":
                return getCityName(player);

            // 城市是否存在
            case "has_city":
            case "in_city":
                return hasCity(player) ? "true" : "false";

            // 城市等级
            case "level":
                return String.valueOf(getCityLevel(player));

            // 城市经验
            case "exp":
            case "experience":
                return String.valueOf(getCityExperience(player));

            // 升级所需经验
            case "exp_needed":
            case "exp_required":
                return String.valueOf(getExpRequired(player));

            // 经验百分比
            case "exp_percent":
                return String.valueOf(getExpPercent(player));

            // 居民数
            case "residents":
            case "resident_count":
            case "population":
                return String.valueOf(getResidentCount(player));

            // 最大居民数
            case "max_residents":
            case "max_population":
                return String.valueOf(getMaxResidents(player));

            // 居民排名
            case "resident_rank":
                return String.valueOf(getResidentRank(player));

            // 市长名称
            case "mayor":
            case "mayor_name":
                return getMayorName(player);

            // 城市排名
            case "rank":
                return getCityRank(player);

            // 国库
            case "treasury":
                return getTreasury(player);

            // 国库格式化
            case "treasury_formatted":
                return formatTreasury(getTreasury(player));

            // 领土数量
            case "claims":
            case "claim_count":
            case "territory":
                return String.valueOf(getClaimCount(player));

            // 城市等级称号
            case "level_title":
                return getLevelTitle(player);

            // 公告
            case "announcement":
                return getAnnouncement(player);

            // 是否有公告
            case "has_announcement":
                return hasAnnouncement(player) ? "true" : "false";

            // 城市类型
            case "type":
                return getCityType(player);

            // 玩家在城市中的等级
            case "rank_display":
                return getPlayerRankDisplay(player);

            default:
                return handleParameterizedPlaceholder(player, params);
        }
    }

    /**
     * 处理带参数的占位符
     */
    private String handleParameterizedPlaceholder(OfflinePlayer player, String params) {
        // %city_name_<city_name>% - 获取指定城市的名称
        if (params.startsWith("name_")) {
            String cityName = params.substring(5);
            return cityName; // 返回城市名
        }

        // %city_level_<city_name>% - 获取指定城市的等级
        if (params.startsWith("level_")) {
            String cityName = params.substring(6);
            return getCityLevelByName(cityName);
        }

        // %city_residents_<city_name>% - 获取指定城市的居民数
        if (params.startsWith("residents_")) {
            String cityName = params.substring(10);
            return getResidentCountByName(cityName);
        }

        // %city_treasury_<city_name>% - 获取指定城市的国库
        if (params.startsWith("treasury_")) {
            String cityName = params.substring(9);
            return getTreasuryByName(cityName);
        }

        return "";
    }

    // ==================== 基础方法 ====================

    private boolean hasCity(OfflinePlayer player) {
        CityService service = cityService();
        if (service == null) return false;
        return service.getPlayerCity(player.getUniqueId()).isPresent();
    }

    private Optional<City> getCity(OfflinePlayer player) {
        CityService service = cityService();
        if (service == null) return Optional.empty();
        return service.getPlayerCity(player.getUniqueId());
    }

    // ==================== 获取方法 ====================

    private String getCityName(OfflinePlayer player) {
        return getCity(player).map(City::name).orElse("无");
    }

    private int getCityLevel(OfflinePlayer player) {
        return getCity(player).map(City::level).orElse(0);
    }

    private int getCityExperience(OfflinePlayer player) {
        return getCity(player).map(City::experience).orElse(0);
    }

    private int getExpRequired(OfflinePlayer player) {
        return getCity(player).map(City::getLevelUpExperience).orElse(0);
    }

    private int getExpPercent(OfflinePlayer player) {
        Optional<City> city = getCity(player);
        if (city.isEmpty()) return 0;
        City c = city.get();
        if (c.getLevelUpExperience() == 0) return 100;
        return (c.experience() * 100) / c.getLevelUpExperience();
    }

    private int getResidentCount(OfflinePlayer player) {
        return getCity(player).map(City::residentCount).orElse(0);
    }

    private int getMaxResidents(OfflinePlayer player) {
        return getCity(player).map(City::getMaxResidents).orElse(0);
    }

    private int getResidentRank(OfflinePlayer player) {
        CityService service = cityService();
        if (service == null) return 0;

        Optional<City> cityOpt = getCity(player);
        if (cityOpt.isEmpty()) return 0;

        int rank = 1;
        for (City city : service.getTopCitiesByResidents(100)) {
            if (city.id().equals(cityOpt.get().id())) {
                return rank;
            }
            rank++;
        }
        return 0;
    }

    private String getMayorName(OfflinePlayer player) {
        Optional<City> city = getCity(player);
        if (city.isEmpty()) return "无";

        UUID mayorId = city.get().getResidentsByRank(CityRank.MAYOR).stream().findFirst().orElse(null);
        if (mayorId == null) return "无";

        OfflinePlayer mayor = Bukkit.getOfflinePlayer(mayorId);
        return mayor.getName() != null ? mayor.getName() : "未知";
    }

    private String getCityRank(OfflinePlayer player) {
        CityService service = cityService();
        if (service == null) return "#0";

        Optional<City> cityOpt = getCity(player);
        if (cityOpt.isEmpty()) return "#0";

        int rank = 1;
        for (City city : service.getTopCitiesByTreasury(100)) {
            if (city.id().equals(cityOpt.get().id())) {
                return "#" + rank;
            }
            rank++;
        }
        return "#0";
    }

    private String getTreasury(OfflinePlayer player) {
        return getCity(player).map(c -> String.format("%.2f", c.treasury())).orElse("0.00");
    }

    private String formatTreasury(String treasury) {
        try {
            double amount = Double.parseDouble(treasury);
            if (amount >= 1_000_000) {
                return String.format("%.1fM", amount / 1_000_000);
            } else if (amount >= 1_000) {
                return String.format("%.1fK", amount / 1_000);
            }
            return String.format("%.0f", amount);
        } catch (NumberFormatException e) {
            return treasury;
        }
    }

    private int getClaimCount(OfflinePlayer player) {
        return getCity(player).map(City::claimCount).orElse(0);
    }

    private String getLevelTitle(OfflinePlayer player) {
        int level = getCityLevel(player);
        if (level >= 10) return "超级都市";
        if (level >= 8) return "大都市";
        if (level >= 6) return "中型城市";
        if (level >= 4) return "小型城市";
        if (level >= 2) return "城镇";
        return "村落";
    }

    private String getAnnouncement(OfflinePlayer player) {
        return getCity(player).map(c -> c.announcement() != null ? c.announcement() : "").orElse("");
    }

    private boolean hasAnnouncement(OfflinePlayer player) {
        Optional<City> city = getCity(player);
        return city.isPresent() && city.get().announcement() != null && !city.get().announcement().isEmpty();
    }

    private String getCityType(OfflinePlayer player) {
        int level = getCityLevel(player);
        if (level >= 10) return "METROPOLIS";
        if (level >= 8) return "METROPOLIS";
        if (level >= 6) return "CITY";
        if (level >= 4) return "TOWN";
        if (level >= 2) return "SETTLEMENT";
        return "SETTLEMENT";
    }

    private String getPlayerRankDisplay(OfflinePlayer player) {
        Optional<City> city = getCity(player);
        if (city.isEmpty()) return "无";

        CityRank rank = city.get().getRank(player.getUniqueId());
        return rank != null ? rank.displayName() : "无";
    }

    // ==================== 按名称查询 ====================

    private String getCityLevelByName(String cityName) {
        CityService service = cityService();
        if (service == null) return "0";
        return service.getCityByName(cityName).map(c -> String.valueOf(c.level())).orElse("0");
    }

    private String getResidentCountByName(String cityName) {
        CityService service = cityService();
        if (service == null) return "0";
        return service.getCityByName(cityName).map(c -> String.valueOf(c.residentCount())).orElse("0");
    }

    private String getTreasuryByName(String cityName) {
        CityService service = cityService();
        if (service == null) return "0.00";
        return service.getCityByName(cityName).map(c -> String.format("%.2f", c.treasury())).orElse("0.00");
    }
}
