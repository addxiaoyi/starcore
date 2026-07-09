package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 和平条约
 * 战争结束时签订的条约，规定战后条款
 */
public final class PeaceTreaty {
    private final UUID id;
    private final UUID warId;
    private final NationId victor;          // 胜利方
    private final NationId defeated;        // 失败方
    private final List<PeaceTerm> terms;    // 条款
    private PeaceTreatyStatus status;
    private final Instant proposedAt;
    private Instant acceptedAt;
    private Instant rejectedAt;

    public PeaceTreaty(
        UUID id,
        UUID warId,
        NationId victor,
        NationId defeated,
        List<PeaceTerm> terms,
        Instant proposedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.victor = Objects.requireNonNull(victor, "victor");
        this.defeated = Objects.requireNonNull(defeated, "defeated");
        this.terms = new ArrayList<>(Objects.requireNonNull(terms, "terms"));
        this.status = PeaceTreatyStatus.PROPOSED;
        this.proposedAt = Objects.requireNonNull(proposedAt, "proposedAt");
    }

    public UUID id() {
        return id;
    }

    public UUID warId() {
        return warId;
    }

    public NationId victor() {
        return victor;
    }

    public NationId defeated() {
        return defeated;
    }

    public List<PeaceTerm> terms() {
        return Collections.unmodifiableList(terms);
    }

    public PeaceTreatyStatus status() {
        return status;
    }

    public Instant proposedAt() {
        return proposedAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant rejectedAt() {
        return rejectedAt;
    }

    /**
     * 接受条约
     */
    public void accept() {
        if (status != PeaceTreatyStatus.PROPOSED) {
            throw new IllegalStateException("Treaty is not in proposed state");
        }
        this.status = PeaceTreatyStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    /**
     * 拒绝条约
     */
    public void reject() {
        if (status != PeaceTreatyStatus.PROPOSED) {
            throw new IllegalStateException("Treaty is not in proposed state");
        }
        this.status = PeaceTreatyStatus.REJECTED;
        this.rejectedAt = Instant.now();
    }

    /**
     * 计算条约严厉程度 (0-100)
     */
    public int severityScore() {
        int score = 0;
        for (PeaceTerm term : terms) {
            score += term.severityScore();
        }
        return Math.min(100, score);
    }

    /**
     * 获取赔款总额
     */
    public BigDecimal totalReparations() {
        return terms.stream()
            .filter(term -> term instanceof ReparationTerm)
            .map(term -> ((ReparationTerm) term).amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 是否包含领土割让
     */
    public boolean includesTerritorialCession() {
        return terms.stream().anyMatch(term -> term instanceof TerritorialCessionTerm);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeaceTreaty other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("PeaceTreaty{id=%s, victor=%s, defeated=%s, status=%s, terms=%d}",
            id, victor, defeated, status, terms.size());
    }

    /**
     * 和平条约状态
     */
    public enum PeaceTreatyStatus {
        PROPOSED("提议中"),
        ACCEPTED("已接受"),
        REJECTED("已拒绝"),
        EXPIRED("已过期");

        private final String displayName;

        PeaceTreatyStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 和平条款基类
     */
    public interface PeaceTerm {
        String description();
        int severityScore();
    }

    /**
     * 战争赔款条款
     */
    public record ReparationTerm(
        BigDecimal amount,
        int installments  // 分期付款次数
    ) implements PeaceTerm {
        @Override
        public String description() {
            if (installments > 1) {
                return String.format("支付战争赔款 %s (分 %d 期)", amount, installments);
            } else {
                return String.format("支付战争赔款 %s", amount);
            }
        }

        @Override
        public int severityScore() {
            return Math.min(50, amount.divide(BigDecimal.valueOf(1000), BigDecimal.ROUND_DOWN).intValue());
        }
    }

    /**
     * 领土割让条款
     */
    public record TerritorialCessionTerm(
        List<String> territoryIds,
        String description
    ) implements PeaceTerm {
        @Override
        public int severityScore() {
            return territoryIds.size() * 10;
        }
    }

    /**
     * 军事限制条款
     */
    public record MilitaryRestrictionTerm(
        int maxArmySize,
        boolean disarmament
    ) implements PeaceTerm {
        @Override
        public String description() {
            if (disarmament) {
                return "完全解除武装";
            } else {
                return String.format("军队规模限制在 %d 人以下", maxArmySize);
            }
        }

        @Override
        public int severityScore() {
            return disarmament ? 30 : 15;
        }
    }

    /**
     * 政治条款
     */
    public record PoliticalTerm(
        String requirement,
        String description
    ) implements PeaceTerm {
        @Override
        public int severityScore() {
            return 10;
        }
    }

    /**
     * 资源贡纳条款
     */
    public record ResourceTributeTerm(
        String resourceType,
        int amount,
        int durationMonths
    ) implements PeaceTerm {
        @Override
        public String description() {
            return String.format("每月贡纳 %d %s，持续 %d 个月", amount, resourceType, durationMonths);
        }

        @Override
        public int severityScore() {
            return Math.min(20, durationMonths / 6);
        }
    }
}
