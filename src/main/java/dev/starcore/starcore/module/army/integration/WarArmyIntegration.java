package dev.starcore.starcore.module.army.integration;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.gui.BattleResultHandler;
import dev.starcore.starcore.module.army.model.ArmyState;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战争与军事系统集成器
 * 负责 WarModule 和 ArmyModule 之间的协作
 */
public final class WarArmyIntegration {

    private final Plugin plugin;
    private final ArmyService armyService;
    private final NationService nationService;
    private final WarService warService;
    private final BattleResultHandler battleResultHandler;
    private final MessageService messages;

    // 正在进行的战斗
    private final Map<UUID, BattleSession> activeBattles = new ConcurrentHashMap<>();
    // 攻城时间记录
    private final Map<UUID, SiegeSession> siegeSessions = new ConcurrentHashMap<>();
    private static final long SIEGE_DURATION_MS = 30 * 60 * 1000; // 30分钟

    public WarArmyIntegration(
        Plugin plugin,
        ArmyService armyService,
        NationService nationService,
        WarService warService,
        BattleResultHandler battleResultHandler,
        MessageService messages
    ) {
        this.plugin = plugin;
        this.armyService = armyService;
        this.nationService = nationService;
        this.warService = warService;
        this.battleResultHandler = battleResultHandler;
        this.messages = messages;

        // 启动攻城检查任务
        startSiegeMonitor();
    }

    /**
     * 战争正式开始的处理
     */
    public void onWarStarted(NationId nationA, NationId nationB) {
        notifyNationOfWar(nationA);
        notifyNationOfWar(nationB);

        // 自动部署防御军队
        autoDeployDefensiveForces(nationA);
        autoDeployDefensiveForces(nationB);
    }

    /**
     * 战争结束的处理
     */
    public void onWarEnded(NationId nationA, NationId nationB) {
        // 通知参战国
        notifyWarEnded(nationA);
        notifyWarEnded(nationB);
    }

    /**
     * 处理宣战
     */
    public void onWarDeclared(NationId nationA, NationId nationB, NationId declaredBy) {
        // 获取宣战国信息
        String declarerName = nationService.nationById(declaredBy)
            .map(Nation::name)
            .orElse("Unknown");

        // 通知双方国家
        notifyNation(declaredBy, "army.war.declared-by", declarerName);
        notifyOtherNation(declaredBy, nationA, nationB, declarerName);
    }

