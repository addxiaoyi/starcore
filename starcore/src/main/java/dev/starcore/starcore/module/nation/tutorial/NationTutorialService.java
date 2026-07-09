package dev.starcore.starcore.module.nation.tutorial;

import dev.starcore.starcore.module.nation.NationModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国家教程服务
 * 管理教程气泡的显示、步骤导航和进度追踪
 */
public class NationTutorialService {

    private final Plugin plugin;
    private final NationModule nationModule;
    private final NationTutorialBubble bubble;
    private final NationTutorialConfig config;

    // 活跃的教程玩家
    private final Map<UUID, ActiveTutorial> activeTutorials = new ConcurrentHashMap<>();

    // 教程冷却（防止频繁触发）
    private final Map<UUID, Long> tutorialCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 60000; // 60秒冷却

    // 玩家首次登录状态
    private final Set<UUID> firstLoginPlayers = new HashSet<>();

    public NationTutorialService(Plugin plugin, NationModule nationModule,
                                 NationTutorialBubble bubble, NationTutorialConfig config) {
        this.plugin = plugin;
        this.nationModule = nationModule;
        this.bubble = bubble;
        this.config = config;
    }

    /**
     * 启动气泡跟随任务
     */
    public void startBubbleFollowTask() {
        bubble.startBubbleFollowTask();
    }

    /**
     * 检查并触发教程
     */
    public void checkAndTriggerTutorial(Player player) {
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (isInCooldown(playerId)) {
            return;
        }

        // 检查是否已有活跃教程
        if (activeTutorials.containsKey(playerId)) {
            return;
        }

        // 获取玩家国家状态
        boolean hasNation = nationModule.getNationByMember(playerId).isPresent();
        boolean isAdmin = hasNation && nationModule.getNationByMember(playerId)
            .map(n -> n.members().stream()
                .anyMatch(m -> m.playerId().equals(playerId) && "admin".equals(m.rank())))
            .orElse(false);

        // 获取合适的教程
        NationTutorialConfig.TutorialContent tutorial = config.getTutorialForPlayer(hasNation, isAdmin);
        if (tutorial == null || !tutorial.enabled()) {
            return;
        }

        // 检查是否首次登录
        if (tutorial.triggerFirstLogin() && !firstLoginPlayers.contains(playerId)) {
            firstLoginPlayers.add(playerId);
            return;
        }

        // 启动教程
        startTutorial(player, tutorial);
    }

    /**
     * 启动教程
     */
    public void startTutorial(Player player, NationTutorialConfig.TutorialContent tutorial) {
        UUID playerId = player.getUniqueId();

        // 设置冷却
        tutorialCooldowns.put(playerId, System.currentTimeMillis());

        // 转换为气泡步骤
        List<NationTutorialBubble.TutorialStep> bubbleSteps = tutorial.toBubbleSteps();
        if (bubbleSteps.isEmpty()) {
            return;
        }

        // 启动教程任务
        NationTutorialBubble.TutorialTask task = bubble.startTutorial(playerId, bubbleSteps);

        // 创建活跃教程
        ActiveTutorial active = new ActiveTutorial(playerId, tutorial, task);
        activeTutorials.put(playerId, active);

        // 发送启动消息
        sendTutorialStartMessage(player, tutorial);
    }

