package dev.starcore.starcore.city;

import dev.starcore.starcore.territory.MultiChunkTerritory;

import java.util.*;

/**
 * City城市系统
 * Nation内的城市管理单元
 */
public class City {

    private final UUID id;
    private String name;
    private UUID nationId;

    // 城市所有者（Mayor市长）
    private UUID mayor;

    // 城市成员
    private final Set<UUID> residents = new HashSet<>();

    // 城市Territory
    private final Set<UUID> territories = new HashSet<>();

    // 城市出生点
    private org.bukkit.Location spawnPoint;

    // 城市等级
    private int level = 1;
    private int maxLevel = 10;

    // 城市类型
    private CityType type = CityType.TOWN;

    // 城市经济
    private double treasury = 0.0;
    private double dailyUpkeep = 50.0;
    private double taxRate = 0.0;

    // 城市设置
    private boolean pvpEnabled = false;
    private boolean publicSpawn = true;
    private boolean openRecruitment = true;

    // 创建时间
    private final long createdTime;

    public City(UUID id, String name, UUID nationId, UUID mayor) {
        this.id = id;
        this.name = name;
        this.nationId = nationId;
        this.mayor = mayor;
        this.createdTime = System.currentTimeMillis();

        // 市长自动成为居民
        this.residents.add(mayor);
    }

    // ==================== 居民管理 ====================

    /**
     * 添加居民
     */
    public boolean addResident(UUID playerId) {
        if (residents.size() >= getMaxResidents()) {
            return false;
        }
        return residents.add(playerId);
    }

    /**
     * 移除居民
     */
    public boolean removeResident(UUID playerId) {
        // 不能移除市长
        if (playerId.equals(mayor)) {
            return false;
        }
        return residents.remove(playerId);
    }

    /**
     * 转让市长
     */
    public void transferMayor(UUID newMayor) {
        if (residents.contains(newMayor)) {
            this.mayor = newMayor;
        }
    }

    /**
     * 是否为市长
     */
    public boolean isMayor(UUID playerId) {
        return playerId.equals(mayor);
    }

    /**
     * 是否为居民
     */
    public boolean isResident(UUID playerId) {
        return residents.contains(playerId);
    }

    /**
     * 获取最大居民数
     */
    public int getMaxResidents() {
        return type.getBaseResidents() + (level - 1) * 5;
    }

    // ==================== Territory管理 ====================

    /**
     * 添加Territory
     */
    public void addTerritory(UUID territoryId) {
        territories.add(territoryId);
    }

    /**
     * 移除Territory
     */
    public void removeTerritory(UUID territoryId) {
        territories.remove(territoryId);
    }

    /**
     * 获取Territory数量
     */
    public int getTerritoryCount() {
        return territories.size();
    }

    // ==================== 等级系统 ====================

    /**
     * 升级
     */
    public boolean levelUp() {
        if (level >= maxLevel) {
            return false;
        }

        // 检查升级条件
        if (!canLevelUp()) {
            return false;
        }

        level++;
        updateType();
        return true;
    }

    /**
     * 检查是否可以升级
     */
    public boolean canLevelUp() {
        if (level >= maxLevel) {
            return false;
        }

        int requiredResidents = level * 10;
        int requiredTerritories = level * 5;

        return residents.size() >= requiredResidents &&
               territories.size() >= requiredTerritories;
    }

    /**
     * 根据等级更新城市类型
     */
    private void updateType() {
        if (level >= 8) {
            type = CityType.METROPOLIS;
        } else if (level >= 5) {
            type = CityType.CITY;
        } else if (level >= 3) {
            type = CityType.TOWN;
        } else {
            type = CityType.SETTLEMENT;
        }
    }

    /**
     * 获取升级所需条件
     */
    public LevelRequirements getNextLevelRequirements() {
        if (level >= maxLevel) {
            return null;
        }

        int requiredResidents = level * 10;
        int requiredTerritories = level * 5;
        double requiredGold = level * 1000.0;

        return new LevelRequirements(
            level + 1,
            requiredResidents,
            requiredTerritories,
            requiredGold
        );
    }

