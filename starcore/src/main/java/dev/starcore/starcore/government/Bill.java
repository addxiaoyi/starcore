package dev.starcore.starcore.government;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 议案记录
 */
public final class Bill {
    private final int billId;
    private final int parliamentId;
    private final UUID proposerId;
    private final String title;
    private final String content;
    private final BillType billType;
    private final Instant proposedAt;
    private BillStatus status;
    private Instant votingStartsAt;
    private Instant votingEndsAt;
    private Integer votesFor;
    private Integer votesAgainst;
    private Integer votesAbstain;
    private Instant enactedAt;

    public enum BillType {
        CONSTITUTIONAL,  // 宪法修正案
        LEGISLATIVE,     // 立法
        BUDGET,          // 预算案
        APPOINTMENT,     // 任命案
        RESOLUTION,      // 决议案
        IMPEACHMENT      // 弹劾案
    }

    public enum BillStatus {
        PROPOSED,        // 已提出
        UNDER_REVIEW,    // 审查中
        SCHEDULED,       // 已排期
        VOTING,          // 投票中
        PASSED,          // 通过
        REJECTED,        // 否决
        ENACTED,         // 已生效
        WITHDRAWN        // 已撤回
    }

    public Bill(int billId, int parliamentId, UUID proposerId, String title,
                String content, BillType billType, Instant proposedAt) {
        this.billId = billId;
        this.parliamentId = parliamentId;
        this.proposerId = Objects.requireNonNull(proposerId, "proposerId");
        this.title = Objects.requireNonNull(title, "title");
        this.content = Objects.requireNonNull(content, "content");
        this.billType = Objects.requireNonNull(billType, "billType");
        this.proposedAt = Objects.requireNonNull(proposedAt, "proposedAt");
        this.status = BillStatus.PROPOSED;
        this.votesFor = 0;
        this.votesAgainst = 0;
        this.votesAbstain = 0;
    }

    public int getBillId() {
        return billId;
    }

    public int getParliamentId() {
        return parliamentId;
    }

    public UUID getProposerId() {
        return proposerId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public BillType getBillType() {
        return billType;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Optional<Instant> getVotingStartsAt() {
        return Optional.ofNullable(votingStartsAt);
    }

    public void setVotingStartsAt(Instant votingStartsAt) {
        this.votingStartsAt = votingStartsAt;
    }

    public Optional<Instant> getVotingEndsAt() {
        return Optional.ofNullable(votingEndsAt);
    }

    public void setVotingEndsAt(Instant votingEndsAt) {
        this.votingEndsAt = votingEndsAt;
    }

    public int getVotesFor() {
        return votesFor;
    }

    public void setVotesFor(int votesFor) {
        this.votesFor = votesFor;
    }

    public int getVotesAgainst() {
        return votesAgainst;
    }

    public void setVotesAgainst(int votesAgainst) {
        this.votesAgainst = votesAgainst;
    }

    public int getVotesAbstain() {
        return votesAbstain;
    }

    public void setVotesAbstain(int votesAbstain) {
        this.votesAbstain = votesAbstain;
    }

    public Optional<Instant> getEnactedAt() {
        return Optional.ofNullable(enactedAt);
    }

    public void setEnactedAt(Instant enactedAt) {
        this.enactedAt = enactedAt;
    }

    /**
     * 获取总投票数
     */
    public int getTotalVotes() {
        return votesFor + votesAgainst + votesAbstain;
    }

    /**
     * 计算支持率
     */
    public double getSupportRate() {
        int total = getTotalVotes();
        if (total == 0) {
            return 0.0;
        }
        return (double) votesFor / total;
    }

    /**
     * 检查是否通过（简单多数）
     */
    public boolean isPassed() {
        return votesFor > votesAgainst;
    }

    /**
     * 检查是否通过（指定比例）
     */
    public boolean isPassed(double requiredRate) {
        return getSupportRate() >= requiredRate;
    }

    /**
     * 检查投票是否正在进行中
     */
    public boolean isVotingActive() {
        if (votingStartsAt == null || votingEndsAt == null) {
            return false;
        }
        Instant now = Instant.now();
        return now.isAfter(votingStartsAt) && now.isBefore(votingEndsAt);
    }

    /**
     * 检查投票是否已结束
     */
    public boolean isVotingEnded() {
        return votingEndsAt != null && Instant.now().isAfter(votingEndsAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bill bill = (Bill) o;
        return billId == bill.billId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId);
    }

    @Override
    public String toString() {
        return "Bill{" +
                "billId=" + billId +
                ", title='" + title + '\'' +
                ", billType=" + billType +
                ", status=" + status +
                ", votesFor=" + votesFor +
                ", votesAgainst=" + votesAgainst +
                '}';
    }
}
