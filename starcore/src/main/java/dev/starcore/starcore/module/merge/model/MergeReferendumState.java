package dev.starcore.starcore.module.merge.model;

/**
 * 合并公投状态枚举
 */
public enum MergeReferendumState {
    /** 待投票 */
    PENDING("待投票"),
    /** 已通过 */
    APPROVED("已通过"),
    /** 已拒绝 */
    REJECTED("已拒绝"),
    /** 已过期 */
    EXPIRED("已过期"),
    /** 已取消 */
    CANCELLED("已取消"),
    /** 已执行合并 */
    EXECUTED("已执行"),
    /** 执行失败 */
    FAILED("执行失败");

    private final String displayName;

    MergeReferendumState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}