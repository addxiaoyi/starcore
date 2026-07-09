package dev.starcore.starcore.foundation.message;

public interface MessageService {
    String format(String key, Object... args);

    /**
     * 获取消息（兼容旧代码）
     */
    default String get(String category, String key) {
        return format(category + "." + key);
    }

    default void reload() {
    }
}
