package dev.starcore.starcore.module.policy.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.Objects;

/**
 * 国策过期事件
 * 当国家激活的国策到期时触发
 */
public class PolicyExpiredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final NationId nationId;
    private final PolicyDefinition definition;
    private final PolicyRuntimeState state;
    private final Instant occurredAt;

    public PolicyExpiredEvent(NationId nationId, PolicyDefinition definition, PolicyRuntimeState state, Instant occurredAt) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.definition = definition;
        this.state = Objects.requireNonNull(state, "state");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public NationId nationId() {
        return nationId;
    }

    public PolicyDefinition definition() {
        return definition;
    }

    /**
     * 获取过期国策的定义（如果可用）
     */
    public PolicyDefinition definitionOrNull() {
        return definition;
    }

    public PolicyRuntimeState state() {
        return state;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
