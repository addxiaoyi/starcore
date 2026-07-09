package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;

import java.util.List;

public record NationResourceDistrictMigrationResult(
    boolean success,
    String code,
    String message,
    NationResourceDistrictSnapshot snapshot,
    ClaimSelectionExplanation explanation
) {
    public NationResourceDistrictMigrationResult {
        code = code == null || code.isBlank() ? (success ? "success" : "error") : code.trim();
        message = message == null ? "" : message;
        explanation = explanation == null ? basicExplanation(success, code, message) : explanation;
    }

    public NationResourceDistrictMigrationResult(
        boolean success,
        String code,
        String message,
        NationResourceDistrictSnapshot snapshot
    ) {
        this(success, code, message, snapshot, null);
    }

    public static NationResourceDistrictMigrationResult success(String code, String message, NationResourceDistrictSnapshot snapshot) {
        return new NationResourceDistrictMigrationResult(true, code, message, snapshot);
    }

    public static NationResourceDistrictMigrationResult success(
        String code,
        String message,
        NationResourceDistrictSnapshot snapshot,
        ClaimSelectionExplanation explanation
    ) {
        return new NationResourceDistrictMigrationResult(true, code, message, snapshot, explanation);
    }

    public static NationResourceDistrictMigrationResult failure(String code, String message, NationResourceDistrictSnapshot snapshot) {
        return new NationResourceDistrictMigrationResult(false, code, message, snapshot);
    }

    public static NationResourceDistrictMigrationResult failure(
        String code,
        String message,
        NationResourceDistrictSnapshot snapshot,
        ClaimSelectionExplanation explanation
    ) {
        return new NationResourceDistrictMigrationResult(false, code, message, snapshot, explanation);
    }

    private static ClaimSelectionExplanation basicExplanation(boolean success, String code, String message) {
        String state = code == null || code.isBlank() ? (success ? "success" : "error") : code.trim();
        String summary = message == null ? "" : message;
        return ClaimSelectionExplanation.of(
            state,
            success ? "success" : "error",
            summary,
            summary.isBlank() ? List.of() : List.of(ClaimSelectionReason.of(state, summary))
        );
    }
}
