package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.NationalReserve;

import java.util.Map;
import java.util.Optional;

/**
 * 战略储备服务
 * 管理国家战略资源储备
 */
public interface ResourceReserveService {
    /**
     * 获取国家储备
     *
     * @param nationId 国家ID
     * @return 国家储备
     */
    Optional<NationalReserve> getReserve(NationId nationId);

    /**
     * 创建国家储备
     *
     * @param nationId 国家ID
     * @return 国家储备
     */
    NationalReserve createReserve(NationId nationId);

    /**
     * 添加储备
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param amount 数量
     * @return 是否成功添加
     */
    boolean addReserve(NationId nationId, String resourceId, long amount);

    /**
     * 消耗储备
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param amount 数量
     * @return 是否成功消耗
     */
    boolean consumeReserve(NationId nationId, String resourceId, long amount);

    /**
     * 获取储备量
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 储备量
     */
    long getReserveAmount(NationId nationId, String resourceId);

    /**
     * 设置储备目标
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param goal 目标数量
     * @return 是否成功设置
     */
    boolean setReserveGoal(NationId nationId, String resourceId, long goal);

    /**
     * 获取储备目标
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 目标数量
     */
    long getReserveGoal(NationId nationId, String resourceId);

    /**
     * 检查是否达到储备目标
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 是否达到目标
     */
    boolean hasMetGoal(NationId nationId, String resourceId);

    /**
     * 获取储备完成度
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 完成度百分比
     */
    double getGoalProgress(NationId nationId, String resourceId);

    /**
     * 获取总体储备完成度
     *
     * @param nationId 国家ID
     * @return 总体完成度百分比
     */
    double getOverallProgress(NationId nationId);

    /**
     * 获取未完成的储备目标数量
     *
     * @param nationId 国家ID
     * @return 未完成数量
     */
    int getUnmetGoalsCount(NationId nationId);

    /**
     * 从普通库存转移到储备
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param amount 数量
     * @return 是否成功转移
     */
    boolean transferToReserve(NationId nationId, String resourceId, long amount);

    /**
     * 从储备转移到普通库存（紧急调用）
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param amount 数量
     * @return 是否成功转移
     */
    boolean emergencyRelease(NationId nationId, String resourceId, long amount);

    /**
     * 获取所有储备信息
     *
     * @param nationId 国家ID
     * @return 储备信息（资源ID -> 数量）
     */
    Map<String, Long> getAllReserves(NationId nationId);

    /**
     * 获取所有储备目标
     *
     * @param nationId 国家ID
     * @return 目标信息（资源ID -> 目标数量）
     */
    Map<String, Long> getAllGoals(NationId nationId);
}
