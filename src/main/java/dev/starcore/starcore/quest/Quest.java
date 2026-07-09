package dev.starcore.starcore.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务类
 * 定义游戏中的任务
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class Quest {

    private final String id;
    private final String name;
    private final String description;
    private final QuestType type;
    private final QuestDifficulty difficulty;
    private final List<QuestObjective> objectives;
    private final QuestReward reward;
    private final List<String> prerequisiteQuests;
    private final int minLevel;
    private final int maxLevel;
    private final long timeLimit; // 时间限制（毫秒，0表示无限制）
    private final boolean autoAccept;
    private final boolean autoComplete;
    private final boolean repeatable;
    private final long cooldown; // 重复任务的冷却时间（毫秒）
    private final String npcGiverId; // 任务发布NPC
    private final String npcCompleterId; // 任务完成NPC
    private final List<String> startCommands; // 接取任务时执行的命令
    private final List<String> completeCommands; // 完成任务时执行的命令
    private final String category; // 任务分类
    private final List<String> tags; // 任务标签

    private Quest(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.difficulty = builder.difficulty;
        this.objectives = builder.objectives;
        this.reward = builder.reward;
        this.prerequisiteQuests = builder.prerequisiteQuests;
        this.minLevel = builder.minLevel;
        this.maxLevel = builder.maxLevel;
        this.timeLimit = builder.timeLimit;
        this.autoAccept = builder.autoAccept;
        this.autoComplete = builder.autoComplete;
        this.repeatable = builder.repeatable;
        this.cooldown = builder.cooldown;
        this.npcGiverId = builder.npcGiverId;
        this.npcCompleterId = builder.npcCompleterId;
        this.startCommands = builder.startCommands;
        this.completeCommands = builder.completeCommands;
        this.category = builder.category;
        this.tags = builder.tags;
    }

    /**
     * 检查玩家是否满足接取条件
     */
    public boolean canAccept(UUID playerId, int playerLevel, List<String> completedQuests) {
        // 检查等级要求
        if (playerLevel < minLevel || (maxLevel > 0 && playerLevel > maxLevel)) {
            return false;
        }

        // 检查前置任务
        for (String prerequisite : prerequisiteQuests) {
            if (!completedQuests.contains(prerequisite)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查所有目标是否完成
     */
    public boolean isAllObjectivesCompleted() {
        return objectives.stream().allMatch(QuestObjective::isCompleted);
    }

    /**
     * 获取完成度百分比
     */
    public double getCompletionPercentage() {
        if (objectives.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (QuestObjective objective : objectives) {
            total += objective.getProgressPercentage();
        }

        return total / objectives.size();
    }

    /**
     * 创建任务实例（带独立的进度）
     */
    public Quest createInstance() {
        Builder builder = new Builder(id, name, description, type, difficulty);
        builder.reward(reward.clone());

        for (QuestObjective objective : objectives) {
            builder.addObjective(objective.clone());
        }

        builder.prerequisiteQuests(new ArrayList<>(prerequisiteQuests))
                .minLevel(minLevel)
                .maxLevel(maxLevel)
                .timeLimit(timeLimit)
                .autoAccept(autoAccept)
                .autoComplete(autoComplete)
                .repeatable(repeatable)
                .cooldown(cooldown)
                .npcGiver(npcGiverId)
                .npcCompleter(npcCompleterId)
                .category(category);

        for (String tag : tags) {
            builder.addTag(tag);
        }

        for (String command : startCommands) {
            builder.addStartCommand(command);
        }

        for (String command : completeCommands) {
            builder.addCompleteCommand(command);
        }

        return builder.build();
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

    public QuestType getType() {
        return type;
    }

    public QuestDifficulty getDifficulty() {
        return difficulty;
    }

    public List<QuestObjective> getObjectives() {
        return new ArrayList<>(objectives);
    }

    public QuestReward getReward() {
        return reward;
    }

    public List<String> getPrerequisiteQuests() {
        return new ArrayList<>(prerequisiteQuests);
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public long getTimeLimit() {
        return timeLimit;
    }

    public boolean isAutoAccept() {
        return autoAccept;
    }

    public boolean isAutoComplete() {
        return autoComplete;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public long getCooldown() {
        return cooldown;
    }

    public String getNpcGiverId() {
        return npcGiverId;
    }

    public String getNpcCompleterId() {
        return npcCompleterId;
    }

    public List<String> getStartCommands() {
        return new ArrayList<>(startCommands);
    }

    public List<String> getCompleteCommands() {
        return new ArrayList<>(completeCommands);
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private final String id;
        private final String name;
        private final String description;
        private final QuestType type;
        private final QuestDifficulty difficulty;
        private final List<QuestObjective> objectives = new ArrayList<>();
        private QuestReward reward = new QuestReward();
        private final List<String> prerequisiteQuests = new ArrayList<>();
        private int minLevel = 0;
        private int maxLevel = 0;
        private long timeLimit = 0;
        private boolean autoAccept = false;
        private boolean autoComplete = false;
        private boolean repeatable = false;
        private long cooldown = 0;
        private String npcGiverId = null;
        private String npcCompleterId = null;
        private final List<String> startCommands = new ArrayList<>();
        private final List<String> completeCommands = new ArrayList<>();
        private String category = "general";
        private final List<String> tags = new ArrayList<>();

        public Builder(String id, String name, String description, QuestType type, QuestDifficulty difficulty) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.difficulty = difficulty;
        }

        public Builder addObjective(QuestObjective objective) {
            this.objectives.add(objective);
            return this;
        }

        public Builder reward(QuestReward reward) {
            this.reward = reward;
            return this;
        }

        public Builder prerequisiteQuests(List<String> quests) {
            this.prerequisiteQuests.addAll(quests);
            return this;
        }

        public Builder addPrerequisiteQuest(String questId) {
            this.prerequisiteQuests.add(questId);
            return this;
        }

        public Builder minLevel(int level) {
            this.minLevel = level;
            return this;
        }

        public Builder maxLevel(int level) {
            this.maxLevel = level;
            return this;
        }

        public Builder timeLimit(long timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder autoAccept(boolean autoAccept) {
            this.autoAccept = autoAccept;
            return this;
        }

        public Builder autoComplete(boolean autoComplete) {
            this.autoComplete = autoComplete;
            return this;
        }

        public Builder repeatable(boolean repeatable) {
            this.repeatable = repeatable;
            return this;
        }

        public Builder cooldown(long cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder npcGiver(String npcId) {
            this.npcGiverId = npcId;
            return this;
        }

        public Builder npcCompleter(String npcId) {
            this.npcCompleterId = npcId;
            return this;
        }

        public Builder addStartCommand(String command) {
            this.startCommands.add(command);
            return this;
        }

        public Builder addCompleteCommand(String command) {
            this.completeCommands.add(command);
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Quest build() {
            return new Quest(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Quest{id='%s', name='%s', type=%s, difficulty=%s, objectives=%d}",
                id, name, type, difficulty, objectives.size());
    }
}
