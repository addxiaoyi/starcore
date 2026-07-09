package dev.starcore.starcore.module.emergency;

import dev.starcore.starcore.module.emergency.model.EmergencyState;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * 紧急状态服务接口
 * 提供国家紧急状态的声明、取消和查询功能
 */
public interface EmergencyService {

    /**
     * 宣布紧急状态
     * @param nationId 国家ID
     * @param type 紧急状态类型
     * @param reason 原因
     * @param durationMinutes 持续时间（分钟）
     * @return 成功返回true
     */
    boolean declareEmergency(NationId nationId, EmergencyState.EmergencyType type, String reason, int durationMinutes);

    /**
     * 取消紧急状态
     * @param nationId 国家ID
     * @return 成功返回true
     */
    boolean cancelEmergency(NationId nationId);

    /**
     * 检查国家是否处于紧急状态
     * @param nationId 国家ID
     * @return 紧急状态信息（如果有）
     */
    Optional<EmergencyState> getEmergencyState(NationId nationId);

    /**
     * 检查国家是否处于任何紧急状态
     * @param nationId 国家ID
     * @return true表示处于紧急状态
     */
    boolean isInEmergency(NationId nationId);

    /**
     * 获取所有处于紧急状态的国家
     * @return 紧急状态列表
     */
    Collection<EmergencyState> getAllEmergencies();

    /**
     * 延长紧急状态时间
     * @param nationId 国家ID
     * @param additionalMinutes 额外时间（分钟）
     * @return 成功返回true
     */
    boolean extendEmergency(NationId nationId, int additionalMinutes);

    /**
     * 获取紧急状态摘要
     * @return 摘要信息
     */
    String summary();
}