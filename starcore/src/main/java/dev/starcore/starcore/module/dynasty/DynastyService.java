package dev.starcore.starcore.module.dynasty;

import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.dynasty.model.SuccessionType;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 王位继承服务接口
 * 管理国家王位继承、禅让、继承顺序等功能
 */
public interface DynastyService {

    /**
     * 获取国家的当前王朝信息
     * @param nationId 国家ID
     * @return 王朝信息（如果存在）
     */
    Optional<Dynasty> getDynasty(NationId nationId);

    /**
     * 创建新王朝（国家创建时自动调用）
     * @param nationId 国家ID
     * @param founderId 创始人ID
     * @param founderName 创始人名称
     * @return 新创建的王朝
     */
    Dynasty createDynasty(NationId nationId, UUID founderId, String founderName);

    /**
     * 禅让王位（君主主动传位）
     * @param nationId 国家ID
     * @param currentMonarch 当前君主ID
     * @param newMonarch 新君主ID
     * @param reason 禅让原因/理由
     * @return 禅让结果
     */
    SuccessionResult abdicate(NationId nationId, UUID currentMonarch, UUID newMonarch, String reason);

    /**
     * 继承王位（君主去世或退位后的自动继承）
     * @param nationId 国家ID
     * @param inheritorId 继承人ID
     * @return 继承结果
     */
    SuccessionResult inherit(NationId nationId, UUID inheritorId);

    /**
     * 添加王位继承人（按继承顺序）
     * @param nationId 国家ID
     * @param monarchId 君主ID
     * @param heirId 继承人ID
     * @return 操作结果
     */
    SuccessionResult addHeir(NationId nationId, UUID monarchId, UUID heirId);

    /**
     * 移除继承人
     * @param nationId 国家ID
     * @param monarchId 君主ID
     * @param heirId 继承人ID
     * @return 操作结果
     */
    SuccessionResult removeHeir(NationId nationId, UUID monarchId, UUID heirId);

    /**
     * 获取继承人列表（按继承顺序）
     * @param nationId 国家ID
     * @return 继承人列表
     */
    List<HeirRecord> getHeirs(NationId nationId);

    /**
     * 设置王位继承类型
     * @param nationId 国家ID
     * @param monarchId 君主ID
     * @param type 继承类型
     * @return 操作结果
     */
    SuccessionResult setSuccessionType(NationId nationId, UUID monarchId, SuccessionType type);

    /**
     * 检查是否处于王位空缺期
     * @param nationId 国家ID
     * @return 是否空缺
     */
    boolean isInterregnum(NationId nationId);

    /**
     * 获取空缺期开始时间
     * @param nationId 国家ID
     * @return 空缺开始时间（如果处于空缺期）
     */
    Optional<Instant> getInterregnumStart(NationId nationId);

    /**
     * 索取王位（继承顺位第一位）
     * @param nationId 国家ID
     * @param claimantId 索取者ID
     * @return 索取结果
     */
    SuccessionResult claimCrown(NationId nationId, UUID claimantId);

    /**
     * 放弃王位索取权
     * @param nationId 国家ID
     * @param claimantId 放弃者ID
     */
    void renounceClaim(NationId nationId, UUID claimantId);

    /**
     * 获取所有空缺王位的国家
     * @return 空缺国家列表
     */
    Collection<NationId> getInterregnumNations();

    /**
     * 保存状态
     */
    void saveState();

    /**
     * 获取摘要
     */
    String summary();

    // ==================== 结果记录 ====================

    /**
     * 继承结果
     */
    record SuccessionResult(boolean success, String message, Dynasty dynasty) {
        public static SuccessionResult ok(String message, Dynasty dynasty) {
            return new SuccessionResult(true, message, dynasty);
        }

        public static SuccessionResult fail(String message) {
            return new SuccessionResult(false, message, null);
        }
    }

    /**
     * 继承人记录
     */
    record HeirRecord(UUID playerId, String playerName, int position, Instant addedAt) {}
}