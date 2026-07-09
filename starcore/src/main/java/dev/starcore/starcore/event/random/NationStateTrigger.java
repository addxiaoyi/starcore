package dev.starcore.starcore.event.random;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.technology.TechnologyService;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 国家状态触发器
 * 检查国家状态条件
 */
public class NationStateTrigger implements EventTrigger {

    // Alias for StateType
    public enum NationStateType {
        MEMBERS, TERRITORY, TREASURY, POWER,
        AT_WAR, HAS_ALLY, PROSPEROUS,
        ONLINE_MEMBERS, RECENT_WARS, POPULATION
    }

    public enum StateType {
        MEMBERS, TERRITORY, TREASURY, POWER,
        AT_WAR, HAS_ALLY, PROSPEROUS,
        ONLINE_MEMBERS, RECENT_WARS, POPULATION
    }

    public enum ComparisonType {
        GREATER_THAN, LESS_THAN, EQUALS
    }

    private final StateType stateType;
    private final String stateValue;
    private final ComparisonType comparison;
    private final double threshold;

    // 静态服务引用
    private static NationService nationService;
    private static ResourceService resourceService;
    private static TechnologyService technologyService;
    private static DiplomacyService diplomacyService;
    private static ServiceRegistry serviceRegistry;
    private static JavaPlugin plugin;

    public NationStateTrigger(StateType stateType, String stateValue, ComparisonType comparison, double threshold) {
        this.stateType = stateType;
        this.stateValue = stateValue;
        this.comparison = comparison;
        this.threshold = threshold;
    }

    public NationStateTrigger(NationStateType stateType, ComparisonType comparison, double threshold) {
        this(convert(stateType), "", comparison, threshold);
    }

    private static StateType convert(NationStateType type) {
        return StateType.valueOf(type.name());
    }

    public static void injectServices(NationService ns, ResourceService rs, TechnologyService ts, DiplomacyService ds, JavaPlugin p) {
        nationService = ns;
        resourceService = rs;
        technologyService = ts;
        diplomacyService = ds;
        plugin = p;
    }

    public static void setServiceRegistry(ServiceRegistry sr) {
        serviceRegistry = sr;
    }

    /**
     * 检查触发器条件
     */
    public boolean check(Nation nation, NationEventContext context) {
        if (nation == null) return false;

        switch (stateType) {
            case MEMBERS:
                return compare(nation.members().size());
            case ONLINE_MEMBERS:
            case POPULATION:
                return compare(getOnlineMemberCount(nation));
            case TERRITORY:
                return compare(context != null ? context.getTerritoryChunks() : 0);
            case TREASURY:
                return compare(context != null ? context.getTreasuryBalance() : 0);
            case POWER:
                return compare(context != null ? context.getTotalPower() : 0);
            case AT_WAR:
                return context != null && context.isAtWar();
            case HAS_ALLY:
                return context != null && context.hasAlly();
            case PROSPEROUS:
                return context != null && context.isProsperous();
            case RECENT_WARS:
                return compare(context != null ? context.getRecentWars() : 0);
            default:
                return false;
        }
    }

    private boolean compare(double value) {
        switch (comparison) {
            case GREATER_THAN: return value > threshold;
            case LESS_THAN: return value < threshold;
            case EQUALS: return Math.abs(value - threshold) < 0.001;
            default: return false;
        }
    }

    private int getOnlineMemberCount(Nation nation) {
        int count = 0;
        if (nation == null) return 0;
        for (NationMember member : nation.members()) {
            if (member.isOnline()) {
                count++;
            }
        }
        return count;
    }

    // EventTrigger implementation
    @Override
    public boolean check(Player player, Location location) {
        // This trigger is checked via NationEventContext, not directly
        return true;
    }

    @Override
    public String getType() {
        return "NATION_STATE";
    }

    @Override
    public String getDescription() {
        return stateType.name() + " " + comparison.name() + " " + threshold;
    }
}
