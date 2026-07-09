package dev.starcore.starcore.module.sovereignty;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 主权声明记录
 * 代表一个国家声称对某区域拥有的主权
 *
 * @param id 主权声明唯一标识符
 * @param nationId 声称主权的国家ID
 * @param regionName 区域名称
 * @param description 主权描述/理由
 * @param significance 主权重要性等级
 * @param status 当前状态
 * @param strength 主权强度（影响争议结果）
 * @param declaredAt 声明时间
 * @param updatedAt 最后更新时间
 */
public record Sovereignty(
        UUID id,
        NationId nationId,
        String regionName,
        String description,
        SovereigntyService.SovereigntySignificance significance,
        SovereigntyService.SovereigntyStatus status,
        int strength,
        Instant declaredAt,
        Instant updatedAt
) {

    /**
     * 创建一个新的主权声明
     */
    public static Sovereignty create(
            NationId nationId,
            String regionName,
            String description,
            SovereigntyService.SovereigntySignificance significance
    ) {
        Instant now = Instant.now();
        return new Sovereignty(
                UUID.randomUUID(),
                nationId,
                regionName,
                description,
                significance,
                SovereigntyService.SovereigntyStatus.CLAIMED,
                0,
                now,
                now
        );
    }

    /**
     * 检查主权是否有效（未被撤销）
     */
    public boolean isActive() {
        return status != SovereigntyService.SovereigntyStatus.REVOKED;
    }

    /**
     * 检查主权是否被承认
     */
    public boolean isRecognized() {
        return status == SovereigntyService.SovereigntyStatus.RECOGNIZED;
    }

    /**
     * 检查主权是否存在争议
     */
    public boolean isContested() {
        return status == SovereigntyService.SovereigntyStatus.CONTESTED
                || status == SovereigntyService.SovereigntyStatus.DISPUTED;
    }

    /**
     * 获取优先权分数（用于解决争议）
     * 分数越高，优先权越大
     */
    public int priorityScore() {
        int score = significance.priority() * 100;
        score += Math.min(strength, 1000); // 强度最多加1000分
        return score;
    }

    @Override
    public String toString() {
        return String.format("Sovereignty{id=%s, region='%s', nation=%s, status=%s, significance=%s, strength=%d}",
                id.toString().substring(0, 8), regionName, nationId, status, significance, strength);
    }
}