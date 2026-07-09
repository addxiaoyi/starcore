package dev.starcore.starcore.social.simulation;

/**
 * 声望变化原因枚举
 */
public enum ReputationReason {
    HELP_PLAYER("帮助玩家"),
    KILL_PLAYER("击杀玩家"),
    TRADE("交易"),
    DONATION("捐赠"),
    COMPLETE_QUEST("完成任务"),
    BUILD("建设"),
    CHAT("聊天"),
    FESTIVAL("节日活动"),
    WAR_PARTICIPATION("参战"),
    COMMAND("命令执行"),
    DAILY_LOGIN("每日登录"),
    ACHIEVEMENT("成就"),
    OTHER("其他");

    private final String displayName;

    ReputationReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
