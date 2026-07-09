package dev.starcore.starcore.module.faith.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 信仰等级变更事件
 * 当国家信仰等级发生变化时触发
 */
public class FaithLevelChangedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nationId;
    private final int previousLevel;
    private final int newLevel;
    private final String previousLevelName;
    private final String newLevelName;
    private final boolean upgraded;

    public FaithLevelChangedEvent(
        NationId nationId,
        int previousLevel,
        int newLevel,
        String previousLevelName,
        String newLevelName
    ) {
        this.nationId = nationId;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.previousLevelName = previousLevelName;
        this.newLevelName = newLevelName;
        this.upgraded = newLevel > previousLevel;
    }

    public NationId getNationId() {
        return nationId;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public String getPreviousLevelName() {
        return previousLevelName;
    }

    public String getNewLevelName() {
        return newLevelName;
    }

    /**
     * 是否是升级（而非降级）
     */
    public boolean isUpgraded() {
        return upgraded;
    }

    /**
     * 是否是降级
     */
    public boolean isDowngraded() {
        return newLevel < previousLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}