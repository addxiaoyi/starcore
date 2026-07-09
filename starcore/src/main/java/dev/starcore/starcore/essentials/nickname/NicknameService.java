package dev.starcore.starcore.essentials.nickname;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 昵称系统服务
 * 管理玩家昵称
 */
public final class NicknameService {
    // 昵称存储 UUID -> 昵称
    private final ConcurrentHashMap<UUID, String> nicknames = new ConcurrentHashMap<>();

    // 反向查找 昵称 -> UUID（小写）
    private final ConcurrentHashMap<String, UUID> nicknameToUUID = new ConcurrentHashMap<>();

    /**
     * 设置昵称
     */
    public boolean setNickname(Player player, String nickname) {
        // 验证昵称
        if (!isValidNickname(nickname)) {
            return false;
        }

        String lowerNickname = nickname.toLowerCase();

        // 检查昵称是否已被使用
        UUID existingOwner = nicknameToUUID.get(lowerNickname);
        if (existingOwner != null && !existingOwner.equals(player.getUniqueId())) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        // 移除旧昵称
        String oldNickname = nicknames.get(playerId);
        if (oldNickname != null) {
            nicknameToUUID.remove(oldNickname.toLowerCase());
        }

        // 设置新昵称
        nicknames.put(playerId, nickname);
        nicknameToUUID.put(lowerNickname, playerId);

        // 更新显示名称
        player.setDisplayName(nickname);
        player.setPlayerListName(nickname);

        return true;
    }

    /**
     * 移除昵称
     */
    public boolean removeNickname(Player player) {
        UUID playerId = player.getUniqueId();
        String nickname = nicknames.remove(playerId);

        if (nickname == null) {
            return false;
        }

        nicknameToUUID.remove(nickname.toLowerCase());

        // 恢复原名
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());

        return true;
    }

    /**
     * 获取昵称
     */
    public Optional<String> getNickname(UUID playerId) {
        return Optional.ofNullable(nicknames.get(playerId));
    }

    /**
     * 通过昵称查找玩家
     */
    public Optional<UUID> getPlayerByNickname(String nickname) {
        if (nickname == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(nicknameToUUID.get(nickname.toLowerCase()));
    }

    /**
     * 检查昵称是否有效
     */
    private boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }

        // 长度限制
        if (nickname.length() < 2 || nickname.length() > 16) {
            return false;
        }

        // 移除颜色代码后检查
        String stripped = nickname.replaceAll("§[0-9a-fk-or]", "");
        if (stripped.length() < 2) {
            return false;
        }

        // 允许字母、数字、下划线、空格、颜色代码
        return nickname.matches("^[a-zA-Z0-9_§ ]+$");
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName(UUID playerId, String realName) {
        return nicknames.getOrDefault(playerId, realName);
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        String nickname = nicknames.remove(playerId);
        if (nickname != null) {
            nicknameToUUID.remove(nickname.toLowerCase());
        }
    }

    /**
     * 加载玩家数据
     */
    public void loadPlayerData(UUID playerId, String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            nicknames.put(playerId, nickname);
            nicknameToUUID.put(nickname.toLowerCase(), playerId);
        }
    }

    /**
     * 保存玩家数据
     */
    public String getPlayerData(UUID playerId) {
        return nicknames.get(playerId);
    }

    /**
     * 获取所有昵称
     */
    public Map<UUID, String> getAllNicknames() {
        return new HashMap<>(nicknames);
    }
}
