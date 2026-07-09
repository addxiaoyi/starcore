package dev.starcore.starcore.quest;

import org.bukkit.entity.EntityType;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务目标类
 * 定义任务需要完成的具体目标
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestObjective {

    /**
     * 目标类型枚举
     */
    public enum ObjectiveType {
        KILL_ENTITY("击杀生物"),
        BREAK_BLOCK("破坏方块"),
        PLACE_BLOCK("放置方块"),
        COLLECT_ITEM("收集物品"),
        CRAFT_ITEM("合成物品"),
        DELIVER_ITEM("提交物品"),
        TALK_NPC("与NPC对话"),
        REACH_LOCATION("到达地点"),
        GAIN_EXPERIENCE("获得经验"),
        EARN_MONEY("赚取金钱"),
        TRADE_WITH_PLAYER("与玩家交易"),
        FISH("钓鱼"),
        ENCHANT("附魔"),
        BREED("繁殖动物"),
        TAME("驯服动物"),
        CONSUME("消耗物品"),
        CUSTOM("自定义目标");

        private final String displayName;

        ObjectiveType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String id;
    private final ObjectiveType type;
    private final String description;
    private final int requiredAmount;
    private int currentProgress;
    private final Map<String, Object> metadata;

    // 目标参数
    private EntityType targetEntity;
    private Material targetMaterial;
    private String targetItemId;
    private String targetNpcId;
    private String targetLocationId;

    /**
     * 构造函数
     */
    public QuestObjective(String id, ObjectiveType type, String description, int requiredAmount) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.requiredAmount = requiredAmount;
        this.currentProgress = 0;
        this.metadata = new HashMap<>();
    }

    /**
     * 增加进度
     */
    public void addProgress(int amount) {
        this.currentProgress = Math.min(currentProgress + amount, requiredAmount);
    }

    /**
     * 设置进度
     */
    public void setProgress(int progress) {
        this.currentProgress = Math.min(progress, requiredAmount);
    }

    /**
     * 检查是否完成
     */
    public boolean isCompleted() {
        return currentProgress >= requiredAmount;
    }

    /**
     * 获取完成百分比
     */
    public double getProgressPercentage() {
        return (double) currentProgress / requiredAmount * 100.0;
    }

    /**
     * 获取进度条显示
     */
    public String getProgressBar() {
        int total = 20;
        int filled = (int) (getProgressPercentage() / 100.0 * total);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < total; i++) {
            if (i < filled) {
                bar.append("§a■");
            } else {
                bar.append("§8□");
            }
        }
        bar.append("§7]");
        return bar.toString();
    }

    /**
     * 获取进度文本
     */
    public String getProgressText() {
        return String.format("§e%d§7/§a%d", currentProgress, requiredAmount);
    }

    /**
     * 重置进度
     */
    public void resetProgress() {
        this.currentProgress = 0;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public ObjectiveType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public EntityType getTargetEntity() {
        return targetEntity;
    }

    public void setTargetEntity(EntityType targetEntity) {
        this.targetEntity = targetEntity;
    }

    public Material getTargetMaterial() {
        return targetMaterial;
    }

    public void setTargetMaterial(Material targetMaterial) {
        this.targetMaterial = targetMaterial;
    }

    public String getTargetItemId() {
        return targetItemId;
    }

    public void setTargetItemId(String targetItemId) {
        this.targetItemId = targetItemId;
    }

    public String getTargetNpcId() {
        return targetNpcId;
    }

    public void setTargetNpcId(String targetNpcId) {
        this.targetNpcId = targetNpcId;
    }

    public String getTargetLocationId() {
        return targetLocationId;
    }

    public void setTargetLocationId(String targetLocationId) {
        this.targetLocationId = targetLocationId;
    }

    /**
     * 添加元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 克隆目标（用于创建新任务实例）
     */
    public QuestObjective clone() {
        QuestObjective cloned = new QuestObjective(id, type, description, requiredAmount);
        cloned.targetEntity = this.targetEntity;
        cloned.targetMaterial = this.targetMaterial;
        cloned.targetItemId = this.targetItemId;
        cloned.targetNpcId = this.targetNpcId;
        cloned.targetLocationId = this.targetLocationId;
        cloned.metadata.putAll(this.metadata);
        return cloned;
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%d/%d)", type.getDisplayName(), description, currentProgress, requiredAmount);
    }
}
