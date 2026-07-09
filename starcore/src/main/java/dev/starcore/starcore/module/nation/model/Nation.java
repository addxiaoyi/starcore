package dev.starcore.starcore.module.nation.model;

import dev.starcore.starcore.module.government.model.GovernmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Nation {
    private final NationId id;
    private String name;
    private final UUID founderId;
    private final NationKind kind;
    private final NationId parentNationId;
    private GovernmentType governmentType;
    private long experience;
    private final Instant foundedAt;
    private final Map<UUID, NationMember> members = new LinkedHashMap<>();

    // ===== 缓存的统计数据（由 NationModule 更新） =====
    private volatile int cachedTerritoryCount = 0;
    private volatile BigDecimal cachedTreasuryBalance = BigDecimal.ZERO;
    private volatile int cachedPolicyCount = 0;
    private volatile int cachedTechnologyCount = 0;
    private volatile int cachedAllyCount = 0;
    private volatile int cachedWarCount = 0;
    private volatile double cachedTaxRate = 0.0;
    // 缓存最后更新时间
    private volatile long cacheLastUpdateTime = 0L;
    private static final long CACHE_VALID_DURATION_MS = 5000L; // 缓存5秒内有效
    // 城镇位置缓存（通过 CityService 获取）
    private org.bukkit.Location capitalLocation = null;

    // 城镇名称列表缓存
    private List<String> townNamesCache = List.of();

    // 城镇位置缓存（城市名称 -> 位置）
    private Map<String, org.bukkit.Location> townLocationsCache = new java.util.LinkedHashMap<>();

    public Nation(NationId id, String name, UUID founderId, String founderName) {
        this(id, name, founderId, founderName, NationKind.NATION, null, Instant.now());
    }

    public Nation(NationId id, String name, UUID founderId, String founderName, NationKind kind, NationId parentNationId) {
        this(id, name, founderId, founderName, kind, parentNationId, Instant.now());
    }

    /**
     * 全参数构造函数（用于从持久化数据恢复国家）
     */
    public Nation(NationId id, String name, UUID founderId, String founderName, NationKind kind, NationId parentNationId, Instant foundedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = normalizeName(name);
        this.founderId = Objects.requireNonNull(founderId, "founderId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.parentNationId = parentNationId;
        this.governmentType = GovernmentType.MONARCHY;
        this.experience = 0L;
        this.foundedAt = foundedAt != null ? foundedAt : Instant.now();
        addMember(founderId, founderName);
    }

    public NationId id() { return id; }
    public NationId getId() { return id; } // Alias for compatibility
    public String name() { return name; }
    public UUID founderId() { return founderId; }
    public boolean isFounder(UUID playerId) {
        return founderId.equals(playerId);
    }
    public NationKind kind() { return kind; }
    public NationId parentNationId() { return parentNationId; }
    public GovernmentType governmentType() { return governmentType; }
    public long experience() { return experience; }
    public Instant foundedAt() { return foundedAt; }
    public Collection<NationMember> members() { return members.values(); }
    public Collection<NationMember> getMembers() { return members.values(); } // Alias for compatibility
    public boolean hasMember(UUID playerId) { return members.containsKey(playerId); }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    /**
     * 检查玩家是否有特定权限
     */
    public boolean hasPermission(UUID playerId, String permission) {
        // 创始人拥有所有权限
        if (founderId.equals(playerId)) {
            return true;
        }
        // 简单实现：所有成员默认有基本权限
        return hasMember(playerId);
    }

    public void rename(String name) { this.name = normalizeName(name); }
    public void setGovernmentType(GovernmentType governmentType) { this.governmentType = Objects.requireNonNull(governmentType, "governmentType"); }
    public void setExperience(long experience) { this.experience = Math.max(0L, experience); }
    // foundedAt 是 final，不提供 setter - 建国日期不可更改
    public void addExperience(long amount) {
        if (amount <= 0L) {
            return;
        }
        long updated = experience + amount;
        this.experience = updated < experience ? Long.MAX_VALUE : updated;
    }
    public void addMember(UUID playerId, String playerName) {
        members.put(playerId, new NationMember(
            playerId,
            normalizeName(playerName),
            "Member",
            Instant.now(),
            Instant.now()
        ));
    }

    public NationMember removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    public void setMemberRank(UUID playerId, String rank) {
        NationMember existing = members.get(playerId);
        if (existing != null) {
            members.put(playerId, new NationMember(
                existing.playerId(),
                existing.playerName(),
                normalizeName(rank),
                existing.joinedAt(),
                existing.lastSeen()
            ));
        }
    }

    public void updateMemberLastSeen(UUID playerId) {
        NationMember existing = members.get(playerId);
        if (existing != null) {
            members.put(playerId, new NationMember(
                existing.playerId(),
                existing.playerName(),
                existing.rank(),
                existing.joinedAt(),
                Instant.now()
            ));
        }
    }

    private static String normalizeName(String input) {
        String normalized = Objects.requireNonNull(input, "input").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        return normalized;
    }

    // ==================== GUI辅助方法（使用缓存数据） ====================

    /**
     * 获取成员数量
     */
    public int memberCount() {
        return members.size();
    }

    /**
     * 获取国家人口（等同于成员数量）
     * 这是公开 API，真实反映国家当前成员数
     */
    public int getPopulation() {
        return members.size();
    }

    /**
     * 获取领土大小（以区块数量计）
     * 这是公开 API，真实反映国家当前领土数量
     */
    public int getTerritorySize() {
        return cachedTerritoryCount;
    }

    /**
     * 检查缓存是否过期（超过5秒未刷新）
     */
    public boolean isCacheStale() {
        return System.currentTimeMillis() - cacheLastUpdateTime > CACHE_VALID_DURATION_MS;
    }

    /**
     * 获取缓存最后更新时间
     */
    public long getCacheLastUpdateTime() {
        return cacheLastUpdateTime;
    }

    /**
     * 标记缓存已更新
     */
    public void markCacheUpdated() {
        this.cacheLastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 获取领土数量（由 NationModule 更新缓存）
     */
    public int territoryCount() {
        return cachedTerritoryCount;
    }

    /**
     * 设置领土数量缓存
     */
    public void setTerritoryCount(int count) {
        this.cachedTerritoryCount = Math.max(0, count);
    }

    /**
     * 获取国库余额（由 NationModule 更新缓存）
     */
    public BigDecimal getTreasuryBalance() {
        return cachedTreasuryBalance != null ? cachedTreasuryBalance : BigDecimal.ZERO;
    }

    /**
     * 设置国库余额缓存
     */
    public void setTreasuryBalance(BigDecimal balance) {
        this.cachedTreasuryBalance = balance != null ? balance : BigDecimal.ZERO;
    }

    /**
     * 获取政府类型字符串
     */
    public String getGovernmentType() {
        return governmentType != null ? governmentType.toString() : "MONARCHY";
    }

    /**
     * 获取建国日期（格式化为本地化字符串）
     */
    public String getFoundedDate() {
        if (foundedAt == null) {
            return "未知";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());
        return formatter.format(foundedAt);
    }

    /**
     * 获取活跃政策数量（由 NationModule 更新缓存）
     */
    public int getActivePolicyCount() {
        return cachedPolicyCount;
    }

    /**
     * 设置政策数量缓存
     */
    public void setPolicyCount(int count) {
        this.cachedPolicyCount = Math.max(0, count);
    }

    /**
     * 获取已解锁科技数量（由 NationModule 更新缓存）
     */
    public int getUnlockedTechCount() {
        return cachedTechnologyCount;
    }

    /**
     * 设置科技数量缓存
     */
    public void setTechnologyCount(int count) {
        this.cachedTechnologyCount = Math.max(0, count);
    }

    /**
     * 获取盟友数量（由 NationModule 更新缓存）
     */
    public int getAllyCount() {
        return cachedAllyCount;
    }

    /**
     * 设置盟友数量缓存
     */
    public void setAllyCount(int count) {
        this.cachedAllyCount = Math.max(0, count);
    }

    /**
     * 获取战争数量（由 NationModule 更新缓存）
     */
    public int getWarCount() {
        return cachedWarCount;
    }

    /**
     * 设置战争数量缓存
     */
    public void setWarCount(int count) {
        this.cachedWarCount = Math.max(0, count);
    }

    /**
     * 获取税率
     */
    public double getTaxRate() {
        return cachedTaxRate;
    }

    /**
     * 设置税率缓存
     */
    public void setTaxRate(double rate) {
        this.cachedTaxRate = Math.max(0.0, rate);
    }

    /**
     * 获取首都位置
     */
    public org.bukkit.Location capitalLocation() {
        return capitalLocation;
    }

    /**
     * 设置首都位置
     */
    public void setCapitalLocation(org.bukkit.Location location) {
        this.capitalLocation = location;
    }

    /**
     * 获取城镇位置（通过名称查找）
     * @deprecated 使用 getTownLocations() 代替
     */
    @Deprecated
    public Optional<org.bukkit.Location> getTownLocation(String townName) {
        return Optional.ofNullable(townLocationsCache.get(townName));
    }

    /**
     * 获取城镇位置缓存
     */
    public Map<String, org.bukkit.Location> getTownLocations() {
        return townLocationsCache != null ? townLocationsCache : Map.of();
    }

    /**
     * 设置城镇位置缓存
     */
    public void setTownLocations(Map<String, org.bukkit.Location> locations) {
        this.townLocationsCache = locations != null ? new java.util.LinkedHashMap<>(locations) : new java.util.LinkedHashMap<>();
    }

    /**
     * 获取城镇名称列表（子城邦）
     * @deprecated 使用 getCityStateNames() 代替
     */
    @Deprecated
    public List<String> getTownNames() {
        return townNamesCache != null ? townNamesCache : List.of();
    }

    /**
     * 设置城镇名称缓存
     */
    public void setTownNames(List<String> townNames) {
        this.townNamesCache = townNames != null ? List.copyOf(townNames) : List.of();
    }

    /**
     * 刷新所有缓存数据（由 NationModule 调用）
     * 此方法在需要时由服务层调用以更新缓存
     */
    public void refreshCachedStats(
            int territoryCount,
            BigDecimal treasuryBalance,
            int policyCount,
            int technologyCount,
            int allyCount,
            int warCount,
            double taxRate,
            org.bukkit.Location capitalLocation) {
        setTerritoryCount(territoryCount);
        setTreasuryBalance(treasuryBalance);
        setPolicyCount(policyCount);
        setTechnologyCount(technologyCount);
        setAllyCount(allyCount);
        setWarCount(warCount);
        setTaxRate(taxRate);
        setCapitalLocation(capitalLocation);
        markCacheUpdated();
    }

    /**
     * 刷新城镇数据缓存（由 NationModule 调用）
     */
    public void refreshTownCache(
            List<String> townNames,
            Map<String, org.bukkit.Location> townLocations,
            org.bukkit.Location capitalLocation) {
        setTownNames(townNames);
        setTownLocations(townLocations);
        setCapitalLocation(capitalLocation);
    }
}
