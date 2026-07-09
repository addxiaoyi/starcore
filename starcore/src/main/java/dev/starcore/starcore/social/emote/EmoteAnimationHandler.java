package dev.starcore.starcore.social.emote;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画播放处理器
 */
public class EmoteAnimationHandler {
    private final Map<UUID, BukkitTask> activeAnimations = new ConcurrentHashMap<>();
    private final EmoteService emoteService;

    public EmoteAnimationHandler(EmoteService emoteService) {
        this.emoteService = emoteService;
    }

    /**
     * 播放动作动画
     */
    public void playAnimation(Player player, EmoteDefinition emote, Player target) {
        // 取消之前的动画
        cancelAnimation(player.getUniqueId());

        // 向周围玩家广播动作
        broadcastEmote(player, emote, target);

        // 根据动画类型播放效果
        switch (emote.getAnimationType().toLowerCase()) {
            case "particle" -> playParticleEffect(player, emote);
            case "pose" -> playPoseAnimation(player, emote);
            case "arm" -> playArmAnimation(player, emote);
            case "fullbody" -> playFullbodyAnimation(player, emote);
        }

        // 动作结束后清理状态
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                emoteService.getOrCreateState(player.getUniqueId()).clearEmote();
                activeAnimations.remove(player.getUniqueId());
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("StarCore"),
            emote.getDurationTicks());

