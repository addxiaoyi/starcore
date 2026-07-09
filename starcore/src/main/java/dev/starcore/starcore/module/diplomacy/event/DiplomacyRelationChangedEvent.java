package dev.starcore.starcore.module.diplomacy.event;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 外交关系变更事件
 * 在外交关系（联盟/敌对/中立）发生变更时触发
 */
public class DiplomacyRelationChangedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final NationId leftNation;
    private final NationId rightNation;
    private final DiplomacyRelation previousRelation;
    private final DiplomacyRelation newRelation;
    private boolean cancelled;

    public DiplomacyRelationChangedEvent(
            NationId leftNation,
            NationId rightNation,
            DiplomacyRelation previousRelation,
            DiplomacyRelation newRelation
    ) {
        this.leftNation = leftNation;
        this.rightNation = rightNation;
        this.previousRelation = previousRelation;
        this.newRelation = newRelation;
    }

    public NationId getLeftNation() {
        return leftNation;
    }

    public NationId getRightNation() {
        return rightNation;
    }

    public DiplomacyRelation getPreviousRelation() {
        return previousRelation;
    }

    public DiplomacyRelation getNewRelation() {
        return newRelation;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
