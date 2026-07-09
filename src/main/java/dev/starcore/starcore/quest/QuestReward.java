package dev.starcore.starcore.quest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务奖励类
 * 定义完成任务后获得的奖励
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestReward {

    private double money;
    private int experience;
    private final List<ItemStack> items;
    private final Map<String, Integer> reputations;
    private final List<String> titles;
    private final List<String> commands;
    private int skillPoints;
    private final Map<String, Object> customRewards;

    /**
     * 构造函数
     */
    public QuestReward() {
        this.money = 0.0;
        this.experience = 0;
        this.items = new ArrayList<>();
        this.reputations = new HashMap<>();
        this.titles = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.skillPoints = 0;
        this.customRewards = new HashMap<>();
    }

    /**
     * 添加金钱奖励
     */
    public QuestReward addMoney(double amount) {
        this.money += amount;
        return this;
    }

    /**
     * 添加经验奖励
     */
    public QuestReward addExperience(int amount) {
        this.experience += amount;
        return this;
    }

    /**
     * 添加物品奖励
     */
    public QuestReward addItem(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            this.items.add(item.clone());
        }
        return this;
    }

    /**
     * 添加物品奖励（通过Material）
     */
    public QuestReward addItem(Material material, int amount) {
        return addItem(new ItemStack(material, amount));
    }

    /**
     * 添加声望奖励
     */
    public QuestReward addReputation(String faction, int amount) {
        this.reputations.merge(faction, amount, Integer::sum);
        return this;
    }

    /**
     * 添加称号奖励
     */
    public QuestReward addTitle(String title) {
        if (title != null && !title.isEmpty()) {
            this.titles.add(title);
        }
        return this;
    }

    /**
     * 添加命令奖励（作为玩家执行）
     */
    public QuestReward addCommand(String command) {
        if (command != null && !command.isEmpty()) {
            this.commands.add(command);
        }
        return this;
    }

    /**
     * 添加技能点奖励
     */
    public QuestReward addSkillPoints(int points) {
        this.skillPoints += points;
        return this;
    }

    /**
     * 添加自定义奖励
     */
    public QuestReward addCustomReward(String key, Object value) {
        this.customRewards.put(key, value);
        return this;
    }

    /**
     * 应用难度倍率
     */
    public void applyDifficultyMultiplier(QuestDifficulty difficulty) {
        this.money *= difficulty.getRewardMultiplier();
        this.experience = (int) (this.experience * difficulty.getExperienceMultiplier());
        this.skillPoints = (int) (this.skillPoints * difficulty.getRewardMultiplier());
    }

    /**
     * 检查是否有奖励
     */
    public boolean hasRewards() {
        return money > 0 || experience > 0 || !items.isEmpty() ||
               !reputations.isEmpty() || !titles.isEmpty() ||
               !commands.isEmpty() || skillPoints > 0 || !customRewards.isEmpty();
    }

    /**
     * 获取奖励摘要
     */
    public List<String> getRewardSummary() {
        List<String> summary = new ArrayList<>();

        if (money > 0) {
            summary.add(String.format("§6金钱: §e%.2f", money));
        }

        if (experience > 0) {
            summary.add(String.format("§b经验: §3%d", experience));
        }

        if (skillPoints > 0) {
            summary.add(String.format("§d技能点: §5%d", skillPoints));
        }

        if (!items.isEmpty()) {
            summary.add(String.format("§a物品: §2%d种", items.size()));
        }

        if (!reputations.isEmpty()) {
            for (Map.Entry<String, Integer> entry : reputations.entrySet()) {
                summary.add(String.format("§e%s声望: §6%+d", entry.getKey(), entry.getValue()));
            }
        }

        if (!titles.isEmpty()) {
            summary.add(String.format("§5称号: §d%s", String.join(", ", titles)));
        }

        return summary;
    }

    /**
     * 克隆奖励
     */
    public QuestReward clone() {
        QuestReward cloned = new QuestReward();
        cloned.money = this.money;
        cloned.experience = this.experience;
        cloned.skillPoints = this.skillPoints;

        for (ItemStack item : this.items) {
            cloned.items.add(item.clone());
        }

        cloned.reputations.putAll(this.reputations);
        cloned.titles.addAll(this.titles);
        cloned.commands.addAll(this.commands);
        cloned.customRewards.putAll(this.customRewards);

        return cloned;
    }

    // Getters

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }

    public Map<String, Integer> getReputations() {
        return new HashMap<>(reputations);
    }

    public List<String> getTitles() {
        return new ArrayList<>(titles);
    }

    public List<String> getCommands() {
        return new ArrayList<>(commands);
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    public Map<String, Object> getCustomRewards() {
        return new HashMap<>(customRewards);
    }

    @Override
    public String toString() {
        return String.format("QuestReward{money=%.2f, exp=%d, items=%d, reputations=%d, titles=%d}",
                money, experience, items.size(), reputations.size(), titles.size());
    }
}
