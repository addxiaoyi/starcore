package dev.starcore.starcore.module.nation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ClaimSelectionReason(
    String code,
    String message,
    Map<String, String> details
) {
    public ClaimSelectionReason {
        code = code == null || code.isBlank() ? "info" : code.trim();
        message = message == null ? "" : message;
        LinkedHashMap<String, String> safeDetails = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    safeDetails.put(key, value == null ? "" : value);
                }
            });
        }
        details = Collections.unmodifiableMap(safeDetails);
    }

    public static ClaimSelectionReason of(String code, String message) {
        return new ClaimSelectionReason(code, message, Map.of());
    }

    public static ClaimSelectionReason of(String code, String message, Map<String, String> details) {
        return new ClaimSelectionReason(code, message, details);
    }
}