    /**
     * 发送教程启动消息
     */
    private void sendTutorialStartMessage(Player player, NationTutorialConfig.TutorialContent tutorial) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("📖 " + tutorial.title() + " 教程已启动！", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(tutorial.description(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("💡 提示：教程气泡将显示在你头顶", NamedTextColor.AQUA));
        player.sendMessage(Component.text("   点击 GUI 物品可前进到下一步", NamedTextColor.AQUA));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
    }

    /**
     * 处理下一步
     */
    public void nextStep(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveTutorial active = activeTutorials.get(playerId);

        if (active == null || !active.task().isRunning()) {
            return;
        }

        active.task().nextStep();

        // 播放音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // 显示当前步骤信息
        showStepInfo(player, active.task());
    }

    /**
     * 处理上一步
     */
    public void previousStep(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveTutorial active = activeTutorials.get(playerId);

        if (active == null || !active.task().isRunning()) {
            return;
        }

        active.task().previousStep();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        showStepInfo(player, active.task());
    }

    /**
     * 显示当前步骤信息
     */
    public void showStepInfo(Player player, NationTutorialBubble.TutorialTask task) {
        NationTutorialBubble.TutorialStep step = task.getCurrentStepData();
        if (step == null) return;

        Component header = Component.text("═══════════════════════════════════", NamedTextColor.GOLD);
        Component stepInfo = Component.text("[步骤 " + task.getCurrentStep() + "/" + task.getTotalSteps() + "] ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(step.title()).color(NamedTextColor.YELLOW));

        Component footer = Component.text("═══════════════════════════════════", NamedTextColor.GOLD);

        // 分隔
        player.sendMessage(header);
        player.sendMessage(stepInfo);
        player.sendMessage(footer);
        // 分隔
    }

    /**
     * 执行当前步骤的命令
     */
    public void executeCurrentCommand(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveTutorial active = activeTutorials.get(playerId);

        if (active == null || !active.task().isRunning()) {
            return;
        }

        NationTutorialBubble.TutorialStep step = active.task().getCurrentStepData();
        if (step == null || step.actionCommand() == null || step.actionCommand().isEmpty()) {
            return;
        }

        // 隐藏气泡
        bubble.hideTutorialBubble(playerId);

        // 执行命令
        String command = step.actionCommand();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        player.performCommand(command);
    }

    /**
     * 完成教程
     */
    public void completeTutorial(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveTutorial active = activeTutorials.remove(playerId);

        if (active != null) {
            active.task().complete();

            // 发送完成消息
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GREEN));
            player.sendMessage(Component.text("✅ 教程完成！", NamedTextColor.GREEN));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("感谢你完成本教程！", NamedTextColor.GRAY));
            player.sendMessage(Component.text("如需再次查看，使用 /sc tutorial", NamedTextColor.GRAY));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GREEN));
            player.sendMessage(Component.text(""));

            // 播放成功音效
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    /**
     * 跳过教程
     */
    public void skipTutorial(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveTutorial active = activeTutorials.remove(playerId);

        if (active != null) {
            active.task().stop();
            bubble.hideTutorialBubble(playerId);

            player.sendMessage(Component.text("教程已跳过。如需再次查看，使用 /sc tutorial", NamedTextColor.YELLOW));
        }
    }

    /**
     * 关闭教程
     */
    public void closeTutorial(Player player) {
        skipTutorial(player);
    }

    /**
     * 检查冷却
     */
    private boolean isInCooldown(UUID playerId) {
        Long lastTime = tutorialCooldowns.get(playerId);
        if (lastTime == null) {
            return false;
        }
        return System.currentTimeMillis() - lastTime < COOLDOWN_MS;
    }

    /**
     * 检查玩家是否有活跃教程
     */
    public boolean hasActiveTutorial(UUID playerId) {
        return activeTutorials.containsKey(playerId);
    }

    /**
     * 获取活跃教程
     */
    public Optional<ActiveTutorial> getActiveTutorial(UUID playerId) {
        return Optional.ofNullable(activeTutorials.get(playerId));
    }

    /**
     * 关闭（清理资源）
     */
    public void shutdown() {
        activeTutorials.values().forEach(active -> active.task().stop());
        activeTutorials.clear();
        bubble.shutdown();
    }

    /**
     * 活跃教程记录
     */
    public record ActiveTutorial(
        UUID playerId,
        NationTutorialConfig.TutorialContent tutorial,
        NationTutorialBubble.TutorialTask task
    ) {}
}
