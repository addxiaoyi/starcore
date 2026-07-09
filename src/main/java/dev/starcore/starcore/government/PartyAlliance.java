package dev.starcore.starcore.government;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 政党联盟
 */
public final class PartyAlliance {
    private final int allianceId;
    private final String name;
    private final List<Integer> partyIds;
    private final Instant formedAt;
    private boolean active;
    private Integer leadPartyId;    // 主导政党
    private String purpose;         // 联盟目的

    public PartyAlliance(int allianceId, String name, List<Integer> partyIds, Instant formedAt) {
        this.allianceId = allianceId;
        this.name = Objects.requireNonNull(name, "name");
        this.partyIds = new ArrayList<>(Objects.requireNonNull(partyIds, "partyIds"));
        this.formedAt = Objects.requireNonNull(formedAt, "formedAt");
        this.active = true;
    }

    public int getAllianceId() {
        return allianceId;
    }

    public String getName() {
        return name;
    }

    public List<Integer> getPartyIds() {
        return Collections.unmodifiableList(partyIds);
    }

    public Instant getFormedAt() {
        return formedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Optional<Integer> getLeadPartyId() {
        return Optional.ofNullable(leadPartyId);
    }

    public void setLeadPartyId(Integer leadPartyId) {
        this.leadPartyId = leadPartyId;
    }

    public Optional<String> getPurpose() {
        return Optional.ofNullable(purpose);
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    /**
     * 添加政党到联盟
     */
    public void addParty(int partyId) {
        if (!partyIds.contains(partyId)) {
            partyIds.add(partyId);
        }
    }

    /**
     * 从联盟中移除政党
     */
    public boolean removeParty(int partyId) {
        return partyIds.remove(Integer.valueOf(partyId));
    }

    /**
     * 检查政党是否在联盟中
     */
    public boolean hasParty(int partyId) {
        return partyIds.contains(partyId);
    }

    /**
     * 获取联盟规模
     */
    public int getSize() {
        return partyIds.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartyAlliance that = (PartyAlliance) o;
        return allianceId == that.allianceId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allianceId);
    }

    @Override
    public String toString() {
        return "PartyAlliance{" +
                "allianceId=" + allianceId +
                ", name='" + name + '\'' +
                ", parties=" + partyIds.size() +
                ", active=" + active +
                '}';
    }
}
