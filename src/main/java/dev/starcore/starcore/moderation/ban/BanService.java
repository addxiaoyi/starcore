package dev.starcore.starcore.moderation.ban;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * E-099: 封禁服务（放逐系统）- 支持持久化到文件，防止服务器重启后封禁丢失
 */
public final class BanService {
    // E-099: 封禁记录（玩家UUID -> 封禁记录）- 内存存储
    private final Map<UUID, BanRecord> bannedPlayers = new ConcurrentHashMap<>();
    // E-099: IP封禁记录（IP -> 封禁记录）
    private final Map<String, BanRecord> bannedIPs = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Logger logger;
    private final BanPersistence persistence;

    public BanService(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.persistence = new BanPersistence(plugin);
        // E-099: 启动时加载持久化数据
        loadAll();
    }

    private void loadAll() {
        List<BanRecord> records = persistence.loadAll();
        for (BanRecord record : records) {
            if (record.getPlayerId() != null) {
                bannedPlayers.put(record.getPlayerId(), record);
            }
            if (record.isIpBan() && record.getIpAddress() != null) {
                bannedIPs.put(record.getIpAddress(), record);
            }
        }
        logger.info("[放逐] 已加载 " + bannedPlayers.size() + " 条封禁记录");
    }

    private void saveAll() {
        persistence.saveAll(new ArrayList<>(bannedPlayers.values()));
    }

