package dev.starcore.starcore.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * 聊天格式化服务
 * 提供气泡聊天和关键词高亮功能
 */
public final class ChatFormatterService {

    private final Plugin plugin;
    private final File configFile;

    // 配置
    private boolean bubbleChatEnabled = true;
    private int bubbleChatDuration = 5; // 秒
    private int bubbleChatRadius = 50; // 方块
    private boolean keywordHighlightEnabled = true;
    private String chatFormat = "&7[{nation_tag}&7] &f{player}&f: {message}";
    private String nationTagFormat = "&6[{nation_name}]";

    // 关键词高亮配置
    // volatile + 不可变快照：异步聊天线程遍历、主线程 reload 整体替换，避免 CME
    private volatile List<KeywordHighlight> keywordHighlights = List.of();

    // 气泡聊天玩家数据
    private final Map<UUID, BubbleChatData> bubbleChats = new ConcurrentHashMap<>();

    // 玩家最近消息（用于检测重复）
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 500; // 500ms 防刷屏

    // 聊天频道
    private final Map<UUID, ChatChannel> playerChannels = new ConcurrentHashMap<>();

    public ChatFormatterService(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "chat-format.yml");
        loadConfig();
    }

    /**
     * 加载配置
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // 气泡聊天配置
        bubbleChatEnabled = config.getBoolean("bubble-chat.enabled", true);
        bubbleChatDuration = config.getInt("bubble-chat.duration-seconds", 5);
        bubbleChatRadius = config.getInt("bubble-chat.radius", 50);

        // 关键词高亮配置
        keywordHighlightEnabled = config.getBoolean("keyword-highlight.enabled", true);
        loadKeywordHighlights(config.getConfigurationSection("keyword-highlight.keywords"));

        // 聊天格式配置
        chatFormat = config.getString("chat-format.default",
            "&7[{nation_tag}&7] &f{player}&f: {message}");
        nationTagFormat = config.getString("chat-format.nation-tag",
            "&6[{nation_name}]");
    }

    /**
     * 保存默认配置
     */
    private void saveDefaultConfig() {
        YamlConfiguration config = new YamlConfiguration();

        // 气泡聊天默认配置
        config.set("bubble-chat.enabled", true);
        config.set("bubble-chat.duration-seconds", 5);
        config.set("bubble-chat.radius", 50);

        // 关键词高亮默认配置
        config.set("keyword-highlight.enabled", true);
        config.set("keyword-highlight.keywords.spam", Map.of(
            "pattern", "spam|广告|推广|qq|微信群",
            "color", "RED",
            "bold", true
        ));
        config.set("keyword-highlight.keywords.important", Map.of(
            "pattern", "重要|紧急|help|求助",
            "color", "YELLOW",
            "bold", true
        ));
        config.set("keyword-highlight.keywords.official", Map.of(
            "pattern", "官方|公告|server|服务器",
            "color", "AQUA",
            "bold", true
        ));
        config.set("keyword-highlight.keywords.emote", Map.of(
            "pattern", ":\\w+:",
            "color", "LIGHT_PURPLE",
            "bold", false
        ));

        // 聊天格式默认配置
        config.set("chat-format.default", "&7[{nation_tag}&7] &f{player}&f: {message}");
        config.set("chat-format.nation-tag", "&6[{nation_name}]");
        config.set("chat-format.no-nation", "&7[{nation_tag}]");
        config.set("chat-format.nation-tag-placeholder", "&c无国家");

        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存聊天配置失败: " + e.getMessage());
        }
    }

    /**
     * 加载关键词高亮配置
     */
    private void loadKeywordHighlights(ConfigurationSection section) {
        // 本地构建完整快照后一次性替换，避免异步聊天线程遍历到半清空的列表
        if (section == null) {
            keywordHighlights = List.of();
            return;
        }

        List<KeywordHighlight> loaded = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection keywordSection = section.getConfigurationSection(key);
            if (keywordSection == null) continue;

            String pattern = keywordSection.getString("pattern", "");
            String colorStr = keywordSection.getString("color", "WHITE");
            boolean bold = keywordSection.getBoolean("bold", false);

            try {
                NamedTextColor color = NamedTextColor.NAMES.value(colorStr.toLowerCase());
                Pattern regex = Pattern.compile("(?i)(" + pattern + ")");
                loaded.add(new KeywordHighlight(key, regex, color, bold));
            } catch (Exception e) {
                plugin.getLogger().warning("加载关键词配置失败: " + key);
            }
        }
        keywordHighlights = List.copyOf(loaded);
    }

    /**
     * 格式化聊天消息
     */
    public Component formatChatMessage(String playerName, String message,
            String nationName, String nationTag, UUID playerUuid) {

        // 检查消息冷却
        if (!checkMessageCooldown(playerUuid, message)) {
            return Component.empty();
        }

        // 构建国家标签
        String nationTagStr;
        if (nationName != null && !nationName.isEmpty()) {
            nationTagStr = nationTagFormat.replace("{nation_name}", nationName);
        } else {
            nationTagStr = configFile.exists() ?
                new YamlConfiguration().getString("chat-format.nation-tag-placeholder", "&c无国家") :
                "&c无国家";
        }

        // 替换占位符
        String format = chatFormat
            .replace("{nation_tag}", nationTagStr)
            .replace("{player}", playerName)
            .replace("{message}", message);

        // 转换为 Component
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(format);

        // 应用关键词高亮
        if (keywordHighlightEnabled) {
            component = applyKeywordHighlight(component, message);
        }

        // 添加悬停事件显示详情
        component = component.hoverEvent(HoverEvent.showText(
            Component.text("点击私聊 " + playerName)
                .color(NamedTextColor.GRAY)
        ));

        // 添加点击事件（私聊）
        component = component.clickEvent(ClickEvent.suggestCommand("/msg " + playerName + " "));

        return component;
    }

    /**
     * 格式化系统消息
     */
    public Component formatSystemMessage(String message) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize("&6[系统] &f" + message);
    }

    /**
     * 格式化公告消息
     */
    public Component formatAnnouncement(String message) {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize("&c&l[公告] &f" + message);
    }

    /**
     * 应用关键词高亮
     */
    private Component applyKeywordHighlight(Component component, String rawMessage) {
        String processedMessage = rawMessage;

        for (KeywordHighlight highlight : keywordHighlights) {
            Matcher matcher = highlight.pattern.matcher(processedMessage);
            if (matcher.find()) {
                // 创建替换文本
                final String matchedText = matcher.group();
                final NamedTextColor color = highlight.color;

                component = component.replaceText(TextReplacementConfig.builder()
                    .match(highlight.pattern)
                    .replacement(builder -> builder
                        .content(matchedText)
                        .color(color)
                        .build())
                    .build());
            }
        }

        return component;
    }

    /**
     * 检查消息冷却（防刷屏）
     */
    private boolean checkMessageCooldown(UUID playerUuid, String message) {
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerUuid);

        if (lastTime != null && (now - lastTime) < MESSAGE_COOLDOWN_MS) {
            // 检查是否重复消息
            String lastMsg = lastMessages.get(playerUuid);
            if (message.equals(lastMsg)) {
                return false; // 拒绝重复消息
            }
        }

        lastMessageTime.put(playerUuid, now);
        lastMessages.put(playerUuid, message);
        return true;
    }

    /**
     * 记录气泡聊天
     */
    public void recordBubbleChat(UUID playerUuid, String message) {
        if (!bubbleChatEnabled) return;

        bubbleChats.put(playerUuid, new BubbleChatData(
            playerUuid,
            message,
            System.currentTimeMillis() + (bubbleChatDuration * 1000L)
        ));
    }

    /**
     * 获取玩家的气泡聊天消息
     */
    public Optional<BubbleChatData> getBubbleChat(UUID playerUuid) {
        BubbleChatData data = bubbleChats.get(playerUuid);
        if (data == null) return Optional.empty();

        // 检查是否过期
        if (System.currentTimeMillis() > data.expireTime) {
            bubbleChats.remove(playerUuid);
            return Optional.empty();
        }

        return Optional.of(data);
    }

    /**
     * 清理过期气泡聊天
     */
    public void cleanupExpiredBubbles() {
        long now = System.currentTimeMillis();
        bubbleChats.entrySet().removeIf(entry -> entry.getValue().expireTime < now);
    }

    /**
     * 设置玩家聊天频道
     */
    public void setPlayerChannel(UUID playerUuid, ChatChannel channel) {
        playerChannels.put(playerUuid, channel);
    }

    /**
     * 获取玩家聊天频道
     */
    public ChatChannel getPlayerChannel(UUID playerUuid) {
        return playerChannels.getOrDefault(playerUuid, ChatChannel.GLOBAL);
    }

    /**
     * 移除玩家数据
     */
    public void removePlayer(UUID playerUuid) {
        bubbleChats.remove(playerUuid);
        playerChannels.remove(playerUuid);
        lastMessages.remove(playerUuid);
        lastMessageTime.remove(playerUuid);
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }

    // ========== Getter 方法 ==========

    public boolean isBubbleChatEnabled() {
        return bubbleChatEnabled;
    }

    public int getBubbleChatDuration() {
        return bubbleChatDuration;
    }

    public int getBubbleChatRadius() {
        return bubbleChatRadius;
    }

    public boolean isKeywordHighlightEnabled() {
        return keywordHighlightEnabled;
    }

    public List<KeywordHighlight> getKeywordHighlights() {
        return Collections.unmodifiableList(keywordHighlights);
    }

    // ========== 内部类 ==========

    /**
     * 关键词高亮配置
     */
    public record KeywordHighlight(
        String name,
        Pattern pattern,
        NamedTextColor color,
        boolean bold
    ) {}

    /**
     * 气泡聊天数据
     */
    public record BubbleChatData(
        UUID playerUuid,
        String message,
        long expireTime
    ) {}

    /**
     * 聊天频道枚举
     */
    public enum ChatChannel {
        GLOBAL("全局"),
        NATION("国家"),
        LOCAL("本地"),
        PARTY("小队"),
        GUILD("星座");

        private final String displayName;

        ChatChannel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 清理指定玩家的所有聊天状态数据
     */
    public void clearPlayerChatData(UUID playerId) {
        bubbleChats.remove(playerId);
        lastMessages.remove(playerId);
        lastMessageTime.remove(playerId);
        playerChannels.remove(playerId);
    }
}