package dev.starcore.starcore.module.diplomacy.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 联盟破裂事件
 * 在两个国家的联盟关系解除时触发
 */
public class AllianceBrokenEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nation1;
    private final NationId nation2;
    private final AllianceBreakReason reason;

    public AllianceBrokenEvent(NationId nation1, NationId nation2, AllianceBreakReason reason) {
        this.nation1 = nation1;
        this.nation2 = nation2;
        this.reason = reason;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    public AllianceBreakReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * 联盟破裂原因
     */
    public enum AllianceBreakReason {
        /** 单方面解除 */
        UNILATERAL_BREAK,
        /** 一方宣战 */
        WAR_DECLARED,
        /** 双方同意解除 */
        MUTUAL_AGREEMENT,
        /** 一方解散 */
        NATION_DISBANDED,
        /** 管理员强制解除 */
        ADMIN_FORCE
    }
}
