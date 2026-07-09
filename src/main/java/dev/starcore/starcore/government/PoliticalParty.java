package dev.starcore.starcore.government;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 政党
 */
public final class PoliticalParty {
    private final int partyId;
    private final String name;
    private final String abbreviation;
    private final UUID founderId;
    private final Instant foundedAt;
    private String ideology;        // 意识形态
    private String platform;        // 政党纲领
    private String color;           // 政党颜色（十六进制）
    private boolean active;
    private int totalSeats;
    private boolean inPower;        // 是否执政

    public PoliticalParty(int partyId, String name, String abbreviation,
                          UUID founderId, Instant foundedAt) {
        this.partyId = partyId;
        this.name = Objects.requireNonNull(name, "name");
        this.abbreviation = Objects.requireNonNull(abbreviation, "abbreviation");
        this.founderId = Objects.requireNonNull(founderId, "founderId");
        this.foundedAt = Objects.requireNonNull(foundedAt, "foundedAt");
        this.active = true;
        this.totalSeats = 0;
        this.inPower = false;
    }

    public int getPartyId() {
        return partyId;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public UUID getFounderId() {
        return founderId;
    }

    public Instant getFoundedAt() {
        return foundedAt;
    }

    public Optional<String> getIdeology() {
        return Optional.ofNullable(ideology);
    }

    public void setIdeology(String ideology) {
        this.ideology = ideology;
    }

    public Optional<String> getPlatform() {
        return Optional.ofNullable(platform);
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Optional<String> getColor() {
        return Optional.ofNullable(color);
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public boolean isInPower() {
        return inPower;
    }

    public void setInPower(boolean inPower) {
        this.inPower = inPower;
    }

    /**
     * 计算席位占比
     */
    public double getSeatPercentage(int totalParliamentSeats) {
        if (totalParliamentSeats == 0) {
            return 0.0;
        }
        return (double) totalSeats / totalParliamentSeats;
    }

    /**
     * 是否为多数党
     */
    public boolean hasMajority(int totalParliamentSeats) {
        return getSeatPercentage(totalParliamentSeats) > 0.5;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PoliticalParty that = (PoliticalParty) o;
        return partyId == that.partyId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partyId);
    }

    @Override
    public String toString() {
        return "PoliticalParty{" +
                "partyId=" + partyId +
                ", name='" + name + '\'' +
                ", abbreviation='" + abbreviation + '\'' +
                ", totalSeats=" + totalSeats +
                ", inPower=" + inPower +
                '}';
    }
}
