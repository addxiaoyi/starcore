package dev.starcore.starcore.module.dynasty.event;

import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.UUID;

/**
 * 王位空缺（空位期）事件
 * 当国家失去君主时触发
 */
public class InterregnumEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum InterregnumCause {
        DEATH,        // 君主死亡
        ABDICATION,    // 君主退位
        EXILE,        // 君主被流放
        DEPOSITION,    // 君主被废黜
        OTHER         // 其他原因
    }

    private final NationId nationId;
    private final Dynasty dynasty;
    private final InterregnumCause cause;
    private final Instant startTime;
    private final UUID formerMonarchId;
    private final String formerMonarchName;

    public InterregnumEvent(
            NationId nationId,
            Dynasty dynasty,
            InterregnumCause cause,
            Instant startTime,
            UUID formerMonarchId,
            String formerMonarchName) {
        this.nationId = nationId;
        this.dynasty = dynasty;
        this.cause = cause;
        this.startTime = startTime;
        this.formerMonarchId = formerMonarchId;
        this.formerMonarchName = formerMonarchName;
    }

    public NationId getNationId() {
        return nationId;
    }

    public Dynasty getDynasty() {
        return dynasty;
    }

    public InterregnumCause getCause() {
        return cause;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public UUID getFormerMonarchId() {
        return formerMonarchId;
    }

    public String getFormerMonarchName() {
        return formerMonarchName;
    }

    /**
     * 获取空位持续天数
     */
    public long getDaysSinceStart() {
        return java.time.Duration.between(startTime, Instant.now()).toDays();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}