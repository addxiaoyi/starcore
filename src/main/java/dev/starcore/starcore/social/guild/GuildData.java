package dev.starcore.starcore.social.guild;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.*;

/**
 * 公会数据（用于持久化）
 */
@SerializableAs("GuildData")
public final class GuildData implements ConfigurationSerializable {
    private UUID id;
    private String name;
    private String tag;
    private UUID leader;
    private Map<String, String> members; // UUID string -> role name
    private int level;
    private int experience;
    private double balance;
    private long createdTime;
    private String description;

    public GuildData() {
        this.members = new HashMap<>();
    }

    public static GuildData fromGuild(Guild guild) {
        GuildData data = new GuildData();
        data.id = guild.getId();
        data.name = guild.getName();
        data.tag = guild.getTag();
        data.leader = guild.getLeader();
        data.members = new HashMap<>();
        for (UUID memberId : guild.getMembers()) {
            data.members.put(memberId.toString(), guild.getMemberRole(memberId).name());
        }
        data.level = guild.getLevel();
        data.experience = guild.getExperience();
        data.balance = guild.getBalance();
        data.createdTime = guild.getCreatedTime();
        data.description = guild.getDescription();
        return data;
    }

    public Guild toGuild() {
        Guild guild = new Guild(id, name, tag, leader);
        guild.setDescription(description != null ? description : "");
        // Restore members (leader is already added in constructor)
        for (Map.Entry<String, String> entry : members.entrySet()) {
            UUID memberId = UUID.fromString(entry.getKey());
            if (!memberId.equals(leader)) {
                try {
                    GuildRole role = GuildRole.valueOf(entry.getValue());
                    guild.getMembersRaw().put(memberId, role);
                } catch (IllegalArgumentException e) {
                    // Default to MEMBER for unknown roles
                    guild.getMembersRaw().put(memberId, GuildRole.MEMBER);
                }
            }
        }
        // Restore level and experience
        guild.setLevelAndExperience(level, experience);
        // Restore balance
        guild.setBalance(balance);
        return guild;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id.toString());
        map.put("name", name);
        map.put("tag", tag);
        map.put("leader", leader.toString());
        map.put("members", members);
        map.put("level", level);
        map.put("experience", experience);
        map.put("balance", balance);
        map.put("createdTime", createdTime);
        map.put("description", description);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static GuildData deserialize(Map<String, Object> map) {
        GuildData data = new GuildData();
        data.id = UUID.fromString((String) map.get("id"));
        data.name = (String) map.get("name");
        data.tag = (String) map.get("tag");
        data.leader = UUID.fromString((String) map.get("leader"));
        data.members = new HashMap<>();
        Object membersObj = map.get("members");
        if (membersObj instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) membersObj).entrySet()) {
                data.members.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        data.level = (Integer) map.getOrDefault("level", 1);
        data.experience = (Integer) map.getOrDefault("experience", 0);
        data.balance = ((Number) map.getOrDefault("balance", 0.0)).doubleValue();
        data.createdTime = ((Number) map.getOrDefault("createdTime", System.currentTimeMillis())).longValue();
        data.description = (String) map.get("description");
        return data;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public Map<String, String> getMembers() { return members; }
    public void setMembers(Map<String, String> members) { this.members = members; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = experience; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
