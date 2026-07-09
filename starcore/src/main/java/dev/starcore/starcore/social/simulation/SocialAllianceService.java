package dev.starcore.starcore.social.simulation;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社交联盟服务
 *
 * 管理玩家之间的社交联盟:
 * - 联盟创建/解散
 * - 联盟成员管理
 * - 联盟资源共享
 * - 联盟活动
 *
 * 数据库持久化通过 AlliancePersistence 实现
 */
public class SocialAllianceService {

    // ==================== 联盟类型枚举 ====================

    public enum AllianceType {
        FRIEND("朋友圈", "§a"),
        PARTY("派对", "§b"),
        GUILD("公会联盟", "§6"),
        NATION("国家联盟", "§c"),
        CULTURAL("文化联盟", "§d"),
        TRADE("商业联盟", "§e"),
        MILITARY("军事同盟", "§4");

        private final String name;
        private final String color;

        AllianceType(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
    }

    // ==================== 联盟数据结构 ====================

    public record SocialAlliance(
        String id,
        String name,
        String tag,
        UUID founder,
        long createdAt,
        Set<UUID> members,
        Set<UUID> applicants,
        AllianceType type,
        Map<String, Object> stats,
        int legacyPoints
    ) {
        public int memberCount() { return members.size(); }
    }

    // ==================== 服务实现 ====================

    private final Map<String, SocialAlliance> alliances = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerAlliances = new ConcurrentHashMap<>();

    // 数据库持久化
    private AlliancePersistence persistence;
    private boolean persistenceEnabled = false;

    /**
     * 默认构造函数 - 无持久化
     */
    public SocialAllianceService() {
        this.persistence = null;
        this.persistenceEnabled = false;
    }

    /**
     * 完整构造函数 - 带数据库持久化
     */
    public SocialAllianceService(JavaPlugin plugin, DatabaseService databaseService, StarCoreScheduler scheduler) {
        this();
        initializePersistence(plugin, databaseService, scheduler);
    }

    /**
     * 初始化数据库持久化
     */
    public void initializePersistence(JavaPlugin plugin, DatabaseService databaseService, StarCoreScheduler scheduler) {
        if (plugin == null || databaseService == null || scheduler == null) {
            return;
        }

        this.persistence = new AlliancePersistence(plugin, databaseService, scheduler);
        this.persistence.initialize();

        // 加载已有数据
        List<SocialAlliance> loadedAlliances = this.persistence.loadAlliances();
        for (SocialAlliance alliance : loadedAlliances) {
            alliances.put(alliance.id(), alliance);
            for (UUID memberId : alliance.members()) {
                playerAlliances.computeIfAbsent(memberId, k -> new HashSet<>()).add(alliance.id());
            }
        }

        this.persistenceEnabled = true;
    }

    /**
     * 保存联盟到数据库（异步）
     */
    private void saveAlliance(SocialAlliance alliance) {
        if (persistenceEnabled && persistence != null) {
            persistence.saveAllianceAsync(alliance);
        }
    }

    /**
     * 创建联盟
     */
    public SocialAlliance createAlliance(UUID founder, String name, String tag) {
        String id = UUID.randomUUID().toString();
        SocialAlliance alliance = new SocialAlliance(
            id, name, tag, founder,
            System.currentTimeMillis(),
            new HashSet<>(Set.of(founder)),
            new HashSet<>(),
            AllianceType.FRIEND,
            new HashMap<>(),
            0
        );
        alliances.put(id, alliance);
        playerAlliances.computeIfAbsent(founder, k -> new HashSet<>()).add(id);

        // 持久化到数据库
        saveAlliance(alliance);

        return alliance;
    }

    /**
     * 加入联盟
     */
    public boolean joinAlliance(UUID playerId, String allianceId) {
        SocialAlliance alliance = alliances.get(allianceId);
        if (alliance == null) return false;

        if (alliance.members().contains(playerId)) return false;

        // 检查是否已申请
        if (!alliance.applicants().contains(playerId)) {
            // 创建新的申请者集合
            Set<UUID> newApplicants = new HashSet<>(alliance.applicants());
            newApplicants.add(playerId);

            // 更新联盟（不可变记录需要创建新实例）
            SocialAlliance updatedAlliance = new SocialAlliance(
                alliance.id(), alliance.name(), alliance.tag(),
                alliance.founder(), alliance.createdAt(),
                alliance.members(), newApplicants,
                alliance.type(), alliance.stats(),
                alliance.legacyPoints()
            );
            alliances.put(allianceId, updatedAlliance);
            return false; // 需要审批
        }

        // 正式加入 - 创建新实例
        Set<UUID> newMembers = new HashSet<>(alliance.members());
        newMembers.add(playerId);
        Set<UUID> newApplicants = new HashSet<>(alliance.applicants());
        newApplicants.remove(playerId);

        SocialAlliance updatedAlliance = new SocialAlliance(
            alliance.id(), alliance.name(), alliance.tag(),
            alliance.founder(), alliance.createdAt(),
            newMembers, newApplicants,
            alliance.type(), alliance.stats(),
            alliance.legacyPoints()
        );
        alliances.put(allianceId, updatedAlliance);
        playerAlliances.computeIfAbsent(playerId, k -> new HashSet<>()).add(allianceId);

        // 持久化到数据库
        saveAlliance(updatedAlliance);

        // 广播加入消息
        broadcastAllianceMessage(allianceId, playerId + " 加入了联盟！");

        return true;
    }

