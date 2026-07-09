package dev.starcore.starcore.module.army.gui;

import dev.starcore.starcore.foundation.animation.ParticleEffectManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战斗结果处理器
 * 处理战斗结束后的各种事件和通知
 */
public final class BattleResultHandler {

    private final ArmyService armyService;
    private final NationService nationService;
    private final MessageService messages;
    private final ParticleEffectManager particleManager;
    private final SoundFeedbackManager soundManager;
    private final Plugin plugin;

    // 战斗报告记录
    private final Map<UUID, List<BattleReportEntry>> playerBattleReports = new ConcurrentHashMap<>();
    private static final int MAX_REPORTS_PER_PLAYER = 50;

    public BattleResultHandler(
        ArmyService armyService,
        NationService nationService,
        MessageService messages,
        ParticleEffectManager particleManager,
        SoundFeedbackManager soundManager,
        Plugin plugin
    ) {
        this.armyService = armyService;
        this.nationService = nationService;
        this.messages = messages;
        this.particleManager = particleManager;
        this.soundManager = soundManager;
        this.plugin = plugin;
    }

    /**
     * 处理战斗结果
     */
    public void handleBattleResult(BattleResult result) {
        // 1. 记录战斗报告
        recordBattleReport(result);

        // 2. 播放战斗动画效果
        playBattleEffects(result);

        // 3. 通知参战国成员
        broadcastToNations(result);

        // 4. 检查是否需要结束战争
        checkWarEnd(result);
    }

    /**
     * 播放战斗动画效果
     */
    private void playBattleEffects(BattleResult result) {
        // 获取涉及国家的在线玩家并播放效果
        armyService.getArmy(result.attackerId()).ifPresent(attacker -> {
            playEffectsForNation(attacker.nationId(), result, true);
        });

        armyService.getArmy(result.defenderId()).ifPresent(defender -> {
            playEffectsForNation(defender.nationId(), result, false);
        });
    }

