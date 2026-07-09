package dev.starcore.starcore.module.city;
import java.util.Optional;

import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.city.model.CityRank;
import dev.starcore.starcore.module.city.model.City.CityVote;
import dev.starcore.starcore.module.city.model.City.VoteType;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 城市服务
 * 提供城市的创建、删除、管理等功能
 */
public final class CityService {

    private final Map<UUID, City> cities = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerCityMap = new ConcurrentHashMap<>();
    private final Map<NationId, Set<UUID>> nationCities = new ConcurrentHashMap<>();

    // 邀请系统
    private final Map<UUID, CityInvite> pendingInvites = new ConcurrentHashMap<>();
    private static final long INVITE_TIMEOUT_MS = 5 * 60 * 1000;  // 5分钟超时

    // 创建冷却
    private final Map<UUID, Long> playerCreateCooldown = new ConcurrentHashMap<>();
    private static final long CREATE_COOLDOWN_MS = 60 * 1000;  // 1分钟冷却

    // 邀请冷却
    private final Map<UUID, Long> playerInviteCooldown = new ConcurrentHashMap<>();
    private static final long INVITE_COOLDOWN_MS = 30 * 1000;  // 30秒冷却

    // 配置
    private int maxCitiesPerNation = 5;  // 每个国家最大城市数

    private final CityStateStorage storage;
    private final NationService nationService;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public CityService(CityStateStorage storage, NationService nationService, org.bukkit.plugin.java.JavaPlugin plugin) {
        this.storage = storage;
        this.nationService = nationService;
        this.plugin = plugin;
        loadAll();
        startCleanupTask();
    }

    // ==================== 邀请记录 ====================

