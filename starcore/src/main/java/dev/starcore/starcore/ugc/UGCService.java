package dev.starcore.starcore.ugc;
import java.util.Optional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UGC（用户生成内容）系统
 */
public final class UGCService {
    // 所有提交
    private final ConcurrentHashMap<UUID, UGCSubmission> submissions = new ConcurrentHashMap<>();

    // 按状态索引
    private final ConcurrentHashMap<SubmissionStatus, Set<UUID>> submissionsByStatus =
        new ConcurrentHashMap<>();

    /**
     * 提交内容
     */
    public UGCSubmission submitContent(UUID authorId, String name, String description, UGCType type, Object content) {
        UGCSubmission submission = new UGCSubmission(
            UUID.randomUUID(),
            authorId,
            name,
            description,
            type,
            content,
            Instant.now()
        );

        submissions.put(submission.getSubmissionId(), submission);
        addToStatusIndex(submission.getSubmissionId(), SubmissionStatus.PENDING);

        return submission;
    }

    /**
     * 审核通过
     */
    public boolean approveSubmission(UUID submissionId, UUID reviewerId) {
        UGCSubmission submission = submissions.get(submissionId);
        if (submission == null) {
            return false;
        }

        removeFromStatusIndex(submissionId, submission.getStatus());
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewerId(reviewerId);
        submission.setReviewedAt(Instant.now());
        addToStatusIndex(submissionId, SubmissionStatus.APPROVED);

        return true;
    }

    /**
     * 审核拒绝
     */
    public boolean rejectSubmission(UUID submissionId, UUID reviewerId, String reason) {
        UGCSubmission submission = submissions.get(submissionId);
        if (submission == null) {
            return false;
        }

        removeFromStatusIndex(submissionId, submission.getStatus());
        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setReviewerId(reviewerId);
        submission.setReviewedAt(Instant.now());
        submission.setRejectReason(reason);
        addToStatusIndex(submissionId, SubmissionStatus.REJECTED);

        return true;
    }

    /**
     * 获取提交
     */
    public Optional<UGCSubmission> getSubmission(UUID submissionId) {
        return Optional.ofNullable(submissions.get(submissionId));
    }

    /**
     * 获取指定状态的提交
     */
    public List<UGCSubmission> getSubmissionsByStatus(SubmissionStatus status) {
        Set<UUID> ids = submissionsByStatus.get(status);
        if (ids == null) {
            return List.of();
        }

        List<UGCSubmission> result = new ArrayList<>();
        for (UUID id : ids) {
            UGCSubmission submission = submissions.get(id);
            if (submission != null) {
                result.add(submission);
            }
        }

        return result;
    }

    /**
     * 获取玩家的提交
     */
    public List<UGCSubmission> getPlayerSubmissions(UUID authorId) {
        List<UGCSubmission> result = new ArrayList<>();
        for (UGCSubmission submission : submissions.values()) {
            if (submission.getAuthorId().equals(authorId)) {
                result.add(submission);
            }
        }
        return result;
    }

    private void addToStatusIndex(UUID submissionId, SubmissionStatus status) {
        submissionsByStatus.computeIfAbsent(status, k -> ConcurrentHashMap.newKeySet())
            .add(submissionId);
    }

    private void removeFromStatusIndex(UUID submissionId, SubmissionStatus status) {
        Set<UUID> ids = submissionsByStatus.get(status);
        if (ids != null) {
            ids.remove(submissionId);
        }
    }

    public static class UGCSubmission {
        private final UUID submissionId;
        private final UUID authorId;
        private final String name;
        private final String description;
        private final UGCType type;
        private final Object content;
        private final Instant submittedAt;

        private SubmissionStatus status;
        private UUID reviewerId;
        private Instant reviewedAt;
        private String rejectReason;

        private int likes;
        private int uses;

        public UGCSubmission(UUID submissionId, UUID authorId, String name, String description,
                           UGCType type, Object content, Instant submittedAt) {
            this.submissionId = submissionId;
            this.authorId = authorId;
            this.name = name;
            this.description = description;
            this.type = type;
            this.content = content;
            this.submittedAt = submittedAt;
            this.status = SubmissionStatus.PENDING;
            this.likes = 0;
            this.uses = 0;
        }

        public UUID getSubmissionId() { return submissionId; }
        public UUID getAuthorId() { return authorId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public UGCType getType() { return type; }
        public Object getContent() { return content; }
        public Instant getSubmittedAt() { return submittedAt; }
        public SubmissionStatus getStatus() { return status; }
        public void setStatus(SubmissionStatus status) { this.status = status; }
        public UUID getReviewerId() { return reviewerId; }
        public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }
        public Instant getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
        public String getRejectReason() { return rejectReason; }
        public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
        public int getLikes() { return likes; }
        public void addLike() { this.likes++; }
        public int getUses() { return uses; }
        public void addUse() { this.uses++; }
    }

    public enum UGCType {
        CUSTOM_KIT("自定义Kit"),
        ARENA_DESIGN("竞技场设计"),
        QUEST("任务"),
        SKIN("皮肤");

        private final String displayName;

        UGCType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SubmissionStatus {
        PENDING("待审核"),
        APPROVED("已通过"),
        REJECTED("已拒绝");

        private final String displayName;

        SubmissionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
