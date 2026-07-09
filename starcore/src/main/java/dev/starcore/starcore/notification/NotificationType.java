package dev.starcore.starcore.notification;

/**
 * 通知类型枚举
 * 定义系统中所有可用的通知类型
 */
public enum NotificationType {
    // ===== 系统通知 =====
    SYSTEM("系统通知", Category.SYSTEM, NotificationPriority.NORMAL),
    MAINTENANCE("维护通知", Category.SYSTEM, NotificationPriority.HIGH),
    UPDATE("更新通知", Category.SYSTEM, NotificationPriority.NORMAL),

    // ===== 国家相关 =====
    NATION_CREATE("国家创建", Category.NATION, NotificationPriority.HIGH),
    NATION_DISBAND("国家解散", Category.NATION, NotificationPriority.HIGH),
    NATION_JOIN("加入国家", Category.NATION, NotificationPriority.NORMAL),
    NATION_LEAVE("离开国家", Category.NATION, NotificationPriority.NORMAL),
    NATION_INVITE("国家邀请", Category.NATION, NotificationPriority.HIGH),
    NATION_KICK("踢出国家", Category.NATION, NotificationPriority.HIGH),
    NATION_UPGRADE("国家升级", Category.NATION, NotificationPriority.NORMAL),
    NATION_TRIBUTE("进贡通知", Category.NATION, NotificationPriority.NORMAL),

    // ===== 外交相关 =====
    DIPLOMACY_WAR_DECLARE("宣战", Category.DIPLOMACY, NotificationPriority.URGENT),
    DIPLOMACY_WAR_END("战争结束", Category.DIPLOMACY, NotificationPriority.HIGH),
    DIPLOMACY_ALLIANCE("联盟请求", Category.DIPLOMACY, NotificationPriority.HIGH),
    DIPLOMACY_ALLIANCE_BREAK("联盟破裂", Category.DIPLOMACY, NotificationPriority.HIGH),
    DIPLOMACY_TRUCE("停战协议", Category.DIPLOMACY, NotificationPriority.HIGH),
    DIPLOMACY_VASSAL("附属国", Category.DIPLOMACY, NotificationPriority.HIGH),

    // ===== 军事相关 =====
    MILITARY_BATTLE("战斗开始", Category.MILITARY, NotificationPriority.HIGH),
    MILITARY_BATTLE_END("战斗结束", Category.MILITARY, NotificationPriority.NORMAL),
    MILITARY_SIEGE("围城战", Category.MILITARY, NotificationPriority.URGENT),
    MILITARY_RAID("袭击", Category.MILITARY, NotificationPriority.HIGH),
    MILITARY_RECRUIT("招募", Category.MILITARY, NotificationPriority.NORMAL),
    MILITARY_TRAINING("训练完成", Category.MILITARY, NotificationPriority.LOW),
    MILITARY_REINFORCEMENT("增援请求", Category.MILITARY, NotificationPriority.HIGH),
    MILITARY_BATTLE_REPORT("战报", Category.MILITARY, NotificationPriority.NORMAL),

    // ===== 经济相关 =====
    ECONOMY_TREASURY_LOW("国库不足", Category.ECONOMY, NotificationPriority.HIGH),
    ECONOMY_TREASURY_DEBT("国债警告", Category.ECONOMY, NotificationPriority.HIGH),
    ECONOMY_TAX_COLLECT("税收", Category.ECONOMY, NotificationPriority.LOW),
    ECONOMY_TRADE("贸易", Category.ECONOMY, NotificationPriority.NORMAL),
    ECONOMY_DONATION("捐赠", Category.ECONOMY, NotificationPriority.NORMAL),
    ECONOMY_SALARY("工资发放", Category.ECONOMY, NotificationPriority.NORMAL),

    // ===== 领土相关 =====
    TERRITORY_CLAIM("领土占领", Category.TERRITORY, NotificationPriority.HIGH),
    TERRITORY_LOST("领土丢失", Category.TERRITORY, NotificationPriority.HIGH),
    TERRITORY_DISPUTE("领土争议", Category.TERRITORY, NotificationPriority.HIGH),
    TERRITORY_UPGRADE("领土升级", Category.TERRITORY, NotificationPriority.NORMAL),

    // ===== 科技相关 =====
    TECH_RESEARCH("科技研究", Category.TECHNOLOGY, NotificationPriority.NORMAL),
    TECH_COMPLETE("科技完成", Category.TECHNOLOGY, NotificationPriority.HIGH),
    TECH_UNLOCK("科技解锁", Category.TECHNOLOGY, NotificationPriority.NORMAL),

    // ===== 社交相关 =====
    SOCIAL_FRIEND("好友", Category.SOCIAL, NotificationPriority.NORMAL),
    SOCIAL_MAIL("邮件", Category.SOCIAL, NotificationPriority.NORMAL),
    SOCIAL_PARTY("队伍", Category.SOCIAL, NotificationPriority.NORMAL),
    SOCIAL_GUILD("公会", Category.SOCIAL, NotificationPriority.NORMAL),

    // ===== 任务相关 =====
    QUEST_NEW("新任务", Category.QUEST, NotificationPriority.NORMAL),
    QUEST_COMPLETE("任务完成", Category.QUEST, NotificationPriority.HIGH),
    QUEST_REWARD("任务奖励", Category.QUEST, NotificationPriority.NORMAL),

    // ===== 活动相关 =====
    EVENT_TOURNAMENT("锦标赛", Category.EVENT, NotificationPriority.HIGH),
    EVENT_DUNGEON("副本", Category.EVENT, NotificationPriority.HIGH),
    EVENT_DAILY("每日活动", Category.EVENT, NotificationPriority.NORMAL),
    EVENT_SEASON("赛季活动", Category.EVENT, NotificationPriority.HIGH),

    // ===== 特殊事件 =====
    SPECIAL_ACHIEVEMENT("成就", Category.SPECIAL, NotificationPriority.HIGH),
    SPECIAL_RANKING("排名变动", Category.SPECIAL, NotificationPriority.NORMAL),
    SPECIAL_ANNIVERSARY("周年庆", Category.SPECIAL, NotificationPriority.HIGH),
    SPECIAL_FESTIVAL("节日活动", Category.SPECIAL, NotificationPriority.HIGH);

    private final String displayName;
    private final Category category;
    private final NotificationPriority defaultPriority;

    NotificationType(String displayName, Category category, NotificationPriority defaultPriority) {
        this.displayName = displayName;
        this.category = category;
        this.defaultPriority = defaultPriority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    public NotificationPriority getPriority() {
        return defaultPriority;
    }

    /**
     * 获取通知类型分类
     */
    public enum Category {
        SYSTEM("系统"),
        NATION("国家"),
        DIPLOMACY("外交"),
        MILITARY("军事"),
        ECONOMY("经济"),
        TERRITORY("领土"),
        TECHNOLOGY("科技"),
        SOCIAL("社交"),
        QUEST("任务"),
        EVENT("活动"),
        SPECIAL("特殊");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}