    /**
     * 邀请记录
     */
    public record CityInvite(UUID cityId, UUID inviterId, UUID invitedId, Instant createdAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusMillis(INVITE_TIMEOUT_MS));
        }

        public long getRemainingSeconds() {
            long remaining = INVITE_TIMEOUT_MS - (Instant.now().toEpochMilli() - createdAt.toEpochMilli());
            return Math.max(0, remaining / 1000);
        }
    }

    /**
     * 发送邀请
     */
    public InviteResult sendInvite(UUID cityId, UUID inviterId, UUID invitedId) {
        City city = cities.get(cityId);
        if (city == null) {
            return InviteResult.failed("城市不存在");
        }

        if (!city.canInvite(inviterId)) {
            return InviteResult.failed("你没有权限邀请成员");
        }

        // 检查邀请者冷却
        Long lastInvite = playerInviteCooldown.get(inviterId);
        if (lastInvite != null && System.currentTimeMillis() - lastInvite < INVITE_COOLDOWN_MS) {
            long remaining = (INVITE_COOLDOWN_MS - (System.currentTimeMillis() - lastInvite)) / 1000;
            return InviteResult.failed("邀请过于频繁，请等待 " + remaining + " 秒");
        }

        if (city.isResident(invitedId)) {
            return InviteResult.failed("该玩家已在城市中");
        }

        // 检查是否在冷却中
        CityInvite existingInvite = pendingInvites.get(invitedId);
        if (existingInvite != null && !existingInvite.isExpired()) {
            return InviteResult.failed("该玩家有待处理的邀请");
        }

        // 检查目标是否在国家中
        Optional<Nation> cityNation = nationService.nationById(city.nationId());
        Optional<Nation> targetNation = nationService.nationOf(invitedId);
        if (cityNation.isEmpty() || targetNation.isEmpty() || !cityNation.get().id().equals(targetNation.get().id())) {
            return InviteResult.failed("目标玩家必须在同一国家");
        }

        // 创建邀请
        CityInvite invite = new CityInvite(cityId, inviterId, invitedId, Instant.now());
        pendingInvites.put(invitedId, invite);

        // 记录邀请冷却
        playerInviteCooldown.put(inviterId, System.currentTimeMillis());

        return InviteResult.success(invite);
    }

    /**
     * 接受邀请
     */
    public InviteResult acceptInvite(UUID playerId) {
        CityInvite invite = pendingInvites.remove(playerId);
        if (invite == null) {
            return InviteResult.failed("没有待处理的邀请");
        }

        if (invite.isExpired()) {
            return InviteResult.failed("邀请已过期");
        }

        City city = cities.get(invite.cityId());
        if (city == null) {
            return InviteResult.failed("城市不存在");
        }

        // 检查玩家是否已在其他城市
        if (playerCityMap.containsKey(playerId)) {
            return InviteResult.failed("你已在其他城市中");
        }

        if (addResident(invite.cityId(), playerId, CityRank.RESIDENT)) {
            return InviteResult.success(null);
        }

        return InviteResult.failed("加入失败");
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
    public Optional<CityInvite> getPendingInvite(UUID playerId) {
        CityInvite invite = pendingInvites.get(playerId);
        if (invite != null && invite.isExpired()) {
            pendingInvites.remove(playerId);
            return Optional.empty();
        }
        return Optional.ofNullable(invite);
    }

    /**
     * 清理过期邀请
     */
    private void cleanupExpiredInvites() {
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            cleanupExpiredInvites();
        }, 20 * 60, 20 * 60);  // 每分钟检查一次
    }

    // ==================== 邀请结果 ====================

    public record InviteResult(boolean success, String message, CityInvite invite) {
        public static InviteResult success(CityInvite invite) {
            return new InviteResult(true, null, invite);
        }

        public static InviteResult failed(String message) {
            return new InviteResult(false, message, null);
        }
    }

    // ==================== 生命周期 ====================

    private void loadAll() {
        Collection<City> loaded = storage.loadAll();
        for (City city : loaded) {
            cities.put(city.id(), city);
            nameIndex.put(city.name().toLowerCase(), city.id());
            nationCities.computeIfAbsent(city.nationId(), k -> new HashSet<>()).add(city.id());

            // 重建玩家-城市映射
            for (UUID playerId : city.residents().keySet()) {
                playerCityMap.put(playerId, city.id());
            }
        }
    }

    public void saveAll() {
        storage.saveAll(cities.values());
    }

    // ==================== 创建和删除 ====================

    /**
     * 设置最大城市数量
     */
    public void setMaxCitiesPerNation(int max) {
        this.maxCitiesPerNation = Math.max(1, max);
    }

    /**
     * 获取最大城市数量
     */
    public int getMaxCitiesPerNation() {
        return maxCitiesPerNation;
    }

    /**
     * 创建城市
     */
    public CreateCityResult createCity(NationId nationId, String name, UUID mayorId, Location spawnChunk) {
        // 检查冷却
        Long lastCreate = playerCreateCooldown.get(mayorId);
        if (lastCreate != null && System.currentTimeMillis() - lastCreate < CREATE_COOLDOWN_MS) {
            long remaining = (CREATE_COOLDOWN_MS - (System.currentTimeMillis() - lastCreate)) / 1000;
            return CreateCityResult.failed("创建冷却中，请等待 " + remaining + " 秒");
        }

        // 检查国家是否存在
        Optional<Nation> nation = nationService.nationById(nationId);
        if (nation.isEmpty()) {
            return CreateCityResult.failed("国家不存在");
        }

        // 检查名称是否重复
        if (nameIndex.containsKey(name.toLowerCase())) {
            return CreateCityResult.failed("城市名称已被使用");
        }

        // 检查玩家是否已是某城市成员
        if (playerCityMap.containsKey(mayorId)) {
            return CreateCityResult.failed("你已是某城市成员");
        }

        // 检查玩家是否是该国家成员
        Optional<Nation> playerNation = nationService.nationOf(mayorId);
        if (playerNation.isEmpty() || !playerNation.get().id().equals(nationId)) {
            return CreateCityResult.failed("你必须是该国家成员才能创建城市");
        }

        // 检查城市数量上限
        Set<UUID> nationCitySet = nationCities.get(nationId);
        if (nationCitySet != null && nationCitySet.size() >= maxCitiesPerNation) {
            return CreateCityResult.failed("该国家已达到最大城市数量上限 (" + maxCitiesPerNation + ")");
        }

        // 创建城市
        City city = City.create(nationId, name, mayorId, spawnChunk);
        cities.put(city.id(), city);
        nameIndex.put(name.toLowerCase(), city.id());
        playerCityMap.put(mayorId, city.id());
        nationCities.computeIfAbsent(nationId, k -> new HashSet<>()).add(city.id());

        // 设置冷却
        playerCreateCooldown.put(mayorId, System.currentTimeMillis());

        // 保存
        storage.save(city);

        return CreateCityResult.success(city);
    }

    /**
     * 删除城市
     */
    public boolean deleteCity(UUID cityId) {
        City city = cities.remove(cityId);
        if (city == null) {
            return false;
        }

        // 清理索引
        nameIndex.remove(city.name().toLowerCase());

        // 清理玩家映射
        for (UUID playerId : city.residents().keySet()) {
            playerCityMap.remove(playerId);
        }

        // 从国家移除
        Set<UUID> nationCitySet = nationCities.get(city.nationId());
        if (nationCitySet != null) {
            nationCitySet.remove(cityId);
        }

        // 删除存储
        storage.delete(cityId);

        return true;
    }

    // ==================== 查询 ====================

    public Optional<City> getCity(UUID cityId) {
        return Optional.ofNullable(cities.get(cityId));
    }

    public Optional<City> getCityByName(String name) {
        UUID cityId = nameIndex.get(name.toLowerCase());
        return cityId != null ? getCity(cityId) : Optional.empty();
    }

    public Optional<City> getPlayerCity(UUID playerId) {
        UUID cityId = playerCityMap.get(playerId);
        return cityId != null ? getCity(cityId) : Optional.empty();
    }

    public Collection<City> getNationCities(NationId nationId) {
        Set<UUID> cityIds = nationCities.get(nationId);
        if (cityIds == null) {
            return Collections.emptyList();
        }
        return cityIds.stream()
            .map(cities::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public Collection<City> getAllCities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    // ==================== 成员管理 ====================

    /**
     * 添加居民
     */
    public boolean addResident(UUID cityId, UUID playerId, CityRank rank) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (playerCityMap.containsKey(playerId)) {
            return false;
        }

        if (city.addResident(playerId, rank)) {
            playerCityMap.put(playerId, cityId);
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 移除居民
     */
    public boolean removeResident(UUID cityId, UUID playerId) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (!city.isResident(playerId)) {
            return false;
        }

        // 如果是市长，且有其他居民，则先转让市长
        if (city.isMayor(playerId) && city.residentCount() > 1) {
            return false; // 不能在有其他居民的情况下移除市长
        }

        if (city.removeResident(playerId)) {
            playerCityMap.remove(playerId);

            // 如果只剩市长一人，删除城市
            if (city.residentCount() == 1 && city.isMayor(city.residents().keySet().iterator().next())) {
                deleteCity(cityId);
            } else {
                storage.save(city);
            }
            return true;
        }
        return false;
    }

    /**
     * 玩家主动离开城市
     */
    public boolean leaveCity(UUID playerId) {
        UUID cityId = playerCityMap.remove(playerId);
        if (cityId == null) {
            return false;
        }

        City city = cities.get(cityId);
        if (city == null) {
            return true;
        }

        // 如果是市长
        if (city.isMayor(playerId)) {
            if (city.residentCount() > 1) {
                playerCityMap.put(playerId, cityId); // 恢复映射
                return false; // 不能在有其他居民的情况下离开
            }
            // 只有市长一人，删除城市
            deleteCity(cityId);
            return true;
        }

        city.removeResident(playerId);
        storage.save(city);
        return true;
    }

    /**
     * 踢出居民
     */
    public boolean kickResident(UUID mayorId, UUID targetId) {
        if (!removeResident(playerCityMap.get(mayorId), targetId)) {
            return false;
        }
        return true;
    }

    // ==================== 出生点 ====================

    /**
     * 设置城市出生点
     */
    public boolean setSpawn(UUID cityId, Location spawnChunk) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        city.setSpawnChunk(spawnChunk);
        storage.save(city);
        return true;
    }

    /**
     * 传送到城市出生点
     */
    public boolean teleportToSpawn(Player player, UUID cityId) {
        City city = cities.get(cityId);
        if (city == null || city.spawnChunk() == null) {
            return false;
        }

        Location spawn = city.spawnChunk().clone();
        spawn.setYaw(player.getLocation().getYaw()); // 保持玩家朝向
        spawn.setPitch(player.getLocation().getPitch());
        player.teleport(spawn);
        return true;
    }

    // ==================== 经济 ====================

    /**
     * 存款到城市国库
     */
    public boolean deposit(UUID cityId, double amount) {
        City city = cities.get(cityId);
        if (city == null || amount <= 0) {
            return false;
        }

        city.deposit(amount);
        storage.save(city);
        return true;
    }

    /**
     * 从城市国库取款
     */
    public boolean withdraw(UUID cityId, double amount) {
        City city = cities.get(cityId);
        if (city == null || amount <= 0) {
            return false;
        }

        if (city.withdraw(amount)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    // ==================== 权限升级/降级 ====================

    /**
     * 任命官员
     */
    public boolean appointOfficer(UUID mayorId, UUID targetId) {
        UUID cityId = playerCityMap.get(mayorId);
        City city = cities.get(cityId);
        if (city == null || !city.isMayor(mayorId)) {
            return false;
        }

        if (city.promoteToOfficer(targetId)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 移除官员
     */
    public boolean removeOfficer(UUID mayorId, UUID targetId) {
        UUID cityId = playerCityMap.get(mayorId);
        City city = cities.get(cityId);
        if (city == null || !city.isMayor(mayorId)) {
            return false;
        }

        if (city.demoteToResident(targetId)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 转让市长
     */
    public boolean transferMayor(UUID currentMayorId, UUID newMayorId) {
        UUID cityId = playerCityMap.get(currentMayorId);
        City city = cities.get(cityId);
        if (city == null || !city.isMayor(currentMayorId)) {
            return false;
        }

        if (city.transferMayor(newMayorId)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    // ==================== 统计 ====================

    public int getTotalCities() {
        return cities.size();
    }

    public int getTotalResidents() {
        return playerCityMap.size();
    }

    public List<City> getTopCitiesByResidents(int limit) {
        return cities.values().stream()
            .sorted((a, b) -> Integer.compare(b.residentCount(), a.residentCount()))
            .limit(limit)
            .toList();
    }

    public List<City> getTopCitiesByTreasury(int limit) {
        return cities.values().stream()
            .sorted((a, b) -> Double.compare(b.treasury(), a.treasury()))
            .limit(limit)
            .toList();
    }

    // ==================== 等级系统 ====================

    /**
     * 添加城市经验
     */
    public boolean addExperience(UUID cityId, int amount) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        city.addExperience(amount);

        // 检查是否升级
        while (city.canLevelUp()) {
            city.levelUp();
        }

        storage.save(city);
        return true;
    }

    /**
     * 升级城市
     */
    public boolean levelUp(UUID cityId) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (city.levelUp()) {
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 获取城市等级
     */
    public int getCityLevel(UUID cityId) {
        City city = cities.get(cityId);
        return city != null ? city.level() : 0;
    }

    /**
     * 获取升级所需经验
     */
    public int getRequiredExperience(UUID cityId) {
        City city = cities.get(cityId);
        return city != null ? city.getLevelUpExperience() : 0;
    }

    // ==================== 公告 ====================

    /**
     * 设置城市公告
     */
    public boolean setAnnouncement(UUID cityId, UUID playerId, String announcement) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (!city.isMayor(playerId)) {
            return false;
        }

        city.setAnnouncement(announcement);
        storage.save(city);
        return true;
    }

    /**
     * 获取城市公告
     */
    public String getAnnouncement(UUID cityId) {
        City city = cities.get(cityId);
        return city != null ? city.announcement() : null;
    }

    /**
     * 更新城市设置
     */
    public boolean updateSettings(UUID cityId, UUID playerId, Boolean pvpEnabled, Boolean publicSpawn, Boolean openRecruitment) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (!city.isMayor(playerId)) {
            return false;
        }

        if (pvpEnabled != null) {
            city.setPvpEnabled(pvpEnabled);
        }
        if (publicSpawn != null) {
            city.setPublicSpawn(publicSpawn);
        }
        if (openRecruitment != null) {
            city.setOpenRecruitment(openRecruitment);
        }

        storage.save(city);
        return true;
    }

    /**
     * 获取城市设置
     */
    public CitySettings getSettings(UUID cityId) {
        City city = cities.get(cityId);
        if (city == null) {
            return null;
        }
        return new CitySettings(city.isPvpEnabled(), city.isPublicSpawn(), city.isOpenRecruitment());
    }

    /**
     * 城市设置记录
     */
    public record CitySettings(boolean pvpEnabled, boolean publicSpawn, boolean openRecruitment) {}

    // ==================== 领土系统 ====================

    /**
     * 添加领土
     */
    public boolean addClaim(UUID cityId, String world, int x, int z) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (city.addClaim(world, x, z)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 移除领土
     */
    public boolean removeClaim(UUID cityId, String world, int x, int z) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (city.removeClaim(world, x, z)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    /**
     * 获取领土数量
     */
    public int getClaimCount(UUID cityId) {
        City city = cities.get(cityId);
        return city != null ? city.claimCount() : 0;
    }

    // ==================== 投票系统 ====================

    /**
     * 创建投票
     */
    public boolean createVote(UUID cityId, UUID creatorId, String title, VoteType type, int durationSeconds) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (!city.isResident(creatorId)) {
            return false;
        }

        String voteId = UUID.randomUUID().toString();
        city.createVote(voteId, creatorId, title, type, durationSeconds);
        storage.save(city);
        return true;
    }

    /**
     * 投票
     */
    public boolean castVote(UUID cityId, UUID playerId, String voteId, boolean approve) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        CityVote vote = city.getVote(voteId);
        if (vote == null || vote.isExpired()) {
            return false;
        }

        if (!city.isResident(playerId)) {
            return false;
        }

        if (vote.hasVoted(playerId)) {
            return false;
        }

        vote.vote(playerId, approve);
        storage.save(city);
        return true;
    }

    /**
     * 获取投票结果
     */
    public boolean getVoteResult(UUID cityId, String voteId) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        CityVote vote = city.getVote(voteId);
        if (vote == null) {
            return false;
        }

        // 投票通过需要超过50%的赞成票
        return vote.isApproved(51);
    }

    /**
     * 关闭投票
     */
    public boolean closeVote(UUID cityId, String voteId) {
        City city = cities.get(cityId);
        if (city == null) {
            return false;
        }

        if (city.removeVote(voteId)) {
            storage.save(city);
            return true;
        }
        return false;
    }

    // ==================== 批量操作 ====================

    /**
     * 批量添加居民
     */
    public int addResidents(UUID cityId, Map<UUID, CityRank> members) {
        City city = cities.get(cityId);
        if (city == null) {
            return 0;
        }

        int count = city.addResidents(members);
        if (count > 0) {
            for (UUID playerId : members.keySet()) {
                playerCityMap.put(playerId, cityId);
            }
            storage.save(city);
        }
        return count;
    }

    /**
     * 批量移除居民
     */
    public int removeResidents(UUID cityId, Collection<UUID> memberIds) {
        City city = cities.get(cityId);
        if (city == null) {
            return 0;
        }

        int count = city.removeResidents(memberIds);
        if (count > 0) {
            for (UUID playerId : memberIds) {
                playerCityMap.remove(playerId);
            }
            storage.save(city);
        }
        return count;
    }

    // ==================== 结果类 ====================

    public record CreateCityResult(boolean success, String message, City city) {
        public static CreateCityResult success(City city) {
            return new CreateCityResult(true, null, city);
        }

        public static CreateCityResult failed(String message) {
            return new CreateCityResult(false, message, null);
        }
    }
}
