package dev.starcore.starcore.module.merge;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import dev.starcore.starcore.module.merge.model.MergeReferendumState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 合并公投服务接口
 * 提供国家合并公投的创建、投票、执行等功能
 */
public interface MergeReferendumService {

    /**
     * 发起合并公投
     * @param proposerId 发起者ID
     * @param proposerName 发起者名称
     * @param nation1Id 第一个国家ID
     * @param nation2Id 第二个国家ID
     * @param targetName 合并后国家名称
     * @return 创建的公投对象
     */
    MergeReferendum propose(UUID proposerId, String proposerName, UUID nation1Id, UUID nation2Id, String targetName);

    /**
     * 发起吞并公投（宗主国吞并卫星国）
     * @param proposerId 发起者ID
     * @param proposerName 发起者名称
     * @param suzerainId 宗主国ID
     * @param vassalId 卫星国ID
     * @return 创建的公投对象
     */
    MergeReferendum proposeAnnexation(UUID proposerId, String proposerName, UUID suzerainId, UUID vassalId);

    /**
     * 玩家投票
     * @param voterId 投票者ID
     * @param referendumId 公投ID
     * @param approve true=赞成, false=反对
     * @return 投票成功返回true
     */
    boolean vote(UUID voterId, UUID referendumId, boolean approve);

    /**
     * 取消公投（仅发起者可取消）
     * @param referendumId 公投ID
     * @param cancellerId 取消者ID
     * @return 取消成功返回true
     */
    boolean cancel(UUID referendumId, UUID cancellerId);

    /**
     * 获取公投详情
     * @param referendumId 公投ID
     * @return 公投对象
     */
    Optional<MergeReferendum> get(UUID referendumId);

    /**
     * 获取国家所有进行中的公投
     * @param nationId 国家ID
     * @return 公投列表
     */
    Collection<MergeReferendum> getNationReferendums(UUID nationId);

    /**
     * 获取玩家参与的所有公投
     * @param playerId 玩家ID
     * @return 公投列表
     */
    List<MergeReferendum> getPlayerReferendums(UUID playerId);

    /**
     * 检查玩家是否已投票
     * @param voterId 投票者ID
     * @param referendumId 公投ID
     * @return 是否已投票
     */
    boolean hasVoted(UUID voterId, UUID referendumId);

    /**
     * 检查玩家是否在公投涉及的国家中
     * @param playerId 玩家ID
     * @param referendumId 公投ID
     * @return 是否参与公投
     */
    boolean isParticipant(UUID playerId, UUID referendumId);

    /**
     * 获取公投统计信息
     * @param referendumId 公投ID
     * @return 统计信息 [赞成票数, 反对票数, 总票数]
     */
    int[] getVoteStats(UUID referendumId);

    /**
     * 检查公投是否通过
     * @param referendumId 公投ID
     * @return 是否通过
     */
    boolean isApproved(UUID referendumId);

    /**
     * 获取合并公投历史
     * @param nationId 国家ID
     * @param limit 限制数量
     * @return 历史公投列表
     */
    List<MergeReferendum> getHistory(UUID nationId, int limit);

    /**
     * 清除过期公投并执行到期的公投
     */
    void processExpiredReferendums();

    /**
     * 获取所有进行中的公投
     * @return 公投列表
     */
    Collection<MergeReferendum> getAllActive();

    /**
     * 强制执行公投（管理员用）
     * @param referendumId 公投ID
     * @return 执行成功返回true
     */
    boolean forceExecute(UUID referendumId);

    /**
     * 获取模块摘要
     * @return 摘要信息
     */
    String summary();
}
