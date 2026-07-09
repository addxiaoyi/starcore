package dev.starcore.starcore.social.simulation.events;

import dev.starcore.starcore.social.simulation.CultureConflictService;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 文化冲突事件
 *
 * 当文化冲突发生、升级、和解或融合时触发
 */
public class CultureConflictEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final CultureConflictService.CultureConflictData conflict;
    private final EventType type;

    public CultureConflictEvent(CultureConflictService.CultureConflictData conflict, EventType type) {
        this.conflict = conflict;
        this.type = type;
    }

    public CultureConflictService.CultureConflictData getConflict() {
        return conflict;
    }

    public EventType getType() {
        return type;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * 事件类型枚举
     */
    public enum EventType {
        DETECTED("检测到冲突"),
        ESCALATED("冲突升级"),
        TENSION_REDUCED("紧张度下降"),
        RECONCILIATION_ATTEMPT("和解尝试"),
        RESOLVED("冲突解决"),
        FUSION_COMPLETED("文化融合完成"),
        WAR_TRIGGERED("冲突引发战争");

        private final String description;

        EventType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
