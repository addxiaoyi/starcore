package dev.starcore.starcore.foundation.epoch;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.message.MessageService;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class EpochService {
    private final ConfigurationService configuration;
    private final MessageService messages;

    public EpochService(ConfigurationService configuration, MessageService messages) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public EpochSnapshot snapshot() {
        return snapshot(Instant.now());
    }

    public EpochSnapshot snapshot(Instant now) {
        Objects.requireNonNull(now, "now");
        // 注意：configuredStart 和 now 都应为 UTC Instant；跨时区部署时若配置文件中
        // 使用本地时间字符串，需要确保解析为 UTC Instant 后再传入本方法，否则 epoch
        // 计算结果会偏移服务器时区对应的小时数。
        boolean enabled = configuration.epochEnabled();
        Instant configuredStart = configuration.epochStartTime();
        Duration duration = positiveDuration(configuration.epochDuration());
        long durationSeconds = duration.getSeconds();
        long elapsedSeconds = Math.max(0L, Duration.between(configuredStart, now).getSeconds());
        long offset = elapsedSeconds / durationSeconds;
        long epochNumber = offset + 1L;
        Instant epochStart = configuredStart.plusSeconds(offset * durationSeconds);
        Instant nextEpochStart = epochStart.plusSeconds(durationSeconds);
        Duration remaining = Duration.between(now, nextEpochStart);
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
        return new EpochSnapshot(enabled, epochNumber, epochStart, nextEpochStart, duration, remaining);
    }

    public String summary() {
        EpochSnapshot snapshot = snapshot();
        if (!snapshot.enabled()) {
            return messages.format("epoch.summary.disabled");
        }
        return messages.format("epoch.summary.active", snapshot.epochNumber(), localizedDuration(snapshot.remaining()));
    }

    public String localizedDuration(Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder builder = new StringBuilder();
        appendUnit(builder, days, messages.format("epoch.duration.day"));
        appendUnit(builder, hours, messages.format("epoch.duration.hour"));
        appendUnit(builder, minutes, messages.format("epoch.duration.minute"));
        appendUnit(builder, seconds, messages.format("epoch.duration.second"));
        return builder.isEmpty() ? messages.format("epoch.duration.zero") : builder.toString().trim();
    }

    public static String humanDuration(Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder builder = new StringBuilder();
        appendUnit(builder, days, "天");
        appendUnit(builder, hours, "小时");
        appendUnit(builder, minutes, "分钟");
        appendUnit(builder, seconds, "秒");
        return builder.isEmpty() ? "0秒" : builder.toString().trim();
    }

    private Duration positiveDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return Duration.ofDays(1);
        }
        return duration;
    }

    private static void appendUnit(StringBuilder builder, long value, String unit) {
        if (value <= 0L) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value).append(unit);
    }
}
