package dev.starcore.starcore.mechanics;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 治疗方案
 * 定义疾病的治疗方法
 */
public class Treatment {

    private final DiseaseType diseaseType;
    private final List<ItemStack> requiredItems;
    private final int duration; // 治疗持续时间（分钟）
    private final int effectiveness; // 有效性 (0-100)
    private final int cost; // 治疗费用

    public Treatment(DiseaseType diseaseType) {
        this.diseaseType = diseaseType;
        this.requiredItems = new ArrayList<>();
        this.duration = calculateDuration();
        this.effectiveness = calculateEffectiveness();
        this.cost = calculateCost();
        initializeRequiredItems();
    }

    /**
     * 初始化所需物品
     */
    private void initializeRequiredItems() {
        switch (diseaseType) {
            case COLD:
                requiredItems.add(new ItemStack(Material.HONEY_BOTTLE, 2));
                requiredItems.add(new ItemStack(Material.GOLDEN_CARROT, 1));
                break;

            case FLU:
                requiredItems.add(new ItemStack(Material.GOLDEN_APPLE, 1));
                requiredItems.add(new ItemStack(Material.HONEY_BOTTLE, 3));
                requiredItems.add(new ItemStack(Material.SUSPICIOUS_STEW, 1));
                break;

            case PLAGUE:
                requiredItems.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
                requiredItems.add(new ItemStack(Material.GOLDEN_APPLE, 3));
                requiredItems.add(new ItemStack(Material.POTION, 2)); // 需要特定药水
                break;

            case POISON:
                requiredItems.add(new ItemStack(Material.MILK_BUCKET, 2));
                requiredItems.add(new ItemStack(Material.HONEY_BOTTLE, 2));
                break;

            case CURSE:
                requiredItems.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                requiredItems.add(new ItemStack(Material.NETHER_STAR, 1));
                break;

            case INFECTION:
                requiredItems.add(new ItemStack(Material.GOLDEN_APPLE, 2));
                requiredItems.add(new ItemStack(Material.FERMENTED_SPIDER_EYE, 1));
                break;

            case FEVER:
                requiredItems.add(new ItemStack(Material.ICE, 10));
                requiredItems.add(new ItemStack(Material.HONEY_BOTTLE, 2));
                requiredItems.add(new ItemStack(Material.GOLDEN_CARROT, 2));
                break;

            case WEAKNESS:
                requiredItems.add(new ItemStack(Material.GOLDEN_APPLE, 1));
                requiredItems.add(new ItemStack(Material.COOKED_BEEF, 5));
                break;
        }
    }

    /**
     * 计算治疗持续时间
     */
    private int calculateDuration() {
        return diseaseType.getSeverity() * 30; // 30分钟 * 严重程度
    }

    /**
     * 计算有效性
     */
    private int calculateEffectiveness() {
        // 基础有效性：严重程度越高，单次治疗效果越低
        return Math.max(50, 100 - diseaseType.getSeverity() * 10);
    }

    /**
     * 计算治疗费用
     */
    private int calculateCost() {
        return diseaseType.getSeverity() * 1000;
    }

    /**
     * 检查玩家是否有足够的物品
     */
    public boolean hasRequiredItems(org.bukkit.entity.Player player) {
        for (ItemStack required : requiredItems) {
            if (!player.getInventory().containsAtLeast(required, required.getAmount())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消耗所需物品
     */
    public void consumeItems(org.bukkit.entity.Player player) {
        for (ItemStack required : requiredItems) {
            player.getInventory().removeItem(required);
        }
    }

    /**
     * 获取治疗描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l治疗方案 - ").append(diseaseType.getDisplayName()).append("\n");
        sb.append("§e持续时间: §f").append(duration).append(" 分钟\n");
        sb.append("§e有效性: §f").append(effectiveness).append("%\n");
        sb.append("§e费用: §f").append(cost).append(" 金币\n");
        sb.append("\n§e所需物品:\n");

        for (ItemStack item : requiredItems) {
            sb.append("  §7- §f")
              .append(item.getAmount())
              .append("x ")
              .append(formatMaterialName(item.getType()))
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化材料名称
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                         .append(word.substring(1))
                         .append(" ");
            }
        }

        return formatted.toString().trim();
    }

    /**
     * 应用治疗效果
     */
    public int applyTreatment(Disease disease) {
        // 根据有效性降低疾病严重程度
        int reduction = (int) (effectiveness * 0.5); // 每次治疗降低50%的有效性值
        disease.improve(reduction);
        disease.setTreated(true);
        return reduction;
    }

    // Getters

    public DiseaseType getDiseaseType() {
        return diseaseType;
    }

    public List<ItemStack> getRequiredItems() {
        return new ArrayList<>(requiredItems);
    }

    public int getDuration() {
        return duration;
    }

    public int getEffectiveness() {
        return effectiveness;
    }

    public int getCost() {
        return cost;
    }
}
