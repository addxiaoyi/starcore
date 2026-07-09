package dev.starcore.starcore.module.anniversary;

import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import dev.starcore.starcore.module.anniversary.model.AnniversaryType;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 国家纪念日服务接口
 * 提供纪念日的创建、管理、查询等功能
 */
public interface AnniversaryService {

    /**
     * 创建国家纪念日
     *
     * @param nationId 国家ID
     * @param name 纪念日名称
     * @param date 纪念日日期
     * @param type 纪念日类型
     * @param description 描述
     * @return 创建的纪念日
     */
    NationAnniversary createAnniversary(UUID nationId, String name, LocalDate date,
                                        AnniversaryType type, String description);

    /**
     * 删除纪念日
     *
     * @param anniversaryId 纪念日ID
     * @return 是否删除成功
     */
    boolean deleteAnniversary(UUID anniversaryId);

    /**
     * 获取国家的所有纪念日
     *
     * @param nationId 国家ID
     * @return 纪念日列表
     */
    List<NationAnniversary> getAnniversaries(UUID nationId);

    /**
     * 获取即将到来的纪念日
     *
     * @param nationId 国家ID
     * @param days 天数范围
     * @return 即将到来的纪念日列表
     */
    List<NationAnniversary> getUpcomingAnniversaries(UUID nationId, int days);

    /**
     * 获取今天的纪念日
     *
     * @param nationId 国家ID
     * @return 今天的纪念日列表
     */
    List<NationAnniversary> getTodayAnniversaries(UUID nationId);

    /**
     * 获取所有国家今天的纪念日
     *
     * @return 所有今天需要庆祝的纪念日
     */
    List<NationAnniversary> getAllTodayAnniversaries();

    /**
     * 获取所有即将到来的纪念日
     *
     * @param days 天数范围
     * @return 所有即将到来的纪念日
     */
    List<NationAnniversary> getAllUpcomingAnniversaries(int days);

    /**
     * 根据ID获取纪念日
     *
     * @param anniversaryId 纪念日ID
     * @return 纪念日（如果存在）
     */
    Optional<NationAnniversary> getAnniversary(UUID anniversaryId);

    /**
     * 更新纪念日
     *
     * @param anniversary 纪念日
     * @return 是否更新成功
     */
    boolean updateAnniversary(NationAnniversary anniversary);

    /**
     * 获取国家成立纪念日（国家创建周年）
     *
     * @param nationId 国家ID
     * @param foundingDate 成立日期
     * @return 纪念日对象
     */
    NationAnniversary getFoundingAnniversary(UUID nationId, LocalDate foundingDate);

    /**
     * 计算距离纪念日的天数
     *
     * @param anniversary 纪念日
     * @return 天数（如果是今天返回0，负数表示已过）
     */
    int daysUntilAnniversary(NationAnniversary anniversary);

    /**
     * 计算纪念日的周年数
     *
     * @param anniversary 纪念日
     * @return 周年数
     */
    int getAnniversaryYear(NationAnniversary anniversary);

    /**
     * 检查是否是国家的重要纪念日
     *
     * @param nationId 国家ID
     * @return 是否是重要纪念日（1周年、5周年、10周年等）
     */
    boolean isMilestoneAnniversary(NationAnniversary anniversary);

    /**
     * 标记纪念日为已庆祝
     *
     * @param anniversaryId 纪念日ID
     * @param celebratedAt 庆祝时间
     */
    void markAsCelebrated(UUID anniversaryId, LocalDateTime celebratedAt);

    /**
     * 保存状态到持久化存储
     */
    void saveState();

    /**
     * 加载状态
     */
    void loadState();

    /**
     * 获取模块摘要
     */
    String summary();
}
