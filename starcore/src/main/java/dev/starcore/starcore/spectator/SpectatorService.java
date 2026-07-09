package dev.starcore.starcore.spectator;
import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 观战系统
 */
public final class SpectatorService {
    // 观战中的玩家 spectatorId -> targetId
    private final ConcurrentHashMap<UUID, UUID> spectators = new ConcurrentHashMap<>();

    // 被观战的玩家 targetId -> Set<spectatorId>
    private final ConcurrentHashMap<UUID, Set<UUID>> spectatorsByTarget = new ConcurrentHashMap<>();

    // 观战前的状态 spectatorId -> SpectatorState
    private final ConcurrentHashMap<UUID, SpectatorState> savedStates = new ConcurrentHashMap<>();

    /**
     * 开始观战
     * D-107: 加入 spectator != target 校验，防止玩家自观导致状态丢失
     */
    public boolean startSpectating(Player spectator, Player target) {
        UUID spectatorId = spectator.getUniqueId();
        UUID targetId = target.getUniqueId();

        // D-107: 禁止自观
        if (spectatorId.equals(targetId)) {
            return false;
        }

        // 检查是否已在观战
        if (spectators.containsKey(spectatorId)) {
            return false;
        }

        // 保存当前状态
        saveState(spectator);

        // 设置观战模式
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.teleport(target.getLocation());
        spectator.setSpectatorTarget(target);

        // 记录关系
        spectators.put(spectatorId, targetId);
        spectatorsByTarget.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet())
            .add(spectatorId);

        spectator.sendMessage("§a正在观战 " + target.getName());
        return true;
    }

    /**
     * 停止观战
     */
    public boolean stopSpectating(Player spectator) {
        UUID spectatorId = spectator.getUniqueId();
        UUID targetId = spectators.remove(spectatorId);

        if (targetId == null) {
            return false;
        }

        // 移除观战关系
        Set<UUID> targetSpectators = spectatorsByTarget.get(targetId);
        if (targetSpectators != null) {
            targetSpectators.remove(spectatorId);
            if (targetSpectators.isEmpty()) {
                spectatorsByTarget.remove(targetId);
            }
        }

        // 恢复状态
        restoreState(spectator);

        spectator.sendMessage("§a已停止观战");
        return true;
    }

    /**
     * 切换观战目标
     */
    public boolean switchTarget(Player spectator, Player newTarget) {
        UUID spectatorId = spectator.getUniqueId();
        UUID oldTargetId = spectators.get(spectatorId);

        if (oldTargetId == null) {
            return false;
        }

        UUID newTargetId = newTarget.getUniqueId();

        // 移除旧的观战关系
        Set<UUID> oldSpectators = spectatorsByTarget.get(oldTargetId);
        if (oldSpectators != null) {
            oldSpectators.remove(spectatorId);
        }

        // 添加新的观战关系
        spectators.put(spectatorId, newTargetId);
        spectatorsByTarget.computeIfAbsent(newTargetId, k -> ConcurrentHashMap.newKeySet())
            .add(spectatorId);

        // 切换目标
        spectator.teleport(newTarget.getLocation());
        spectator.setSpectatorTarget(newTarget);

        spectator.sendMessage("§a切换观战目标: " + newTarget.getName());
        return true;
    }

    /**
     * 检查是否在观战
     */
    public boolean isSpectating(UUID playerId) {
        return spectators.containsKey(playerId);
    }

    /**
     * 获取观战目标
     */
    public Optional<UUID> getSpectatingTarget(UUID spectatorId) {
        return Optional.ofNullable(spectators.get(spectatorId));
    }

    /**
     * 获取观众列表
     */
    public Set<UUID> getSpectators(UUID targetId) {
        Set<UUID> targetSpectators = spectatorsByTarget.get(targetId);
        return targetSpectators != null ? new HashSet<>(targetSpectators) : Set.of();
    }

    /**
     * 保存玩家状态
     */
    private void saveState(Player player) {
        SpectatorState state = new SpectatorState(
            player.getGameMode(),
            player.getLocation().clone(),
            player.isFlying(),
            player.getAllowFlight()
        );
        savedStates.put(player.getUniqueId(), state);
    }

    /**
     * 恢复玩家状态
     * D-108: 先 teleport 再 setGameMode，避免切回 SURVIVAL 后仍在原 SPECTATOR 高空位置导致摔落伤害
     */
    private void restoreState(Player player) {
        SpectatorState state = savedStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        // 先传送回原位置（此时仍是 SPECTATOR，可飞行不怕摔落）
        player.teleport(state.location());
        player.setFlying(state.flying());
        player.setAllowFlight(state.allowFlight());
        // 最后切换模式
        player.setGameMode(state.gameMode());
    }

    /**
     * 观战状态
     */
    private record SpectatorState(
        GameMode gameMode,
        org.bukkit.Location location,
        boolean flying,
        boolean allowFlight
    ) {}

    /**
     * D-108: 处理观战玩家退出 —— 恢复状态并清理记录。
     * 由 PlayerQuitEvent 监听器调用，确保玩家下线时自动 stopSpectating，
     * 防止 savedStates 残留导致重连后仍处 SPECTATOR 模式。
     */
    public void handlePlayerQuit(Player player) {
        UUID spectatorId = player.getUniqueId();
        if (spectators.containsKey(spectatorId)) {
            // 先恢复状态（若 savedStates 有记录）
            if (savedStates.containsKey(spectatorId)) {
                restoreState(player);
            }
            // 清理观战关系
            UUID targetId = spectators.remove(spectatorId);
            if (targetId != null) {
                Set<UUID> targetSpectators = spectatorsByTarget.get(targetId);
                if (targetSpectators != null) {
                    targetSpectators.remove(spectatorId);
                    if (targetSpectators.isEmpty()) {
                        spectatorsByTarget.remove(targetId);
                    }
                }
            }
        }
        // 清理残留状态（防止异常路径遗漏）
        savedStates.remove(spectatorId);
    }
}
