package dev.starcore.starcore.module.city.model;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * 城市数据模型
 * 城市是国家的下级行政单位，属于某个国家（Nation）
 */
public final class City {
    private final UUID id;
    private final NationId nationId;
    private String name;
    private String announcement;  // 城市公告
    private Location spawnChunk;  // 城市出生点所在区块
    private final Map<UUID, CityRank> residents;  // 成员及其等级
    private CityRank mayorRank;  // 市长级别（固定为 MAYOR）

    // 城市元数据
    private final Instant createdAt;
    private Instant lastUpdated;

    // 城市经济
    private double treasury;

    // 城市等级系统
    private int level = 1;
    private int maxLevel = 10;
    private int experience = 0;

    // 城市领土
    private final Set<String> claims;  // 存储格式: "world,x,z"

    // 城市设置
    private boolean pvpEnabled = false;
    private boolean publicSpawn = true;
    private boolean openRecruitment = true;

    // 投票相关
    private final Map<String, CityVote> activeVotes;  // 进行中的投票

    public City(
        UUID id,
        NationId nationId,
        String name,
        Location spawnChunk,
        Map<UUID, CityRank> residents,
        CityRank mayorRank,
        double treasury,
        Instant createdAt
    ) {
        this(id, nationId, name, spawnChunk, residents, mayorRank, treasury, createdAt, Instant.now());
    }

    /**
     * 完整构造函数（用于反序列化）
     */
    public City(
        UUID id,
        NationId nationId,
        String name,
        Location spawnChunk,
        Map<UUID, CityRank> residents,
        CityRank mayorRank,
        double treasury,
        Instant createdAt,
        Instant lastUpdated
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.name = Objects.requireNonNull(name, "name");
        this.spawnChunk = spawnChunk;
        this.residents = new HashMap<>(Objects.requireNonNull(residents, "residents"));
        this.mayorRank = Objects.requireNonNull(mayorRank, "mayorRank");
        this.treasury = treasury;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = lastUpdated != null ? lastUpdated : Instant.now();
        this.claims = new HashSet<>();
        this.activeVotes = new HashMap<>();
    }

