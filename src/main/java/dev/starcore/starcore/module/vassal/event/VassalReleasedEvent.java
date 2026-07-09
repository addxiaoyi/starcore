package dev.starcore.starcore.module.vassal.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 宗藩关系解除事件
 * Fired when a vassal relationship is terminated
 */
public class VassalReleasedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum ReleaseReason {
        SUZERAIN_LIBERATES,    // 宗主国主动释放
        VASSAL_INDEPENDENCE,   // 藩属国宣布独立
        WAR_DEFEAT,           // 战争失败被解除
        PEACEFUL_AGREEMENT    // 和平协议解除
    }

    private final NationId suzerainId;
    private final NationId vassalId;
    private final ReleaseReason reason;

    public VassalReleasedEvent(NationId suzerainId, NationId vassalId, ReleaseReason reason) {
        this.suzerainId = suzerainId;
        this.vassalId = vassalId;
        this.reason = reason;
    }

    public NationId suzerainId() {
        return suzerainId;
    }

    public NationId vassalId() {
        return vassalId;
    }

    public ReleaseReason reason() {
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