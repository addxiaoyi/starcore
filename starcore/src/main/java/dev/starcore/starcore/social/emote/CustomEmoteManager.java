package dev.starcore.starcore.social.emote;
import java.util.Optional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义动作管理器
 */
public class CustomEmoteManager {
    private final EmoteService emoteService;
    private final Map<String, CustomEmote> customEmotes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerCustomEmotes = new ConcurrentHashMap<>();

    public CustomEmoteManager(EmoteService emoteService) {
        this.emoteService = emoteService;
    }

    /**
     * 自定义动作数据
     */
    public static class CustomEmote {
        private final String id;
        private String name;
        private String description;
        private final UUID creatorId;
        private final long createdAt;
        private final String animationType;
        private final String animationData;
        private int durationTicks;
        private int cooldownSeconds;
        private final boolean isPublic;
        private final Map<String, Object> metadata;

        public CustomEmote(String id, String name, String description, UUID creatorId,
                          String animationType, String animationData, int durationTicks,
                          int cooldownSeconds, boolean isPublic) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.creatorId = creatorId;
            this.createdAt = System.currentTimeMillis();
            this.animationType = animationType;
            this.animationData = animationData;
            this.durationTicks = durationTicks;
            this.cooldownSeconds = cooldownSeconds;
            this.isPublic = isPublic;
            this.metadata = new HashMap<>();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public UUID getCreatorId() { return creatorId; }
        public long getCreatedAt() { return createdAt; }
        public String getAnimationType() { return animationType; }
        public String getAnimationData() { return animationData; }
        public int getDurationTicks() { return durationTicks; }
        public void setDurationTicks(int v) { this.durationTicks = v; }
        public int getCooldownSeconds() { return cooldownSeconds; }
        public void setCooldownSeconds(int v) { this.cooldownSeconds = v; }
        public boolean isPublic() { return isPublic; }
        public Map<String, Object> getMetadata() { return metadata; }

        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }

