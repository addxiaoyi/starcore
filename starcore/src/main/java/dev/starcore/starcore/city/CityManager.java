package dev.starcore.starcore.city;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * City管理器
 * 管理所有City的创建、查询、升级
 */
public class CityManager {

    // City存储
    private final Map<UUID, City> cities = new ConcurrentHashMap<>();

    // 名称索引
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    // 玩家City映射
    private final Map<UUID, UUID> playerCityMap = new ConcurrentHashMap<>();

    // Nation -> City映射
    private final Map<UUID, Set<UUID>> nationCities = new ConcurrentHashMap<>();

    // 邀请系统
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();
    private static final long INVITE_TIMEOUT_MS = 5 * 60 * 1000;  // 5分钟超时

    /**
     * 邀请记录
     */
    public record PendingInvite(UUID cityId, UUID inviterId, UUID invitedId, long createdAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > INVITE_TIMEOUT_MS;
        }
    }

    /**
     * 发送邀请
     */
    public boolean sendInvite(UUID cityId, UUID inviterId, UUID invitedId) {
        City city = cities.get(cityId);
        if (city == null || !city.isMayor(inviterId)) {
            return false;
        }

        if (city.isResident(invitedId)) {
            return false;
        }

        // 创建邀请
        pendingInvites.put(invitedId, new PendingInvite(cityId, inviterId, invitedId, System.currentTimeMillis()));
        return true;
    }

    /**
     * 接受邀请
     */
    public InviteAcceptResult acceptInvite(UUID playerId) {
        PendingInvite invite = pendingInvites.remove(playerId);
        if (invite == null) {
            return new InviteAcceptResult(false, "没有待处理的邀请");
        }

        if (invite.isExpired()) {
            return new InviteAcceptResult(false, "邀请已过期");
        }

        if (playerCityMap.containsKey(playerId)) {
            return new InviteAcceptResult(false, "你已在其他城市中");
        }

        if (joinCity(playerId, invite.cityId())) {
            return new InviteAcceptResult(true, "已加入城市");
        }

        return new InviteAcceptResult(false, "加入失败");
    }

    /**
     * 拒绝邀请
     */
    public boolean declineInvite(UUID playerId) {
        return pendingInvites.remove(playerId) != null;
    }

    /**
     * 获取待处理邀请
     */
    public PendingInvite getPendingInvite(UUID playerId) {
        return pendingInvites.get(playerId);
    }

    /**
     * 邀请结果
     */
    public record InviteAcceptResult(boolean success, String message) {}

    /**
     * 创建City
     */
    public City createCity(String name, UUID nationId, UUID mayorId) {
        // 检查名称
        if (nameIndex.containsKey(name.toLowerCase())) {
            return null;
        }

        // 检查玩家是否已有City
        if (playerCityMap.containsKey(mayorId)) {
            return null;
        }

        // 创建City
        UUID cityId = UUID.randomUUID();
        City city = new City(cityId, name, nationId, mayorId);

        // 注册
        cities.put(cityId, city);
        nameIndex.put(name.toLowerCase(), cityId);
        playerCityMap.put(mayorId, cityId);

        // 注册到Nation - 使用线程安全的 ConcurrentHashMap.newKeySet()
        nationCities.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet())
            .add(cityId);

        return city;
    }

    /**
     * 删除City
     */
    public boolean deleteCity(UUID cityId) {
        City city = cities.remove(cityId);
        if (city == null) {
            return false;
        }

        // 移除索引
        nameIndex.remove(city.getName().toLowerCase());

        // 移除所有居民映射
        for (UUID resident : city.getResidents()) {
            playerCityMap.remove(resident);
        }

        // 从Nation移除
        if (city.getNationId() != null) {
            Set<UUID> nationCitySet = nationCities.get(city.getNationId());
            if (nationCitySet != null) {
                nationCitySet.remove(cityId);
            }
        }

        return true;
    }

    /**
     * 获取City
     */
    public City getCity(UUID cityId) {
        return cities.get(cityId);
    }

    /**
     * 获取City（通过名称）
     */
    public City getCityByName(String name) {
        UUID cityId = nameIndex.get(name.toLowerCase());
        return cityId != null ? cities.get(cityId) : null;
    }

    /**
     * 获取玩家的City
     */
    public City getPlayerCity(UUID playerId) {
        UUID cityId = playerCityMap.get(playerId);
        return cityId != null ? cities.get(cityId) : null;
    }

    /**
     * 检查玩家是否有City
     */
    public boolean hasCity(UUID playerId) {
        return playerCityMap.containsKey(playerId);
    }

    /**
     * 通过居民获取City
     */
    public City getCityByResident(UUID playerId) {
        return getPlayerCity(playerId);
    }

    /**
     * 玩家加入City
     */
    public boolean joinCity(UUID playerId, UUID cityId) {
        // 检查是否已有City
        if (playerCityMap.containsKey(playerId)) {
            return false;
        }

        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        // 添加居民
        if (city.addResident(playerId)) {
            playerCityMap.put(playerId, cityId);
            return true;
        }

        return false;
    }

    /**
     * 玩家离开City
     */
    public boolean leaveCity(UUID playerId) {
        UUID cityId = playerCityMap.remove(playerId);
        if (cityId == null) {
            return false;
        }

        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        // 如果是市长离开
        if (city.isMayor(playerId)) {
            // 如果只剩市长一人，删除City
            if (city.getResidentCount() <= 1) {
                deleteCity(cityId);
                return true;
            }

            // 转让给第一个居民
            UUID newMayor = city.getResidents().stream()
                .filter(id -> !id.equals(playerId))
                .findFirst()
                .orElse(null);

            if (newMayor != null) {
                city.transferMayor(newMayor);
            }
        }

        // 移除居民
        city.removeResident(playerId);
        return true;
    }

    /**
     * 踢出居民
     */
    public boolean kickResident(UUID mayorId, UUID targetId) {
        City city = getPlayerCity(mayorId);
        if (city == null || !city.isMayor(mayorId)) {
            return false;
        }

        if (!city.isResident(targetId)) {
            return false;
        }

        city.removeResident(targetId);
        playerCityMap.remove(targetId);
        return true;
    }

    /**
     * 获取Nation的所有City
     */
    public Set<City> getNationCities(UUID nationId) {
        Set<UUID> cityIds = nationCities.get(nationId);
        if (cityIds == null) {
            return Collections.emptySet();
        }

        Set<City> result = new HashSet<>();
        for (UUID id : cityIds) {
            City city = cities.get(id);
            if (city != null) {
                result.add(city);
            }
        }
        return result;
    }

    /**
     * 获取Nation的首都（等级最高的City）
     */
    public City getNationCapital(UUID nationId) {
        return getNationCities(nationId).stream()
            .max(Comparator.comparingInt(City::getLevel))
            .orElse(null);
    }

    /**
     * City升级
     */
    public LevelUpResult levelUpCity(UUID cityId) {
        City city = cities.get(cityId);
        if (city == null) {
            return new LevelUpResult(false, "City不存在");
        }

        if (!city.canLevelUp()) {
            City.LevelRequirements req = city.getNextLevelRequirements();
            return new LevelUpResult(false, "不满足升级条件: " + req);
        }

        // 扣除升级费用
        City.LevelRequirements req = city.getNextLevelRequirements();
        if (!city.withdraw(req.requiredGold())) {
            return new LevelUpResult(false, "金币不足");
        }

        // 升级
        if (city.levelUp()) {
            return new LevelUpResult(true, "升级成功！现在是 " + city.getColoredTypeName());
        }

        return new LevelUpResult(false, "升级失败");
    }

    /**
     * 获取所有City
     */
    public Collection<City> getAllCities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    /**
     * 获取Top City（按等级）
     */
    public List<City> getTopCitiesByLevel(int limit) {
        return cities.values().stream()
            .sorted((a, b) -> Integer.compare(b.getLevel(), a.getLevel()))
            .limit(limit)
            .toList();
    }

    /**
     * 获取Top City（按居民数）
     */
    public List<City> getTopCitiesByResidents(int limit) {
        return cities.values().stream()
            .sorted((a, b) -> Integer.compare(b.getResidentCount(), a.getResidentCount()))
            .limit(limit)
            .toList();
    }

    /**
     * 获取统计信息
     */
    public CityStats getStats() {
        int totalCities = cities.size();
        int totalResidents = playerCityMap.size();

        Map<City.CityType, Integer> typeCount = new EnumMap<>(City.CityType.class);
        for (City city : cities.values()) {
            typeCount.merge(city.getType(), 1, Integer::sum);
        }

        return new CityStats(
            totalCities,
            totalResidents,
            nationCities.size(),
            typeCount
        );
    }

    // ==================== 结果类 ====================

    /**
     * 升级结果
     */
    public record LevelUpResult(boolean success, String message) {}

    /**
     * 统计信息
     */
    public record CityStats(
        int totalCities,
        int totalResidents,
        int nationsWithCities,
        Map<City.CityType, Integer> typeDistribution
    ) {
        @Override
        public String toString() {
            return String.format(
                "CityStats[cities=%d, residents=%d, nations=%d]",
                totalCities, totalResidents, nationsWithCities
            );
        }
    }
}
