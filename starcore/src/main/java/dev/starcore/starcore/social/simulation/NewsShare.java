package dev.starcore.starcore.social.simulation;

import java.util.UUID;

/**
 * 新闻分享记录
 */
public record NewsShare(
    String newsId,
    UUID sharer,
    UUID recipient,
    ShareType type,
    long timestamp
) {}