    /**
     * 为国家成员播放效果
     */
    private void playEffectsForNation(UUID nationId, BattleResult result, boolean isAttacker) {
        nationService.nationById(new NationId(nationId)).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    // 根据战斗结果播放不同效果
                    switch (result.outcome()) {
                        case ATTACKER_VICTORY -> {
                            if (isAttacker) {
                                // 攻击方获胜 - 播放胜利效果
                                particleManager.playBattleVictoryFull(player);
                                soundManager.playVictory(player);
                            } else {
                                // 防守方失败 - 播放失败效果
                                particleManager.playBattleDefeatFull(player);
                                soundManager.playDefeat(player);
                            }
                        }
                        case DEFENDER_VICTORY -> {
                            if (!isAttacker) {
                                // 防守方获胜 - 播放胜利效果
                                particleManager.playBattleVictoryFull(player);
                                soundManager.playVictory(player);
                            } else {
                                // 攻击方失败 - 播放失败效果
                                particleManager.playBattleDefeatFull(player);
                                soundManager.playDefeat(player);
                            }
                        }
                        case DRAW -> {
                            // 平局 - 播放普通战斗效果
                            particleManager.playAttackSlash(player);
                            soundManager.play(player, SoundFeedbackManager.SoundType.EXPLOSION);
                        }
                        case BOTH_DESTROYED -> {
                            // 同归于尽 - 播放特殊效果
                            particleManager.playSiegeImpact(player);
                            soundManager.play(player, SoundFeedbackManager.SoundType.EXPLOSION, 0.8f, 0.7f);
                        }
                    }

                    // 根据伤亡情况播放额外效果
                    if (result.attackerCasualties() > 10 || result.defenderCasualties() > 10) {
                        // 大规模战斗 - 播放战场迷雾
                        particleManager.playBattleCloud(player);
                    }
                }
            });
        });
    }

    /**
     * 向参战国成员发送战斗报告
     */
    private void broadcastToNations(BattleResult result) {
        Component report = buildBattleReport(result);

        // 通知攻击方国家成员
        armyService.getArmy(result.attackerId()).ifPresent(attacker -> {
            notifyNationMembers(attacker.nationId(), report, result, true);
        });

        // 通知防守方国家成员
        armyService.getArmy(result.defenderId()).ifPresent(defender -> {
            notifyNationMembers(defender.nationId(), report, result, false);
        });
    }

    /**
     * 通知国家成员
     */
    private void notifyNationMembers(UUID nationId, Component report, BattleResult result, boolean isAttacker) {
        nationService.nationById(new NationId(nationId)).ifPresent(nation -> {
            Component header = Component.text("[战斗报告]", NamedTextColor.GOLD);

            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(header);
                    player.sendMessage(report);

                    // 添加额外状态信息
                    if (isAttacker) {
                        switch (result.outcome()) {
                            case ATTACKER_VICTORY -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-attacked.victory"),
                                NamedTextColor.GREEN
                            ));
                            case DEFENDER_VICTORY -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-attacked.defeat"),
                                NamedTextColor.RED
                            ));
                            case DRAW -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-attacked.draw"),
                                NamedTextColor.YELLOW
                            ));
                            case BOTH_DESTROYED -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-attacked.mutual"),
                                NamedTextColor.DARK_RED
                            ));
                        }
                    } else {
                        switch (result.outcome()) {
                            case DEFENDER_VICTORY -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-defended.victory"),
                                NamedTextColor.GREEN
                            ));
                            case ATTACKER_VICTORY -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-defended.defeat"),
                                NamedTextColor.RED
                            ));
                            case DRAW -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-defended.draw"),
                                NamedTextColor.YELLOW
                            ));
                            case BOTH_DESTROYED -> player.sendMessage(Component.text(
                                messages.format("army.battle.you-defended.mutual"),
                                NamedTextColor.DARK_RED
                            ));
                        }
                    }
                }
            });
        });
    }

    /**
     * 检查是否需要结束战争
     */
    private void checkWarEnd(BattleResult result) {
        // 检查是否一方所有军队都被消灭
        NationId attackerNation = getArmyNation(result.attackerId());
        NationId defenderNation = getArmyNation(result.defenderId());

        if (attackerNation == null || defenderNation == null) {
            return;
        }

        // 如果防守方所有军队都被消灭
        if (isNationDefenseless(defenderNation)) {
            // 向攻击方领导发送提示
            notifyNationLeaders(attackerNation, "army.war.enemy-defenseless");
        }

        // 如果攻击方所有军队都被消灭
        if (isNationDefenseless(attackerNation)) {
            notifyNationLeaders(defenderNation, "army.war.attacker-defenseless");
        }
    }

    private NationId getArmyNation(UUID armyId) {
        return armyService.getArmy(armyId)
            .map(a -> new NationId(a.nationId()))
            .orElse(null);
    }

    private boolean isNationDefenseless(NationId nationId) {
        List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());
        return armies.isEmpty();
    }

    /**
     * 通知国家领导
     */
    private void notifyNationLeaders(NationId nationId, String messageKey) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline() &&
                    (nation.isFounder(player.getUniqueId()) || "Leader".equals(member.rank()))) {
                    player.sendMessage(Component.text(
                        messages.format(messageKey),
                        NamedTextColor.GOLD
                    ));
                }
            });
        });
    }

    /**
     * 记录战斗报告
     */
    private void recordBattleReport(BattleResult result) {
        // 记录到攻击方
        addReport(result.attackerId(), new BattleReportEntry(result, true));
        // 记录到防守方
        addReport(result.defenderId(), new BattleReportEntry(result, false));
    }

    private void addReport(UUID nationId, BattleReportEntry entry) {
        playerBattleReports.computeIfAbsent(nationId, k -> new ArrayList<>())
            .add(0, entry); // 添加到列表开头

        // 限制记录数量
        List<BattleReportEntry> reports = playerBattleReports.get(nationId);
        if (reports.size() > MAX_REPORTS_PER_PLAYER) {
            reports.remove(reports.size() - 1);
        }
    }

    /**
     * 获取玩家的战斗报告
     */
    public List<BattleReportEntry> getPlayerReports(UUID nationId) {
        return playerBattleReports.getOrDefault(nationId, Collections.emptyList());
    }

    /**
     * 获取最近 N 场战斗报告
     */
    public List<BattleReportEntry> getRecentReports(UUID nationId, int count) {
        List<BattleReportEntry> reports = playerBattleReports.getOrDefault(nationId, Collections.emptyList());
        return reports.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * 构建战斗报告组件
     */
    private Component buildBattleReport(BattleResult result) {
        TextComponent.Builder builder = Component.text();

        // 标题栏
        builder.append(Component.text("================================", NamedTextColor.GOLD));
        builder.append(Component.text("\n"));
        builder.append(Component.text("         战  斗  报  告         ", NamedTextColor.GOLD));
        builder.append(Component.text("\n"));
        builder.append(Component.text("================================", NamedTextColor.GOLD));
        builder.append(Component.text("\n"));

        // 战斗结果
        NamedTextColor resultColor = switch (result.outcome()) {
            case ATTACKER_VICTORY -> NamedTextColor.GREEN;
            case DEFENDER_VICTORY -> NamedTextColor.RED;
            case DRAW -> NamedTextColor.YELLOW;
            case BOTH_DESTROYED -> NamedTextColor.DARK_RED;
        };

        builder.append(Component.text("结果: ", NamedTextColor.GRAY));
        builder.append(Component.text(result.description(), resultColor));
        builder.append(Component.text("\n"));
        builder.append(Component.text("--------------------------------", NamedTextColor.DARK_GRAY));
        builder.append(Component.text("\n"));

        // 攻击方信息
        builder.append(Component.text("[攻击方]", NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  军队ID: " + result.attackerId().toString().substring(0, 8), NamedTextColor.GRAY));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  造成伤害: ", NamedTextColor.GRAY));
        builder.append(Component.text(String.format("%.1f", result.attackerDamage()), NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  伤亡人数: ", NamedTextColor.GRAY));
        builder.append(Component.text(result.attackerCasualties() + " 士兵", NamedTextColor.YELLOW));
        builder.append(Component.text("\n"));
        builder.append(Component.text("--------------------------------", NamedTextColor.DARK_GRAY));
        builder.append(Component.text("\n"));

        // 防守方信息
        builder.append(Component.text("[防守方]", NamedTextColor.BLUE));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  军队ID: " + result.defenderId().toString().substring(0, 8), NamedTextColor.GRAY));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  受到伤害: ", NamedTextColor.GRAY));
        builder.append(Component.text(String.format("%.1f", result.defenderDamage()), NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  伤亡人数: ", NamedTextColor.GRAY));
        builder.append(Component.text(result.defenderCasualties() + " 士兵", NamedTextColor.YELLOW));
        builder.append(Component.text("\n"));
        builder.append(Component.text("================================", NamedTextColor.DARK_GRAY));

        return builder.build();
    }

    /**
     * 清理过期数据
     */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24小时前

        // 清理旧的战报（实际应该按时间清理，但这里简化处理）
        playerBattleReports.values().forEach(list -> {
            list.removeIf(entry -> entry.timestamp() < cutoff);
        });
    }

    // ==================== 内部类 ====================

    /**
     * 战斗报告条目
     */
    public record BattleReportEntry(BattleResult result, boolean wasAttacker, long timestamp) {
        public BattleReportEntry(BattleResult result, boolean wasAttacker) {
            this(result, wasAttacker, System.currentTimeMillis());
        }
    }

    // ==================== 动画播放方法 ====================

    /**
     * 为玩家播放攻击动画
     */
    public void playAttackAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playAttackSlash(player);
        }
        if (soundManager != null) {
            soundManager.play(player, SoundFeedbackManager.SoundType.EXPLOSION, 0.5f, 1.0f);
        }
    }

    /**
     * 为玩家播放防御动画
     */
    public void playDefenseAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playShieldBlock(player);
        }
        if (soundManager != null) {
            soundManager.play(player, SoundFeedbackManager.SoundType.EQUIP, 0.4f, 1.2f);
        }
    }

    /**
     * 为玩家播放暴击动画
     */
    public void playCriticalAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playCriticalHit(player);
        }
        if (soundManager != null) {
            soundManager.play(player, SoundFeedbackManager.SoundType.MAGIC, 0.6f, 1.5f);
        }
    }

    /**
     * 为玩家播放冲锋动画
     */
    public void playChargeAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playCavalryCharge(player);
        }
        if (soundManager != null) {
            soundManager.play(player, SoundFeedbackManager.SoundType.EXPLOSION, 0.6f, 0.9f);
        }
    }

    /**
     * 为玩家播放战吼动画
     */
    public void playWarCryAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playWarCry(player);
        }
        if (soundManager != null) {
            soundManager.play(player, SoundFeedbackManager.SoundType.ALERT, 0.7f, 1.3f);
        }
    }

    /**
     * 为玩家播放胜利动画
     */
    public void playVictoryAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playBattleVictoryFull(player);
        }
        if (soundManager != null) {
            soundManager.playVictory(player);
        }
    }

    /**
     * 为玩家播放失败动画
     */
    public void playDefeatAnimation(Player player) {
        if (particleManager != null) {
            particleManager.playBattleDefeatFull(player);
        }
        if (soundManager != null) {
            soundManager.playDefeat(player);
        }
    }
}
