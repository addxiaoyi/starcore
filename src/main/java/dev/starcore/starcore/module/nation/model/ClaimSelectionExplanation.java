package dev.starcore.starcore.module.nation.model;

import java.util.List;

public record ClaimSelectionExplanation(
    String state,
    String severity,
    String summary,
    List<ClaimSelectionReason> reasons
) {
    public ClaimSelectionExplanation {
        state = state == null || state.isBlank() ? "unknown" : state.trim();
        severity = severity == null || severity.isBlank() ? "info" : severity.trim();
        summary = summary == null ? "" : summary;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static ClaimSelectionExplanation of(String state, String severity, String summary, List<ClaimSelectionReason> reasons) {
        return new ClaimSelectionExplanation(state, severity, summary, reasons);
    }

    public static ClaimSelectionExplanation basic(boolean canSubmit, String message) {
        return new ClaimSelectionExplanation(
            canSubmit ? "ready" : "blocked",
            canSubmit ? "success" : "error",
            message,
            message == null || message.isBlank()
                ? List.of()
                : List.of(ClaimSelectionReason.of(canSubmit ? "ready" : "blocked", message))
        );
    }
}
