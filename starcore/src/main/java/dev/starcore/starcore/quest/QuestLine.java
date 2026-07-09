package dev.starcore.starcore.quest;

import dev.starcore.starcore.util.ColorCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务线类
 * 定义一系列连续的任务序列
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestLine {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> questSequence; // 任务序列（按顺序）
    private final Map<String, String> dialogues; // 任务ID -> 对话文本
    private final String category;
    private final boolean isMainStory; // 是否为主线故事

    private QuestLine(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.questSequence = builder.questSequence;
        this.dialogues = builder.dialogues;
        this.category = builder.category;
        this.isMainStory = builder.isMainStory;
    }

    /**
     * 获取任务在任务线中的位置
     */
    public int getQuestIndex(String questId) {
        return questSequence.indexOf(questId);
    }

    /**
     * 获取下一个任务ID
     */
    public String getNextQuest(String currentQuestId) {
        int index = getQuestIndex(currentQuestId);
        if (index >= 0 && index < questSequence.size() - 1) {
            return questSequence.get(index + 1);
        }
        return null;
    }

    /**
     * 获取前一个任务ID
     */
    public String getPreviousQuest(String currentQuestId) {
        int index = getQuestIndex(currentQuestId);
        if (index > 0) {
            return questSequence.get(index - 1);
        }
        return null;
    }

    /**
     * 检查是否为最后一个任务
     */
    public boolean isLastQuest(String questId) {
        return questSequence.indexOf(questId) == questSequence.size() - 1;
    }

    /**
     * 检查是否为第一个任务
     */
    public boolean isFirstQuest(String questId) {
        return questSequence.indexOf(questId) == 0;
    }

    /**
     * 获取任务对话
     */
    public String getDialogue(String questId) {
        return dialogues.getOrDefault(questId, "");
    }

    /**
     * 获取任务线信息
     */
    public List<String> getQuestLineInfo() {
        List<String> info = new ArrayList<>();

        info.add("§6========== " + name + " ==========");
        info.add("§7" + description);
        info.add("");
        info.add(String.format("§7类型: §e%s", isMainStory ? "主线故事" : "支线故事"));
        info.add(String.format("§7分类: §e%s", category));
        info.add(String.format("§7任务数量: §a%d", questSequence.size()));
        info.add("§6=============================");

        return info;
    }

    /**
     * 获取任务序列摘要
     */
    public List<String> getQuestSequenceSummary() {
        List<String> summary = new ArrayList<>();

        summary.add("§6任务序列:");
        for (int i = 0; i < questSequence.size(); i++) {
            String questId = questSequence.get(i);
            summary.add(String.format("  §7%d. §f%s", i + 1, questId));
        }

        return summary;
    }

    /**
     * 获取总任务数
     */
    public int getTotalQuests() {
        return questSequence.size();
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

    public List<String> getQuestSequence() {
        return new ArrayList<>(questSequence);
    }

    public Map<String, String> getDialogues() {
        return new ConcurrentHashMap<>(dialogues);
    }

    public String getCategory() {
        return category;
    }

    public boolean isMainStory() {
        return isMainStory;
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private final List<String> questSequence = new ArrayList<>();
        private final Map<String, String> dialogues = new ConcurrentHashMap<>();
        private String category = "general";
        private boolean isMainStory = false;

        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addQuest(String questId) {
            this.questSequence.add(questId);
            return this;
        }

        public Builder addQuests(List<String> questIds) {
            this.questSequence.addAll(questIds);
            return this;
        }

        public Builder addDialogue(String questId, String dialogue) {
            this.dialogues.put(questId, dialogue);
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder mainStory(boolean isMainStory) {
            this.isMainStory = isMainStory;
            return this;
        }

        public QuestLine build() {
            return new QuestLine(this);
        }
    }

    @Override
    public String toString() {
        return String.format("QuestLine{id='%s', name='%s', quests=%d, mainStory=%s}",
                id, name, questSequence.size(), isMainStory);
    }
}
