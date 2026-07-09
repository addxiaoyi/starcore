package dev.starcore.starcore.module.nation.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国家引导教程气泡系统
 * 在玩家头顶显示悬浮教程提示
 */
public class NationTutorialBubble {

    private final org.bukkit.plugin.Plugin plugin;

    // 玩家头顶的气泡 ArmorStand
    private final Map<UUID, ArmorStand> bubbleStands = new ConcurrentHashMap<>();

    // 教程任务追踪
    private final Map<UUID, TutorialTask> tutorialTasks = new ConcurrentHashMap<>();

    // 已完成的教程步骤（持久化用）
    private final Map<UUID, Set<String>> completedSteps = new ConcurrentHashMap<>();

    // 当前显示的气泡内容
    private final Map<UUID, String> currentBubbleText = new ConcurrentHashMap<>();

    public NationTutorialBubble(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示教程气泡
     */
    public void showTutorialBubble(Player player, TutorialStep step) {
        String content = formatStepContent(step);

        // 如果内容相同且气泡存在，不重复创建
        if (content.equals(currentBubbleText.get(player.getUniqueId())) && bubbleStands.containsKey(player.getUniqueId())) {
            return;
        }

        currentBubbleText.put(player.getUniqueId(), content);

        // 获取或创建 ArmorStand
        ArmorStand stand = bubbleStands.computeIfAbsent(player.getUniqueId(), uuid -> {
            Location loc = player.getLocation().clone();
            loc.setY(loc.getY() + 2.8);

            ArmorStand armorStand = (ArmorStand) player.getWorld()
                .spawnEntity(loc, EntityType.ARMOR_STAND);

            // 配置 ArmorStand
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName("");
            armorStand.setRemoveWhenFarAway(false);

            // 移除默认装备
            armorStand.getEquipment().setHelmet(new ItemStack(org.bukkit.Material.AIR));

            return armorStand;
        });

        // 更新气泡文本
        stand.setCustomName(content);
        stand.teleport(player.getLocation().clone().add(0, 2.8, 0));
    }

    /**
     * 隐藏教程气泡
     */
    public void hideTutorialBubble(UUID playerId) {
        ArmorStand stand = bubbleStands.remove(playerId);
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        currentBubbleText.remove(playerId);
    }

    /**
     * 隐藏教程气泡（播放器版本）
     */
    public void hideTutorialBubble(Player player) {
        hideTutorialBubble(player.getUniqueId());
    }

    /**
     * 隐藏所有教程气泡
     */
    public void hideAllBubbles() {
        bubbleStands.values().forEach(stand -> {
            if (!stand.isDead()) {
                stand.remove();
            }
        });
        bubbleStands.clear();
        currentBubbleText.clear();
    }

    /**
     * 更新所有气泡位置（跟随玩家）
     */
    public void updateAllBubblePositions() {
        bubbleStands.forEach((playerId, stand) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !stand.isDead()) {
                stand.teleport(player.getLocation().clone().add(0, 2.8, 0));
            } else {
                // 玩家不在线，清理
                if (!stand.isDead()) {
                    stand.remove();
                }
                bubbleStands.remove(playerId);
                currentBubbleText.remove(playerId);
            }
        });
    }

    /**
     * 启动气泡跟随任务
     */
    public void startBubbleFollowTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllBubblePositions();
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    /**
     * 显示步骤气泡
     */
    private String formatStepContent(TutorialStep step) {
        StringBuilder sb = new StringBuilder();

        // 步骤指示器
        if (step.stepNumber() > 0) {
            sb.append("§7§l[步骤 ").append(step.stepNumber()).append("/").append(step.totalSteps()).append("] §r\n");
        }

        // 标题
        sb.append("§e§l").append(step.title()).append(" §r\n");

        // 分隔线
        sb.append("§8───────────────── §r\n");

        // 内容
        for (String line : step.content()) {
            sb.append("§7").append(line).append(" §r\n");
        }

        // 提示
        if (step.hint() != null && !step.hint().isEmpty()) {
            sb.append("\n§a▸ ").append(step.hint());
        }

        return sb.toString();
    }

    /**
     * 标记教程步骤完成
     */
    public void completeStep(UUID playerId, String stepId) {
        Set<String> steps = completedSteps.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        steps.add(stepId);
    }

    /**
     * 检查步骤是否完成
     */
    public boolean isStepCompleted(UUID playerId, String stepId) {
        Set<String> steps = completedSteps.get(playerId);
        return steps != null && steps.contains(stepId);
    }

    /**
     * 获取玩家已完成的步骤
     */
    public Set<String> getCompletedSteps(UUID playerId) {
        return completedSteps.getOrDefault(playerId, Collections.emptySet());
    }

    /**
     * 重置玩家教程进度
     */
    public void resetProgress(UUID playerId) {
        completedSteps.remove(playerId);
        hideTutorialBubble(playerId);
        tutorialTasks.remove(playerId);
    }

    /**
     * 启动教程任务
     */
    public TutorialTask startTutorial(UUID playerId, List<TutorialStep> steps) {
        TutorialTask task = new TutorialTask(playerId, steps, this);
        tutorialTasks.put(playerId, task);
        task.start();
        return task;
    }

    /**
     * 停止教程任务
     */
    public void stopTutorial(UUID playerId) {
        TutorialTask task = tutorialTasks.remove(playerId);
        if (task != null) {
            task.stop();
        }
        hideTutorialBubble(playerId);
    }

    /**
     * 获取当前教程任务
     */
    public Optional<TutorialTask> getTutorialTask(UUID playerId) {
        return Optional.ofNullable(tutorialTasks.get(playerId));
    }

    /**
     * 关闭（清理资源）
     */
    public void shutdown() {
        hideAllBubbles();
        tutorialTasks.values().forEach(TutorialTask::stop);
        tutorialTasks.clear();
    }

    /**
     * 教程步骤记录
     */
    public record TutorialStep(
        String id,
        int stepNumber,
        int totalSteps,
        String title,
        List<String> content,
        String hint,
        String actionCommand
    ) {}

    /**
     * 教程任务
     */
    public static class TutorialTask {
        private final UUID playerId;
        private final List<TutorialStep> steps;
        private final NationTutorialBubble bubble;

        private int currentStepIndex = 0;
        private boolean running = false;
        private BukkitTask displayTask;
        private BukkitTask autoAdvanceTask;

        public TutorialTask(UUID playerId, List<TutorialStep> steps, NationTutorialBubble bubble) {
            this.playerId = playerId;
            this.steps = steps;
            this.bubble = bubble;
        }

        public void start() {
            if (running || steps.isEmpty()) return;
            running = true;
            showCurrentStep();

            // 自动前进任务（每个步骤显示15秒后自动前进）
            autoAdvanceTask = Bukkit.getScheduler().runTaskLater(bubble.plugin, () -> {
                if (running && hasNextStep()) {
                    nextStep();
                }
            }, 300L); // 15秒
        }

        public void stop() {
            running = false;
            if (displayTask != null) {
                displayTask.cancel();
            }
            if (autoAdvanceTask != null) {
                autoAdvanceTask.cancel();
            }
            bubble.hideTutorialBubble(playerId);
        }

        public void nextStep() {
            if (!hasNextStep()) {
                complete();
                return;
            }

            currentStepIndex++;
            showCurrentStep();

            // 取消之前的自动前进任务，重新计时
            if (autoAdvanceTask != null) {
                autoAdvanceTask.cancel();
            }
            autoAdvanceTask = Bukkit.getScheduler().runTaskLater(bubble.plugin, () -> {
                if (running && hasNextStep()) {
                    nextStep();
                }
            }, 300L);
        }

        public void previousStep() {
            if (currentStepIndex <= 0) return;
            currentStepIndex--;
            showCurrentStep();
        }

        public void showCurrentStep() {
            if (!running || currentStepIndex < 0 || currentStepIndex >= steps.size()) return;

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;

            TutorialStep step = steps.get(currentStepIndex);
            bubble.showTutorialBubble(player, step);
        }

        public boolean hasNextStep() {
            return currentStepIndex < steps.size() - 1;
        }

        public boolean hasPreviousStep() {
            return currentStepIndex > 0;
        }

        public int getCurrentStep() {
            return currentStepIndex;
        }

        public int getTotalSteps() {
            return steps.size();
        }

        public TutorialStep getCurrentStepData() {
            if (currentStepIndex < 0 || currentStepIndex >= steps.size()) return null;
            return steps.get(currentStepIndex);
        }

        public void complete() {
            running = false;
            if (displayTask != null) {
                displayTask.cancel();
            }
            if (autoAdvanceTask != null) {
                autoAdvanceTask.cancel();
            }

            // 标记所有步骤完成
            for (TutorialStep step : steps) {
                bubble.completeStep(playerId, step.id());
            }

            bubble.hideTutorialBubble(playerId);
        }

        public boolean isRunning() {
            return running;
        }
    }
}