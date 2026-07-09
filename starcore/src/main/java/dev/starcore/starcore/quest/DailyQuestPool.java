package dev.starcore.starcore.quest;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 每日任务池
 * 管理每日任务模板和生成逻辑
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class DailyQuestPool {

    private final List<Quest> templates; // 任务模板
    private final Map<QuestDifficulty, List<Quest>> templatesByDifficulty; // 按难度分类的模板
    private final Map<String, List<Quest>> templatesByCategory; // 按分类的模板
    private final Map<String, Integer> questWeights; // 任务权重（ID -> 权重）

    /**
     * 构造函数
     */
    public DailyQuestPool() {
        this.templates = new ArrayList<>();
        this.templatesByDifficulty = new EnumMap<>(QuestDifficulty.class);
        this.templatesByCategory = new HashMap<>();
        this.questWeights = new HashMap<>();
    }

    /**
     * 添加任务模板
     */
    public void addTemplate(Quest quest) {
        templates.add(quest);

        // 按难度索引
        templatesByDifficulty.computeIfAbsent(quest.getDifficulty(), k -> new ArrayList<>()).add(quest);

        // 按分类索引
        templatesByCategory.computeIfAbsent(quest.getCategory(), k -> new ArrayList<>()).add(quest);

        // 设置默认权重
        questWeights.putIfAbsent(quest.getId(), 10);
    }

    /**
     * 批量添加模板
     */
    public void addTemplates(Collection<Quest> quests) {
        quests.forEach(this::addTemplate);
    }

    /**
     * 设置任务权重
     */
    public void setQuestWeight(String questId, int weight) {
        questWeights.put(questId, weight);
    }

    /**
     * 生成随机任务列表
     */
    public List<Quest> generateRandomQuests(int count) {
        if (templates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Quest> selected = new ArrayList<>();
        List<Quest> available = new ArrayList<>(templates);

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            Quest quest = selectWeightedRandom(available);
            selected.add(quest.createInstance());
            available.remove(quest);
        }

        return selected;
    }

    /**
     * 生成个性化任务列表（根据玩家等级和历史）
     */
    public List<Quest> generatePersonalizedQuests(Player player, int count) {
        if (templates.isEmpty()) {
            return Collections.emptyList();
        }

        int playerLevel = player.getLevel();
        QuestDifficulty recommendedDifficulty = QuestDifficulty.recommendForPlayerLevel(playerLevel);

        List<Quest> selected = new ArrayList<>();

        // 根据难度分配任务数量
        Map<QuestDifficulty, Integer> distribution = calculateDifficultyDistribution(
                recommendedDifficulty, count);

        for (Map.Entry<QuestDifficulty, Integer> entry : distribution.entrySet()) {
            QuestDifficulty difficulty = entry.getKey();
            int questCount = entry.getValue();

            List<Quest> difficultyTemplates = templatesByDifficulty.getOrDefault(
                    difficulty, Collections.emptyList());

            if (!difficultyTemplates.isEmpty()) {
                List<Quest> available = new ArrayList<>(difficultyTemplates);

                for (int i = 0; i < questCount && !available.isEmpty(); i++) {
                    Quest quest = selectWeightedRandom(available);
                    selected.add(quest.createInstance());
                    available.remove(quest);
                }
            }
        }

        // 如果数量不足，从所有模板中随机补充
        while (selected.size() < count && selected.size() < templates.size()) {
            List<Quest> remaining = templates.stream()
                    .filter(q -> selected.stream().noneMatch(s -> s.getId().equals(q.getId())))
                    .collect(Collectors.toList());

            if (remaining.isEmpty()) break;

            Quest quest = selectWeightedRandom(remaining);
            selected.add(quest.createInstance());
        }

        return selected;
    }

    /**
     * 计算难度分布
     */
    private Map<QuestDifficulty, Integer> calculateDifficultyDistribution(
            QuestDifficulty recommended, int totalCount) {

        Map<QuestDifficulty, Integer> distribution = new EnumMap<>(QuestDifficulty.class);

        switch (recommended) {
            case EASY:
                distribution.put(QuestDifficulty.EASY, (int) (totalCount * 0.6));
                distribution.put(QuestDifficulty.NORMAL, (int) (totalCount * 0.3));
                distribution.put(QuestDifficulty.HARD, (int) (totalCount * 0.1));
                break;

            case NORMAL:
                distribution.put(QuestDifficulty.EASY, (int) (totalCount * 0.3));
                distribution.put(QuestDifficulty.NORMAL, (int) (totalCount * 0.5));
                distribution.put(QuestDifficulty.HARD, (int) (totalCount * 0.2));
                break;

            case HARD:
                distribution.put(QuestDifficulty.NORMAL, (int) (totalCount * 0.3));
                distribution.put(QuestDifficulty.HARD, (int) (totalCount * 0.5));
                distribution.put(QuestDifficulty.EXPERT, (int) (totalCount * 0.2));
                break;

            case EXPERT:
                distribution.put(QuestDifficulty.HARD, (int) (totalCount * 0.2));
                distribution.put(QuestDifficulty.EXPERT, (int) (totalCount * 0.6));
                distribution.put(QuestDifficulty.LEGENDARY, (int) (totalCount * 0.2));
                break;

            case LEGENDARY:
                distribution.put(QuestDifficulty.EXPERT, (int) (totalCount * 0.3));
                distribution.put(QuestDifficulty.LEGENDARY, (int) (totalCount * 0.7));
                break;
        }

        return distribution;
    }

    /**
     * 根据权重随机选择任务
     */
    private Quest selectWeightedRandom(List<Quest> quests) {
        if (quests.isEmpty()) {
            return null;
        }

        if (quests.size() == 1) {
            return quests.get(0);
        }

        // 计算总权重
        int totalWeight = 0;
        for (Quest quest : quests) {
            totalWeight += questWeights.getOrDefault(quest.getId(), 10);
        }

        // 随机选择
        int randomValue = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (Quest quest : quests) {
            currentWeight += questWeights.getOrDefault(quest.getId(), 10);
            if (randomValue < currentWeight) {
                return quest;
            }
        }

        // 兜底返回最后一个
        return quests.get(quests.size() - 1);
    }

    /**
     * 按分类生成任务
     */
    public List<Quest> generateQuestsByCategory(String category, int count) {
        List<Quest> categoryTemplates = templatesByCategory.getOrDefault(category, Collections.emptyList());

        if (categoryTemplates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Quest> selected = new ArrayList<>();
        List<Quest> available = new ArrayList<>(categoryTemplates);

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            Quest quest = selectWeightedRandom(available);
            selected.add(quest.createInstance());
            available.remove(quest);
        }

        return selected;
    }

    /**
     * 按难度生成任务
     */
    public List<Quest> generateQuestsByDifficulty(QuestDifficulty difficulty, int count) {
        List<Quest> difficultyTemplates = templatesByDifficulty.getOrDefault(difficulty, Collections.emptyList());

        if (difficultyTemplates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Quest> selected = new ArrayList<>();
        List<Quest> available = new ArrayList<>(difficultyTemplates);

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            Quest quest = selectWeightedRandom(available);
            selected.add(quest.createInstance());
            available.remove(quest);
        }

        return selected;
    }

    /**
     * 获取指定标签的任务
     */
    public List<Quest> getTemplatesByTag(String tag) {
        return templates.stream()
                .filter(quest -> quest.getTags().contains(tag))
                .collect(Collectors.toList());
    }

    /**
     * 移除模板
     */
    public void removeTemplate(String questId) {
        templates.removeIf(quest -> quest.getId().equals(questId));

        for (List<Quest> list : templatesByDifficulty.values()) {
            list.removeIf(quest -> quest.getId().equals(questId));
        }

        for (List<Quest> list : templatesByCategory.values()) {
            list.removeIf(quest -> quest.getId().equals(questId));
        }

        questWeights.remove(questId);
    }

    /**
     * 清空任务池
     */
    public void clear() {
        templates.clear();
        templatesByDifficulty.clear();
        templatesByCategory.clear();
        questWeights.clear();
    }

    /**
     * 获取模板数量
     */
    public int getTemplateCount() {
        return templates.size();
    }

    /**
     * 获取指定难度的模板数量
     */
    public int getTemplateCountByDifficulty(QuestDifficulty difficulty) {
        return templatesByDifficulty.getOrDefault(difficulty, Collections.emptyList()).size();
    }

    /**
     * 获取所有模板
     */
    public List<Quest> getAllTemplates() {
        return new ArrayList<>(templates);
    }

    /**
     * 检查是否有足够的模板
     */
    public boolean hasSufficientTemplates(int requiredCount) {
        return templates.size() >= requiredCount;
    }

    @Override
    public String toString() {
        return String.format("DailyQuestPool{templates=%d, byDifficulty=%d, byCategory=%d}",
                templates.size(), templatesByDifficulty.size(), templatesByCategory.size());
    }
}
