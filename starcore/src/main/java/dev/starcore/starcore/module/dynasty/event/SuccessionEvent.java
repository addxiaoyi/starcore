package dev.starcore.starcore.module.dynasty.event;

import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.dynasty.model.SuccessionType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 继承事件
 * 描述王位继承/禅让的详细信息
 */
public class SuccessionEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum SuccessionKind {
        ABDICATION,   // 禅让（主动传位）
        INHERITANCE,  // 继承（自动继承）
        CORONATION,   // 加冕（索取王位）
        FORCE_MAJORE  // 强制继承（特殊情况）
    }

    private final NationId nationId;
    private final Dynasty dynasty;
    private final SuccessionKind kind;
    private final UUID previousMonarchId;
    private final String previousMonarchName;
    private final UUID newMonarchId;
    private final String newMonarchName;
    private final String reason;
    private final long previousReignDays;

    public SuccessionEvent(
            NationId nationId,
            Dynasty dynasty,
            SuccessionKind kind,
            UUID previousMonarchId,
            String previousMonarchName,
            UUID newMonarchId,
            String newMonarchName,
            String reason) {
        this.nationId = nationId;
        this.dynasty = dynasty;
        this.kind = kind;
        this.previousMonarchId = previousMonarchId;
        this.previousMonarchName = previousMonarchName;
        this.newMonarchId = newMonarchId;
        this.newMonarchName = newMonarchName;
        this.reason = reason;
        this.previousReignDays = dynasty != null ? dynasty.getDaysSinceMonarch() : 0;
    }

    public NationId getNationId() {
        return nationId;
    }

    public Dynasty getDynasty() {
        return dynasty;
    }

    public SuccessionKind getKind() {
        return kind;
    }

    public UUID getPreviousMonarchId() {
        return previousMonarchId;
    }

    public String getPreviousMonarchName() {
        return previousMonarchName;
    }

    public UUID getNewMonarchId() {
        return newMonarchId;
    }

    public String getNewMonarchName() {
        return newMonarchName;
    }

    public String getReason() {
        return reason;
    }

    public long getPreviousReignDays() {
        return previousReignDays;
    }

    /**
     * 是否是禅让
     */
    public boolean isAbdication() {
        return kind == SuccessionKind.ABDICATION;
    }

    /**
     * 是否是自动继承
     */
    public boolean isInheritance() {
        return kind == SuccessionKind.INHERITANCE;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}