package dev.starcore.starcore.module.army.commander;

import dev.starcore.starcore.module.army.commander.model.CommanderLevel;
import dev.starcore.starcore.module.army.commander.model.CommanderSkill;
import dev.starcore.starcore.module.army.commander.model.SkillType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 指挥官技能服务接口
 * 提供指挥官技能的管理、激活和使用
 */
public interface CommanderService {

    /**
     * 获取玩家的指挥官等级
     */
    CommanderLevel getCommanderLevel(UUID playerId);

    /**
     * 获取玩家的技能经验值
     */
    int getExperience(UUID playerId);

    /**
     * 获取玩家已解锁的技能列表
     */
    List<CommanderSkill> getUnlockedSkills(UUID playerId);

    /**
     * 获取玩家技能学习进度
     */
    Map<SkillType, Integer> getSkillProgress(UUID playerId);

    /**
     * 检查玩家是否已解锁指定技能
     */
    boolean hasSkill(UUID playerId, SkillType skillType);

    /**
     * 获取玩家技能等级
     */
    int getSkillLevel(UUID playerId, SkillType skillType);

    /**
     * 解锁新技能
     * @return 是否成功解锁（可能因为等级不足或已解锁而失败）
     */
    boolean unlockSkill(UUID playerId, SkillType skillType);

    /**
     * 升级已有技能
     * @return 是否成功升级
     */
    boolean upgradeSkill(UUID playerId, SkillType skillType);

    /**
     * 消耗技能经验值
     */
    void consumeExperience(UUID playerId, int amount);

    /**
     * 添加经验值
     */
    void addExperience(UUID playerId, int amount);

    /**
     * 检查玩家是否可以使用技能
     */
    boolean canUseSkill(Player player, SkillType skillType);

    /**
     * 使用技能
     * @param player 玩家
     * @param skillType 技能类型
     * @param targetId 目标ID（如军队ID）
     * @return 技能使用结果消息
     */
    String useSkill(Player player, SkillType skillType, UUID targetId);

    /**
     * 获取技能冷却剩余时间（秒）
     */
    long getSkillCooldown(UUID playerId, SkillType skillType);

    /**
     * 重置玩家技能（用于重新分配）
     * @return 退还的经验值
     */
    int resetSkills(UUID playerId);

    /**
     * 获取指挥官的加成倍率
     * @param nationId 国家ID
     * @return 加成倍率（1.0 = 无加成）
     */
    double getNationMoraleBonus(NationId nationId);

    /**
     * 获取国家内最高指挥官
     */
    Optional<UUID> getHighestCommander(NationId nationId);

    /**
     * 保存玩家数据
     */
    void savePlayerData(UUID playerId);

    /**
     * 加载玩家数据
     */
    void loadPlayerData(UUID playerId);
}
