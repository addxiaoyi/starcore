package dev.starcore.starcore.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务章节类
 * 定义主线任务的章节结构
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestChapter {

    private final String id;
    private final String name;
    private final String description;
    private final int chapterNumber;
    private final String previousChapterId; // 前置章节ID
    private final List<String> requiredQuests; // 本章节必须完成的任务ID
    private final List<String> optionalQuests; // 本章节可选任务ID
    private final String storyText; // 章节剧情文本
    private final List<String> unlockRewards; // 解锁章节时的奖励
    private final int recommendedLevel; // 推荐等级

    private QuestChapter(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.chapterNumber = builder.chapterNumber;
        this.previousChapterId = builder.previousChapterId;
        this.requiredQuests = builder.requiredQuests;
        this.optionalQuests = builder.optionalQuests;
        this.storyText = builder.storyText;
        this.unlockRewards = builder.unlockRewards;
        this.recommendedLevel = builder.recommendedLevel;
    }

    /**
     * 获取章节完整显示名称
     */
    public String getFullName() {
        return String.format("第%d章 - %s", chapterNumber, name);
    }

    /**
     * 获取章节信息
     */
    public List<String> getChapterInfo() {
        List<String> info = new ArrayList<>();

        info.add("§6========== " + getFullName() + " ==========");
        info.add("§7" + description);

        if (storyText != null && !storyText.isEmpty()) {
            info.add("");
            info.add("§e剧情:");
            info.add("§f" + storyText);
        }

        info.add("");
        info.add(String.format("§7推荐等级: §e%d", recommendedLevel));
        info.add(String.format("§7必需任务: §a%d", requiredQuests.size()));
        info.add(String.format("§7可选任务: §b%d", optionalQuests.size()));

        info.add("§6================================");

        return info;
    }

    /**
     * 检查是否是第一章
     */
    public boolean isFirstChapter() {
        return previousChapterId == null || previousChapterId.isEmpty();
    }

    /**
     * 获取所有任务ID（必需+可选）
     */
    public List<String> getAllQuestIds() {
        List<String> allQuests = new ArrayList<>(requiredQuests);
        allQuests.addAll(optionalQuests);
        return allQuests;
    }

    // Getters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getChapterNumber() {
        return chapterNumber;
    }

    public String getPreviousChapterId() {
        return previousChapterId;
    }

    public List<String> getRequiredQuests() {
        return new ArrayList<>(requiredQuests);
    }

    public List<String> getOptionalQuests() {
        return new ArrayList<>(optionalQuests);
    }

    public String getStoryText() {
        return storyText;
    }

    public List<String> getUnlockRewards() {
        return new ArrayList<>(unlockRewards);
    }

    public int getRecommendedLevel() {
        return recommendedLevel;
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private int chapterNumber;
        private String previousChapterId = null;
        private final List<String> requiredQuests = new ArrayList<>();
        private final List<String> optionalQuests = new ArrayList<>();
        private String storyText = "";
        private final List<String> unlockRewards = new ArrayList<>();
        private int recommendedLevel = 0;

        public Builder(String id, String name, int chapterNumber) {
            this.id = id;
            this.name = name;
            this.chapterNumber = chapterNumber;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder previousChapter(String chapterId) {
            this.previousChapterId = chapterId;
            return this;
        }

        public Builder addRequiredQuest(String questId) {
            this.requiredQuests.add(questId);
            return this;
        }

        public Builder addOptionalQuest(String questId) {
            this.optionalQuests.add(questId);
            return this;
        }

        public Builder storyText(String story) {
            this.storyText = story;
            return this;
        }

        public Builder addUnlockReward(String reward) {
            this.unlockRewards.add(reward);
            return this;
        }

        public Builder recommendedLevel(int level) {
            this.recommendedLevel = level;
            return this;
        }

        public QuestChapter build() {
            return new QuestChapter(this);
        }
    }

    @Override
    public String toString() {
        return String.format("QuestChapter{id='%s', name='%s', number=%d, quests=%d}",
                id, name, chapterNumber, requiredQuests.size());
    }
}
