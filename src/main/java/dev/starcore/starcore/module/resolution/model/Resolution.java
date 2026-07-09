package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Resolution {
    private final UUID id;
    private final NationId nationId;
    private final UUID proposerId;
    private final String proposerName;
    private final ResolutionAction action;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Set<UUID> signatures = new LinkedHashSet<>();
    private ResolutionState state;

    public Resolution(NationId nationId, UUID proposerId, String proposerName, ResolutionAction action, Instant createdAt, Instant expiresAt) {
        this(UUID.randomUUID(), nationId, proposerId, proposerName, action, createdAt, expiresAt, ResolutionState.OPEN, Set.of());
    }

    public Resolution(UUID id, NationId nationId, UUID proposerId, String proposerName, ResolutionAction action, Instant createdAt, Instant expiresAt, ResolutionState state, Collection<UUID> signatures) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.proposerId = Objects.requireNonNull(proposerId, "proposerId");
        this.proposerName = Objects.requireNonNull(proposerName, "proposerName");
        this.action = Objects.requireNonNull(action, "action");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.state = Objects.requireNonNull(state, "state");
        this.signatures.addAll(Objects.requireNonNull(signatures, "signatures"));
    }

    public UUID id() { return id; }
    public NationId nationId() { return nationId; }
    public UUID proposerId() { return proposerId; }
    public String proposerName() { return proposerName; }
    public ResolutionAction action() { return action; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public ResolutionState state() { return state; }
    public Set<UUID> signatures() { return Set.copyOf(signatures); }

    public boolean isOpen() { return state == ResolutionState.OPEN; }
    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
    public boolean sign(UUID signerId) { return isOpen() && signatures.add(signerId); }
    public void markEnacted() { state = ResolutionState.ENACTED; }
    public void markFailed() { state = ResolutionState.FAILED; }
    public void markExpired() { state = ResolutionState.EXPIRED; }
    public void markCancelled() { state = ResolutionState.CANCELLED; }
}
