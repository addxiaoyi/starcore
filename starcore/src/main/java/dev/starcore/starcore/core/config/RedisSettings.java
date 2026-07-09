package dev.starcore.starcore.core.config;

public record RedisSettings(
    boolean enabled,
    String host,
    int port,
    String password,
    int database
) {
    public static RedisSettings from(ConfigurationService configuration) {
        return new RedisSettings(
            configuration.redisEnabled(),
            configuration.redisHost(),
            configuration.redisPort(),
            configuration.redisPassword(),
            configuration.redisDatabase()
        );
    }

    public static RedisSettings disabled() {
        return new RedisSettings(false, "127.0.0.1", 6379, "", 0);
    }

    public String summary(boolean connected) {
        if (!enabled) {
            return "已关闭";
        }
        String status = connected ? "已连接" : "未连接";
        String auth = password != null && !password.isBlank() ? "***" : "无密码";
        return "%s %s (%s:%d/%d, %s)".formatted(status, connected ? "运行中" : "连接中", host, port, database, auth);
    }
}