    // ==================== 经济系统 ====================

    /**
     * 存款
     */
    public void deposit(double amount) {
        if (amount > 0) {
            treasury += amount;
        }
    }

    /**
     * 取款
     */
    public boolean withdraw(double amount) {
        if (amount > 0 && treasury >= amount) {
            treasury -= amount;
            return true;
        }
        return false;
    }

    /**
     * 计算每日维护费
     */
    public double calculateDailyUpkeep() {
        double base = type.getBaseUpkeep();
        double residentCost = residents.size() * 2.0;
        double territoryCost = territories.size() * 5.0;
        double levelMultiplier = 1.0 + (level * 0.05);

        dailyUpkeep = (base + residentCost + territoryCost) * levelMultiplier;
        return dailyUpkeep;
    }

    // ==================== Getter/Setter ====================

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public UUID getMayor() {
        return mayor;
    }

    public Set<UUID> getResidents() {
        return Collections.unmodifiableSet(residents);
    }

    public int getResidentCount() {
        return residents.size();
    }

    public Set<UUID> getTerritories() {
        return Collections.unmodifiableSet(territories);
    }

    public int getLevel() {
        return level;
    }

    public CityType getType() {
        return type;
    }

    public double getTreasury() {
        return treasury;
    }

    public double getDailyUpkeep() {
        return dailyUpkeep;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0, Math.min(1.0, taxRate));
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isPublicSpawn() {
        return publicSpawn;
    }

    public void setPublicSpawn(boolean publicSpawn) {
        this.publicSpawn = publicSpawn;
    }

    public boolean isOpenRecruitment() {
        return openRecruitment;
    }

    public void setOpenRecruitment(boolean openRecruitment) {
        this.openRecruitment = openRecruitment;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getAgeDays() {
        long diff = System.currentTimeMillis() - createdTime;
        return diff / (1000 * 60 * 60 * 24);
    }

    /**
     * 获取城市出生点
     */
    public org.bukkit.Location getSpawnPoint() {
        return spawnPoint;
    }

    /**
     * 设置城市出生点
     */
    public void setSpawnPoint(org.bukkit.Location spawnPoint) {
        this.spawnPoint = spawnPoint;
    }

    /**
     * 设置城市等级（用于数据库加载）
     */
    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(maxLevel, level));
        updateType();
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取带颜色的类型名称
     */
    public String getColoredTypeName() {
        return type.getColoredName();
    }

    /**
     * 获取完整显示名
     */
    public String getDisplayName() {
        return getColoredTypeName() + " §f" + name;
    }

    @Override
    public String toString() {
        return String.format(
            "City[name=%s, type=%s, level=%d, residents=%d]",
            name, type, level, residents.size()
        );
    }

    // ==================== 内部类 ====================

    /**
     * 城市类型
     */
    public enum CityType {
        SETTLEMENT("定居点", "§7", 20, 20.0),
        TOWN("城镇", "§a", 50, 50.0),
        CITY("城市", "§e", 100, 100.0),
        METROPOLIS("大都市", "§6", 200, 200.0);

        private final String displayName;
        private final String colorCode;
        private final int baseResidents;
        private final double baseUpkeep;

        CityType(String displayName, String colorCode,
                int baseResidents, double baseUpkeep) {
            this.displayName = displayName;
            this.colorCode = colorCode;
            this.baseResidents = baseResidents;
            this.baseUpkeep = baseUpkeep;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }

        public String getColoredName() {
            return colorCode + displayName;
        }

        public int getBaseResidents() {
            return baseResidents;
        }

        public double getBaseUpkeep() {
            return baseUpkeep;
        }
    }

    /**
     * 升级需求
     */
    public record LevelRequirements(
        int targetLevel,
        int requiredResidents,
        int requiredTerritories,
        double requiredGold
    ) {
        @Override
        public String toString() {
            return String.format(
                "升级到 Lv.%d: %d居民, %d领地, %.0f金币",
                targetLevel, requiredResidents, requiredTerritories, requiredGold
            );
        }
    }
}
