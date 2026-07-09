package dev.starcore.starcore.moderation;
import java.util.Optional;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理服务
 * 处理禁言、封禁、踢出等
 */
public final class ModerationService {
    // 禁言列表 UUID -> MuteInfo
    private final ConcurrentHashMap<UUID, MuteInfo> mutedPlayers = new ConcurrentHashMap<>();

    // 监狱列表 UUID -> JailInfo
    private final ConcurrentHashMap<UUID, JailInfo> jailedPlayers = new ConcurrentHashMap<>();

    /**
     * 禁言玩家
     */
    public boolean mutePlayer(UUID playerId, Duration duration, String reason, String moderator) {
        Instant expiresAt = duration != null ? Instant.now().plus(duration) : null;

        MuteInfo muteInfo = new MuteInfo(
            playerId,
            Instant.now(),
            expiresAt,
            reason,
            moderator
        );

        mutedPlayers.put(playerId, muteInfo);

        return true;
    }

    /**
     * 解除禁言
     */
    public boolean unmutePlayer(UUID playerId) {
        return mutedPlayers.remove(playerId) != null;
    }

    /**
     * 检查是否被禁言
     */
    public boolean isMuted(UUID playerId) {
        MuteInfo mute = mutedPlayers.get(playerId);

        if (mute == null) {
            return false;
        }

        // 检查是否过期
        if (mute.expiresAt() != null && Instant.now().isAfter(mute.expiresAt())) {
            mutedPlayers.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * 获取禁言信息
     */
    public Optional<MuteInfo> getMuteInfo(UUID playerId) {
        return Optional.ofNullable(mutedPlayers.get(playerId));
    }

    /**
     * 封禁玩家
     */
    public boolean banPlayer(OfflinePlayer player, Duration duration, String reason, String moderator) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);

        Date expires = duration != null
            ? Date.from(Instant.now().plus(duration))
            : null;

        banList.addBan(
            player.getName(),
            reason,
            expires,
            moderator
        );

        // 如果在线则踢出
        if (player.isOnline()) {
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer != null) {
                onlinePlayer.kick(net.kyori.adventure.text.Component.text(
                    "你已被封禁\n原因: " + reason +
                    (expires != null ? "\n到期时间: " + expires : "\n永久封禁")
                ));
            }
        }

        return true;
    }

    /**
     * 解封玩家
     */
    public boolean unbanPlayer(String playerName) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.pardon(playerName);
        return true;
    }

    /**
     * 踢出玩家
     */
    public boolean kickPlayer(Player player, String reason) {
        player.kick(net.kyori.adventure.text.Component.text(
            "你已被踢出服务器\n" + (reason != null ? "原因: " + reason : "")
        ));
        return true;
    }

    /**
     * 关入监狱
     */
    public boolean jailPlayer(UUID playerId, Duration duration, String reason, String moderator) {
        Instant expiresAt = duration != null ? Instant.now().plus(duration) : null;

        JailInfo jailInfo = new JailInfo(
            playerId,
            Instant.now(),
            expiresAt,
            reason,
            moderator
        );

        jailedPlayers.put(playerId, jailInfo);

        return true;
    }

    /**
     * 释放出狱
     */
    public boolean unjailPlayer(UUID playerId) {
        return jailedPlayers.remove(playerId) != null;
    }

    /**
     * 检查是否在监狱
     */
    public boolean isJailed(UUID playerId) {
        JailInfo jail = jailedPlayers.get(playerId);

        if (jail == null) {
            return false;
        }

        // 检查是否过期
        if (jail.expiresAt() != null && Instant.now().isAfter(jail.expiresAt())) {
            jailedPlayers.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * 获取监狱信息
     */
    public Optional<JailInfo> getJailInfo(UUID playerId) {
        return Optional.ofNullable(jailedPlayers.get(playerId));
    }

    /**
     * 清理过期记录
     */
    public void cleanupExpired() {
        Instant now = Instant.now();

        // 清理过期禁言
        mutedPlayers.entrySet().removeIf(entry -> {
            MuteInfo mute = entry.getValue();
            return mute.expiresAt() != null && now.isAfter(mute.expiresAt());
        });

        // 清理过期监狱
        jailedPlayers.entrySet().removeIf(entry -> {
            JailInfo jail = entry.getValue();
            return jail.expiresAt() != null && now.isAfter(jail.expiresAt());
        });
    }

    /**
     * 禁言信息
     */
    public record MuteInfo(
        UUID playerId,
        Instant mutedAt,
        Instant expiresAt,
        String reason,
        String moderator
    ) {
        public boolean isPermanent() {
            return expiresAt == null;
        }

        public Duration getRemaining() {
            if (isPermanent()) {
                return null;
            }
            return Duration.between(Instant.now(), expiresAt);
        }
    }

    /**
     * 监狱信息
     */
    public record JailInfo(
        UUID playerId,
        Instant jailedAt,
        Instant expiresAt,
        String reason,
        String moderator
    ) {
        public boolean isPermanent() {
            return expiresAt == null;
        }

        public Duration getRemaining() {
            if (isPermanent()) {
                return null;
            }
            return Duration.between(Instant.now(), expiresAt);
        }
    }
}
