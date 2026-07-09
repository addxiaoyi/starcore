package dev.starcore.starcore.module.army.mercenary.event;

import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 雇佣兵完成任务事件
 */
public final class MercenaryMissionCompletedEvent extends org.bukkit.event.Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID contractId;
    private final UUID mercenaryId;
    private final UUID nationId;
    private final String mercenaryName;
    private final String nationName;
    private final String missionType;
    private final int reward;
    private final int experienceGained;

    public MercenaryMissionCompletedEvent(
        UUID contractId,
        UUID mercenaryId,
        UUID nationId,
        String mercenaryName,
        String nationName,
        String missionType,
        int reward,
        int experienceGained
    ) {
        this.contractId = contractId;
        this.mercenaryId = mercenaryId;
        this.nationId = nationId;
        this.mercenaryName = mercenaryName;
        this.nationName = nationName;
        this.missionType = missionType;
        this.reward = reward;
        this.experienceGained = experienceGained;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getMercenaryId() {
        return mercenaryId;
    }

    public UUID getNationId() {
        return nationId;
    }

    public String getMercenaryName() {
        return mercenaryName;
    }

    public String getNationName() {
        return nationName;
    }

    public String getMissionType() {
        return missionType;
    }

    public int getReward() {
        return reward;
    }

    public int getExperienceGained() {
        return experienceGained;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}