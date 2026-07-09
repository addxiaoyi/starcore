package dev.starcore.starcore.module.policy.model;

import java.util.Optional;

public record PolicyActivationResult(
    boolean successful,
    PolicyActivationFailure failure,
    PolicyDefinition policyDefinition,
    String message
) {
    public static PolicyActivationResult success(PolicyDefinition definition) {
        return new PolicyActivationResult(true, null, definition, "Policy activated");
    }

    public static PolicyActivationResult failure(PolicyActivationFailure failure, PolicyDefinition definition, String message) {
        return new PolicyActivationResult(false, failure, definition, message);
    }

    public Optional<PolicyActivationFailure> failureReason() {
        return Optional.ofNullable(failure);
    }

    public Optional<PolicyDefinition> definition() {
        return Optional.ofNullable(policyDefinition);
    }
}
