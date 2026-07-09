package dev.starcore.starcore.module.army.prisoner.event;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.UUID;

/**
 * 俘虏被释放事件
 * 在俘虏被释放时触发
 */
public class PrisonerReleasedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final PrisonerOfWar prisoner;
    private final ReleaseReason reason;
    private final UUID releasedBy;
    private final Instant releaseTime;
    private boolean cancelled;

    public PrisonerReleasedEvent(PrisonerOfWar prisoner, ReleaseReason reason, UUID releasedBy) {
        this.prisoner = prisoner;
        this.reason = reason;
        this.releasedBy = releasedBy;
        this.releaseTime = Instant.now();
    }

    public PrisonerOfWar getPrisoner() {
        return prisoner;
    }

    public ReleaseReason getReason() {
        return reason;
    }

    public UUID getReleasedBy() {
        return releasedBy;
    }

    public Instant getReleaseTime() {
        return releaseTime;
    }

    public UUID getPrisonerId() {
        return prisoner.prisonerId();
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

    /**
     * 释放原因枚举
     */
    public enum ReleaseReason {
        RANSOM_PAID("ransom", "支付赎金"),
        EXCHANGE("exchange", "战俘交换"),
        LABOR_COMPLETED("labor", "劳役完成"),
        WAR_ENDED("war_end", "战争结束"),
        MERCY("mercy", "仁慈释放"),
        ESCAPE("escape", "逃跑"),
        ADMIN_FORCE("admin", "管理员强制");

        private final String key;
        private final String displayName;

        ReleaseReason(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }
    }
}
