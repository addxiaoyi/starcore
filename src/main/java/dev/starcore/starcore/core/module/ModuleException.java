package dev.starcore.starcore.core.module;

import java.util.Objects;

public class ModuleException extends RuntimeException {
    public ModuleException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public ModuleException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
    }
}
