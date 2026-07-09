package dev.starcore.starcore.quest;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.List;
import java.util.UUID;

/**
 * 任务进度服务
 * 监听游戏事件并自动更新任务进度
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestProgressService implements Listener {

    private final QuestService questService;

    /**
     * 构造函数
     */
    public QuestProgressService(QuestService questService) {
        this.questService = questService;
    }

    /**
     * 监听实体击杀
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        UUID playerId = killer.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        // 检查所有活跃任务
        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                // 检查是否为击杀目标
                if (objective.getType() == QuestObjective.ObjectiveType.KILL_ENTITY) {
                    if (objective.getTargetEntity() == event.getEntityType()) {
                        objective.addProgress(1);

                        // 通知玩家
                        if (objective.isCompleted()) {
                            killer.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                            // 检查任务是否全部完成
                            if (quest.isAllObjectivesCompleted()) {
                                killer.sendMessage(String.format("§6任务 [%s] 已完成！请前往提交。", quest.getName()));
                            }
                        } else {
                            killer.sendMessage(String.format("§e任务进度: %s %s",
                                    objective.getDescription(), objective.getProgressText()));
                        }
                    }
                }
            }
        }
    }

    /**
     * 监听方块破坏
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                if (objective.getType() == QuestObjective.ObjectiveType.BREAK_BLOCK) {
                    if (objective.getTargetMaterial() == event.getBlock().getType()) {
                        objective.addProgress(1);

                        if (objective.isCompleted()) {
                            player.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                            if (quest.isAllObjectivesCompleted()) {
                                player.sendMessage(String.format("§6任务 [%s] 已完成！", quest.getName()));
                            }
                        } else {
                            player.sendMessage(String.format("§e任务进度: %s %s",
                                    objective.getDescription(), objective.getProgressText()));
                        }
                    }
                }
            }
        }
    }

    /**
     * 监听方块放置
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                if (objective.getType() == QuestObjective.ObjectiveType.PLACE_BLOCK) {
                    if (objective.getTargetMaterial() == event.getBlock().getType()) {
                        objective.addProgress(1);

                        if (objective.isCompleted()) {
                            player.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                            if (quest.isAllObjectivesCompleted()) {
                                player.sendMessage(String.format("§6任务 [%s] 已完成！", quest.getName()));
                            }
                        } else {
                            player.sendMessage(String.format("§e任务进度: %s %s",
                                    objective.getDescription(), objective.getProgressText()));
                        }
                    }
                }
            }
        }
    }

    /**
     * 监听钓鱼
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                if (objective.getType() == QuestObjective.ObjectiveType.FISH) {
                    objective.addProgress(1);

                    if (objective.isCompleted()) {
                        player.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                        if (quest.isAllObjectivesCompleted()) {
                            player.sendMessage(String.format("§6任务 [%s] 已完成！", quest.getName()));
                        }
                    } else {
                        player.sendMessage(String.format("§e任务进度: %s %s",
                                objective.getDescription(), objective.getProgressText()));
                    }
                }
            }
        }
    }

    /**
     * 监听物品合成
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                if (objective.getType() == QuestObjective.ObjectiveType.CRAFT_ITEM) {
                    if (objective.getTargetMaterial() == event.getRecipe().getResult().getType()) {
                        int amount = event.getRecipe().getResult().getAmount();
                        objective.addProgress(amount);

                        if (objective.isCompleted()) {
                            player.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                            if (quest.isAllObjectivesCompleted()) {
                                player.sendMessage(String.format("§6任务 [%s] 已完成！", quest.getName()));
                            }
                        } else {
                            player.sendMessage(String.format("§e任务进度: %s %s",
                                    objective.getDescription(), objective.getProgressText()));
                        }
                    }
                }
            }
        }
    }

    /**
     * 监听附魔
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            List<QuestObjective> objectives = quest.getObjectives();

            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);

                if (objective.getType() == QuestObjective.ObjectiveType.ENCHANT) {
                    objective.addProgress(1);

                    if (objective.isCompleted()) {
                        player.sendMessage(String.format("§a任务目标完成: %s", objective.getDescription()));

                        if (quest.isAllObjectivesCompleted()) {
                            player.sendMessage(String.format("§6任务 [%s] 已完成！", quest.getName()));
                        }
                    } else {
                        player.sendMessage(String.format("§e任务进度: %s %s",
                                objective.getDescription(), objective.getProgressText()));
                    }
                }
            }
        }
    }

    /**
     * 手动更新任务进度
     */
    public void updateProgress(UUID playerId, String questId, int objectiveIndex, int amount) {
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        Quest quest = playerQuest.getActiveQuest(questId);

        if (quest == null) {
            return;
        }

        List<QuestObjective> objectives = quest.getObjectives();
        if (objectiveIndex >= 0 && objectiveIndex < objectives.size()) {
            QuestObjective objective = objectives.get(objectiveIndex);
            objective.addProgress(amount);
        }
    }

    /**
     * 检查并完成任务
     */
    public void checkAutoComplete(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (Quest quest : playerQuest.getActiveQuests().values()) {
            if (quest.isAutoComplete() && quest.isAllObjectivesCompleted()) {
                questService.completeQuest(player, quest.getId());
                player.sendMessage(String.format("§a任务 [%s] 自动完成！", quest.getName()));
            }
        }
    }
}
