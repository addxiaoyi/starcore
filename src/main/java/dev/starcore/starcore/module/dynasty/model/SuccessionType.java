package dev.starcore.starcore.module.dynasty.model;

/**
 * 王位继承类型枚举
 * 定义不同的王位继承规则
 */
public enum SuccessionType {
    /**
     * 长子继承制（男性优先）
     */
    MALE_PREMIogeniture("长子继承制", "长子优先继承王位"),

    /**
     * 幼子继承制（男性优先）
     */
    ULTIMogeniture("幼子继承制", "幼子优先继承王位"),

    /**
     * 嫡长子继承制
     */
    PRIMOGENITURE("嫡长子继承制", "正室长子优先继承"),

    /**
     * 平等继承制（所有继承人平等竞争）
     */
    ELECTIVE_MONARCHY("选举君主制", "由继承人投票决定"),

    /**
     * 禅让制（君主指定继承人）
     */
    APPOINTMENT("禅让制", "君主可指定继承人"),

    /**
     * 血统继承制（只允许皇室血统）
     */
    HEREDITARY("血统继承制", "仅皇室血统可继承"),

    /**
     * 绝对继承制（无限制继承）
     */
    ABSOLUTE("绝对继承制", "任何国家成员都可继承"),

    /**
     * 议会继承制（议会决定继承人）
     */
    PARLIAMENTARY("议会继承制", "议会投票决定继承人");

    private final String displayName;
    private final String description;

    SuccessionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 检查这种继承类型是否需要继承人列表
     */
    public boolean requiresHeirList() {
        return this == ELECTIVE_MONARCHY || this == PARLIAMENTARY;
    }

    /**
     * 检查这种继承类型是否允许君主指定继承人
     */
    public boolean allowsAppointment() {
        return this == APPOINTMENT;
    }

    /**
     * 检查这种继承类型是否需要皇室血统
     */
    public boolean requiresRoyalBlood() {
        return this == HEREDITARY || this == MALE_PREMIogeniture || this == ULTIMogeniture || this == PRIMOGENITURE;
    }
}