package dev.starcore.starcore.module.policy.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.Objects;

/**
 * 国策激活事件
 * 当国家激活一个国策时触发
 */
public class PolicyActivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final NationId nationId;
    private final PolicyDefinition definition;
    private final PolicyRuntimeState state;
    private final Instant occurredAt;

    public PolicyActivatedEvent(NationId nationId, PolicyDefinition definition, PolicyRuntimeState state, Instant occurredAt) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.state = Objects.requireNonNull(state, "state");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static PolicyActivatedEvent create(NationId nationId, PolicyDefinition definition, Instant now) {
        PolicyRuntimeState state = new PolicyRuntimeState(
            definition.key(),
            now,
            now.plusSeconds(definition.durationSeconds()),
            now.plusSeconds(definition.cooldownSeconds())
        );
        return new PolicyActivatedEvent(nationId, definition, state, now);
    }

    public NationId nationId() {
        return nationId;
    }

    public PolicyDefinition definition() {
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
