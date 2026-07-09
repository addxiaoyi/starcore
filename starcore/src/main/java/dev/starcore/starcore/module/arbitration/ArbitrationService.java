package dev.starcore.starcore.module.arbitration;

import dev.starcore.starcore.module.arbitration.model.ArbitrationCase;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCaseType;
import dev.starcore.starcore.module.arbitration.model.ArbitrationResult;
import dev.starcore.starcore.module.arbitration.model.ArbitrationStatus;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领土仲裁服务
 * 负责处理领土纠纷的提交、审理和裁决
 */
public interface ArbitrationService {

    /**
     * 提交仲裁申请
     * @param claimant 申诉方国家ID
     * @param respondent 被申诉方国家ID
     * @param caseType 案件类型
     * @param disputedChunks 争议区块列表
     * @param evidence 证据描述
     * @param claimFee 申诉费用
     * @return 仲裁案例
     */
    ArbitrationCase submitCase(
        NationId claimant,
        NationId respondent,
        ArbitrationCaseType caseType,
        List<ChunkCoordinate> disputedChunks,
        String evidence,
        BigDecimal claimFee
    );

    /**
     * 接受仲裁申请
     * @param caseId 案例ID
     * @param arbitrator 仲裁员
     * @return 是否成功
     */
    boolean acceptCase(UUID caseId, UUID arbitrator);

    /**
     * 提交答辩
     * @param caseId 案例ID
     * @param respondent 被申诉方
     * @param defense 答辩内容
     * @return 是否成功
     */
    boolean submitDefense(UUID caseId, NationId respondent, String defense);

    /**
     * 添加证据
     * @param caseId 案例ID
     * @param submitter 提交方
     * @param evidence 证据内容
     * @return 是否成功
     */
    boolean addEvidence(UUID caseId, NationId submitter, String evidence);

    /**
     * 做出裁决
     * @param caseId 案例ID
     * @param arbitrator 仲裁员
     * @param result 裁决结果
     * @param ruling 裁决详情
     * @return 是否成功
     */
    boolean makeRuling(UUID caseId, UUID arbitrator, ArbitrationResult result, String ruling);

    /**
     * 取消仲裁（申诉方撤诉）
     * @param caseId 案例ID
     * @return 是否成功
     */
    boolean withdrawCase(UUID caseId);

    /**
     * 获取仲裁案例
     * @param caseId 案例ID
     * @return 仲裁案例
     */
    Optional<ArbitrationCase> getCase(UUID caseId);

    /**
     * 获取国家的所有仲裁案例
     * @param nationId 国家ID
     * @return 案例列表
     */
    Collection<ArbitrationCase> getCasesForNation(NationId nationId);

    /**
     * 获取等待分配的案例
     * @return 待分配案例
     */
    Collection<ArbitrationCase> getPendingCases();

    /**
     * 获取仲裁员正在审理的案例
     * @param arbitrator 仲裁员UUID
     * @return 案例列表
     */
    Collection<ArbitrationCase> getCasesForArbitrator(UUID arbitrator);

    /**
     * 检查国家是否有未解决的仲裁案件
     * @param nationId 国家ID
     * @return 是否有未解决案件
     */
    boolean hasPendingCase(NationId nationId);

    /**
     * 获取指定状态的案例
     * @param status 案例状态
     * @return 案例列表
     */
    Collection<ArbitrationCase> getCasesByStatus(ArbitrationStatus status);

    /**
     * 获取仲裁费用配置
     */
    BigDecimal getFilingFee();

    /**
     * 获取最低申诉费用
     */
    BigDecimal getMinimumClaimFee();

    /**
     * 获取最高申诉费用
     */
    BigDecimal getMaximumClaimFee();

    /**
     * 保存状态
     */
    void saveState();

    /**
     * 获取摘要
     */
    String summary();

    /**
     * 仲裁案例记录
     */
    record CaseRecord(
        UUID caseId,
        NationId claimant,
        NationId respondent,
        ArbitrationCaseType caseType,
        ArbitrationStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {}
}