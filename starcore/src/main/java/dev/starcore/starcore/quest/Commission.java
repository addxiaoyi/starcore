package dev.starcore.starcore.quest;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 委托类
 * 玩家发布的任务委托
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class Commission {

    private String id;
    private UUID publisherId; // 发布者
    private String publisherName;
    private String title; // 委托标题
    private String description; // 委托描述
    private double reward; // 赏金
    private int minLevel; // 最低等级要求
    private int minReputation; // 最低声望要求
    private long publishTime; // 发布时间
    private long expireTime; // 过期时间
    private UUID acceptorId; // 接取者
    private long acceptTime; // 接取时间
    private boolean completed; // 是否完成
    private long completeTime; // 完成时间
    private CommissionType type; // 委托类型
    private QuestDifficulty difficulty; // 难度
    private final List<String> requirements; // 要求列表
    private String category; // 分类

    // 目标参数（用于验证完成条件）
    private String targetEntity; // 目标实体类型（如 ZOMBIE, CREEPER）
    private int targetAmount; // 目标数量
    private int currentProgress; // 当前进度
    private String targetLocation; // 目标位置（坐标或位置名）
    private String targetItem; // 目标物品
    private boolean verifyOnComplete; // 完成时是否验证
    private boolean publisherConfirmed; // 发布者是否已确认（BUILD类型）
    private boolean acceptorNotified; // 接取者是否已通知完成

    // 排行榜统计字段
    private int totalCompleted; // 总完成次数
    private double totalEarned; // 总获得赏金
    private int streakDays; // 连续完成天数
    private long lastCompletedTime; // 上次完成时间

    /**
     * 委托类型
     */
    public enum CommissionType {
        COLLECT("收集"),
        KILL("击杀"),
        BUILD("建造"),
        ESCORT("护送"),
        DELIVERY("运送"),
        EXPLORE("探索"),
        CUSTOM("自定义");

        private final String displayName;

        CommissionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 构造函数
     */
    public Commission(String title, String description, double reward) {
        this.title = title;
        this.description = description;
        this.reward = reward;
        this.requirements = new ArrayList<>();
        this.minLevel = 0;
        this.minReputation = 0;
        this.completed = false;
        this.type = CommissionType.CUSTOM;
        this.difficulty = QuestDifficulty.NORMAL;
        this.category = "general";
        this.expireTime = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L); // 7天后过期
        this.publisherConfirmed = false;
        this.acceptorNotified = false;
        this.totalCompleted = 0;
        this.totalEarned = 0;
        this.streakDays = 0;
        this.lastCompletedTime = 0;
    }

    /**
     * 检查是否已接取
     */
    public boolean isAccepted() {
        return acceptorId != null;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        return Math.max(0, expireTime - System.currentTimeMillis());
    }

    /**
     * 获取剩余时间文本
     */
    public String getRemainingTimeText() {
        long remaining = getRemainingTime();

        if (remaining == 0) {
            return "已过期";
        }

        long days = remaining / (24 * 60 * 60 * 1000);
        long hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);

        if (days > 0) {
            return String.format("%d天%d小时", days, hours);
        } else if (hours > 0) {
            return String.format("%d小时", hours);
        } else {
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return String.format("%d分钟", minutes);
        }
    }

    // ========== 颜色代码常量 (技术债务，待统一重构) ==========
    // @deprecated 硬编码颜色代码，应使用统一颜色常量类
    private static final String C_GREEN = "§a";
    private static final String C_RED = "§c";
    private static final String C_YELLOW = "§e";
    private static final String C_GRAY = "§7";
    private static final String C_GOLD = "§6";
    private static final String C_WHITE = "§f";
    private static final String C_AQUA = "§b";

    /**
     * 获取委托状态
     */
    public String getStatus() {
        if (completed) {
            return C_GREEN + "已完成";
        } else if (isExpired()) {
            return C_RED + "已过期";
        } else if (isAccepted()) {
            return C_YELLOW + "进行中";
        } else {
            return C_GRAY + "待接取";
        }
    }

    /**
     * 获取委托详情
     */
    public List<String> getCommissionDetails() {
        List<String> details = new ArrayList<>();

        details.add(C_GOLD + "========== 委托详情 ==========");
        details.add(String.format("%s标题: %s%s", C_YELLOW, C_WHITE, title));
        details.add(String.format("%s描述: %s%s", C_GRAY, C_WHITE, description));
        details.add("");
        details.add(String.format("%s类型: %s%s", C_GRAY, C_YELLOW, type.getDisplayName()));
        details.add(String.format("%s难度: %s", C_GRAY, difficulty.getColoredName()));
        details.add(String.format("%s赏金: %s%.2f", C_GRAY, C_GOLD, reward));
        details.add(String.format("%s等级要求: %s%d", C_GRAY, C_YELLOW, minLevel));
        if (minReputation > 0) {
            details.add(String.format("%s声望要求: %s%d", C_GRAY, C_YELLOW, minReputation));
        }
        details.add(String.format("%s状态: %s", C_GRAY, getStatus()));

        if (!isExpired()) {
            details.add(String.format("%s剩余时间: %s%s", C_GRAY, C_YELLOW, getRemainingTimeText()));
        }

        if (!requirements.isEmpty()) {
            details.add("");
            details.add(C_GRAY + "要求:");
            for (String req : requirements) {
                details.add("  " + C_WHITE + "- " + req);
            }
        }

        details.add("");
        details.add(String.format("%s发布者: %s%s", C_GRAY, C_AQUA, publisherName));

        if (isAccepted() && acceptorId != null) {
            // 获取接取者名称
            String acceptorName = Bukkit.getPlayer(acceptorId) != null
                ? Bukkit.getPlayer(acceptorId).getName()
                : Bukkit.getOfflinePlayer(acceptorId).getName();
            details.add(String.format("%s接取者: %s%s", C_GRAY, C_AQUA, acceptorName != null ? acceptorName : "未知玩家"));
        }

        // 显示验证状态
        if (isAccepted() && !completed) {
            details.add(String.format("%s验证状态: %s%s", C_GRAY, C_YELLOW, getVerificationStatus()));
        }

        details.add(C_GOLD + "============================");

        return details;
    }

    /**
     * 添加要求
     */
    public void addRequirement(String requirement) {
        requirements.add(requirement);
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(UUID publisherId) {
        this.publisherId = publisherId;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public int getMinReputation() {
        return minReputation;
    }

    public void setMinReputation(int minReputation) {
        this.minReputation = minReputation;
    }

    public long getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(long publishTime) {
        this.publishTime = publishTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public UUID getAcceptorId() {
        return acceptorId;
    }

    public void setAcceptorId(UUID acceptorId) {
        this.acceptorId = acceptorId;
    }

    public long getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(long acceptTime) {
        this.acceptTime = acceptTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(long completeTime) {
        this.completeTime = completeTime;
    }

    public CommissionType getType() {
        return type;
    }

    public void setType(CommissionType type) {
        this.type = type;
    }

    public QuestDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(QuestDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getRequirements() {
        return new ArrayList<>(requirements);
    }

    // ========== 目标验证相关方法 ==========

    /**
     * 检查是否需要验证目标
     */
    public boolean needsVerification() {
        return verifyOnComplete && (
            (targetEntity != null && !targetEntity.isEmpty()) ||
            (targetItem != null && !targetItem.isEmpty()) ||
            (targetLocation != null && !targetLocation.isEmpty())
        );
    }

    /**
     * 检查目标是否完成
     */
    public boolean isTargetComplete() {
        // BUILD类型需要发布者确认
        if (type == CommissionType.BUILD && !publisherConfirmed) {
            return false;
        }

        if (!needsVerification()) {
            return true; // 不需要验证的委托自动通过
        }

        // 根据类型验证
        switch (type) {
            case KILL:
                return currentProgress >= targetAmount;
            case COLLECT:
            case BUILD:
            case EXPLORE:
            default:
                return true; // 其他类型默认通过（由事件监听器或玩家手动报告）
        }
    }

    /**
     * 检查是否可以提交完成
     */
    public boolean canSubmitComplete() {
        if (completed || isExpired()) {
            return false;
        }
        if (!isAccepted()) {
            return false;
        }
        return isTargetComplete();
    }

    /**
     * 获取验证状态描述
     */
    public String getVerificationStatus() {
        if (!needsVerification() && type != CommissionType.BUILD) {
            return "无需验证";
        }
        if (type == CommissionType.BUILD && !publisherConfirmed) {
            return "等待发布者确认";
        }
        if (needsVerification() && !isTargetComplete()) {
            return String.format("进度: %d/%d", currentProgress, targetAmount);
        }
        return "已验证";
    }

    // ========== 目标参数 Getters & Setters ==========

    public String getTargetEntity() {
        return targetEntity;
    }

    public void setTargetEntity(String targetEntity) {
        this.targetEntity = targetEntity;
        if (targetEntity != null) {
            this.verifyOnComplete = true;
        }
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(int targetAmount) {
        this.targetAmount = targetAmount;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = Math.min(currentProgress, targetAmount > 0 ? targetAmount : Integer.MAX_VALUE);
    }

    /**
     * 增加进度
     */
    public void addProgress(int amount) {
        this.currentProgress = Math.min(this.currentProgress + amount, targetAmount > 0 ? targetAmount : Integer.MAX_VALUE);
    }

    public String getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(String targetLocation) {
        this.targetLocation = targetLocation;
    }

    public String getTargetItem() {
        return targetItem;
    }

    public void setTargetItem(String targetItem) {
        this.targetItem = targetItem;
    }

    public boolean isVerifyOnComplete() {
        return verifyOnComplete;
    }

    public void setVerifyOnComplete(boolean verifyOnComplete) {
        this.verifyOnComplete = verifyOnComplete;
    }

    /**
     * 发布者确认完成（BUILD类型）
     */
    public boolean confirmByPublisher() {
        if (this.type != CommissionType.BUILD) {
            return false;
        }
        this.publisherConfirmed = true;
        return true;
    }

    /**
     * 通知接取者完成
     */
    public void notifyAcceptorComplete() {
        this.acceptorNotified = true;
    }

    /**
     * 是否需要发布者确认
     */
    public boolean needsPublisherConfirmation() {
        return type == CommissionType.BUILD && !publisherConfirmed;
    }

    // ========== 排行榜统计相关方法 ==========

    /**
     * 记录完成统计
     */
    public void recordCompletion(double earnedReward) {
        this.totalCompleted++;
        this.totalEarned += earnedReward;
        this.lastCompletedTime = System.currentTimeMillis();
    }

    /**
     * 计算连续完成天数
     */
    public void updateStreak() {
        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;

        if (lastCompletedTime == 0) {
            this.streakDays = 1;
        } else {
            long daysSinceLast = (now - lastCompletedTime) / oneDay;
            if (daysSinceLast <= 1) {
                this.streakDays++;
            } else {
                this.streakDays = 1;
            }
        }
    }

    // ========== 原有 Getters & Setters ==========

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isPublisherConfirmed() {
        return publisherConfirmed;
    }

    public void setPublisherConfirmed(boolean publisherConfirmed) {
        this.publisherConfirmed = publisherConfirmed;
    }

    public boolean isAcceptorNotified() {
        return acceptorNotified;
    }

    public void setAcceptorNotified(boolean acceptorNotified) {
        this.acceptorNotified = acceptorNotified;
    }

    public int getTotalCompleted() {
        return totalCompleted;
    }

    public void setTotalCompleted(int totalCompleted) {
        this.totalCompleted = totalCompleted;
    }

    public double getTotalEarned() {
        return totalEarned;
    }

    public void setTotalEarned(double totalEarned) {
        this.totalEarned = totalEarned;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public long getLastCompletedTime() {
        return lastCompletedTime;
    }

    public void setLastCompletedTime(long lastCompletedTime) {
        this.lastCompletedTime = lastCompletedTime;
    }

    @Override
    public String toString() {
        return String.format("Commission{id='%s', title='%s', reward=%.2f, status='%s'}",
                id, title, reward, getStatus());
    }
}
