package dev.starcore.starcore.ai.bot;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI机器人服务
 * 管理所有训练机器人
 *
 * 需要注入 MessageService 来支持国际化。
 * 示例初始化方式:
 *   this.messages = context.serviceRegistry().require(MessageService.class);
 */
public final class AIBotService {
    // 全局机器人上限 - 防止 DoS 攻击
    private static final int MAX_GLOBAL_BOTS = 20;

    // 活跃的机器人 botId -> AITrainingBot
    private final ConcurrentHashMap<UUID, AITrainingBot> activeBots = new ConcurrentHashMap<>();

    // 玩家的机器人 playerId -> botId
    private final ConcurrentHashMap<UUID, UUID> playerBots = new ConcurrentHashMap<>();

    // 消息服务（国际化）
    private MessageService messages;

    /**
     * 设置消息服务
     */
    public void setMessageService(MessageService messages) {
        this.messages = messages;
    }

    /**
     * 发送本地化消息
     */
    private void send(Player player, String key, Object... args) {
        if (messages != null) {
            String msg = messages.format(key, args);
            MessageUtil.send(player, msg);
        } else {
            // 回退：如果没有消息服务，使用原始键名
            player.sendMessage(key);
        }
    }

    /**
     * 生成训练机器人
     */
    public AITrainingBot spawnBot(Player player, AITrainingBot.BotDifficulty difficulty) {
        // 检查全局上限 - 防止服务器过载
        if (activeBots.size() >= MAX_GLOBAL_BOTS) {
            send(player, "ai.bot.limit-reached", MAX_GLOBAL_BOTS);
            return null;
        }

        // 检查是否已有机器人
        if (playerBots.containsKey(player.getUniqueId())) {
            send(player, "ai.bot.already-exists");
            return null;
        }

        String botName = "TrainingBot_" + difficulty.displayName();
        AITrainingBot bot = new AITrainingBot(botName, difficulty, player);

        activeBots.put(bot.getBotId(), bot);
        playerBots.put(player.getUniqueId(), bot.getBotId());

        send(player, "ai.bot.spawn-success", difficulty.displayName());
        send(player, "ai.bot.global-count", activeBots.size(), MAX_GLOBAL_BOTS);

        return bot;
    }

    /**
     * 移除机器人
     */
    public boolean removeBot(UUID playerId) {
        UUID botId = playerBots.remove(playerId);
        if (botId == null) {
            return false;
        }

        AITrainingBot bot = activeBots.remove(botId);
        if (bot != null) {
            // 生成报告
            AITrainingBot.BotReport report = bot.generateReport();
            Player owner = bot.getOwner();
            if (owner != null && owner.isOnline()) {
                sendReport(owner, report);
            }
        }

        return true;
    }

    /**
     * 获取玩家的机器人
     */
    public Optional<AITrainingBot> getPlayerBot(UUID playerId) {
        UUID botId = playerBots.get(playerId);
        if (botId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeBots.get(botId));
    }

    /**
     * AI Tick更新
     */
    public void tickAll() {
        for (AITrainingBot bot : activeBots.values()) {
            try {
                bot.tick();
            } catch (Exception e) {
                // 忽略错误继续运行
            }
        }
    }

    /**
     * 发送战斗报告
     */
    private void sendReport(Player player, AITrainingBot.BotReport report) {
        send(player, "ai.bot.report-header");
        send(player, "ai.bot.report-bot", report.botName());
        send(player, "ai.bot.report-difficulty", report.difficulty().displayName());
        send(player, "ai.bot.report-kills", report.kills());
        send(player, "ai.bot.report-deaths", report.deaths());
        send(player, "ai.bot.report-kd", String.format("%.2f", report.getKDRatio()));
        send(player, "ai.bot.report-duration", String.format("%.1f", report.durationMs() / 1000.0));
        send(player, "ai.bot.report-footer");
    }

    /**
     * 获取所有活跃机器人
     */
    public Collection<AITrainingBot> getAllBots() {
        return new ArrayList<>(activeBots.values());
    }

    /**
     * 清理所有机器人
     */
    public void shutdown() {
        for (AITrainingBot bot : activeBots.values()) {
            if (bot.getOwner() != null && bot.getOwner().isOnline()) {
                sendReport(bot.getOwner(), bot.generateReport());
            }
        }
        activeBots.clear();
        playerBots.clear();
    }
}
