package dev.starcore.starcore.module.policy.model;

public enum PolicyActivationFailure {
    UNKNOWN_POLICY,
    ALREADY_ACTIVE,
    CONFLICTING_POLICY,
    MISSING_PREREQUISITE,
    ON_COOLDOWN,
    MISSING_TREASURY_SERVICE,
    INSUFFICIENT_TREASURY,

    // Backward-compatible names retained for older tests and integrations.
    COOLDOWN_NOT_EXPIRED,
    PREREQUISITES_NOT_MET,
    MUTUALLY_EXCLUSIVE,
    NOT_LEADER
}
