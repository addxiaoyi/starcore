package dev.starcore.starcore.mechanics;

import dev.starcore.starcore.util.MessageUtil;

/**
 * 疾病类型枚举
 */
public enum DiseaseType {

    COLD("感冒", 1, 0.3, 7),
    FLU("流感", 2, 0.5, 10),
    PLAGUE("瘟疫", 5, 0.8, 14),
    POISON("中毒", 3, 0.1, 5),
    CURSE("诅咒", 4, 0.2, 21),
    INFECTION("感染", 2, 0.4, 7),
    FEVER("发烧", 1, 0.3, 5),
    WEAKNESS("虚弱", 1, 0.2, 3);

    private final String displayName;
    private final int severity; // 严重程度 (1-5)
    private final double contagion; // 传染性 (0-1)
    private final int duration; // 持续天数

    DiseaseType(String displayName, int severity, double contagion, int duration) {
        this.displayName = displayName;
        this.severity = severity;
        this.contagion = contagion;
        this.duration = duration;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeverity() {
        return severity;
    }

    public double getContagion() {
        return contagion;
    }

    public int getDuration() {
        return duration;
    }

    /**
     * 获取带颜色的名称
     */
    public String getColoredName() {
        String color = switch (severity) {
            case 1 -> MessageUtil.GREEN;
            case 2 -> MessageUtil.YELLOW;
            case 3 -> MessageUtil.GOLD;
            case 4 -> MessageUtil.RED;
            default -> MessageUtil.DARK_RED;
        };
        return color + displayName;
    }

    /**
     * 获取严重程度描述
     */
    public String getSeverityDescription() {
        switch (severity) {
            case 1: return MessageUtil.GREEN + "轻微";
            case 2: return MessageUtil.YELLOW + "一般";
            case 3: return MessageUtil.GOLD + "严重";
            case 4: return MessageUtil.RED + "危险";
            case 5: return MessageUtil.DARK_RED + "致命";
            default: return MessageUtil.WHITE + "未知";
        }
    }

    /**
     * 获取颜色代码
     */
    public String getColorCode() {
        switch (severity) {
            case 1: return MessageUtil.GREEN;
            case 2: return MessageUtil.YELLOW;
            case 3: return MessageUtil.GOLD;
            case 4: return MessageUtil.RED;
            case 5: return MessageUtil.DARK_RED;
            default: return MessageUtil.WHITE;
        }
    }

    /**
     * 获取症状描述
     */
    public String getSymptoms() {
        switch (this) {
            case COLD:
                return "轻微咳嗽，移动速度-5%";
            case FLU:
                return "发烧咳嗽，移动速度-10%，饥饿+20%";
            case PLAGUE:
                return "严重虚弱，生命上限-30%，持续伤害";
            case POISON:
                return "中毒状态，持续损失生命";
            case CURSE:
                return "被诅咒，所有属性-20%，幸运-50%";
            case INFECTION:
                return "伤口感染，缓慢损失生命";
            case FEVER:
                return "高烧不退，视野模糊";
            case WEAKNESS:
                return "身体虚弱，攻击力-15%";
            default:
                return "未知症状";
        }
    }

    /**
     * 获取治疗难度
     */
    public int getTreatmentDifficulty() {
        return severity * 20; // 1-100
    }
}