        activeAnimations.put(player.getUniqueId(), task);
    }

    /**
     * 向周围玩家广播动作
     */
    private void broadcastEmote(Player player, EmoteDefinition emote, Player target) {
        String message = buildEmoteMessage(player, emote, target);

        // 向周围玩家发送消息
        Location loc = player.getLocation();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(loc) <= 30) {
                nearby.sendMessage(message);
            }
        }

        // 如果是全局可见的动作，向全服广播
        if (emote.isGlobal()) {
            // 可以在这里添加全服广播的逻辑
        }
    }

    /**
     * 构建动作消息
     */
    private String buildEmoteMessage(Player player, EmoteDefinition emote, Player target) {
        String baseName = emote.getName();

        // 根据动作类型构建不同格式的消息
        return switch (emote.getAnimationType().toLowerCase()) {
            case "arm" -> String.format("§6[动作] §e%s §f做了一个 §a%s §f动作",
                player.getName(), baseName);
            case "pose" -> String.format("§6[动作] §e%s §f摆出了 §a%s §f姿势",
                player.getName(), baseName);
            case "fullbody" -> String.format("§6[动作] §e%s §f做出了 §a%s §f动作",
                player.getName(), baseName);
            default -> String.format("§6[动作] §e%s §f执行了 §a%s",
                player.getName(), baseName);
        };
    }

    /**
     * 播放粒子效果
     */
    private void playParticleEffect(Player player, EmoteDefinition emote) {
        Location loc = player.getLocation().add(0, 1.5, 0);

        switch (emote.getAnimationData().toUpperCase()) {
            case "HEART" -> {
                loc.getWorld().spawnParticle(Particle.HEART, loc, 10, 0.5, 0.5, 0.5, 0.02);
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f);
            }
            case "ANGER" -> {
                loc.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc, 15, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 0.5f, 1.0f);
            }
            case "LAUGH" -> {
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.5, 0.5, 0.5, 0.02);
            }
            case "SPARKLE" -> {
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 30, 0.3, 0.3, 0.3, 0.02);
                loc.getWorld().spawnParticle(Particle.WITCH, loc, 20, 0.3, 0.3, 0.3, 0.01);
            }
            case "MUSIC" -> {
                loc.getWorld().spawnParticle(Particle.NOTE, loc, 10, 0.3, 0.3, 0.3, 0.01);
            }
            default -> {
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    /**
     * 播放姿势动画
     */
    private void playPoseAnimation(Player player, EmoteDefinition emote) {
        Location loc = player.getLocation();

        switch (emote.getAnimationData().toUpperCase()) {
            case "BOW" -> {
                // 鞠躬效果
                loc.getWorld().playSound(loc, Sound.BLOCK_BAMBOO_PLACE, 0.3f, 1.5f);
                loc.getWorld().spawnParticle(Particle.CLOUD, loc.add(0, 0.1, 0), 5, 0.2, 0.1, 0.2, 0.01);
            }
            case "SIT" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_WOOL_PLACE, 0.3f, 1.0f);
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 8, 0.3, 0.1, 0.3, 0.01);
            }
            case "SLEEP" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_WOOL_PLACE, 0.2f, 0.8f);
                loc.getWorld().spawnParticle(Particle.MYCELIUM, loc.add(0, 0.1, 0), 10, 0.4, 0.1, 0.4, 0.01);
            }
            case "CRY" -> {
                loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc.add(0, 1.6, 0), 15, 0.2, 0.2, 0.2, 0.02);
            }
            case "LAUGH" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_CELEBRATE, 0.5f, 1.2f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
            }
            case "ANGRY" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 0.5f, 1.0f);
                loc.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc.add(0, 1.5, 0), 10, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    /**
     * 播放手臂动画
     */
    private void playArmAnimation(Player player, EmoteDefinition emote) {
        Location loc = player.getLocation();

        switch (emote.getAnimationData().toUpperCase()) {
            case "WAVE", "ARM_RAISE" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_AMBIENT, 0.3f, 1.5f);
            }
            case "CLAP" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.2f);
            }
            case "THUMBSUP" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 2, 0), 8, 0.2, 0.2, 0.2, 0.01);
            }
            case "SWORD" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                loc.getWorld().spawnParticle(Particle.CRIT, loc.add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
            }
            case "SALUTE" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_AMBIENT, 0.3f, 1.3f);
            }
            case "POINT" -> {
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 3, 0.1, 0.1, 0.1, 0.02);
            }
            case "HUG" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.2f);
                loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0.01);
            }
            case "KISS" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f);
                loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.5, 0.5), 8, 0.2, 0.2, 0.2, 0.01);
            }
            case "HANDSHAKE" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.3f);
            }
            case "EAT" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            }
            case "DRINK" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            }
            case "FACEPALM" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 0.3f, 1.0f);
            }
        }
    }

    /**
     * 播放全身动画
     */
    private void playFullbodyAnimation(Player player, EmoteDefinition emote) {
        Location loc = player.getLocation();

        switch (emote.getAnimationData().toUpperCase()) {
            case "HUG" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.2f);
                loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
            }
            case "KISS" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.6, 0), 12, 0.3, 0.3, 0.3, 0.02);
                loc.getWorld().spawnParticle(Particle.FLAME, loc.add(0, 1.5, 0), 5, 0.1, 0.1, 0.1, 0.01);
            }
            case "DANCE" -> {
                loc.getWorld().playSound(loc, Sound.MUSIC_DISC_CAT, 0.5f, 1.0f);
                // 周期性粒子效果
                new BukkitRunnable() {
                    int count = 0;
                    @Override
                    public void run() {
                        if (count++ >= 5) {
                            cancel();
                            return;
                        }
                        loc.getWorld().spawnParticle(Particle.NOTE, loc.add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0.01);
                        loc.getWorld().spawnParticle(Particle.FLAME, loc.add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0.01);
                    }
                }.runTaskTimer(Bukkit.getPluginManager().getPlugin("StarCore"), 0L, 10L);
            }
            case "SPIN" -> {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.2f);
                loc.getWorld().spawnParticle(Particle.WITCH, loc.add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
            }
            case "ATTACK" -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.2f);
                loc.getWorld().spawnParticle(Particle.CRIT, loc.add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    /**
     * 取消玩家的动画
     */
    public void cancelAnimation(UUID playerId) {
        BukkitTask task = activeAnimations.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 取消所有动画
     */
    public void cancelAllAnimations() {
        activeAnimations.values().forEach(BukkitTask::cancel);
        activeAnimations.clear();
    }
}
