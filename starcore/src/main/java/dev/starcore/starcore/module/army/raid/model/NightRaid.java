package dev.starcore.starcore.module.army.raid.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 夜间突袭记录
 * 代表一次完整的突袭行动
 */
public record NightRaid(
    UUID id,
    NationId attackerNationId,
    NationId targetNationId,
    Instant startedAt,
    Instant endedAt,
    RaidStatus status,
    int totalSoldiers,
    int attackingSoldiers,
    int defendingSoldiers,
    int casualtiesAttacker,
    int casualtiesDefender,
    double lootValue,
    String lootDescription,
    RaidResult result,
    String description
) {
    /**
     * 突袭状态
     */
    public enum RaidStatus {
        PREPARING,    // 准备中
        ACTIVE,       // 进行中
        COMPLETED,    // 已完成
        CANCELLED,    // 已取消
        FAILED        // 失败
    }

    /**
     * 突袭结果
     */
    public enum RaidResult {
        SUCCESS,      // 成功
        PARTIAL,      // 部分成功
        REPELLED,     // 被击退
        TIMEOUT,      // 超时
        DRAW          // 平局
    }

    /**
     * 创建新突袭
     */
    public static NightRaid create(NationId attacker, NationId target, int soldiers) {
        return new NightRaid(
            UUID.randomUUID(),
            attacker,
            target,
            Instant.now(),
            null,
            RaidStatus.PREPARING,
            soldiers,
            soldiers,
            0,
            0,
            0,
            0.0,
            "",
            null,
            ""
        );
    }

    /**
     * 获取持续时间（秒）
     */
    public long durationSeconds() {
        if (endedAt == null) {
            return 0;
        }
        return endedAt.getEpochSecond() - startedAt.getEpochSecond();
    }

    /**
     * 突袭是否已完成
     */
    public boolean isCompleted() {
        return status == RaidStatus.COMPLETED || status == RaidStatus.CANCELLED || status == RaidStatus.FAILED;
    }

    /**
     * 是否成功
     */
    public boolean isSuccessful() {
        return result == RaidResult.SUCCESS || result == RaidResult.PARTIAL;
    }

    /**
     * 获取突袭进度百分比
     */
    public double progressPercent() {
        if (status == RaidStatus.COMPLETED) {
            return 100.0;
        }
        if (status == RaidStatus.PREPARING) {
            return 0.0;
        }
        // 假设最大持续 5 分钟
        long maxDuration = 300;
        return Math.min(100.0, (durationSeconds() * 100.0) / maxDuration);
    }

    /**
     * 格式化突袭报告
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 突袭报告 ==========\n");
        sb.append("突袭ID: ").append(id().toString().substring(0, 8)).append("\n");
        sb.append("状态: ").append(status()).append("\n");
        if (result != null) {
            sb.append("结果: ").append(result()).append("\n");
        }
        sb.append("攻击方损失: ").append(casualtiesAttacker()).append(" 士兵\n");
        sb.append("防守方损失: ").append(casualtiesDefender()).append(" 士兵\n");
        if (lootValue() > 0) {
            sb.append("掠夺价值: ").append(String.format("%.2f", lootValue())).append("\n");
        }
        if (!description().isEmpty()) {
            sb.append("详情: ").append(description()).append("\n");
        }
        sb.append("==============================");
        return sb.toString();
    }
}