package dev.starcore.starcore.module.sovereignty;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyClaim;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 主权声明服务接口
 * 负责管理国家主权声明的核心逻辑
 */
public interface SovereigntyService {

    /**
     * 声明主权
     * @param nationId 国家ID
     * @param regionName 区域名称
     * @param description 主权描述
     * @param significance 主权重要性等级
     * @return 主权声明对象
     */
    SovereigntyRecord declareSovereignty(NationId nationId, String regionName, String description, SovereigntySignificance significance);

    /**
     * 撤销主权声明
     * @param sovereigntyId 主权声明ID
     * @return 是否成功撤销
     */
    boolean revokeSovereignty(UUID sovereigntyId);

    /**
     * 获取主权声明
     * @param sovereigntyId 主权声明ID
     * @return 主权声明对象
     */
    Optional<SovereigntyRecord> getSovereignty(UUID sovereigntyId);

    /**
     * 获取国家的所有主权声明
     * @param nationId 国家ID
     * @return 主权声明集合
     */
    Collection<SovereigntyRecord> getNationSovereignties(NationId nationId);

    /**
     * 获取区域的主权声明
     * @param regionName 区域名称
     * @return 主权声明对象
     */
    Optional<SovereigntyRecord> getSovereigntyByRegion(String regionName);

    /**
     * 检查国家是否声称对某区域拥有主权
     * @param nationId 国家ID
     * @param regionName 区域名称
     * @return 是否声称
     */
    boolean hasSovereigntyClaim(NationId nationId, String regionName);

    /**
     * 更新主权声明的强度
     * @param sovereigntyId 主权声明ID
     * @param strengthDelta 强度变化值
     * @return 更新后的主权声明对象
     */
    SovereigntyRecord updateStrength(UUID sovereigntyId, int strengthDelta);

    /**
     * 更新主权声明状态
     * @param sovereigntyId 主权声明ID
     * @param status 新状态
     * @return 是否成功更新
     */
    boolean updateStatus(UUID sovereigntyId, SovereigntyStatus status);

    /**
     * 获取所有有效的主权声明
     * @return 主权声明集合
     */
    Collection<SovereigntyRecord> getAllActiveSovereignties();

    /**
     * 获取主权重叠冲突
     * @param regionName 区域名称
     * @return 所有声称该区域主权的国家列表
     */
    Collection<SovereigntyRecord> getCompetingClaims(String regionName);

    /**
     * 添加主权要求领土
     * @param sovereigntyId 主权声明ID
     * @param world 世界名
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 是否成功添加
     */
    boolean addClaimedTerritory(UUID sovereigntyId, String world, int chunkX, int chunkZ);

    /**
     * 移除主权要求领土
     * @param sovereigntyId 主权声明ID
     * @param world 世界名
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 是否成功移除
     */
    boolean removeClaimedTerritory(UUID sovereigntyId, String world, int chunkX, int chunkZ);

    /**
     * 获取主权声明的领土列表
     * @param sovereigntyId 主权声明ID
     * @return 领土列表
     */
    Collection<SovereigntyClaim> getClaimedTerritories(UUID sovereigntyId);

    /**
     * 确认主权（解决争议）
     * @param sovereigntyId 被确认的主权声明ID
     * @param disputedIds 被争议的其他主权声明ID列表
     * @return 是否成功确认
     */
    boolean confirmSovereignty(UUID sovereigntyId, Collection<UUID> disputedIds);

    /**
     * 保存状态到持久化存储
     */
    void saveState();

    /**
     * 获取服务摘要
     */
    String summary();

    /**
     * 主权重要性等级
     */
    enum SovereigntySignificance {
        LOW("低", 1),
        MEDIUM("中", 2),
        HIGH("高", 3),
        CRITICAL("关键", 5);

        private final String displayName;
        private final int priority;

        SovereigntySignificance(String displayName, int priority) {
            this.displayName = displayName;
            this.priority = priority;
        }

        public String displayName() {
            return displayName;
        }

        public int priority() {
            return priority;
        }
    }

    /**
     * 主权声明状态
     */
    enum SovereigntyStatus {
        CLAIMED("已声称"),
        RECOGNIZED("已承认"),
        CONTESTED("争议中"),
        DISPUTED("被质疑"),
        REVOKED("已撤销");

        private final String displayName;

        SovereigntyStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}