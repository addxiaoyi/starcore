package dev.starcore.starcore.pvp.duel;

import org.bukkit.Location;
import java.util.UUID;

/**
 * 决斗竞技场
 */
public final class DuelArena {
    private final UUID id;
    private final String name;
    private final Location spawn1;     // 挑战者出生点
    private final Location spawn2;     // 对手出生点
    private final Location spectator;  // 观众位置

    private boolean available = true;  // 是否可用
    private UUID currentDuel;          // 当前决斗ID

    public DuelArena(UUID id, String name, Location spawn1, Location spawn2, Location spectator) {
        this.id = id;
        this.name = name;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
        this.spectator = spectator;
    }

    /**
     * 占用竞技场
     */
    public void occupy(UUID duelId) {
        this.available = false;
        this.currentDuel = duelId;
    }

    /**
     * 释放竞技场
     */
    public void release() {
        this.available = true;
        this.currentDuel = null;
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Location getSpawn1() { return spawn1.clone(); }
    public Location getSpawn2() { return spawn2.clone(); }
    public Location getSpectator() { return spectator != null ? spectator.clone() : null; }
    public Location getSpectatorSpawn() { return getSpectator(); }
    public boolean isAvailable() { return available; }
    public UUID getCurrentDuel() { return currentDuel; }
}
