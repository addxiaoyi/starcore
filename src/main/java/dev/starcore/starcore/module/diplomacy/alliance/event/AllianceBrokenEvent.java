package dev.starcore.starcore.module.diplomacy.alliance.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 联盟破裂事件
 * 在两个国家的联盟关系解除时触发
 */
public class AllianceBrokenEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum BreakReason {
        UNILATERAL("单方面解除"),
        MUTUAL("双方同意解除"),
        WAR_DECLARED("宣战后解除"),
        NATION_DISBANDED("国家解散"),
        TIMEOUT("过期解除");

        private final String description;

        BreakReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final NationId nation1;
    private final NationId nation2;
    private final NationId brokenBy;
    private final BreakReason reason;

    public AllianceBrokenEvent(NationId nation1, NationId nation2, NationId brokenBy, BreakReason reason) {
        this.nation1 = nation1;
        this.nation2 = nation2;
        this.brokenBy = brokenBy;
        this.reason = reason;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    public NationId getBrokenBy() {
        return brokenBy;
    }

    public BreakReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
