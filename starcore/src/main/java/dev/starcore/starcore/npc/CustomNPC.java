package dev.starcore.starcore.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自定义 NPC 数据类
 */
public class CustomNPC {
    private final UUID id;
    private String name;
    private EntityType entityType;
    private Location location;
    private boolean spawned;

    // NPC 配置
    private String displayName;
    private String skin; // 玩家类型的皮肤
    private boolean invulnerable;
    private boolean showInTabList;
    private float lookYaw;
    private float lookPitch;

    // 交互配置
    private List<String> dialogues;
    private List<String> commands; // 点击时执行的命令
    private String permission; // 交互所需权限

    // 行为配置
    private boolean lookAtPlayer;
    private double interactionRange;

    // 实体引用
    private transient org.bukkit.entity.Entity entity;

    public CustomNPC(UUID id, String name, EntityType entityType, Location location) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.location = location.clone();
        this.spawned = false;

        // 默认配置
        this.displayName = name;
        this.invulnerable = true;
        this.showInTabList = false;
        this.lookYaw = 0;
        this.lookPitch = 0;
        this.dialogues = new ArrayList<>();
        this.commands = new ArrayList<>();
        this.lookAtPlayer = true;
        this.interactionRange = 5.0;
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public EntityType getEntityType() { return entityType; }
    public Location getLocation() { return location.clone(); }
    public boolean isSpawned() { return spawned; }
    public String getDisplayName() { return displayName; }
    public String getSkin() { return skin; }
    public boolean isInvulnerable() { return invulnerable; }
    public boolean isShowInTabList() { return showInTabList; }
    public float getLookYaw() { return lookYaw; }
    public float getLookPitch() { return lookPitch; }
    public List<String> getDialogues() { return new ArrayList<>(dialogues); }
    public List<String> getCommands() { return new ArrayList<>(commands); }
    public String getPermission() { return permission; }
    public boolean isLookAtPlayer() { return lookAtPlayer; }
    public double getInteractionRange() { return interactionRange; }
    public org.bukkit.entity.Entity getEntity() { return entity; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }
    public void setLocation(Location location) { this.location = location.clone(); }
    public void setSpawned(boolean spawned) { this.spawned = spawned; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSkin(String skin) { this.skin = skin; }
    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    public void setShowInTabList(boolean showInTabList) { this.showInTabList = showInTabList; }
    public void setLookYaw(float lookYaw) { this.lookYaw = lookYaw; }
    public void setLookPitch(float lookPitch) { this.lookPitch = lookPitch; }
    public void setDialogues(List<String> dialogues) { this.dialogues = new ArrayList<>(dialogues); }
    public void setCommands(List<String> commands) { this.commands = new ArrayList<>(commands); }
    public void setPermission(String permission) { this.permission = permission; }
    public void setLookAtPlayer(boolean lookAtPlayer) { this.lookAtPlayer = lookAtPlayer; }
    public void setInteractionRange(double interactionRange) { this.interactionRange = interactionRange; }
    public void setEntity(org.bukkit.entity.Entity entity) { this.entity = entity; }

    // 工具方法
    public void addDialogue(String dialogue) {
        this.dialogues.add(dialogue);
    }

    public void removeDialogue(String dialogue) {
        this.dialogues.remove(dialogue);
    }

    public void clearDialogues() {
        this.dialogues.clear();
    }

    public void addCommand(String command) {
        this.commands.add(command);
    }

    public void removeCommand(String command) {
        this.commands.remove(command);
    }

    public void clearCommands() {
        this.commands.clear();
    }

    /**
     * 检查玩家是否在交互范围内
     */
    public boolean isInRange(Location playerLocation) {
        if (location.getWorld() != playerLocation.getWorld()) {
            return false;
        }
        return location.distance(playerLocation) <= interactionRange;
    }

    /**
     * 计算朝向玩家的 Yaw 角度
     */
    public float calculateYawTowards(Location targetLocation) {
        double dx = targetLocation.getX() - location.getX();
        double dz = targetLocation.getZ() - location.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
    }

    /**
     * 计算朝向玩家的 Pitch 角度
     */
    public float calculatePitchTowards(Location targetLocation) {
        double dx = targetLocation.getX() - location.getX();
        double dy = targetLocation.getY() - location.getY();
        double dz = targetLocation.getZ() - location.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, distance));
    }
}
