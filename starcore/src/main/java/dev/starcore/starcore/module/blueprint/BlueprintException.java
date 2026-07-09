package dev.starcore.starcore.module.blueprint;

/**
 * 蓝图异常
 */
public class BlueprintException extends RuntimeException {
    public BlueprintException(String message) {
        super(message);
    }

    public BlueprintException(String message, Throwable cause) {
        super(message, cause);
    }
}
