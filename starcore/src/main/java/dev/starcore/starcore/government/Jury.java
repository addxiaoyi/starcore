package dev.starcore.starcore.government;
import java.util.Optional;

import java.util.*;

/**
 * 陪审团
 */
public final class Jury {
    private final int caseId;
    private final List<UUID> jurors;
    private final Map<UUID, JuryVote> votes;
    private boolean deliberationComplete;

    public enum JuryVote {
        GUILTY,
        NOT_GUILTY,
        ABSTAIN
    }

    public Jury(int caseId, List<UUID> jurors) {
        this.caseId = caseId;
        this.jurors = new ArrayList<>(Objects.requireNonNull(jurors, "jurors"));
        this.votes = new HashMap<>();
        this.deliberationComplete = false;
    }

    public int getCaseId() {
        return caseId;
    }

    public List<UUID> getJurors() {
        return Collections.unmodifiableList(jurors);
    }

    public boolean isJuror(UUID playerId) {
        return jurors.contains(playerId);
    }

    public void castVote(UUID jurorId, JuryVote vote) {
        if (!jurors.contains(jurorId)) {
            throw new IllegalArgumentException("Player is not a juror");
        }
        votes.put(jurorId, Objects.requireNonNull(vote, "vote"));
    }

    public Optional<JuryVote> getVote(UUID jurorId) {
        return Optional.ofNullable(votes.get(jurorId));
    }

    public Map<UUID, JuryVote> getAllVotes() {
        return Collections.unmodifiableMap(votes);
    }

    public boolean hasAllVoted() {
        return votes.size() == jurors.size();
    }

    public boolean isDeliberationComplete() {
        return deliberationComplete;
    }

    public void setDeliberationComplete(boolean complete) {
        this.deliberationComplete = complete;
    }

    /**
     * 计算投票结果
     * @return Optional包含多数意见，如果没有多数则为空
     */
    public Optional<JuryVote> getMajorityVerdict() {
        if (!hasAllVoted()) {
            return Optional.empty();
        }

        Map<JuryVote, Long> voteCounts = new HashMap<>();
        for (JuryVote vote : votes.values()) {
            if (vote != JuryVote.ABSTAIN) {
                voteCounts.merge(vote, 1L, Long::sum);
            }
        }

        long guilty = voteCounts.getOrDefault(JuryVote.GUILTY, 0L);
        long notGuilty = voteCounts.getOrDefault(JuryVote.NOT_GUILTY, 0L);

        // 需要超过半数
        long majority = jurors.size() / 2;

        if (guilty > majority) {
            return Optional.of(JuryVote.GUILTY);
        } else if (notGuilty > majority) {
            return Optional.of(JuryVote.NOT_GUILTY);
        }

        return Optional.empty();
    }

    /**
     * 获取投票统计
     */
    public Map<JuryVote, Long> getVoteCounts() {
        Map<JuryVote, Long> counts = new HashMap<>();
        for (JuryVote vote : JuryVote.values()) {
            counts.put(vote, 0L);
        }
        for (JuryVote vote : votes.values()) {
            counts.merge(vote, 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Jury jury = (Jury) o;
        return caseId == jury.caseId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId);
    }

    @Override
    public String toString() {
        return "Jury{" +
                "caseId=" + caseId +
                ", jurors=" + jurors.size() +
                ", votes=" + votes.size() +
                ", deliberationComplete=" + deliberationComplete +
                '}';
    }
}
