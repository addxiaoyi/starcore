package dev.starcore.starcore.mechanics;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 疾病
 * 表示玩家感染的疾病
 */
public class Disease {

    private final UUID id;
    private final UUID playerId;
    private final DiseaseType type;
    private final long infectionTime;
    private int severity; // 当前严重程度 (1-100)
    private long lastDamageTime;
    private boolean treated; // 是否正在接受治疗

    public Disease(UUID playerId, DiseaseType type) {
        this.id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.infectionTime = System.currentTimeMillis();
        this.severity = type.getSeverity() * 20; // 初始严重程度
        this.lastDamageTime = 0;
        this.treated = false;
    }

    /**
     * 应用疾病效果到玩家
     */
    public void applyEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        switch (type) {
            case COLD:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 200, 0, true, true));
                break;

            case FLU:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 200, 0, true, true));
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HUNGER, 200, 0, true, true));
                break;

            case PLAGUE:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 200, 1, true, true));
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 200, 1, true, true));
                // 持续伤害
                long now = System.currentTimeMillis();
                if (now - lastDamageTime > 5000) { // 每5秒伤害一次
                    player.damage(1.0);
                    lastDamageTime = now;
                }
                break;

            case POISON:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON, 200, 0, true, true));
                break;

            case CURSE:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 200, 0, true, true));
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.UNLUCK, 200, 1, true, true));
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 200, 0, true, true));
                break;

            case INFECTION:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WITHER, 200, 0, true, true));
                break;

            case FEVER:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NAUSEA, 200, 0, true, true));
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, 100, 0, true, true));
                break;

            case WEAKNESS:
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 200, 0, true, true));
                break;
        }
    }

    /**
     * 移除疾病效果
     */
    public void removeEffects(Player player) {
        if (player == null) return;

        // 移除所有相关的药水效果
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.UNLUCK);
    }

    /**
     * 疾病恶化
     */
    public void worsen(int amount) {
        this.severity = Math.min(100, this.severity + amount);
    }

    /**
     * 疾病好转
     */
    public void improve(int amount) {
        this.severity = Math.max(0, this.severity - amount);
    }

    /**
     * 检查是否已痊愈
     */
    public boolean isCured() {
        return severity <= 0;
    }

    /**
     * 检查是否已过期（超过持续时间）
     */
    public boolean isExpired() {
        long days = (System.currentTimeMillis() - infectionTime) / (1000 * 60 * 60 * 24);
        return days >= type.getDuration();
    }

    /**
     * 获取感染天数
     */
    public int getDaysSinceInfection() {
        return (int) ((System.currentTimeMillis() - infectionTime) / (1000 * 60 * 60 * 24));
    }

    /**
     * 获取剩余天数
     */
    public int getRemainingDays() {
        return Math.max(0, type.getDuration() - getDaysSinceInfection());
    }

    /**
     * 获取疾病描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getColorCode()).append("§l[").append(type.getDisplayName()).append("]\n");
        sb.append("§7严重程度: ").append(type.getSeverityDescription())
          .append(" §f(").append(severity).append("/100)\n");
        sb.append("§7症状: §f").append(type.getSymptoms()).append("\n");
        sb.append("§7感染时间: §f").append(getDaysSinceInfection()).append(" 天前\n");
        sb.append("§7预计持续: §f").append(getRemainingDays()).append(" 天\n");

        if (treated) {
            sb.append("§a正在接受治疗\n");
        }

        return sb.toString();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public DiseaseType getType() {
        return type;
    }

    public long getInfectionTime() {
        return infectionTime;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = Math.max(0, Math.min(100, severity));
    }

    public boolean isTreated() {
        return treated;
    }

    public void setTreated(boolean treated) {
        this.treated = treated;
    }
}
