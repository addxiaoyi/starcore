package dev.starcore.starcore.module.military;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.situation.WarSituationService;
import dev.starcore.starcore.war.BattlefieldService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 军事指挥服务实现
 * 提供统一的军事指挥功能
 */
public class MilitaryServiceImpl implements MilitaryService, StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "military",
        "Military Command Center",
        ModuleLayer.MODULE,
        List.of("army", "war", "diplomacy"),
        List.of(MilitaryService.class),
        "Unified military command center with real-time battle status preview"
    );

    private final NationService nationService;
    private final ArmyService armyService;
    private final WarService warService;
    private final WarSituationService situationService;
    private final MilitaryAllianceService allianceService;
    private final BattlefieldService battlefieldService;
    private final StarCoreScheduler scheduler;

    public MilitaryServiceImpl(StarCoreContext context) {
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        this.armyService = context.serviceRegistry().find(ArmyService.class).orElse(null);
        this.warService = context.serviceRegistry().find(WarService.class).orElse(null);
        this.situationService = context.serviceRegistry().find(WarSituationService.class).orElse(null);
        this.allianceService = context.serviceRegistry().find(MilitaryAllianceService.class).orElse(null);
        this.battlefieldService = findBattlefieldService(context);
        this.scheduler = context.scheduler();
    }

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public MilitaryOverview getOverview(NationId nationId) {
        if (nationService == null) {
            return new MilitaryOverview(nationId, 0, 0, 0, 0, 0, 0.0, System.currentTimeMillis());
        }

        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return new MilitaryOverview(nationId, 0, 0, 0, 0, 0, 0.0, System.currentTimeMillis());
        }

        // 统计军队数量
        int totalArmies = 0;
        double militaryPower = 0.0;
        if (armyService != null) {
            List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());
            totalArmies = armies.size();
            // 使用 soldiers() 作为军事力量指标
            militaryPower = armies.stream()
                .mapToDouble(ArmyUnit::soldiers)
                .sum();
        }

        // 统计正在进行的战斗
        int activeBattles = 0;
        if (warService != null) {
            Collection<WarSnapshot> activeWars = warService.activeWarsOf(nationId);
            activeBattles = activeWars.size();
        }

        // 统计盟友和敌人
        int allies = 0;
        int enemies = 0;
        if (allianceService != null) {
            allies = allianceService.getMilitaryAllies(nationId, null).size();
        }
        if (warService != null) {
            enemies = (int) warService.activeWarsOf(nationId).stream()
                .map(war -> war.left().equals(nationId) ? war.right() : war.left())
                .count();
        }

        // 统计领地
        int provinces = nationOpt.get().territoryCount();

        return new MilitaryOverview(
            nationId,
            totalArmies,
            activeBattles,
            allies,
            enemies,
            provinces,
            militaryPower,
            System.currentTimeMillis()
        );
    }

    @Override
    public List<BattleSummary> getActiveBattles(NationId nationId) {
        List<BattleSummary> battles = new ArrayList<>();

        if (warService == null) {
            return battles;
        }

        Collection<WarSnapshot> activeWars = warService.activeWarsOf(nationId);
        for (WarSnapshot war : activeWars) {
            // 获取敌对国家
            NationId enemyId = war.left().equals(nationId) ? war.right() : war.left();
            String enemyName = nationService != null ?
                nationService.nationById(enemyId)
                    .map(Nation::name)
                    .orElse("Unknown") : "Unknown";

            battles.add(new BattleSummary(
                UUID.randomUUID().toString(),
                "战场区域",
                enemyName,
                war.ended() ? "ENDED" : "ACTIVE",
                0, // Casualties 需要从其他服务获取
                0,
                war.declaredAt().toEpochMilli(),
                calculateProgress(war)
            ));
        }

        return battles;
    }

    @Override
    public List<AllianceSummary> getAlliances(NationId nationId) {
        List<AllianceSummary> alliances = new ArrayList<>();

        if (allianceService == null) {
            return alliances;
        }

        Collection<NationId> allies = allianceService.getMilitaryAllies(nationId, null);
        for (NationId allyId : allies) {
            String allyName = nationService != null ?
                nationService.nationById(allyId)
                    .map(Nation::name)
                    .orElse("Unknown") : "Unknown";

            alliances.add(new AllianceSummary(
                allyId,
                allyName,
                "MILITARY",
                System.currentTimeMillis()
            ));
        }

        return alliances;
    }

    @Override
    public DefenseStatus getDefenseStatus(NationId nationId) {
        int totalDefense = 0;
        int activeDefense = 0;
        int underAttack = 0;
        List<String> weakPoints = new ArrayList<>();
        double defenseMorale = 1.0;

        if (armyService != null) {
            List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());
            totalDefense = armies.size();
            // 防御型军队
            activeDefense = (int) armies.stream()
                .filter(a -> a.type() != null && "DEFENSE".equals(a.type().name()))
                .count();
        }

        if (warService != null) {
            Collection<WarSnapshot> activeWars = warService.activeWarsOf(nationId);
            underAttack = (int) activeWars.stream()
                .filter(w -> w.left().equals(nationId) || w.right().equals(nationId))
                .count();
        }

        // 检测弱点
        if (totalDefense == 0) {
            weakPoints.add("没有防御军队");
        }
        if (underAttack > 0) {
            weakPoints.add("正在遭受攻击");
        }

        return new DefenseStatus(totalDefense, activeDefense, underAttack, weakPoints, defenseMorale);
    }

    @Override
    public OffensiveStatus getOffensiveStatus(NationId nationId) {
        int totalOffensive = 0;
        int activeOffensive = 0;
        int victories = 0;
        int defeats = 0;

        if (armyService != null) {
            List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());
            totalOffensive = armies.size();
            // 进攻型军队
            activeOffensive = (int) armies.stream()
                .filter(a -> a.type() != null && "OFFENSE".equals(a.type().name()))
                .count();
        }

        if (warService != null) {
            Collection<WarSnapshot> wars = warService.warHistory(nationId);
            victories = (int) wars.stream()
                .filter(w -> w.ended() &&
                    (w.left().equals(nationId) || w.right().equals(nationId)))
                .count();
            defeats = (int) wars.stream()
                .filter(w -> !w.ended() &&
                    (w.left().equals(nationId) || w.right().equals(nationId)))
                .count();
        }

        int total = victories + defeats;
        double winRate = total > 0 ? (double) victories / total : 0.0;

        return new OffensiveStatus(totalOffensive, activeOffensive, victories, defeats, winRate);
    }

    @Override
    public boolean canAttack(NationId nationId) {
        if (warService == null) {
            return false;
        }

        // 检查是否已经在战争中
        if (!warService.activeWarsOf(nationId).isEmpty()) {
            return false;
        }

        // 检查是否有足够的军队
        if (armyService != null) {
            return !armyService.getNationArmies(nationId.value()).isEmpty();
        }

        return true;
    }

    @Override
    public List<MilitarySuggestion> getSuggestions(NationId nationId) {
        List<MilitarySuggestion> suggestions = new ArrayList<>();

        // 防御建议
        DefenseStatus defense = getDefenseStatus(nationId);
        if (defense.totalDefense() == 0) {
            suggestions.add(new MilitarySuggestion(
                "HIGH",
                "建立防御军队",
                "你的国家目前没有防御军队，建议至少建立一支防御军队",
                "/army create defense"
            ));
        }

        if (defense.underAttack() > 0) {
            suggestions.add(new MilitarySuggestion(
                "URGENT",
                "正在遭受攻击",
                "你的国家正在遭受攻击，请立即查看战况并采取行动",
                "/military status"
            ));
        }

        // 进攻建议
        OffensiveStatus offensive = getOffensiveStatus(nationId);
        if (offensive.winRate() > 0.7) {
            suggestions.add(new MilitarySuggestion(
                "MEDIUM",
                "进攻优势",
                "你的军队表现出色，可以考虑扩大战果",
                "/military attack"
            ));
        }

        // 联盟建议
        if (getAlliances(nationId).isEmpty()) {
            suggestions.add(new MilitarySuggestion(
                "LOW",
                "建立军事联盟",
                "建议与其他国家建立军事联盟以增强实力",
                "/diplomacy alliance"
            ));
        }

        return suggestions;
    }

    // ==================== 私有方法 ====================

    private double calculateProgress(WarSnapshot war) {
        long durationMs = System.currentTimeMillis() - war.declaredAt().toEpochMilli();
        long maxDuration = 7 * 24 * 60 * 60 * 1000L; // 7天
        return Math.min(1.0, (double) durationMs / maxDuration);
    }

    private BattlefieldService findBattlefieldService(StarCoreContext context) {
        var bfService = context.serviceRegistry().find(BattlefieldService.class).orElse(null);
        if (bfService != null) {
            return bfService;
        }

        WarService warServiceLocal = context.serviceRegistry().find(WarService.class).orElse(null);
        if (warServiceLocal != null) {
            try {
                java.lang.reflect.Method method = warServiceLocal.getClass().getMethod("getBattlefieldService");
                Object result = method.invoke(warServiceLocal);
                if (result instanceof BattlefieldService) {
                    return (BattlefieldService) result;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