        /**
         * 转换为标准动作定义
         */
        public EmoteDefinition toEmoteDefinition() {
            return EmoteDefinition.builder()
                .id(id)
                .name(name)
                .description(description)
                .animationType(animationType)
                .animationData(animationData)
                .durationTicks(durationTicks)
                .cooldownSeconds(cooldownSeconds)
                .category("custom")
                .isGlobal(isPublic)
                .permission("starcore.emote.custom." + id)
                .build();
        }
    }

    /**
     * 创建自定义动作
     */
    public CustomEmote createCustomEmote(UUID creatorId, String name, String description,
                                        String animationType, String animationData,
                                        int durationTicks, int cooldownSeconds, boolean isPublic) {
        // D-055: 使用完整 UUID 防止 ID 冲突；id 与 emoteService 注册一致小写化
        String id = "custom_" + java.util.UUID.randomUUID().toString();

        CustomEmote customEmote = new CustomEmote(
            id, name, description, creatorId,
            animationType, animationData, durationTicks, cooldownSeconds, isPublic
        );

        customEmotes.put(id, customEmote);

        // 注册到主服务
        emoteService.registerEmote(customEmote.toEmoteDefinition());

        // 记录创建者
        playerCustomEmotes.computeIfAbsent(creatorId, k -> new HashSet<>()).add(id);

        return customEmote;
    }

    /**
     * 获取自定义动作
     */
    public Optional<CustomEmote> getCustomEmote(String id) {
        return Optional.ofNullable(customEmotes.get(id));
    }

    /**
     * 获取玩家的自定义动作
     */
    public List<CustomEmote> getPlayerCustomEmotes(UUID playerId) {
        return playerCustomEmotes.getOrDefault(playerId, Collections.emptySet())
            .stream()
            .map(customEmotes::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 获取所有公开自定义动作
     */
    public List<CustomEmote> getPublicCustomEmotes() {
        return customEmotes.values().stream()
            .filter(CustomEmote::isPublic)
            .toList();
    }

    /**
     * 获取所有自定义动作
     */
    public Collection<CustomEmote> getAllCustomEmotes() {
        return customEmotes.values();
    }

    /**
     * 删除自定义动作
     */
    public boolean deleteCustomEmote(String id, UUID deleterId) {
        CustomEmote emote = customEmotes.get(id);
        if (emote == null) return false;

        // 只有创建者或管理员可以删除
        boolean isCreator = emote.getCreatorId().equals(deleterId);
        org.bukkit.entity.Player adminDeleter = org.bukkit.Bukkit.getPlayer(deleterId);
        boolean isAdmin = adminDeleter != null && adminDeleter.hasPermission("starcore.emote.admin");
        if (!isCreator && !isAdmin) {
            return false;
        }

        customEmotes.remove(id);
        Set<String> creatorEmotes = playerCustomEmotes.get(emote.getCreatorId());
        if (creatorEmotes != null) creatorEmotes.remove(id);

        // D-052: 从 emoteService 注销，避免删除后玩家仍可执行
        emoteService.unregisterEmote(id);

        // 触发持久化
        notifyChanged();
        return true;
    }

    /**
     * 更新自定义动作。
     * D-053: 重新注册到 emoteService，并替换字段（CustomEmote 字段改为非 final）。
     */
    public boolean updateCustomEmote(String id, UUID updaterId,
                                    String newName, String newDescription,
                                    int newDurationTicks, int newCooldownSeconds) {
        CustomEmote emote = customEmotes.get(id);
        if (emote == null) return false;

        if (!emote.getCreatorId().equals(updaterId)) {
            return false;
        }

        if (newName != null) emote.setName(newName);
        if (newDescription != null) emote.setDescription(newDescription);
        emote.setDurationTicks(newDurationTicks);
        emote.setCooldownSeconds(newCooldownSeconds);

        // 重新注册到 emoteService 以反映新字段
        emoteService.registerEmote(emote.toEmoteDefinition());

        notifyChanged();
        return true;
    }

    /** 持久化钩子（D-054）。SocialModule 配置时调用 setOnChanged 注入实际落盘逻辑 */
    public interface ChangeListener { void onChanged(); }
    private ChangeListener changeListener;
    public void setOnChanged(ChangeListener l) { this.changeListener = l; }
    private void notifyChanged() { if (changeListener != null) changeListener.onChanged(); }

    /**
     * 检查玩家是否有自定义动作
     */
    public boolean hasCustomEmotes(UUID playerId) {
        return playerCustomEmotes.containsKey(playerId) &&
               !playerCustomEmotes.get(playerId).isEmpty();
    }

    /**
     * 获取自定义动作数量
     */
    public int getCustomEmoteCount() {
        return customEmotes.size();
    }

    /**
     * 序列化到配置
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> emotes = new ArrayList<>();

        for (CustomEmote emote : customEmotes.values()) {
            Map<String, Object> emoteData = new HashMap<>();
            emoteData.put("id", emote.getId());
            emoteData.put("name", emote.getName());
            emoteData.put("description", emote.getDescription());
            emoteData.put("creatorId", emote.getCreatorId().toString());
            emoteData.put("animationType", emote.getAnimationType());
            emoteData.put("animationData", emote.getAnimationData());
            emoteData.put("durationTicks", emote.getDurationTicks());
            emoteData.put("cooldownSeconds", emote.getCooldownSeconds());
            emoteData.put("isPublic", emote.isPublic());
            emotes.add(emoteData);
        }

        data.put("customEmotes", emotes);
        return data;
    }

    /**
     * 从配置反序列化
     */
    @SuppressWarnings("unchecked")
    public void deserialize(Map<String, Object> data) {
        List<Map<String, Object>> emotes = (List<Map<String, Object>>) data.get("customEmotes");
        if (emotes == null) return;

        for (Map<String, Object> emoteData : emotes) {
            try {
                String id = (String) emoteData.get("id");
                String name = (String) emoteData.get("name");
                String description = (String) emoteData.get("description");
                UUID creatorId = UUID.fromString((String) emoteData.get("creatorId"));
                String animationType = (String) emoteData.get("animationType");
                String animationData = (String) emoteData.get("animationData");
                // D-055: YAML 数字可能为 Long/Double，统一用 Number.intValue() 谨慎转换
                int durationTicks = asInt(emoteData.get("durationTicks"), 50);
                int cooldownSeconds = asInt(emoteData.get("cooldownSeconds"), 5);
                // D-056: YAML 中可能为 String "true"/"false"，宽容解析
                boolean isPublic = asBool(emoteData.get("isPublic"), false);

                CustomEmote emote = new CustomEmote(
                    id, name, description, creatorId,
                    animationType, animationData, durationTicks, cooldownSeconds, isPublic
                );

                customEmotes.put(id, emote);
                emoteService.registerEmote(emote.toEmoteDefinition());
                playerCustomEmotes.computeIfAbsent(creatorId, k -> new HashSet<>()).add(id);
            } catch (Exception ex) {
                // 跳过损坏条目，避免整批加载失败
            }
        }
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }
}
