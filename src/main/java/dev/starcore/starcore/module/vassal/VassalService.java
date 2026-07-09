package dev.starcore.starcore.module.vassal;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.vassal.model.VassalRelation;
import dev.starcore.starcore.module.vassal.model.VassalRelationSnapshot;
import dev.starcore.starcore.module.vassal.model.VassalType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/**
 * 宗藩系统服务接口
 * Service interface for the vassal system
 */
public interface VassalService {
    
    // ==================== 宗主国视角 ====================
    
    /**
     * 获取宗主国的所有藩属
     */
    Collection<VassalRelationSnapshot> vassalsOf(NationId suzerainId);
    
    /**
     * 获取宗藩关系详情
     */
    Optional<VassalRelation> relationById(NationId suzerainId, NationId vassalId);
    
    // ==================== 藩属国视角 ====================
    
    /**
     * 获取藩属国的宗主国
     */
    Optional<VassalRelationSnapshot> suzerainOf(NationId vassalId);
    
    // ==================== 宗藩关系管理 ====================
    
    /**
     * 发送宗藩邀请
     */
    VassalInviteResult sendInvite(NationId suzerainId, NationId vassalId, VassalType type);
    
    /**
     * 接受宗藩邀请
     */
    VassalResult acceptInvite(NationId vassalId, NationId suzerainId);
    
    /**
     * 拒绝宗藩邀请
     */
    void rejectInvite(NationId vassalId, NationId suzerainId);
    
    /**
     * 取消宗藩邀请
     */
    void cancelInvite(NationId suzerainId, NationId vassalId);
    
    /**
     * 解除宗藩关系（宗主国发起）
     */
    boolean releaseVassal(NationId suzerainId, NationId vassalId);
    
    /**
     * 独立（藩属国发起）
     */
    VassalIndependenceResult declareIndependence(NationId vassalId);
    
    // ==================== 贡金管理 ====================
    
    /**
     * 设置贡金金额
     */
    boolean setTribute(NationId suzerainId, NationId vassalId, BigDecimal amount);
    
    /**
     * 藩属国缴纳贡金
     */
    TributeResult payTribute(NationId vassalId, NationId suzerainId, BigDecimal amount);
    
    /**
     * 获取待处理的宗藩邀请
     */
    Collection<VassalInviteInfo> getPendingInvites(NationId nationId);
    
    // ==================== 宗主保护 ====================
    
    /**
     * 检查国家是否受宗主国保护
     */
    boolean isUnderProtection(NationId nationId);
    
    /**
     * 获取保护该国家的宗主国
     */
    Optional<NationId> getProtector(NationId nationId);
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查两个国家是否存在宗藩关系
     */
    boolean hasVassalRelation(NationId nation1, NationId nation2);
    
    /**
     * 获取宗藩关系快照
     */
    Optional<VassalRelationSnapshot> getSnapshot(NationId nation1, NationId nation2);
    
    /**
     * 获取统计信息
     */
    VassalStats getStats();
    
    /**
     * 获取模块摘要
     */
    String summary();
    
    // ==================== 结果类 ====================
    
    record VassalInviteResult(boolean success, String message) {}
    
    record VassalResult(boolean success, String message) {}
    
    record VassalIndependenceResult(boolean success, String message, boolean warStarted) {}
    
    record TributeResult(boolean success, String message, BigDecimal amount) {}
    
    record VassalInviteInfo(
        NationId suzerainId,
        String suzerainName,
        VassalType type,
        long remainingMs
    ) {}
    
    record VassalStats(
        int totalVassals,
        int totalSuzerains,
        int pendingInvites,
        int totalTributeToday
    ) {}
}
