package dev.starcore.starcore.module.vassal.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 附庸邀请信息
 */
public final class VassalInviteInfo {
    private final UUID inviteId;
    private final NationId suzerainNationId;
    private final NationId vassalNationId;
    private final UUID inviterPlayerId;
    private final String inviterName;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final VassalType vassalType;
    private final double tributeRate;

    public VassalInviteInfo(
            NationId suzerainNationId,
            NationId vassalNationId,
            UUID inviterPlayerId,
            String inviterName,
            int expiryMinutes,
            VassalType vassalType,
            double tributeRate
    ) {
        this.inviteId = UUID.randomUUID();
        this.suzerainNationId = suzerainNationId;
        this.vassalNationId = vassalNationId;
        this.inviterPlayerId = inviterPlayerId;
        this.inviterName = inviterName;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(expiryMinutes * 60L);
        this.vassalType = vassalType;
        this.tributeRate = tributeRate;
    }

    public UUID inviteId() {
        return inviteId;
    }

    public NationId suzerainNationId() {
        return suzerainNationId;
    }

    public NationId vassalNationId() {
        return vassalNationId;
    }

    public UUID inviterPlayerId() {
        return inviterPlayerId;
    }

    public String inviterName() {
        return inviterName;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public VassalType vassalType() {
        return vassalType;
    }

    public double tributeRate() {
        return tributeRate;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}