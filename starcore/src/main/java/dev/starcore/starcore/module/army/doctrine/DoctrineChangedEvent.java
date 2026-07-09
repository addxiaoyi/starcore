package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 军事学说变更事件
 * 当国家切换军事学说时触发
 */
public class DoctrineChangedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final DoctrineType previousDoctrine;
    private final DoctrineType newDoctrine;
    private final String changedBy;

    public DoctrineChangedEvent(
        UUID nationId,
        DoctrineType previousDoctrine,
        DoctrineType newDoctrine,
        String changedBy
    ) {
        this.nationId = nationId;
        this.previousDoctrine = previousDoctrine;
        this.newDoctrine = newDoctrine;
        this.changedBy = changedBy;
    }

    public UUID nationId() {
        return nationId;
    }

    public DoctrineType previousDoctrine() {
        return previousDoctrine;
    }

    public DoctrineType newDoctrine() {
        return newDoctrine;
    }

    public String changedBy() {
        return changedBy;
    }

    /**
     * 检查是否是新采用学说
     */
    public boolean isNewAdoption() {
        return previousDoctrine == DoctrineType.NONE && newDoctrine != DoctrineType.NONE;
    }

    /**
     * 检查是否是切换学说
     */
    public boolean isSwitch() {
        return previousDoctrine != DoctrineType.NONE && newDoctrine != DoctrineType.NONE;
    }

    /**
     * 检查是否是清除学说
     */
    public boolean isClear() {
        return previousDoctrine != DoctrineType.NONE && newDoctrine == DoctrineType.NONE;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}