    /**
     * 创建新城市
     */
    public static City create(NationId nationId, String name, UUID mayorId, Location spawnChunk) {
        UUID id = UUID.randomUUID();
        Map<UUID, CityRank> residents = new HashMap<>();
        residents.put(mayorId, CityRank.MAYOR);
        City city = new City(id, nationId, name, spawnChunk, residents, CityRank.MAYOR, 0.0, Instant.now());
        return city;
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public NationId nationId() {
        return nationId;
    }

    public String name() {
        return name;
    }

    public Location spawnChunk() {
        return spawnChunk;
    }

    public Map<UUID, CityRank> residents() {
        return Collections.unmodifiableMap(residents);
    }

    public CityRank mayorRank() {
        return mayorRank;
    }

    public double treasury() {
        return treasury;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public int residentCount() {
        return residents.size();
    }

    // ==================== 公告 ====================

    public String announcement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
        this.lastUpdated = Instant.now();
    }

    // ==================== 等级系统 ====================

    public int level() {
        return level;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int experience() {
        return experience;
    }

    public int getMaxResidents() {
        // 根据等级计算最大居民数
        return 20 + (level - 1) * 5;
    }

    /**
     * 添加经验值
     */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;
        this.experience += amount;
        this.lastUpdated = Instant.now();
        checkLevelUp();
        return true;
    }

    /**
     * 检查是否可以升级
     */
    public boolean canLevelUp() {
        if (level >= maxLevel) return false;
        int requiredExp = getLevelUpExperience();
        return experience >= requiredExp;
    }

    /**
     * 获取升级所需经验
     */
    public int getLevelUpExperience() {
        return level * 100;
    }

    /**
     * 升级
     */
    public boolean levelUp() {
        if (!canLevelUp()) return false;
        experience -= getLevelUpExperience();
        level++;
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 检查并执行自动升级
     */
    private void checkLevelUp() {
        while (canLevelUp()) {
            levelUp();
        }
    }

    // ==================== 领土系统 ====================

    public Set<String> claims() {
        return Collections.unmodifiableSet(claims);
    }

    public int claimCount() {
        return claims.size();
    }

    /**
     * 添加领土
     */
    public boolean addClaim(String world, int x, int z) {
        String key = world + "," + x + "," + z;
        if (claims.add(key)) {
            this.lastUpdated = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 移除领土
     */
    public boolean removeClaim(String world, int x, int z) {
        String key = world + "," + x + "," + z;
        if (claims.remove(key)) {
            this.lastUpdated = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 检查是否有指定领土
     */
    public boolean hasClaim(String world, int x, int z) {
        return claims.contains(world + "," + x + "," + z);
    }

    // ==================== 城市设置 ====================

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
        this.lastUpdated = Instant.now();
    }

    public boolean isPublicSpawn() {
        return publicSpawn;
    }

    public void setPublicSpawn(boolean publicSpawn) {
        this.publicSpawn = publicSpawn;
        this.lastUpdated = Instant.now();
    }

    public boolean isOpenRecruitment() {
        return openRecruitment;
    }

    public void setOpenRecruitment(boolean openRecruitment) {
        this.openRecruitment = openRecruitment;
        this.lastUpdated = Instant.now();
    }

    // ==================== 批量成员操作 ====================

    /**
     * 批量添加居民
     * @return 成功添加的数量
     */
    public int addResidents(Map<UUID, CityRank> members) {
        int count = 0;
        for (Map.Entry<UUID, CityRank> entry : members.entrySet()) {
            if (addResident(entry.getKey(), entry.getValue())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 批量移除居民
     * @return 成功移除的数量
     */
    public int removeResidents(Collection<UUID> memberIds) {
        int count = 0;
        for (UUID playerId : memberIds) {
            if (removeResident(playerId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取所有居民UUID
     */
    public Set<UUID> getResidentIds() {
        return new HashSet<>(residents.keySet());
    }

    /**
     * 获取特定等级的所有居民
     */
    public Set<UUID> getResidentsByRank(CityRank rank) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, CityRank> entry : residents.entrySet()) {
            if (entry.getValue() == rank) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 遍历所有居民（只读）
     */
    public void forEachResident(Consumer<UUID> action) {
        residents.keySet().forEach(action);
    }

    // ==================== 权限检查 ====================

    public boolean isMayor(UUID playerId) {
        CityRank rank = residents.get(playerId);
        return rank == CityRank.MAYOR;
    }

    public boolean isOfficer(UUID playerId) {
        CityRank rank = residents.get(playerId);
        return rank == CityRank.OFFICER || rank == CityRank.MAYOR;
    }

    public boolean isResident(UUID playerId) {
        return residents.containsKey(playerId);
    }

    public CityRank getRank(UUID playerId) {
        return residents.getOrDefault(playerId, null);
    }

    public boolean canManageCity(UUID playerId) {
        return isMayor(playerId);
    }

    public boolean canInvite(UUID playerId) {
        return isOfficer(playerId);
    }

    public boolean canKick(UUID playerId) {
        return isMayor(playerId);
    }

    public boolean canSetSpawn(UUID playerId) {
        return isOfficer(playerId);
    }

    // ==================== 成员管理 ====================

    /**
     * 添加居民
     */
    public boolean addResident(UUID playerId, CityRank rank) {
        if (residents.containsKey(playerId)) {
            return false;
        }
        if (rank == null) {
            rank = CityRank.RESIDENT;
        }
        residents.put(playerId, rank);
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 添加普通居民
     */
    public boolean addResident(UUID playerId) {
        return addResident(playerId, CityRank.RESIDENT);
    }

    /**
     * 移除居民
     */
    public boolean removeResident(UUID playerId) {
        // 不能移除市长
        if (isMayor(playerId)) {
            return false;
        }
        boolean removed = residents.remove(playerId) != null;
        if (removed) {
            this.lastUpdated = Instant.now();
        }
        return removed;
    }

    /**
     * 升级居民为官员
     */
    public boolean promoteToOfficer(UUID playerId) {
        if (!residents.containsKey(playerId)) {
            return false;
        }
        if (isMayor(playerId)) {
            return false;  // 市长不能被升级
        }
        residents.put(playerId, CityRank.OFFICER);
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 降级官员为居民
     */
    public boolean demoteToResident(UUID playerId) {
        if (!isOfficer(playerId)) {
            return false;
        }
        if (isMayor(playerId)) {
            return false;  // 市长不能被降级
        }
        residents.put(playerId, CityRank.RESIDENT);
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 转让市长
     */
    public boolean transferMayor(UUID newMayorId) {
        if (!residents.containsKey(newMayorId)) {
            return false;
        }
        // 获取当前市长
        UUID currentMayor = residents.entrySet().stream()
            .filter(e -> e.getValue() == CityRank.MAYOR)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);

        if (currentMayor == null) {
            return false;
        }

        // 交换权限
        residents.put(currentMayor, CityRank.OFFICER);
        residents.put(newMayorId, CityRank.MAYOR);
        this.lastUpdated = Instant.now();
        return true;
    }

    // ==================== 经济管理 ====================

    public void deposit(double amount) {
        if (amount > 0) {
            this.treasury += amount;
            this.lastUpdated = Instant.now();
        }
    }

    public boolean withdraw(double amount) {
        if (amount > 0 && treasury >= amount) {
            this.treasury -= amount;
            this.lastUpdated = Instant.now();
            return true;
        }
        return false;
    }

    // ==================== 设置方法 ====================

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.lastUpdated = Instant.now();
    }

    public void setSpawnChunk(Location spawnChunk) {
        this.spawnChunk = spawnChunk;
        this.lastUpdated = Instant.now();
    }

    public void setTreasury(double treasury) {
        this.treasury = treasury;
        this.lastUpdated = Instant.now();
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof City city)) return false;
        return id.equals(city.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("City{id=%s, nationId=%s, name=%s, residents=%d}",
            id, nationId, name, residentCount());
    }

    // ==================== 投票系统 ====================

    /**
     * 创建投票
     */
    public CityVote createVote(String voteId, UUID creatorId, String title, VoteType type, int durationSeconds) {
        CityVote vote = new CityVote(voteId, creatorId, title, type, durationSeconds, Instant.now());
        activeVotes.put(voteId, vote);
        this.lastUpdated = Instant.now();
        return vote;
    }

    /**
     * 获取投票
     */
    public CityVote getVote(String voteId) {
        return activeVotes.get(voteId);
    }

    /**
     * 移除投票
     */
    public boolean removeVote(String voteId) {
        if (activeVotes.remove(voteId) != null) {
            this.lastUpdated = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 获取所有活跃投票
     */
    public Collection<CityVote> getActiveVotes() {
        return new ArrayList<>(activeVotes.values());
    }

    /**
     * 清理过期投票
     */
    public int cleanupExpiredVotes() {
        int removed = 0;
        for (Iterator<Map.Entry<String, CityVote>> it = activeVotes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, CityVote> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            this.lastUpdated = Instant.now();
        }
        return removed;
    }

    // ==================== 内部类 ====================

    /**
     * 投票类型
     */
    public enum VoteType {
        KICK_MEMBER("踢出成员"),
        CHANGE_SETTINGS("修改设置"),
        PROMOTE_MEMBER("晋升成员"),
        WAR_DECLARATION("宣战"),
        EXPULSION("驱逐成员");

        private final String displayName;

        VoteType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 城市投票
     */
    public static class CityVote {
        private final String id;
        private final UUID creatorId;
        private final String title;
        private final VoteType type;
        private final int durationSeconds;
        private final Instant createdAt;
        private final Map<UUID, Boolean> votes;  // true=赞成, false=反对

        public CityVote(String id, UUID creatorId, String title, VoteType type, int durationSeconds, Instant createdAt) {
            this.id = id;
            this.creatorId = creatorId;
            this.title = title;
            this.type = type;
            this.durationSeconds = durationSeconds;
            this.createdAt = createdAt;
            this.votes = new HashMap<>();
        }

        public String id() { return id; }
        public UUID creatorId() { return creatorId; }
        public String title() { return title; }
        public VoteType type() { return type; }
        public int durationSeconds() { return durationSeconds; }
        public Instant createdAt() { return createdAt; }

        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(durationSeconds));
        }

        public int getRemainingSeconds() {
            long remaining = durationSeconds - (Instant.now().getEpochSecond() - createdAt.getEpochSecond());
            return Math.max(0, (int) remaining);
        }

        public void vote(UUID playerId, boolean approve) {
            votes.put(playerId, approve);
        }

        public void removeVote(UUID playerId) {
            votes.remove(playerId);
        }

        public Boolean getVote(UUID playerId) {
            return votes.get(playerId);
        }

        public boolean hasVoted(UUID playerId) {
            return votes.containsKey(playerId);
        }

        public int getYesVotes() {
            return (int) votes.values().stream().filter(Boolean::booleanValue).count();
        }

        public int getNoVotes() {
            return (int) votes.values().stream().filter(v -> !v.booleanValue()).count();
        }

        public int getTotalVotes() {
            return votes.size();
        }

        /**
         * 计算投票结果
         * @param requiredPercent 赞成票所需的最低百分比（0-100）
         * @return true=通过, false=未通过
         */
        public boolean isApproved(int requiredPercent) {
            if (votes.isEmpty()) return false;
            int yes = getYesVotes();
            int total = getTotalVotes();
            return (yes * 100 / total) >= requiredPercent;
        }
    }
}