    /**
     * 自动部署防御军队
     */
    private void autoDeployDefensiveForces(NationId nationId) {
        nationService.nationById(nationId).ifPresent(nation -> {
            // 获取所有领土位置
            List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());

            // 将驻守在首都附近的军队设为防御状态
            armies.stream()
                .filter(a -> a.state() == ArmyState.STATIONARY)
                .filter(a -> isNearCapital(a.location(), nation))
                .forEach(a -> a.setState(ArmyState.DEFENDING));

            if (!armies.isEmpty()) {
                notifyNationLeaders(nationId, "army.war.auto-defend");
            }
        });
    }

    /**
     * 检查是否在首都附近
     */
    private boolean isNearCapital(Location loc, Nation nation) {
        if (nation.capitalLocation() == null) {
            return false;
        }
        return loc.getWorld().equals(nation.capitalLocation().getWorld()) &&
               loc.distance(nation.capitalLocation()) < 500;
    }

    /**
     * 开始一场战斗
     */
    public BattleSession startBattle(UUID attackerId, UUID defenderId) {
        Optional<ArmyUnit> attackerOpt = armyService.getArmy(attackerId);
        Optional<ArmyUnit> defenderOpt = armyService.getArmy(defenderId);

        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return null;
        }

        ArmyUnit attacker = attackerOpt.get();
        ArmyUnit defender = defenderOpt.get();

        // 创建战斗会话
        BattleSession session = new BattleSession(
            UUID.randomUUID(),
            attacker,
            defender,
            System.currentTimeMillis()
        );

        activeBattles.put(session.id(), session);

        // 设置军队状态为战斗中
        attacker.setState(ArmyState.ATTACKING);
        defender.setState(ArmyState.DEFENDING);

        // 广播战斗开始
        broadcastBattleStart(session);

        return session;
    }

    /**
     * 结束战斗
     */
    public BattleResult endBattle(UUID battleId) {
        BattleSession session = activeBattles.remove(battleId);
        if (session == null) {
            return null;
        }

        Optional<ArmyUnit> attackerOpt = armyService.getArmy(session.attacker().id());
        Optional<ArmyUnit> defenderOpt = armyService.getArmy(session.defender().id());

        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return null;
        }

        ArmyUnit attacker = attackerOpt.get();
        ArmyUnit defender = defenderOpt.get();

        // 尝试执行战斗
        try {
            BattleResult result = armyService.attack(attacker.id(), defender.id());

            // 处理战斗结果
            battleResultHandler.handleBattleResult(result);

            // 重置军队状态
            if (attacker.isAlive()) {
                attacker.setState(ArmyState.STATIONARY);
            }
            if (defender.isAlive()) {
                defender.setState(ArmyState.STATIONARY);
            }

            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Battle execution failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 开始攻城
     */
    public SiegeSession startSiege(UUID armyId, UUID territoryId) {
        Optional<ArmyUnit> armyOpt = armyService.getArmy(armyId);
        if (armyOpt.isEmpty()) {
            return null;
        }

        ArmyUnit army = armyOpt.get();
        army.setState(ArmyState.SIEGING);

        SiegeSession session = new SiegeSession(
            UUID.randomUUID(),
            army,
            territoryId,
            System.currentTimeMillis()
        );

        siegeSessions.put(session.id(), session);

        // 广播攻城开始
        broadcastSiegeStart(army);

        return session;
    }

    /**
     * 结束攻城
     */
    public void endSiege(UUID siegeId) {
        SiegeSession session = siegeSessions.remove(siegeId);
        if (session != null) {
            armyService.getArmy(session.siegingArmy().id()).ifPresent(army -> {
                army.setState(ArmyState.STATIONARY);
            });
        }
    }

    /**
     * 启动攻城监控任务
     */
    private void startSiegeMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            // 检查过期的攻城
            List<UUID> expiredSieges = new ArrayList<>();
            for (Map.Entry<UUID, SiegeSession> entry : siegeSessions.entrySet()) {
                SiegeSession session = entry.getValue();
                if (now - session.startTime() > SIEGE_DURATION_MS) {
                    expiredSieges.add(entry.getKey());

                    // 触发占领
                    handleSiegeCompletion(session);
                }
            }

            // 移除过期的攻城
            expiredSieges.forEach(siegeSessions::remove);

        }, 20L * 60, 20L * 60); // 每分钟检查
    }

    /**
     * 处理攻城完成
     */
    private void handleSiegeCompletion(SiegeSession session) {
        // 获取领土
        dev.starcore.starcore.territory.TerritoryService territoryService =
            Bukkit.getServer().getServicesManager()
                .load(dev.starcore.starcore.territory.TerritoryService.class);

        if (territoryService == null) {
            return;
        }

        var territory = territoryService.getTerritory(session.territoryId());
        if (territory == null) {
            return;
        }

        UUID oldOwner = territory.getOwnerId();

        // 占领
        territoryService.transferOwnership(territory.getId(), session.siegingArmy().nationId());

        // 广播
        broadcastSiegeEnd(session, territory.getName(), true);

        // 通知原所有者
        if (oldOwner != null) {
            notifyNationMembers(new NationId(oldOwner),
                Component.text("你们的领土 [" + territory.getName() + "] 被敌军占领!", NamedTextColor.RED));
        }

        // 重置军队状态
        armyService.getArmy(session.siegingArmy().id()).ifPresent(army -> {
            army.setState(ArmyState.STATIONARY);
        });
    }

    // ==================== 通知方法 ====================

    private void notifyNationOfWar(NationId nationId) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("army.war.started"),
                        NamedTextColor.DARK_RED
                    ));
                }
            });
        });
    }

    private void notifyWarEnded(NationId nationId) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("army.war.ended"),
                        NamedTextColor.GREEN
                    ));
                }
            });
        });
    }

    private void notifyNation(NationId nationId, String key, String... args) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format(key, (Object[]) args),
                        NamedTextColor.GOLD
                    ));
                }
            });
        });
    }

    private void notifyOtherNation(NationId declarer, NationId nationA, NationId nationB, String declarerName) {
        NationId target = declarer.equals(nationA) ? nationB : nationA;
        notifyNation(target, "army.war.declared-against", declarerName);
    }

    private void notifyNationLeaders(NationId nationId, String key) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline() &&
                    (nation.isFounder(player.getUniqueId()) || "Leader".equals(member.rank()))) {
                    player.sendMessage(Component.text(
                        messages.format(key),
                        NamedTextColor.GOLD
                    ));
                }
            });
        });
    }

    private void notifyNationMembers(NationId nationId, Component message) {
        nationService.nationById(nationId).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            });
        });
    }

    private void broadcastBattleStart(BattleSession session) {
        // 通知攻击方
        nationService.nationById(new NationId(session.attacker().nationId())).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("army.battle.started"),
                        NamedTextColor.YELLOW
                    ));
                }
            });
        });

        // 通知防守方
        nationService.nationById(new NationId(session.defender().nationId())).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("army.battle.under-attack"),
                        NamedTextColor.RED
                    ));
                }
            });
        });
    }

    private void broadcastSiegeStart(ArmyUnit army) {
        nationService.nationById(new NationId(army.nationId())).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("army.siege.started", army.id().toString().substring(0, 8)),
                        NamedTextColor.YELLOW
                    ));
                }
            });
        });
    }

    private void broadcastSiegeEnd(SiegeSession session, String territoryName, boolean success) {
        nationService.nationById(new NationId(session.siegingArmy().nationId())).ifPresent(nation -> {
            nation.members().forEach(member -> {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    if (success) {
                        player.sendMessage(Component.text(
                            messages.format("army.siege.success", territoryName),
                            NamedTextColor.GREEN
                        ));
                    } else {
                        player.sendMessage(Component.text(
                            messages.format("army.siege.failed", territoryName),
                            NamedTextColor.RED
                        ));
                    }
                }
            });
        });
    }

    // ==================== 内部类 ====================

    /**
     * 战斗会话
     */
    public record BattleSession(
        UUID id,
        ArmyUnit attacker,
        ArmyUnit defender,
        long startTime
    ) {}

    /**
     * 攻城会话
     */
    public record SiegeSession(
        UUID id,
        ArmyUnit siegingArmy,
        UUID territoryId,
        long startTime
    ) {}
}