    /**
     * 离开联盟
     */
    public boolean leaveAlliance(UUID playerId, String allianceId) {
        SocialAlliance alliance = alliances.get(allianceId);
        if (alliance == null) return false;

        if (playerId.equals(alliance.founder())) {
            return false; // 创始人不能离开
        }

        // 创建新实例更新成员
        Set<UUID> newMembers = new HashSet<>(alliance.members());
        newMembers.remove(playerId);

        SocialAlliance updatedAlliance = new SocialAlliance(
            alliance.id(), alliance.name(), alliance.tag(),
            alliance.founder(), alliance.createdAt(),
            newMembers, alliance.applicants(),
            alliance.type(), alliance.stats(),
            alliance.legacyPoints()
        );
        alliances.put(allianceId, updatedAlliance);

        Set<String> playerAllianceSet = playerAlliances.get(playerId);
        if (playerAllianceSet != null) {
            playerAllianceSet.remove(allianceId);
        }

        // 持久化到数据库
        saveAlliance(updatedAlliance);

        broadcastAllianceMessage(allianceId, playerId + " 离开了联盟！");

        return true;
    }

    /**
     * 解散联盟
     */
    public boolean dissolveAlliance(String allianceId) {
        SocialAlliance alliance = alliances.remove(allianceId);
        if (alliance == null) return false;

        for (UUID memberId : alliance.members()) {
            Set<String> playerAllianceSet = playerAlliances.get(memberId);
            if (playerAllianceSet != null) {
                playerAllianceSet.remove(allianceId);
            }
        }

        // 从数据库删除
        if (persistenceEnabled && persistence != null) {
            persistence.deleteAlliance(allianceId);
        }

        return true;
    }

    /**
     * 邀请加入
     */
    public void inviteToAlliance(UUID inviterId, UUID targetId, String allianceId) {
        SocialAlliance alliance = alliances.get(allianceId);
        if (alliance == null) return;

        if (!alliance.members().contains(inviterId)) return;

        // 创建新实例更新申请者
        Set<UUID> newApplicants = new HashSet<>(alliance.applicants());
        newApplicants.add(targetId);

        SocialAlliance updatedAlliance = new SocialAlliance(
            alliance.id(), alliance.name(), alliance.tag(),
            alliance.founder(), alliance.createdAt(),
            alliance.members(), newApplicants,
            alliance.type(), alliance.stats(),
            alliance.legacyPoints()
        );
        alliances.put(allianceId, updatedAlliance);

        // 持久化
        saveAlliance(updatedAlliance);

        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            target.sendMessage("§6§l【联盟邀请】§e" + inviterId + " 邀请你加入 " + alliance.name());
            target.sendMessage("§a使用 /alliance accept " + allianceId + " 接受邀请");
        }
    }

    /**
     * 获取联盟
     */
    public Optional<SocialAlliance> getAlliance(String allianceId) {
        return Optional.ofNullable(alliances.get(allianceId));
    }

    /**
     * 获取玩家联盟
     */
    public Set<String> getPlayerAlliances(UUID playerId) {
        return playerAlliances.getOrDefault(playerId, Set.of());
    }

    /**
     * 广播联盟消息
     */
    public void broadcastAllianceMessage(String allianceId, String message) {
        SocialAlliance alliance = alliances.get(allianceId);
        if (alliance == null) return;

        for (UUID memberId : alliance.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage("§6[§e" + alliance.name() + "§6]§f " + message);
            }
        }
    }

    /**
     * 获取联盟排行榜
     */
    public List<SocialAlliance> getAllianceLeaderboard() {
        return alliances.values().stream()
            .sorted((a, b) -> Integer.compare(b.legacyPoints(), a.legacyPoints()))
            .limit(10)
            .toList();
    }

    /**
     * 添加联盟遗产点数
     */
    public void addLegacyPoints(String allianceId, int points) {
        SocialAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            // 更新 legacyPoints
            Map<String, Object> newStats = new HashMap<>(alliance.stats());
            newStats.put("legacy", alliance.legacyPoints() + points);
            SocialAlliance updatedAlliance = new SocialAlliance(
                alliance.id(), alliance.name(), alliance.tag(),
                alliance.founder(), alliance.createdAt(),
                alliance.members(), alliance.applicants(),
                alliance.type(), newStats,
                alliance.legacyPoints() + points
            );
            alliances.put(allianceId, updatedAlliance);

            // 持久化到数据库
            saveAlliance(updatedAlliance);
        }
    }

    /**
     * 检查数据库持久化是否可用
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    /**
     * 获取联盟数量
     */
    public int getAllianceCount() {
        return alliances.size();
    }
}