    /**
     * 封禁玩家
     * E-099: 持久化封禁记录; E-100: Bukkit API 必须在主线程执行
     */
    public BanRecord banPlayer(UUID playerId, String playerName, UUID bannedBy, String reason, long duration, boolean ipBan, String ipAddress) {
        // 创建封禁记录
        BanRecord record = new BanRecord(playerId, playerName, bannedBy, reason, duration, ipBan, ipAddress);
        bannedPlayers.put(playerId, record);

        // 如果是IP封禁，也记录IP
        if (ipBan && ipAddress != null) {
            bannedIPs.put(ipAddress, record);
        }

        // E-099: 持久化
        persistence.saveAll(new ArrayList<>(bannedPlayers.values()));

        // E-100: kickPlayer 必须在主线程执行
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kickPlayer(getKickMessage(record));
            });
        }

        // 全服广播
        broadcastBan(record);

        return record;
    }

    /**
     * 临时封禁
     */
    public BanRecord banTemp(UUID playerId, String playerName, UUID bannedBy, String reason, long duration, TimeUnit unit) {
        long durationMillis = unit.toMillis(duration);
        return banPlayer(playerId, playerName, bannedBy, reason, durationMillis, false, null);
    }

    /**
     * 永久封禁
     */
    public BanRecord banPermanent(UUID playerId, String playerName, UUID bannedBy, String reason) {
        return banPlayer(playerId, playerName, bannedBy, reason, -1, false, null);
    }

    /**
     * IP封禁
     */
    public BanRecord banIP(UUID playerId, String playerName, String ipAddress, UUID bannedBy, String reason, long duration) {
        return banPlayer(playerId, playerName, bannedBy, reason, duration, true, ipAddress);
    }

    /**
     * 解除封禁
     */
    public boolean unbanPlayer(UUID playerId) {
        BanRecord record = bannedPlayers.remove(playerId);
        if (record != null) {
            record.unban();

            // 如果是IP封禁，也解除IP
            if (record.isIpBan() && record.getIpAddress() != null) {
                bannedIPs.remove(record.getIpAddress());
            }

            // E-099: 持久化
            persistence.saveAll(new ArrayList<>(bannedPlayers.values()));

            broadcastUnban(record);
            return true;
        }
        return false;
    }

    /**
     * 解除IP封禁
     */
    public boolean unbanIP(String ipAddress) {
        BanRecord record = bannedIPs.remove(ipAddress);
        if (record != null) {
            bannedPlayers.remove(record.getPlayerId());
            record.unban();
            return true;
        }
        return false;
    }

    /**
     * 检查玩家是否被封禁
     */
    public boolean isBanned(UUID playerId) {
        BanRecord record = bannedPlayers.get(playerId);
        if (record == null) return false;

        // 检查是否过期
        if (record.isExpired()) {
            bannedPlayers.remove(playerId);
            return false;
        }

        return record.isActive();
    }

    /**
     * 检查IP是否被封禁
     */
    public boolean isIPBanned(String ipAddress) {
        BanRecord record = bannedIPs.get(ipAddress);
        if (record == null) return false;

        // 检查是否过期
        if (record.isExpired()) {
            bannedIPs.remove(ipAddress);
            return false;
        }

        return record.isActive();
    }

    /**
     * 获取封禁记录
     */
    public BanRecord getBanRecord(UUID playerId) {
        BanRecord record = bannedPlayers.get(playerId);
        if (record != null && record.isExpired()) {
            bannedPlayers.remove(playerId);
            return null;
        }
        return record;
    }

    /**
     * 获取所有被封禁的玩家
     */
    public List<BanRecord> getAllBannedPlayers() {
        // 清理过期记录
        bannedPlayers.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return new ArrayList<>(bannedPlayers.values());
    }

    /**
     * 格式化剩余时间
     */
    public String formatRemainingTime(BanRecord record) {
        if (record.isPermanent()) {
            return "永久";
        }

        long remaining = record.getRemainingTime();
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天" + (hours % 24) + "小时";
        } else if (hours > 0) {
            return hours + "小时" + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 获取踢出消息
     */
    private String getKickMessage(BanRecord record) {
        StringBuilder message = new StringBuilder();
        message.append("§c╔════════════════════════════════╗\n");
        message.append("§c║                                ║\n");
        message.append("§c║     §f你已被 §c§l放逐 §f出服务器     §c║\n");
        message.append("§c║                                ║\n");
        message.append("§c╚════════════════════════════════╝\n");
        message.append("\n");
        message.append("§f原因: §7").append(record.getReason()).append("\n");
        message.append("§f时长: §e").append(formatRemainingTime(record)).append("\n");
        if (record.isIpBan()) {
            message.append("§c类型: IP封禁\n");
        }
        message.append("\n");
        message.append("§7如有异议，请联系管理员\n");
        return message.toString();
    }

    /**
     * 广播封禁消息
     */
    private void broadcastBan(BanRecord record) {
        String duration = record.isPermanent() ? "永久" : formatRemainingTime(record);
        String type = record.isIpBan() ? "[IP封禁]" : "[封禁]";
        String message = String.format(
            "§c%s §f玩家 §e%s §f被放逐 §c%s §f原因: §7%s",
            type,
            record.getPlayerName(),
            duration,
            record.getReason()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 广播解除封禁消息
     */
    private void broadcastUnban(BanRecord record) {
        String message = String.format(
            "§a[放逐] §f玩家 §e%s §f已被解除封禁",
            record.getPlayerName()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 清理过期记录
     */
    public int cleanupExpired() {
        int count = 0;

        // 清理玩家封禁
        Iterator<Map.Entry<UUID, BanRecord>> playerIterator = bannedPlayers.entrySet().iterator();
        while (playerIterator.hasNext()) {
            if (playerIterator.next().getValue().isExpired()) {
                playerIterator.remove();
                count++;
            }
        }

        // 清理IP封禁
        Iterator<Map.Entry<String, BanRecord>> ipIterator = bannedIPs.entrySet().iterator();
        while (ipIterator.hasNext()) {
            if (ipIterator.next().getValue().isExpired()) {
                ipIterator.remove();
                count++;
            }
        }

        // E-099: 持久化清理结果
        if (count > 0) {
            persistence.saveAll(new ArrayList<>(bannedPlayers.values()));
        }

        return count;
    }

    /**
     * E-099: 封禁持久化 - 将封禁记录保存到文件
     */
    private static class BanPersistence {
        private final java.nio.file.Path banFile;
        private final com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

        BanPersistence(Plugin plugin) {
            this.banFile = plugin.getDataFolder().toPath().resolve("bans.json");
        }

        void saveAll(List<BanRecord> records) {
            try {
                java.nio.file.Files.createDirectories(banFile.getParent());
                String json = gson.toJson(records);
                java.nio.file.Files.writeString(banFile, json);
            } catch (Exception e) {
                // 静默失败，避免封禁功能因 IO 问题中断
            }
        }

        List<BanRecord> loadAll() {
            try {
                if (java.nio.file.Files.exists(banFile)) {
                    String json = java.nio.file.Files.readString(banFile);
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<BannedPlayerData>>(){}.getType();
                    List<BannedPlayerData> dataList = gson.fromJson(json, type);
                    if (dataList == null) return List.of();
                    List<BanRecord> records = new ArrayList<>();
                    for (BannedPlayerData data : dataList) {
                        records.add(data.toRecord());
                    }
                    return records;
                }
            } catch (Exception e) {
                // 加载失败返回空列表
            }
            return List.of();
        }

        private static class BannedPlayerData {
            String playerId;
            String playerName;
            String bannedBy;
            String reason;
            long duration;
            boolean ipBan;
            String ipAddress;
            long banTime;  // 使用 BanRecord 的 banTime 字段

            BanRecord toRecord() {
                return new BanRecord(
                    playerId != null ? java.util.UUID.fromString(playerId) : null,
                    playerName, bannedBy != null ? java.util.UUID.fromString(bannedBy) : null,
                    reason, duration, ipBan, ipAddress
                );
            }
        }
    }
}
