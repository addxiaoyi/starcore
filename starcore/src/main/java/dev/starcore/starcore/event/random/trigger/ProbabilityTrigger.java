package dev.starcore.starcore.event.random.trigger;

import dev.starcore.starcore.event.random.EventTrigger;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Random;

/**
 * 概率触发器
 * 基于随机概率触发事件
 */
public class ProbabilityTrigger implements EventTrigger {

    private final double probability;
    private final Random random;

    /**
     * 创建概率触发器
     *
     * @param probability 触发概率（0.0-1.0）
     */
    public ProbabilityTrigger(double probability) {
        this.probability = Math.max(0.0, Math.min(1.0, probability));
        this.random = new Random();
    }

    @Override
    public boolean check(Player player, Location location) {
        return random.nextDouble() <= probability;
    }

    @Override
    public String getType() {
        return "PROBABILITY";
    }

    @Override
    public String getDescription() {
        return String.format("概率触发器 [概率=%.2f%%]", probability * 100);
    }

    public double getProbability() {
        return probability;
    }
}
