package dev.starcore.starcore.module.army.exercise;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 战争演习服务接口
 * 提供军事演习的创建、管理、执行等功能
 */
public interface ExerciseService {

    /**
     * 创建一个新的演习
     *
     * @param organizerId 组织者国家ID
     * @param name 演习名称
     * @param exerciseType 演习类型
     * @return 创建的演习
     */
    Exercise createExercise(UUID organizerId, String name, ExerciseType exerciseType);

    /**
     * 开始演习
     *
     * @param exerciseId 演习ID
     * @return 是否成功开始
     */
    boolean startExercise(UUID exerciseId);

    /**
     * 结束演习
     *
     * @param exerciseId 演习ID
     * @param reason 结束原因
     * @return 演习结果
     */
    ExerciseResult endExercise(UUID exerciseId, String reason);

    /**
     * 加入演习
     *
     * @param exerciseId 演习ID
     * @param nationId 国家ID
     * @param soldierCount 参与士兵数量
     * @return 是否成功加入
     */
    boolean joinExercise(UUID exerciseId, UUID nationId, int soldierCount);

    /**
     * 离开演习
     *
     * @param exerciseId 演习ID
     * @param nationId 国家ID
     * @return 是否成功离开
     */
    boolean leaveExercise(UUID exerciseId, UUID nationId);

    /**
     * 获取演习
     *
     * @param exerciseId 演习ID
     * @return 演习（如果存在）
     */
    Optional<Exercise> getExercise(UUID exerciseId);

    /**
     * 获取国家参与的演习
     *
     * @param nationId 国家ID
     * @return 演习列表
     */
    List<Exercise> getExercisesByNation(UUID nationId);

    /**
     * 获取所有活跃演习
     *
     * @return 活跃演习列表
     */
    List<Exercise> getActiveExercises();

    /**
     * 获取等待开始的演习
     *
     * @return 等待中的演习列表
     */
    List<Exercise> getPendingExercises();

    /**
     * 获取组织者的演习
     *
     * @param organizerId 组织者ID
     * @return 演习列表
     */
    List<Exercise> getExercisesByOrganizer(UUID organizerId);

    /**
     * 更新演习状态
     *
     * @param exerciseId 演习ID
     * @param newState 新状态
     */
    void updateExerciseState(UUID exerciseId, ExerciseState newState);

    /**
     * 处理演习战斗
     *
     * @param exerciseId 演习ID
     * @param attackerNationId 攻击方国家ID
     * @param defenderNationId 防守方国家ID
     * @return 战斗结果
     */
    ExerciseBattleResult processBattle(UUID exerciseId, UUID attackerNationId, UUID defenderNationId);

    /**
     * 检查国家是否可以加入演习
     *
     * @param exerciseId 演习ID
     * @param nationId 国家ID
     * @return 检查结果消息
     */
    String checkJoinEligibility(UUID exerciseId, UUID nationId);

    /**
     * 获取当前配置
     *
     * @return 配置
     */
    ExerciseConfig getConfig();
}
