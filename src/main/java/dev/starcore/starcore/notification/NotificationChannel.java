package dev.starcore.starcore.notification;

/**
 * 通知渠道
 * 定义通知的发送方式和显示位置
 */
public enum NotificationChannel {
    /**
     * 聊天栏通知
     */
    CHAT("聊天栏", true, true),

    /**
     * Action Bar 通知（屏幕上方中央）
     */
    ACTION_BAR("动作条", true, false),

    /**
     * 标题通知（屏幕中央大字）
     */
    TITLE("标题", true, false),

    /**
     * 通知中心（自定义 GUI）
     */
    NOTIFICATION_CENTER("通知中心", false, true),

    /**
     * 声音提示
     */
    SOUND("声音", true, false),

    /**
     * 标题栏提示（电脑屏幕右下角）
     */
    TOAST("弹出提示", true, false),

    /**
     * Boss 血条（用于极端紧急通知）
     */
    BOSS_BAR("Boss血条", true, false),

    /**
     * 地图标记（用于战争/领土通知）
     */
    MAP_MARKER("地图标记", false, true);

    private final String displayName;
    private final boolean supportsChatColor;
    private final boolean supportsBatch;

    NotificationChannel(String displayName, boolean supportsChatColor, boolean supportsBatch) {
        this.displayName = displayName;
        this.supportsChatColor = supportsChatColor;
        this.supportsBatch = supportsBatch;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean supportsChatColor() {
        return supportsChatColor;
    }

    public boolean supportsBatch() {
        return supportsBatch;
    }
}