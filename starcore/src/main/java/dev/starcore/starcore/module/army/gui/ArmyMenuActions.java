package dev.starcore.starcore.module.army.gui;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import dev.starcore.starcore.foundation.animation.ParticleEffectManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.*;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 军队 GUI 操作处理类
 * 负责处理所有军队管理的核心逻辑，包含战斗动画反馈
 */
public final class ArmyMenuActions {

    private final ArmyService armyService;
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final WarService warService;
    private final TerritoryService territoryService;
    private final MessageService messages;
    private final ParticleEffectManager particleManager;

    // 玩家等待输入的状态
    private final Map<UUID, PlayerInputState> playerInputStates = new ConcurrentHashMap<>();

    public ArmyMenuActions(
        ArmyService armyService,
        NationService nationService,
        DiplomacyService diplomacyService,
        WarService warService,
        TerritoryService territoryService,
        MessageService messages
    ) {
        this(armyService, nationService, diplomacyService, warService, territoryService, messages, null);
    }

    public ArmyMenuActions(
        ArmyService armyService,
        NationService nationService,
        DiplomacyService diplomacyService,
        WarService warService,
        TerritoryService territoryService,
        MessageService messages,
        ParticleEffectManager particleManager
    ) {
        this.armyService = armyService;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.territoryService = territoryService;
        this.messages = messages;
        this.particleManager = particleManager;
    }

    /**
     * 召回所有军队到首都
     */
    public void recallAll(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            sendError(player, "army.not-in-nation");
            return;
        }

        Nation nation = nationOpt.get();
        List<ArmyUnit> armies = armyService.getNationArmies(nation.id().value());
        Location capitalLoc = nation.capitalLocation();

        if (capitalLoc == null) {
            // 没有首都，尝试获取第一个城镇位置
            Map<String, Location> townLocs = nation.getTownLocations();
            if (!townLocs.isEmpty()) {
                capitalLoc = townLocs.values().iterator().next();
            }
        }

        if (capitalLoc == null) {
            sendError(player, "army.recall.no-capital");
            return;
        }

        int recalled = 0;
        int failed = 0;

        for (ArmyUnit army : armies) {
            if (army.state() == ArmyState.STATIONARY) {
                continue; // 驻扎中的军队不召回
            }

            try {
                armyService.moveArmy(army.id(), capitalLoc);
                recalled++;
            } catch (Exception e) {
                failed++;
            }
        }

