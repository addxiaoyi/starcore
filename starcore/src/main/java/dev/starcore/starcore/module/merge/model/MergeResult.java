package dev.starcore.starcore.module.merge.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;

/**
 * 合并结果模型
 * 记录合并操作的结果
 */
public final class MergeResult {
    private final UUID id;
    private final NationId resultNationId;
    private final String resultNationName;
    private final List<NationId> mergedNationIds;
    private final List<String> mergedNationNames;
    private final UUID initiatorId;
    private final String initiatorName;
    private final Instant mergedAt;
    private final int totalMembers;
    private final int totalClaims;

    public MergeResult(
            UUID id,
            NationId resultNationId,
            String resultNationName,
            List<NationId> mergedNationIds,
            List<String> mergedNationNames,
            UUID initiatorId,
            String initiatorName,
            Instant mergedAt,
            int totalMembers,
            int totalClaims
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.resultNationId = Objects.requireNonNull(resultNationId, "resultNationId");
        this.resultNationName = Objects.requireNonNull(resultNationName, "resultNationName");
        this.mergedNationIds = List.copyOf(mergedNationIds);
        this.mergedNationNames = List.copyOf(mergedNationNames);
        this.initiatorId = Objects.requireNonNull(initiatorId, "initiatorId");
        this.initiatorName = Objects.requireNonNull(initiatorName, "initiatorName");
        this.mergedAt = Objects.requireNonNull(mergedAt, "mergedAt");
        this.totalMembers = totalMembers;
        this.totalClaims = totalClaims;
    }

    public static MergeResult create(
            NationId resultNationId,
            String resultNationName,
            Collection<NationId> mergedNationIds,
            Collection<String> mergedNationNames,
            UUID initiatorId,
            String initiatorName,
            int totalMembers,
            int totalClaims
    ) {
        return new MergeResult(
                UUID.randomUUID(),
                resultNationId,
                resultNationName,
                new ArrayList<>(mergedNationIds),
                new ArrayList<>(mergedNationNames),
                initiatorId,
                initiatorName,
                Instant.now(),
                totalMembers,
                totalClaims
        );
    }

    public UUID id() { return id; }
    public NationId resultNationId() { return resultNationId; }
    public String resultNationName() { return resultNationName; }
    public List<NationId> mergedNationIds() { return mergedNationIds; }
    public List<String> mergedNationNames() { return mergedNationNames; }
    public UUID initiatorId() { return initiatorId; }
    public String initiatorName() { return initiatorName; }
    public Instant mergedAt() { return mergedAt; }
    public int totalMembers() { return totalMembers; }
    public int totalClaims() { return totalClaims; }

    public int nationCount() { return mergedNationIds.size(); }
}