        if (recalled > 0) {
            player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.recall.success", recalled), NamedTextColor.GREEN)));
        }
        if (failed > 0) {
            player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.recall.failed", failed), NamedTextColor.RED)));
        }
        if (recalled == 0 && failed == 0) {
            player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.recall.no-armies"), NamedTextColor.YELLOW)));
        }
    }

    /**
     * 改变军队状态
     */
    public void changeArmyState(Player player, ArmyUnit army, ArmyState newState) {
        if (army == null) {
            sendError(player, "army.error.not-found");
            return;
        }

        ArmyState currentState = army.state();

        // 检查状态转换是否合法
        if (!isValidStateTransition(currentState, newState)) {
            sendError(player, "army.gui.state.invalid-transition");
            return;
        }

        // 特殊状态处理
        switch (newState) {
            case SIEGING -> {
                // 攻城模式 - 检查是否在敌对领土附近
                NationId armyNationId = new NationId(army.nationId());
                // 检查是否有交战国
                boolean hasEnemies = warService.activeWarsOf(armyNationId).stream()
                    .anyMatch(war -> warService.atWar(armyNationId, war.left().equals(armyNationId) ? war.right() : war.left()));
                if (!hasEnemies) {
                    sendError(player, "army.siege.not-at-war");
                    return;
                }
            }
            case MARCHING -> {
                // 行军需要先选择目标
                playerInputStates.put(player.getUniqueId(), new PlayerInputState(
                    InputType.MOVE_TARGET,
                    army,
                    null
                ));
                player.closeInventory();
                player.sendMessage(Component.text()
                    .append(Component.text(messages.format("army.gui.move.select-target"), NamedTextColor.YELLOW)));
                player.sendMessage(Component.text()
                    .append(Component.text("请在聊天框输入目标坐标 (格式: x,y,z)", NamedTextColor.GRAY)));
                return;
            }
            default -> {
                // 其他状态直接切换
                army.setState(newState);
                player.sendMessage(Component.text()
                    .append(Component.text(messages.format("army.gui.state.changed", newState.key()), NamedTextColor.GREEN)));
            }
        }
    }

    /**
     * 处理移动目标输入
     */
    public boolean handleMoveTargetInput(Player player, String input) {
        PlayerInputState state = playerInputStates.get(player.getUniqueId());
        if (state == null || state.type() != InputType.MOVE_TARGET) {
            return false;
        }

        ArmyUnit army = state.army();
        Location destination = parseLocation(input, player.getLocation());

        if (destination == null) {
            sendError(player, "army.gui.move.invalid-coords");
            return true;
        }

        try {
            armyService.moveArmy(army.id(), destination);
            player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.moved", army.id().toString().substring(0, 8)), NamedTextColor.GREEN)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendError(player, e.getMessage());
        }

        playerInputStates.remove(player.getUniqueId());
        return true;
    }

    /**
     * 处理进攻目标选择
     */
    public void selectAttackTarget(Player player, ArmyUnit army, UUID targetArmyId) {
        Optional<ArmyUnit> targetOpt = armyService.getArmy(targetArmyId);
        if (targetOpt.isEmpty()) {
            sendError(player, "army.error.target-not-found");
            return;
        }

        ArmyUnit target = targetOpt.get();

        // 检查是否敌对
        NationId attackerNationId = new NationId(army.nationId());
        NationId defenderNationId = new NationId(target.nationId());

        if (!warService.atWar(attackerNationId, defenderNationId)) {
            sendError(player, "army.attack.not-at-war");
            return;
        }

        // 检查距离
        double distance = army.location().distance(target.location());
        if (distance > 100) {
            sendError(player, "army.attack.too-far");
            return;
        }

        try {
            BattleResult result = armyService.attack(army.id(), targetArmyId);
            onBattleResult(player, result);
        } catch (Exception e) {
            sendError(player, e.getMessage());
        }
    }

    /**
     * 发起进攻 - 打开敌对目标选择
     */
    public void initiateAttack(Player player, ArmyUnit army) {
        NationId armyNationId = new NationId(army.nationId());

        // 检查是否有正在交战的国家
        Collection<dev.starcore.starcore.module.war.WarSnapshot> wars = warService.activeWarsOf(armyNationId);
        if (wars.isEmpty()) {
            sendError(player, "army.attack.no-enemies");
            return;
        }

        // 设置输入状态并让玩家选择目标
        playerInputStates.put(player.getUniqueId(), new PlayerInputState(
            InputType.ATTACK_TARGET,
            army,
            null
        ));

        player.closeInventory();

        // 显示可选目标列表
        player.sendMessage(Component.text("=== 选择进攻目标 ===", NamedTextColor.RED));
        player.sendMessage(Component.text("请在聊天框输入目标军队ID的前8位", NamedTextColor.YELLOW));

        // 显示附近敌对军队
        List<ArmyUnit> nearbyEnemies = armyService.getArmiesNear(army.location(), 100).stream()
            .filter(a -> !a.nationId().equals(army.nationId()))
            .filter(a -> warService.atWar(armyNationId, new NationId(a.nationId())))
            .collect(Collectors.toList());

        if (nearbyEnemies.isEmpty()) {
            player.sendMessage(Component.text("附近没有可攻击的目标", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("附近敌对军队:", NamedTextColor.RED));
            for (ArmyUnit enemy : nearbyEnemies) {
                String shortId = enemy.id().toString().substring(0, 8);
                player.sendMessage(Component.text()
                    .append(Component.text("  [" + shortId + "] ", NamedTextColor.GOLD))
                    .append(Component.text(enemy.type().key() + " - " + enemy.soldiers() + "士兵", NamedTextColor.GRAY)));
            }
        }
    }

    /**
     * 处理进攻目标ID输入
     */
    public boolean handleAttackTargetInput(Player player, String input) {
        PlayerInputState state = playerInputStates.get(player.getUniqueId());
        if (state == null || state.type() != InputType.ATTACK_TARGET) {
            return false;
        }

        ArmyUnit army = state.army();
        String targetIdPrefix = input.trim();

        if (targetIdPrefix.length() < 8) {
            sendError(player, "army.gui.attack.invalid-id");
            return true;
        }

        // 查找匹配的军队
        List<ArmyUnit> nearbyEnemies = armyService.getArmiesNear(army.location(), 100).stream()
            .filter(a -> !a.nationId().equals(army.nationId()))
            .collect(Collectors.toList());

        UUID targetId = null;
        for (ArmyUnit enemy : nearbyEnemies) {
            if (enemy.id().toString().startsWith(targetIdPrefix)) {
                targetId = enemy.id();
                break;
            }
        }

        if (targetId == null) {
            sendError(player, "army.gui.attack.target-not-found");
            return true;
        }

        // 执行攻击
        selectAttackTarget(player, army, targetId);
        playerInputStates.remove(player.getUniqueId());
        return true;
    }

    /**
     * 处理解散确认
     */
    public boolean handleDisbandConfirm(Player player, String input, AnvilConfirmCallback callback) {
        PlayerInputState state = playerInputStates.get(player.getUniqueId());
        if (state == null || state.type() != InputType.DISBAND_CONFIRM) {
            return false;
        }

        ArmyUnit army = state.army();

        // 检查确认码
        String confirmCode = army.id().toString().substring(0, 8);
        if (!input.trim().equalsIgnoreCase(confirmCode)) {
            sendError(player, "army.gui.disband.wrong-code");
            return true;
        }

        // 执行解散
        armyService.disbandArmy(army.id());
        player.sendMessage(Component.text()
            .append(Component.text(messages.format("army.disbanded", confirmCode), NamedTextColor.GREEN)));

        if (callback != null) {
            callback.onConfirmed();
        }

        playerInputStates.remove(player.getUniqueId());
        return true;
    }

    /**
     * 请求解散确认
     */
    public void requestDisbandConfirm(Player player, ArmyUnit army, AnvilConfirmCallback callback) {
        String confirmCode = army.id().toString().substring(0, 8);

        playerInputStates.put(player.getUniqueId(), new PlayerInputState(
            InputType.DISBAND_CONFIRM,
            army,
            null
        ));

        player.closeInventory();

        // 发送确认提示
        player.sendMessage(Component.text("=== 解散确认 ===", NamedTextColor.RED));
        player.sendMessage(Component.text("即将解散: " + army.type().key() + " [" + confirmCode + "]", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("请在聊天框输入 [" + confirmCode + "] 确认解散", NamedTextColor.GRAY));

        if (callback != null) {
            callback.onRequested();
        }
    }

    /**
     * 处理战斗结果
     */
    public void onBattleResult(Player player, BattleResult result) {
        Component report = buildBattleReport(result);
        player.sendMessage(report);

        // 播放战斗动画效果
        playBattleEffects(player, result);

        // 根据结果发送额外消息
        switch (result.outcome()) {
            case ATTACKER_VICTORY -> {
                player.sendMessage(Component.text()
                    .append(Component.text(messages.format("army.battle.victory"), NamedTextColor.GREEN)));
                // 播放胜利效果
                playVictoryEffects(player);
            }
            case DEFENDER_VICTORY -> {
                player.sendMessage(Component.text()
                    .append(Component.text(messages.format("army.battle.defeat"), NamedTextColor.RED)));
                // 播放失败效果
                playDefeatEffects(player);
            }
            case DRAW -> player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.battle.draw"), NamedTextColor.YELLOW)));
            case BOTH_DESTROYED -> player.sendMessage(Component.text()
                .append(Component.text(messages.format("army.battle.mutual"), NamedTextColor.DARK_RED)));
        }
    }

    /**
     * 播放战斗动画效果
     */
    private void playBattleEffects(Player player, BattleResult result) {
        if (particleManager == null) return;

        Location loc = player.getLocation();

        // 根据伤害量决定效果强度
        double totalDamage = result.attackerDamage() + result.defenderDamage();
        if (totalDamage > 50) {
            // 大规模战斗 - 战场迷雾效果
            particleManager.playBattleCloud(player);
            // 血迹效果
            particleManager.playBloodSplatter(player);
        } else if (totalDamage > 20) {
            // 中等战斗
            particleManager.playAttackSlash(player);
        } else {
            // 小规模冲突
            particleManager.playPreset(player, ParticleEffectManager.ParticlePreset.CRITICAL);
        }
    }

    /**
     * 播放胜利动画效果
     */
    private void playVictoryEffects(Player player) {
        if (particleManager == null) return;

        // 播放胜利爆发效果
        particleManager.playVictoryBurst(player);

        // 播放战吼效果
        particleManager.playWarCry(player);

        // 播放战旗飘扬效果
        particleManager.playBattleFlag(player);
    }

    /**
     * 播放失败动画效果
     */
    private void playDefeatEffects(Player player) {
        if (particleManager == null) return;

        // 播放失败消散效果
        particleManager.playDefeatFade(player);

        // 播放投降旗帜效果
        particleManager.playSurrenderFlag(player);
    }

    /**
     * 播放军队移动动画
     */
    public void playMoveAnimation(Player player) {
        if (particleManager == null) return;
        particleManager.playMarchingBoots(player);
    }

    /**
     * 播放军队冲锋动画
     */
    public void playChargeAnimation(Player player) {
        if (particleManager == null) return;
        particleManager.playCavalryCharge(player);
    }

    /**
     * 播放攻城动画
     */
    public void playSiegeAnimation(Player player) {
        if (particleManager == null) return;
        particleManager.playSiegeImpact(player);
    }

    /**
     * 向相关玩家广播战斗结果
     */
    public void broadcastBattleResult(BattleResult result) {
        // 获取攻击方和防守方所属的玩家
        armyService.getArmy(result.attackerId()).ifPresent(attacker -> {
            nationService.nationOf(attacker.nationId()).ifPresent(nation -> {
                nation.members().forEach(member -> {
                    Player player = Bukkit.getPlayer(member.playerId());
                    if (player != null && player.isOnline()) {
                        onBattleResult(player, result);
                    }
                });
            });
        });

        armyService.getArmy(result.defenderId()).ifPresent(defender -> {
            nationService.nationOf(defender.nationId()).ifPresent(nation -> {
                nation.members().forEach(member -> {
                    Player player = Bukkit.getPlayer(member.playerId());
                    if (player != null && player.isOnline()) {
                        onBattleResult(player, result);
                    }
                });
            });
        });
    }

    /**
     * 检查国家间是否为战争状态
     */
    public boolean areAtWar(NationId nationId1, NationId nationId2) {
        return warService.atWar(nationId1, nationId2);
    }

    /**
     * 获取所有敌对国家
     */
    public List<Nation> getEnemyNations(NationId nationId) {
        return warService.activeWarsOf(nationId).stream()
            .map(war -> {
                NationId enemyId = war.left().equals(nationId) ? war.right() : war.left();
                return nationService.nationById(enemyId).orElse(null);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 检查状态转换是否合法
     */
    private boolean isValidStateTransition(ArmyState from, ArmyState to) {
        // 可以从任何状态转入防御
        if (to == ArmyState.DEFENDING) {
            return true;
        }

        // 驻扎只能转入行军或防御
        if (from == ArmyState.STATIONARY) {
            return to == ArmyState.MARCHING || to == ArmyState.DEFENDING;
        }

        // 行军只能转入驻扎、攻城或进攻
        if (from == ArmyState.MARCHING) {
            return to == ArmyState.STATIONARY || to == ArmyState.SIEGING || to == ArmyState.ATTACKING;
        }

        // 攻城/进攻只能转入驻扎或防御
        if (from == ArmyState.SIEGING || from == ArmyState.ATTACKING) {
            return to == ArmyState.STATIONARY || to == ArmyState.DEFENDING;
        }

        // 防御可以转入任何状态
        return true;
    }

    /**
     * 解析坐标字符串
     */
    private Location parseLocation(String input, Location defaultLoc) {
        try {
            String[] parts = input.trim().split(",");
            if (parts.length >= 3) {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                return new Location(defaultLoc.getWorld(), x, y, z);
            }
        } catch (NumberFormatException e) {
            // 忽略
        }
        return null;
    }

    /**
     * 构建战斗报告
     */
    private Component buildBattleReport(BattleResult result) {
        TextComponent.Builder builder = Component.text();

        builder.append(Component.text("========== 战斗报告 ==========", NamedTextColor.GOLD));
        builder.append(Component.text("\n"));

        // 结果
        NamedTextColor outcomeColor = switch (result.outcome()) {
            case ATTACKER_VICTORY -> NamedTextColor.GREEN;
            case DEFENDER_VICTORY -> NamedTextColor.RED;
            case DRAW -> NamedTextColor.YELLOW;
            case BOTH_DESTROYED -> NamedTextColor.DARK_RED;
        };
        builder.append(Component.text("结果: " + result.description(), outcomeColor));
        builder.append(Component.text("\n\n"));

        // 攻击方
        builder.append(Component.text("--- 攻击方 ---", NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  ID: " + result.attackerId().toString().substring(0, 8), NamedTextColor.GRAY));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  造成伤害: " + String.format("%.1f", result.attackerDamage()), NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  伤亡: " + result.attackerCasualties() + " 士兵", NamedTextColor.YELLOW));
        builder.append(Component.text("\n\n"));

        // 防守方
        builder.append(Component.text("--- 防守方 ---", NamedTextColor.BLUE));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  ID: " + result.defenderId().toString().substring(0, 8), NamedTextColor.GRAY));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  受到伤害: " + String.format("%.1f", result.defenderDamage()), NamedTextColor.RED));
        builder.append(Component.text("\n"));
        builder.append(Component.text("  伤亡: " + result.defenderCasualties() + " 士兵", NamedTextColor.YELLOW));
        builder.append(Component.text("\n"));

        builder.append(Component.text("==============================", NamedTextColor.GOLD));

        return builder.build();
    }

    private void sendError(Player player, String key) {
        player.sendMessage(Component.text(messages.format(key), NamedTextColor.RED));
    }

    /**
     * 检查玩家是否在等待特定类型的输入
     */
    public boolean isWaitingForInput(Player player, InputType type) {
        PlayerInputState state = playerInputStates.get(player.getUniqueId());
        return state != null && state.type() == type;
    }

    /**
     * 清除玩家的输入状态
     */
    public void clearInputState(Player player) {
        playerInputStates.remove(player.getUniqueId());
    }

    // ==================== 内部类 ====================

    /**
     * 玩家输入状态
     */
    public record PlayerInputState(
        InputType type,
        ArmyUnit army,
        Object data
    ) {}

    /**
     * 输入类型
     */
    public enum InputType {
        MOVE_TARGET,       // 移动目标选择
        ATTACK_TARGET,    // 进攻目标选择
        DISBAND_CONFIRM   // 解散确认
    }

    /**
     * 确认回调接口
     */
    @FunctionalInterface
    public interface AnvilConfirmCallback {
        void onConfirmed();
        default void onRequested() {}
    }
}
